package app.overbay.orbit.ui.settings

import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BackupPreviewStats
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.HouseholdUserAccess
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.data.model.StorageMode

enum class NotificationScheduleType {
    WEEKLY_SNAPSHOT,
    INCOME,
    ACCOUNT_SNAPSHOTS
}

sealed interface SettingsDialogState {
    data object None : SettingsDialogState
    data class BackupExport(val jsonText: String) : SettingsDialogState
    data class BackupRestorePaste(val draftJson: String = "") : SettingsDialogState
    data class RestorePreview(val rawJson: String, val stats: BackupPreviewStats) : SettingsDialogState
    data class MigrateWorkspace(
        val serverUrl: String,
        val isMigrating: Boolean = false,
        val message: String? = null
    ) : SettingsDialogState
    data object PartnerInvite : SettingsDialogState
    data class ConfirmRemovePartnerUser(val user: HouseholdUserAccess) : SettingsDialogState
    data class ConfirmRevokePartnerShare(val share: HouseholdShare) : SettingsDialogState
    data class GoogleDriveClientId(val draftClientId: String) : SettingsDialogState
    data object ConfirmRestoreGoogleDrive : SettingsDialogState
    data object ConfirmDisconnectGoogleDrive : SettingsDialogState
    data object ConfirmDisconnectSimpleFin : SettingsDialogState
    data object ConfirmReapplyRules : SettingsDialogState
    data object ConfirmLoadSampleData : SettingsDialogState
    data object ConfirmResetWorkspace : SettingsDialogState
    data object ConfirmSignOut : SettingsDialogState
    data object ConfirmSwitchAccount : SettingsDialogState
    data object SupportDevelopment : SettingsDialogState
    data object TransactionQuickActions : SettingsDialogState
    data class NotificationSchedule(val type: NotificationScheduleType) : SettingsDialogState
}

data class SettingsUiState(
    val preferences: OrbitPreferences = OrbitPreferences(),
    val storageMode: StorageMode = StorageMode.LOCAL,
    val syncStatus: SyncResult = SyncResult.NoOp,
    val isSyncing: Boolean = false,
    val activeSection: SettingsSection? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val statusMessage: String? = null,
    val accounts: List<Account> = emptyList(),
    val pushConfig: PushNotificationConfigInfo = PushNotificationConfigInfo(),
    val isPushTesting: Boolean = false,
    val simpleFinLogs: List<SimpleFinSyncLogEntry> = emptyList(),
    val isSimpleFinLogsExpanded: Boolean = false,
    val isLoadingSimpleFinLogs: Boolean = false,
    val isConnectingSimpleFin: Boolean = false,
    val householdSharing: HouseholdSharingState? = null,
    val isLoadingSharing: Boolean = false,
    val activeDialog: SettingsDialogState = SettingsDialogState.None
)
