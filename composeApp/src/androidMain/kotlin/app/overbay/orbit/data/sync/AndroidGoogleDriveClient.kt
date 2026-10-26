package app.overbay.orbit.data.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Production Android implementation of [RemoteDriveClient] communicating with
 * Google Drive REST v3 API inside the private application data container (appDataFolder).
 */
class AndroidGoogleDriveClient(
    private val context: Context,
    private val account: Account
) : RemoteDriveClient {

    companion object {
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
        private const val OAUTH_SCOPE = "oauth2:$SCOPE_APPDATA $SCOPE_FILE"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val CONNECT_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 60000
    }

    private val fileIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun isAvailable(): Boolean {
        return getAuthToken() != null
    }

    override suspend fun getConnectedEmail(): String? {
        return account.name
    }

    override suspend fun listFiles(): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext emptyList()
        val allFiles = mutableListOf<RemoteFileInfo>()
        var pageToken: String? = null

        do {
            val pageParam = if (pageToken != null) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&fields=files(id,name,size),nextPageToken&pageSize=1000$pageParam"
            var conn = openConnection(url, "GET", token)
            var responseCode = conn.responseCode

            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                invalidateToken(token)
                token = getAuthToken() ?: return@withContext allFiles
                conn.disconnect()
                conn = openConnection(url, "GET", token)
                responseCode = conn.responseCode
            }

            if (responseCode in 200..299) {
                try {
                    val jsonStr = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val root = JSONObject(jsonStr)
                    val filesArr = root.optJSONArray("files")
                    if (filesArr != null) {
                        for (i in 0 until filesArr.length()) {
                            val item = filesArr.getJSONObject(i)
                            val fId = item.optString("id")
                            val fName = item.optString("name")
                            if (fName.isNotBlank() && fId.isNotBlank()) {
                                fileIdCache[fName] = fId
                            }
                            allFiles.add(
                                RemoteFileInfo(
                                    id = fId,
                                    name = fName,
                                    sizeBytes = item.optLong("size", 0L)
                                )
                            )
                        }
                    }
                    pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    pageToken = null
                } finally {
                    conn.disconnect()
                }
            } else {
                conn.disconnect()
                pageToken = null
            }
        } while (pageToken != null)

        allFiles
    }

    override suspend fun downloadTextFile(fileName: String): String? = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext null
        var fileId = fileIdCache[fileName] ?: findFileId(fileName, token) ?: return@withContext null

        var conn = openConnection("$DRIVE_API_BASE/files/$fileId?alt=media", "GET", token)
        var responseCode = conn.responseCode

        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            invalidateToken(token)
            token = getAuthToken() ?: return@withContext null
            conn.disconnect()
            conn = openConnection("$DRIVE_API_BASE/files/$fileId?alt=media", "GET", token)
            responseCode = conn.responseCode
        }

        if (responseCode in 200..299) {
            try {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } else {
            conn.disconnect()
            null
        }
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext false
        var fileId = fileIdCache[fileName] ?: findFileId(fileName, token)

        val success = if (fileId != null) {
            updateExistingFile(fileId, content, token)
        } else {
            createNewFile(fileName, content, token)
        }

        if (!success) {
            invalidateToken(token)
            val freshToken = getAuthToken() ?: return@withContext false
            fileId = findFileId(fileName, freshToken)
            if (fileId != null) {
                updateExistingFile(fileId, content, freshToken)
            } else {
                createNewFile(fileName, content, freshToken)
            }
        } else {
            true
        }
    }

    override suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val token = getAuthToken() ?: return@withContext false
        val fileId = fileIdCache[fileName] ?: findFileId(fileName, token) ?: return@withContext true
        val conn = openConnection("$DRIVE_API_BASE/files/$fileId", "DELETE", token)
        val success = try {
            val code = conn.responseCode
            code in 200..299 || code == HttpURLConnection.HTTP_NOT_FOUND
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
        if (success) {
            fileIdCache.remove(fileName)
        }
        success
    }

    override suspend fun wipeAllFiles(): Boolean = withContext(Dispatchers.IO) {
        val token = getAuthToken() ?: return@withContext false
        val allFiles = listFiles()
        var allDeleted = true
        for (f in allFiles) {
            if (f.id.isNotBlank()) {
                val conn = openConnection("$DRIVE_API_BASE/files/${f.id}", "DELETE", token)
                try {
                    val code = conn.responseCode
                    if (code !in 200..299 && code != HttpURLConnection.HTTP_NOT_FOUND) {
                        allDeleted = false
                    }
                } catch (_: Exception) {
                    allDeleted = false
                } finally {
                    conn.disconnect()
                }
            }
        }
        if (allDeleted) {
            fileIdCache.clear()
        }
        allDeleted
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val token = try {
            GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)
        } catch (_: Exception) {
            null
        }
        if (token != null) {
            invalidateToken(token)
        }
        fileIdCache.clear()
    }

    private suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun invalidateToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {}
    }

    private fun findFileId(fileName: String, token: String): String? {
        val safeName = fileName.replace("'", "\\'")
        val query = "'appDataFolder' in parents and name = '$safeName' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name,size,modifiedTime)&orderBy=modifiedTime desc"
        val conn = openConnection(url, "GET", token)
        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonStr)
                val files = root.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val id = files.getJSONObject(0).optString("id")
                    if (id.isNotBlank()) {
                        fileIdCache[fileName] = id
                    }
                    id.takeIf { it.isNotBlank() }
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun updateExistingFile(fileId: String, content: String, token: String): Boolean {
        val url = "$DRIVE_UPLOAD_BASE/files/$fileId?uploadType=media"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "OrbitMoney-Android")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
        }
        return try {
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun createNewFile(fileName: String, content: String, token: String): Boolean {
        val boundary = "====OrbitBoundary${System.currentTimeMillis()}===="
        val url = "$DRIVE_UPLOAD_BASE/files?uploadType=multipart"
        val conn = openConnection(url, "POST", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        return try {
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put("appDataFolder"))
            }.toString()

            val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
            val metadataBytes = metadataJson.toByteArray(StandardCharsets.UTF_8)

            val part1Header = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
            val part2Header = ("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
            val closing = ("\r\n--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8)

            val totalLength = part1Header.size + metadataBytes.size + part2Header.size + contentBytes.size + closing.size
            conn.setFixedLengthStreamingMode(totalLength)

            conn.outputStream.use { os ->
                os.write(part1Header)
                os.write(metadataBytes)
                os.write(part2Header)
                os.write(contentBytes)
                os.write(closing)
                os.flush()
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(urlString: String, method: String, token: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("User-Agent", "OrbitMoney-Android")
        return conn
    }
}
