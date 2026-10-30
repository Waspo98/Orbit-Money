package app.overbay.orbit.ui.settings

import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.BackupPreviewStats
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.data.model.StorageMode

data class SettingsUiState(
    val preferences: OrbitPreferences = OrbitPreferences(),
    val storageMode: StorageMode = StorageMode.LOCAL,
    val syncStatus: SyncResult = SyncResult.NoOp,
    val isSyncing: Boolean = false,
    val activeSubpage: String? = null, // null = Hub, "preferences", "data"
    val statusMessage: String? = null,

    // Push Notifications
    val pushConfig: PushNotificationConfigInfo = PushNotificationConfigInfo(),
    val isPushTesting: Boolean = false,

    // SimpleFIN
    val simpleFinLogs: List<SimpleFinSyncLogEntry> = emptyList(),
    val isSimpleFinLogsExpanded: Boolean = false,
    val isLoadingSimpleFinLogs: Boolean = false,

    // Household Partner Sharing
    val householdSharing: HouseholdSharingState? = null,
    val isLoadingSharing: Boolean = false,
    val showInvitePartnerDialog: Boolean = false,

    // Backup & Restore Preview
    val backupExportJson: String? = null,
    val showBackupExportDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val restoreJsonText: String = "",
    val restorePreviewStats: BackupPreviewStats? = null,
    val showRestorePreviewDialog: Boolean = false,

    // Migration
    val showMigrateDialog: Boolean = false,
    val migrateServerUrl: String = "",
    val isMigrating: Boolean = false,
    val migrateMessage: String? = null,

    // Google Drive Client ID Dialog
    val showGoogleDriveClientIdDialog: Boolean = false,
    val googleDriveClientIdDraft: String = "",

    // System Confirmations
    val showResetConfirmation: Boolean = false,
    val showDonateDialog: Boolean = false
)
