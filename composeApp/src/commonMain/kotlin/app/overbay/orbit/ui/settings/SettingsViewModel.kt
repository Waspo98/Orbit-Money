package app.overbay.orbit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.fetchPushNotificationConfig
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.sendTestNotification
import app.overbay.orbit.data.setMhaEnabled
import app.overbay.orbit.data.updateNotificationPreferences
import app.overbay.orbit.ui.settings.controllers.SettingsBackupController
import app.overbay.orbit.ui.settings.controllers.SettingsSyncSharingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class SettingsViewModel(
    private val repository: OrbitRepository,
    customScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = customScope ?: (viewModelScope + Dispatchers.Default)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val syncSharingController = SettingsSyncSharingController(repository, scope, _uiState)
    private val backupController = SettingsBackupController(repository, scope, _uiState)

    init {
        scope.launch {
            repository.preferences.collect { prefs ->
                _uiState.update { current ->
                    val previousPrefs = current.preferences
                    val nextServerUrl = if (current.serverUrlDraft == previousPrefs.selfHostedServerUrl) {
                        prefs.selfHostedServerUrl
                    } else {
                        current.serverUrlDraft
                    }
                    val nextSimpleFinToken = if (current.simpleFinTokenDraft == previousPrefs.simpleFinToken.orEmpty()) {
                        prefs.simpleFinToken.orEmpty()
                    } else {
                        current.simpleFinTokenDraft
                    }
                    val nextCutover = if (current.simpleFinCutoverDateDraft == previousPrefs.simpleFinCutoverDate) {
                        prefs.simpleFinCutoverDate
                    } else {
                        current.simpleFinCutoverDateDraft
                    }
                    current.copy(
                        preferences = prefs,
                        serverUrlDraft = nextServerUrl,
                        simpleFinTokenDraft = nextSimpleFinToken,
                        simpleFinCutoverDateDraft = nextCutover
                    )
                }
            }
        }
        scope.launch {
            repository.storageMode.collect { mode ->
                _uiState.update { it.copy(storageMode = mode) }
            }
        }
        scope.launch {
            repository.syncStatus.collect { status ->
                _uiState.update { it.copy(syncStatus = status, isSyncing = false) }
            }
        }
        scope.launch {
            repository.accounts.collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }

    fun openSection(section: SettingsSection?) {
        _uiState.update { it.copy(activeSection = section, isSearchActive = false) }
        when (section) {
            SettingsSection.NOTIFICATIONS -> loadPushConfig()
            SettingsSection.BANK_FEEDS -> loadSimpleFinLogs()
            SettingsSection.SYNC_AND_SHARING -> loadHouseholdSharing()
            else -> Unit
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun showDialog(dialog: SettingsDialogState) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = SettingsDialogState.None) }
    }

    // Appearance, Workspace & Security Preferences
    fun setThemePreset(preset: OrbitThemePreset) {
        val updated = _uiState.value.preferences.copy(themePreset = preset)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setThemeMode(mode: OrbitThemeMode) {
        val updated = _uiState.value.preferences.copy(themeMode = mode)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setAmoledDark(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(amoledDarkMode = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setDynamicColor(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(dynamicColor = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setBiometricLock(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(biometricLockEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setBiometricTimeoutSeconds(seconds: Int) {
        val updated = _uiState.value.preferences.copy(biometricTimeoutSeconds = seconds.coerceAtLeast(0))
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(hapticsEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setMhaTrackerEnabled(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(mhaTrackerEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.setMhaEnabled(enabled) }
    }

    fun setCardRewardsToolsEnabled(enabled: Boolean?) {
        val updated = _uiState.value.preferences.copy(cardRewardsToolsEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun toggleRouteHidden(routeName: String) {
        val current = _uiState.value.preferences.hiddenRouteNames
        val updatedList = if (current.contains(routeName)) current - routeName else current + routeName
        val updated = _uiState.value.preferences.copy(hiddenRouteNames = updatedList)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun moveRouteOrder(fromIndex: Int, toIndex: Int, currentList: List<String>) {
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        val mutable = currentList.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        val updated = _uiState.value.preferences.copy(moreRouteOrder = mutable)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun saveTransactionQuickActions(pinnedIds: List<String>, orderIds: List<String>) {
        val updated = _uiState.value.preferences.copy(
            transactionPinnedActions = pinnedIds,
            transactionActionOrder = orderIds
        )
        _uiState.update {
            it.copy(
                preferences = updated,
                activeDialog = SettingsDialogState.None
            )
        }
        scope.launch { repository.updatePreferences(updated) }
    }

    // Push Notifications
    fun loadPushConfig() {
        scope.launch {
            val res = repository.fetchPushNotificationConfig()
            res.getOrNull()?.let { config ->
                _uiState.update { it.copy(pushConfig = config) }
            }
        }
    }

    fun sendTestPush() {
        _uiState.update { it.copy(isPushTesting = true) }
        scope.launch {
            val res = repository.sendTestNotification()
            _uiState.update {
                it.copy(
                    isPushTesting = false,
                    statusMessage = if (res.isSuccess) "Test notification sent" else "Failed to send test push"
                )
            }
        }
    }

    fun updateNotificationPreferences(config: NotificationPreferencesConfig) {
        val updated = _uiState.value.preferences.copy(notificationPreferences = config)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updateNotificationPreferences(config) }
    }

    // Sync, SimpleFIN & Household Sharing Delegations
    fun setStorageMode(mode: StorageMode) = syncSharingController.setStorageMode(mode)
    fun setServerUrl(url: String) = syncSharingController.setServerUrl(url)
    fun updateServerUrlDraft(url: String) = syncSharingController.updateServerUrlDraft(url)
    fun setShowServerPasswordLogin(show: Boolean) = syncSharingController.setShowServerPasswordLogin(show)
    fun updateServerUsernameDraft(username: String) = syncSharingController.updateServerUsernameDraft(username)
    fun updateServerPasswordDraft(password: String) = syncSharingController.updateServerPasswordDraft(password)
    fun updateSimpleFinTokenDraft(token: String) = syncSharingController.updateSimpleFinTokenDraft(token)
    fun updateSimpleFinCutoverDateDraft(date: String?) = syncSharingController.updateSimpleFinCutoverDateDraft(date)
    fun setShowSimpleFinReconnectEditor(show: Boolean) = syncSharingController.setShowSimpleFinReconnectEditor(show)
    fun loginToSelfHostedServer(username: String, password: String) =
        syncSharingController.loginToSelfHostedServer(username, password)
    fun triggerSync() = syncSharingController.triggerSync()
    fun logout() = syncSharingController.logout()
    fun setupSimpleFinBridge(token: String, cutoverDate: String?) =
        syncSharingController.setupSimpleFinBridge(token, cutoverDate)
    fun disconnectSimpleFin() = syncSharingController.disconnectSimpleFin()
    fun loadSimpleFinLogs() = syncSharingController.loadSimpleFinLogs()
    fun toggleSimpleFinLogsExpanded() = syncSharingController.toggleSimpleFinLogsExpanded()
    fun unmuteSimpleFinAccount(label: String) = syncSharingController.unmuteSimpleFinAccount(label)
    fun loadHouseholdSharing() = syncSharingController.loadHouseholdSharing()
    fun inviteHouseholdPartner(email: String, role: String, accessLevel: String) =
        syncSharingController.inviteHouseholdPartner(email, role, accessLevel)
    fun updatePartnerAccess(shareId: Long, accessLevel: String) =
        syncSharingController.updatePartnerAccess(shareId, accessLevel)
    fun updateHouseholdMemberAccess(userId: Long, accessLevel: String) =
        syncSharingController.updateHouseholdMemberAccess(userId, accessLevel)
    fun revokePartnerShare(shareId: Long) = syncSharingController.revokePartnerShare(shareId)
    fun removeHouseholdUser(userId: Long) = syncSharingController.removeHouseholdUser(userId)

    // Backup, Exports, Google Drive, Migration & Maintenance Delegations
    fun reapplyAllRules() = backupController.reapplyAllRules()
    suspend fun exportCsv(): String = backupController.exportCsv()
    suspend fun exportBudgetingJson(): String = backupController.exportBudgetingJson()
    fun exportBackupToFile(onReady: (String) -> Unit) = backupController.exportBackupToFile(onReady)
    fun openBackupExportJsonDialog(): String = backupController.openBackupExportJsonDialog()
    fun openRestorePasteDialog() = backupController.openRestorePasteDialog()
    fun updateRestorePasteDraft(text: String) = backupController.updateRestorePasteDraft(text)
    fun previewBackupForRestore(rawJson: String) = backupController.previewBackupForRestore(rawJson)
    fun confirmRestoreBackup() = backupController.confirmRestoreBackup()
    fun setGoogleDriveSync(enabled: Boolean) = backupController.setGoogleDriveSync(enabled)
    fun openGoogleDriveClientIdDialog() = backupController.openGoogleDriveClientIdDialog()
    fun saveGoogleDriveClientId(id: String) = backupController.saveGoogleDriveClientId(id)
    fun backupToGoogleDrive() = backupController.backupToGoogleDrive()
    fun restoreFromGoogleDrive() = backupController.restoreFromGoogleDrive()
    fun disconnectGoogleDrive() = backupController.disconnectGoogleDrive()
    fun openMigrateDialog() = backupController.openMigrateDialog()
    fun executeMigration(url: String) = backupController.executeMigration(url)
    fun replayWalkthrough(onNavigateToOnboarding: () -> Unit) =
        backupController.replayWalkthrough(onNavigateToOnboarding)
    fun resetToDemoData() = backupController.resetToDemoData()
    fun loadSampleDemoData(onCompleted: () -> Unit = {}) = backupController.loadSampleDemoData(onCompleted)
}
