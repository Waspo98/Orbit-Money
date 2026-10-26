package app.overbay.orbit.data.sync

import kotlinx.serialization.Serializable

/**
 * Metadata descriptor for a file in cloud storage.
 */
@Serializable
data class RemoteFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long = 0L
)

/**
 * Platform-independent abstraction for remote cloud storage (Google Drive appDataFolder).
 */
interface RemoteDriveClient {
    /**
     * Checks whether cloud sync is currently authenticated and ready to perform operations.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Returns the email of the authenticated cloud account, or null if not signed in.
     */
    suspend fun getConnectedEmail(): String?

    /**
     * Lists all files currently stored in the remote app data space.
     */
    suspend fun listFiles(): List<RemoteFileInfo>

    /**
     * Downloads the text content of a file by name from the app-data space in cloud storage.
     * Returns null if the file does not exist.
     */
    suspend fun downloadTextFile(fileName: String): String?

    /**
     * Creates or updates a text file by name in the app-data space in cloud storage.
     * Returns true if the upload was successful.
     */
    suspend fun uploadTextFile(fileName: String, content: String): Boolean

    /**
     * Deletes a file by name from the app-data space, if it exists.
     */
    suspend fun deleteFile(fileName: String): Boolean

    /**
     * Permanently deletes all files in the remote app data space.
     */
    suspend fun wipeAllFiles(): Boolean

    /**
     * Signs out / disconnects the current account.
     */
    suspend fun disconnect()
}

/**
 * An in-memory implementation of [RemoteDriveClient] used for offline mocking, unit testing, and fallbacks.
 */
class InMemoryRemoteDriveClient(
    private var email: String? = null,
    val storage: MutableMap<String, String> = mutableMapOf()
) : RemoteDriveClient {

    override suspend fun isAvailable(): Boolean = email != null

    override suspend fun getConnectedEmail(): String? = email

    fun setConnectedEmail(newEmail: String?) {
        email = newEmail
    }

    override suspend fun listFiles(): List<RemoteFileInfo> {
        return storage.map { (name, content) ->
            RemoteFileInfo(
                id = name,
                name = name,
                sizeBytes = content.length.toLong()
            )
        }
    }

    override suspend fun downloadTextFile(fileName: String): String? {
        return storage[fileName]
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
        storage[fileName] = content
        return true
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        return storage.remove(fileName) != null
    }

    override suspend fun wipeAllFiles(): Boolean {
        storage.clear()
        return true
    }

    override suspend fun disconnect() {
        email = null
    }
}

typealias LocalFileDriveClient = InMemoryRemoteDriveClient
