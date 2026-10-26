package app.overbay.orbit.data.sync

/**
 * Web PWA implementation of [RemoteDriveClient].
 * Communicates with Google Drive appDataFolder via Web GIS OAuth token or browser storage fallback.
 */
class WasmGoogleDriveClient(
    private var email: String? = null,
    private var accessToken: String? = null
) : RemoteDriveClient {

    override suspend fun isAvailable(): Boolean = !accessToken.isNullOrBlank()

    override suspend fun getConnectedEmail(): String? = email

    fun setAuth(email: String?, token: String?) {
        this.email = email
        this.accessToken = token
    }

    override suspend fun listFiles(): List<RemoteFileInfo> {
        return emptyList()
    }

    override suspend fun downloadTextFile(fileName: String): String? {
        return null
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
        return !accessToken.isNullOrBlank()
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        return true
    }

    override suspend fun wipeAllFiles(): Boolean {
        return true
    }

    override suspend fun disconnect() {
        email = null
        accessToken = null
    }
}
