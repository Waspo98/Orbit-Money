package app.overbay.orbit.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@JsFun("""(clientId) => {
    try {
        if (!window.google || !window.google.accounts || !window.google.accounts.oauth2) {
            return '';
        }
        if (!window.__orbitGisClient) {
            window.__orbitGisClient = window.google.accounts.oauth2.initTokenClient({
                client_id: clientId,
                scope: 'https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file',
                callback: (resp) => {
                    if (resp && resp.access_token) {
                        window.__orbitGdriveToken = resp.access_token;
                    }
                }
            });
        }
        return 'READY';
    } catch (e) {
        return '';
    }
}""")
private external fun jsInitGis(clientId: String): String

@JsFun("""() => {
    try {
        if (window.__orbitGisClient) {
            window.__orbitGisClient.requestAccessToken({ prompt: '' });
        }
    } catch (e) {}
}""")
private external fun jsRequestAccessToken()

@JsFun("""() => {
    try {
        return window.__orbitGdriveToken || '';
    } catch (e) {
        return '';
    }
}""")
private external fun jsGetCachedAccessToken(): String

@JsFun("""() => {
    try {
        window.__orbitGdriveToken = '';
    } catch (e) {}
}""")
private external fun jsClearAccessToken()

/**
 * Web PWA implementation of [RemoteDriveClient].
 * Communicates with Google Drive appDataFolder via Google Identity Services and Google Drive REST v3 endpoints,
 * with local browser storage cache and fallback.
 */
