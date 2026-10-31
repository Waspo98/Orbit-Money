package app.overbay.orbit.data.sync

import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Coordinates backing up and restoring Orbit workspace snapshots to/from private Google Drive appDataFolder.
 */
class CloudSyncManager(
    private var remoteClient: RemoteDriveClient? = null,
    initialEmail: String? = null
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _connectedEmail = MutableStateFlow<String?>(initialEmail)
    val connectedEmail: StateFlow<String?> = _connectedEmail.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus(isConnected = initialEmail != null, connectedEmail = initialEmail))
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    fun setRemoteClient(client: RemoteDriveClient?) {
        this.remoteClient = client
    }

    suspend fun connect(email: String = "user@example.com") {
        _connectedEmail.value = email
        _syncStatus.value = _syncStatus.value.copy(
            isConnected = true,
            connectedEmail = email,
            error = null
        )
    }

    suspend fun disconnect() {
        _isSyncing.value = true
        try {
            remoteClient?.disconnect()
            _connectedEmail.value = null
            _syncStatus.value = CloudSyncStatus(
                isConnected = false,
                connectedEmail = null,
                isSyncing = false
            )
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun backupSnapshot(snapshot: OrbitWorkspaceSnapshot): SyncResult = syncMutex.withLock {
        val email = _connectedEmail.value ?: remoteClient?.getConnectedEmail()
        if (email == null) {
            val failure = SyncResult.Error("Not connected to Google Drive")
            _lastSyncResult.value = failure
            _syncStatus.value = _syncStatus.value.copy(error = failure.message)
            return failure
        }
        _connectedEmail.value = email

        val client = remoteClient
        if (client == null) {
            val failure = SyncResult.Error("Cloud drive client is not configured")
            _lastSyncResult.value = failure
            _syncStatus.value = _syncStatus.value.copy(error = failure.message)
            return failure
        }

        _isSyncing.value = true
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)
        try {
            val backup = CloudBackupMetadata(
                version = 1,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                appVersion = "0.9.0-beta",
                snapshot = snapshot
            )
            val jsonContent = withContext(Dispatchers.Default) {
                json.encodeToString(backup)
            }
            val success = client.uploadTextFile(REMOTE_BACKUP_FILENAME, jsonContent)
            if (success) {
                val result = SyncResult.Success("Backup uploaded to Google Drive")
                _lastSyncResult.value = result
                _syncStatus.value = _syncStatus.value.copy(
                    isConnected = true,
                    connectedEmail = email,
                    lastSyncTimestamp = backup.timestamp,
                    isSyncing = false,
                    error = null
                )
                result
            } else {
                val err = SyncResult.Error("Failed to upload workspace backup to Google Drive")
                _lastSyncResult.value = err
                _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = err.message)
                err
            }
        } catch (e: Exception) {
            val err = SyncResult.Error("Cloud backup failed: ${e.message ?: "Unknown error"}", e)
            _lastSyncResult.value = err
            _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = err.message)
            err
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun restoreSnapshot(): OrbitWorkspaceSnapshot? = syncMutex.withLock {
        val email = _connectedEmail.value ?: remoteClient?.getConnectedEmail()
        if (email == null) {
            _lastSyncResult.value = SyncResult.Error("Not connected to Google Drive")
            return null
        }
        _connectedEmail.value = email

        val client = remoteClient ?: return null

        _isSyncing.value = true
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)
        try {
            val text = client.downloadTextFile(REMOTE_BACKUP_FILENAME) ?: run {
                _lastSyncResult.value = SyncResult.Error("No backup file found in Google Drive")
                return null
            }
            // Try parsing as CloudBackupMetadata, fallback to raw OrbitWorkspaceSnapshot
            val snapshot = try {
                val metadata = json.decodeFromString<CloudBackupMetadata>(text)
                metadata.snapshot
            } catch (_: Exception) {
                json.decodeFromString<OrbitWorkspaceSnapshot>(text)
            }
            _lastSyncResult.value = SyncResult.Success("Backup restored from Google Drive")
            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTimestamp = Clock.System.now().toEpochMilliseconds(),
                isSyncing = false,
                error = null
            )
            snapshot
        } catch (e: Exception) {
            _lastSyncResult.value = SyncResult.Error("Restore failed: ${e.message ?: "Corrupt backup"}", e)
            _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = e.message)
            null
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun wipeRemoteData(): SyncResult = syncMutex.withLock {
        val client = remoteClient ?: run {
            val err = SyncResult.Error("Cloud client unavailable")
            _lastSyncResult.value = err
            return err
        }
        _isSyncing.value = true
        try {
            val success = client.wipeAllFiles()
            val result = if (success) {
                SyncResult.Success("Google Drive workspace backup wiped")
            } else {
                SyncResult.Error("Failed to wipe cloud backup")
            }
            _lastSyncResult.value = result
            result
        } catch (e: Exception) {
            val err = SyncResult.Error("Wipe failed: ${e.message}", e)
            _lastSyncResult.value = err
            err
        } finally {
            _isSyncing.value = false
        }
    }
}
