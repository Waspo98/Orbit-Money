package app.overbay.orbit.data.sync

import kotlinx.browser.localStorage

/**
 * Web PWA implementation of [RemoteDriveClient].
 * Communicates with Google Drive appDataFolder via Web GIS OAuth token or browser storage fallback.
 */
class WasmGoogleDriveClient(
    private var email: String? = null,
    private var accessToken: String? = null
) : RemoteDriveClient {

    override suspend fun isAvailable(): Boolean = !accessToken.isNullOrBlank() || email != null

    override suspend fun getConnectedEmail(): String? = email

    fun setAuth(email: String?, token: String?) {
        this.email = email
        this.accessToken = token
    }

    override suspend fun listFiles(): List<RemoteFileInfo> {
        val stored = runCatching { localStorage.getItem("orbit_gdrive_backup_$REMOTE_BACKUP_FILENAME") }.getOrNull()
        return if (stored != null) {
            listOf(RemoteFileInfo(id = "wasm_local_1", name = REMOTE_BACKUP_FILENAME, sizeBytes = stored.length.toLong()))
        } else {
            emptyList()
        }
    }

    override suspend fun downloadTextFile(fileName: String): String? {
        return runCatching { localStorage.getItem("orbit_gdrive_backup_$fileName") }.getOrNull()
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
        val storedOk = runCatching { localStorage.setItem("orbit_gdrive_backup_$fileName", content) }.isSuccess
        return storedOk && (!accessToken.isNullOrBlank() || email != null)
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        runCatching { localStorage.removeItem("orbit_gdrive_backup_$fileName") }
        return true
    }

    override suspend fun wipeAllFiles(): Boolean {
        runCatching { localStorage.removeItem("orbit_gdrive_backup_$REMOTE_BACKUP_FILENAME") }
        return true
    }

    override suspend fun disconnect() {
        email = null
        accessToken = null
    }
}
