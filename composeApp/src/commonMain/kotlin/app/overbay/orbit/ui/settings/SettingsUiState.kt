package app.overbay.orbit.ui.settings

import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BackupPreviewStats
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.HouseholdUserAccess
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.data.model.SimpleFinStatusInfo
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
    data class BackupRestorePaste(
        val draftJson: String = "",
        val errorMessage: String? = null
    ) : SettingsDialogState
    data class RestorePreview(
        val rawJson: String,
        val stats: BackupPreviewStats,
        val isRestoring: Boolean = false,
        val errorMessage: String? = null
    ) : SettingsDialogState
    data class MigrateWorkspace(
        val serverUrl: String,
        val isMigrating: Boolean = false,
        val message: String? = null
    ) : SettingsDialogState
    data class PartnerInvite(
        val isSending: Boolean = false,
        val errorMessage: String? = null
    ) : SettingsDialogState
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
    data class CustomThemePicker(val initialHex: String) : SettingsDialogState
}

data class SettingsUiState(
    val preferences: OrbitPreferences = OrbitPreferences(),
    val storageMode: StorageMode = StorageMode.LOCAL,
    val syncStatus: SyncResult = SyncResult.NoOp,
    val isSyncing: Boolean = false,
    val isAuthenticated: Boolean = true,
    val currentUser: String? = null,
    val authMethod: String? = null,
    val activeSection: SettingsSection? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val statusMessage: String? = null,
    val accounts: List<Account> = emptyList(),
    val pushConfig: PushNotificationConfigInfo = PushNotificationConfigInfo(),
    val isPushTesting: Boolean = false,
    val simpleFinStatus: SimpleFinStatusInfo = SimpleFinStatusInfo(),
    val simpleFinLogs: List<SimpleFinSyncLogEntry> = emptyList(),
    val isSimpleFinLogsExpanded: Boolean = false,
    val isLoadingSimpleFinLogs: Boolean = false,
    val isConnectingSimpleFin: Boolean = false,
    val serverUrlDraft: String = "",
    val showReauthOptions: Boolean = false,
    val showServerPasswordLogin: Boolean = false,
    val serverUsernameDraft: String = "",
    val serverPasswordDraft: String = "",
    val simpleFinTokenDraft: String = "",
    val simpleFinCutoverDateDraft: String? = null,
    val showSimpleFinReconnectEditor: Boolean = false,
    val householdSharing: HouseholdSharingState? = null,
    val isLoadingSharing: Boolean = false,
    val activeDialog: SettingsDialogState = SettingsDialogState.None
)
