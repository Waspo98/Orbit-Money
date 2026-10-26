package app.overbay.orbit.data.sync

import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import kotlinx.serialization.Serializable

const val REMOTE_BACKUP_FILENAME = "orbit_workspace_backup.json"

@Serializable
data class CloudBackupMetadata(
    val version: Int = 1,
    val timestamp: Long = 0L,
    val appVersion: String = "0.9.0-beta",
    val snapshot: OrbitWorkspaceSnapshot
)

@Serializable
data class CloudSyncStatus(
    val isConnected: Boolean = false,
    val connectedEmail: String? = null,
    val lastSyncTimestamp: Long? = null,
    val isSyncing: Boolean = false,
    val error: String? = null
)