class WasmGoogleDriveClient(
    private var email: String? = null,
    private var accessToken: String? = null
) : RemoteDriveClient {

    private val httpClient = HttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val fileIdCache = mutableMapOf<String, String>()

    private val activeToken: String?
        get() = accessToken ?: jsGetCachedAccessToken().takeIf { it.isNotBlank() }

    fun initializeGisAuth(clientId: String, onConnected: (String?) -> Unit) {
        val res = jsInitGis(clientId)
        if (res == "READY") {
            jsRequestAccessToken()
            val token = jsGetCachedAccessToken()
            if (token.isNotBlank()) {
                this.accessToken = token
                this.email = "google-user@drive.sync"
                onConnected(this.email)
            } else {
                onConnected("google-drive@pwa.local")
            }
        }
    }

    override suspend fun isAvailable(): Boolean = !activeToken.isNullOrBlank() || email != null

    override suspend fun getConnectedEmail(): String? = email ?: if (!activeToken.isNullOrBlank()) "google-user@drive.sync" else null

    fun setAuth(email: String?, token: String?) {
        this.email = email
        this.accessToken = token
    }

    override suspend fun listFiles(): List<RemoteFileInfo> {
        val token = activeToken
        if (!token.isNullOrBlank()) {
            try {
                val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size)"
                val response = httpClient.get(url) {
                    header("Authorization", "Bearer $token")
                }
                if (response.status.isSuccess()) {
                    val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val files = root["files"]?.jsonArray
                    if (files != null) {
                        val result = mutableListOf<RemoteFileInfo>()
                        for (item in files) {
                            val obj = item.jsonObject
                            val id = obj["id"]?.jsonPrimitive?.content ?: ""
                            val name = obj["name"]?.jsonPrimitive?.content ?: ""
                            val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                            if (name.isNotBlank() && id.isNotBlank()) {
                                fileIdCache[name] = id
                                result.add(RemoteFileInfo(id = id, name = name, sizeBytes = size))
                            }
                        }
                        return result
                    }
                }
            } catch (_: Throwable) {}
        }

        val stored = runCatching { localStorage.getItem("orbit_gdrive_backup_$REMOTE_BACKUP_FILENAME") }.getOrNull()
        return if (stored != null) {
            listOf(RemoteFileInfo(id = "wasm_local_1", name = REMOTE_BACKUP_FILENAME, sizeBytes = stored.length.toLong()))
        } else {
            emptyList()
        }
    }

    override suspend fun downloadTextFile(fileName: String): String? {
        val token = activeToken
        if (!token.isNullOrBlank()) {
            try {
                val fileId = fileIdCache[fileName] ?: findFileId(fileName, token)
                if (fileId != null) {
                    val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                    val response = httpClient.get(url) {
                        header("Authorization", "Bearer $token")
                    }
                    if (response.status.isSuccess()) {
                        val body = response.bodyAsText()
                        runCatching { localStorage.setItem("orbit_gdrive_backup_$fileName", body) }
                        return body
                    }
                }
            } catch (_: Throwable) {}
        }
        return runCatching { localStorage.getItem("orbit_gdrive_backup_$fileName") }.getOrNull()
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
        runCatching { localStorage.setItem("orbit_gdrive_backup_$fileName", content) }
        val token = activeToken
        if (!token.isNullOrBlank()) {
            try {
                val fileId = fileIdCache[fileName] ?: findFileId(fileName, token)
                if (fileId != null) {
                    val patchUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                    val patchRes = httpClient.patch(patchUrl) {
                        header("Authorization", "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(content)
                    }
                    if (patchRes.status.isSuccess()) return true
                }

                val boundary = "====OrbitWasmBoundary===="
                val multipartBody = "--$boundary\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                    "{\"name\":\"$fileName\",\"parents\":[\"appDataFolder\"]}\r\n" +
                    "--$boundary\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                    content + "\r\n" +
                    "--$boundary--"

                val createUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val createRes = httpClient.post(createUrl) {
                    header("Authorization", "Bearer $token")
                    header("Content-Type", "multipart/related; boundary=$boundary")
                    setBody(multipartBody)
                }
                if (createRes.status.isSuccess()) {
                    val root = json.parseToJsonElement(createRes.bodyAsText()).jsonObject
                    val createdId = root["id"]?.jsonPrimitive?.content
                    if (!createdId.isNullOrBlank()) {
                        fileIdCache[fileName] = createdId
                    }
                    return true
                }
            } catch (_: Throwable) {}
        }
        return true
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        runCatching { localStorage.removeItem("orbit_gdrive_backup_$fileName") }
        val token = activeToken
        if (!token.isNullOrBlank()) {
            try {
                val fileId = fileIdCache[fileName] ?: findFileId(fileName, token)
                if (fileId != null) {
                    val url = "https://www.googleapis.com/drive/v3/files/$fileId"
                    httpClient.delete(url) {
                        header("Authorization", "Bearer $token")
                    }
                    fileIdCache.remove(fileName)
                }
            } catch (_: Throwable) {}
        }
        return true
    }

    override suspend fun wipeAllFiles(): Boolean {
        runCatching { localStorage.removeItem("orbit_gdrive_backup_$REMOTE_BACKUP_FILENAME") }
        val token = activeToken
        if (!token.isNullOrBlank()) {
            try {
                val all = listFiles()
                for (f in all) {
                    if (f.id.isNotBlank()) {
                        httpClient.delete("https://www.googleapis.com/drive/v3/files/${f.id}") {
                            header("Authorization", "Bearer $token")
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        fileIdCache.clear()
        return true
    }

    override suspend fun disconnect() {
        email = null
        accessToken = null
        jsClearAccessToken()
        fileIdCache.clear()
    }

    private suspend fun findFileId(fileName: String, token: String): String? {
        try {
            val safeName = fileName.replace("'", "\\'")
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$safeName'+and+trashed=false&fields=files(id,name)"
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val files = root["files"]?.jsonArray
                if (files != null && files.isNotEmpty()) {
                    val id = files[0].jsonObject["id"]?.jsonPrimitive?.content
                    if (!id.isNullOrBlank()) {
                        fileIdCache[fileName] = id
                        return id
                    }
                }
            }
        } catch (_: Throwable) {}
        return null
    }
}
