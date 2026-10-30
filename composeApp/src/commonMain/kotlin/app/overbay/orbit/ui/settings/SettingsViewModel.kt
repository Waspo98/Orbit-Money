package app.overbay.orbit.ui.settings

import androidx.lifecycle.ViewModel
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.*
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.ServerUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            repository.preferences.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
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
            repository.pendingSettingsSubpage.collect { subpage ->
                _uiState.update { it.copy(activeSubpage = subpage) }
                if (subpage == "data") {
                    loadSimpleFinLogs()
                    loadHouseholdSharing()
                } else if (subpage == "preferences") {
                    loadPushConfig()
                }
            }
        }
    }

    fun setSubpage(subpage: String?) {
        repository.requestOpenSettingsSubpage(subpage)
    }

    fun setStorageMode(mode: StorageMode) {
        val updated = _uiState.value.preferences.copy(storageMode = mode)
        _uiState.update { it.copy(preferences = updated, storageMode = mode) }
        scope.launch {
            repository.setStorageMode(mode)
        }
    }

    fun setServerUrl(url: String) {
        val trimmed = url.trim()
        val updated = _uiState.value.preferences.copy(selfHostedServerUrl = trimmed)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun loginToSelfHostedServer(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Username and password required") }
            return
        }
        val normalizedUrl = ServerUrlNormalizer.normalize(_uiState.value.preferences.selfHostedServerUrl)
        _uiState.update { it.copy(isSyncing = true, statusMessage = "Authenticating...") }
        scope.launch {
            val client = repository.createApiClient(normalizedUrl)
            val loginRes = client.login(username.trim(), password)
            if (loginRes.isSuccess) {
                repository.setStorageMode(StorageMode.SELF_HOSTED, normalizedUrl)
                val txCount = repository.transactions.value.size
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = SyncResult.Success("Signed in as ${username.trim()} ($txCount transactions)"),
                        statusMessage = "Logged in as ${username.trim()}"
                    )
                }
            } else {
                val errMsg = loginRes.exceptionOrNull()?.message ?: "Authentication failed"
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = SyncResult.Error(errMsg),
                        statusMessage = "Login failed: $errMsg"
                    )
                }
            }
        }
    }

    fun triggerSync() {
        _uiState.update { it.copy(isSyncing = true, statusMessage = "Syncing workspace...") }
        scope.launch {
            val res = repository.sync(force = true)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = if (res is SyncResult.Success) "Sync completed successfully" else "Sync completed with status: $res"
                )
            }
        }
    }

    fun logout() {
        scope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    syncStatus = SyncResult.NoOp,
                    statusMessage = "Signed out of server"
                )
            }
        }
    }

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

    // SimpleFIN
    fun setSimpleFinToken(token: String?) {
        val updated = _uiState.value.preferences.copy(simpleFinToken = token?.trim())
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setupSimpleFinBridge(token: String, cutoverDate: String?) {
        scope.launch {
            val res = repository.setupSimpleFinBridge(token.trim(), cutoverDate)
            if (res.isSuccess) {
                _uiState.update { it.copy(statusMessage = "SimpleFIN connected successfully") }
                loadSimpleFinLogs()
            } else {
                _uiState.update { it.copy(statusMessage = "SimpleFIN setup failed: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun disconnectSimpleFin() {
        scope.launch {
            repository.disconnectSimpleFinBridge()
            _uiState.update { it.copy(statusMessage = "SimpleFIN disconnected", simpleFinLogs = emptyList()) }
        }
    }

    fun loadSimpleFinLogs() {
        _uiState.update { it.copy(isLoadingSimpleFinLogs = true) }
        scope.launch {
            val res = repository.fetchSimpleFinSyncLogs()
            _uiState.update {
                it.copy(
                    simpleFinLogs = res.getOrDefault(emptyList()),
                    isLoadingSimpleFinLogs = false
                )
            }
        }
    }

    fun toggleSimpleFinLogsExpanded() {
        _uiState.update { it.copy(isSimpleFinLogsExpanded = !it.isSimpleFinLogsExpanded) }
    }

    // Household Partner Sharing
    fun loadHouseholdSharing() {
        _uiState.update { it.copy(isLoadingSharing = true) }
        scope.launch {
            val res = repository.fetchHouseholdSharing()
            _uiState.update {
                it.copy(
                    householdSharing = res.getOrNull(),
                    isLoadingSharing = false
                )
            }
        }
    }

    fun openInvitePartnerDialog(show: Boolean) {
        _uiState.update { it.copy(showInvitePartnerDialog = show) }
    }

    fun inviteHouseholdPartner(email: String, role: String, accessLevel: String) {
        scope.launch {
            val res = repository.inviteHouseholdPartner(email.trim(), role, accessLevel)
            if (res.isSuccess) {
                _uiState.update { it.copy(showInvitePartnerDialog = false, statusMessage = "Invitation sent to $email") }
                loadHouseholdSharing()
            } else {
                _uiState.update { it.copy(statusMessage = "Invite failed: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun updatePartnerAccess(shareId: Long, accessLevel: String) {
        scope.launch {
            repository.updatePartnerShareAccess(shareId, accessLevel)
            loadHouseholdSharing()
        }
    }

    fun revokePartnerShare(shareId: Long) {
        scope.launch {
            repository.revokePartnerShare(shareId)
            loadHouseholdSharing()
        }
    }

    fun removeHouseholdUser(userId: Long) {
        scope.launch {
            repository.removeHouseholdMemberUser(userId)
            loadHouseholdSharing()
        }
    }

    // Rules & Maintenance
    fun reapplyAllRules() {
        _uiState.update { it.copy(statusMessage = "Reapplying rules across all transactions...") }
        scope.launch {
            repository.runRulesOnAllTransactions()
            _uiState.update { it.copy(statusMessage = "Rules reapplied successfully") }
        }
    }

    // Import / Export & Backup
    suspend fun exportCsv(): String = repository.exportBudgetingCsv().getOrDefault("")

    suspend fun exportBudgetingJson(): String = repository.exportBudgetingJson().getOrDefault("")

    fun exportBackup(): String {
        val json = repository.exportBackupJson()
        _uiState.update { it.copy(backupExportJson = json, showBackupExportDialog = true) }
        return json
    }

    fun closeBackupExportDialog() {
        _uiState.update { it.copy(showBackupExportDialog = false, backupExportJson = null) }
    }

    fun openRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = true) }
    }

    fun closeRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = false, restoreJsonText = "") }
    }

    fun setRestoreJsonText(text: String) {
        _uiState.update { it.copy(restoreJsonText = text) }
    }

    fun previewBackupForRestore(rawJson: String) {
        val preview = repository.previewBackupJson(rawJson)
        if (preview.isSuccess) {
            _uiState.update {
                it.copy(
                    restoreJsonText = rawJson,
                    restorePreviewStats = preview.getOrNull(),
                    showRestoreDialog = false,
                    showRestorePreviewDialog = true
                )
            }
        } else {
            _uiState.update { it.copy(statusMessage = "Invalid backup JSON: ${preview.exceptionOrNull()?.message}") }
        }
    }

    fun closeRestorePreviewDialog() {
        _uiState.update { it.copy(showRestorePreviewDialog = false, restorePreviewStats = null) }
    }

    fun confirmRestoreBackup() {
        val jsonText = _uiState.value.restoreJsonText
        restoreBackup(jsonText)
    }

    fun restoreBackup(rawJson: String) {
        scope.launch {
            val result = repository.restoreBackupJson(rawJson)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        showRestoreDialog = false,
                        showRestorePreviewDialog = false,
                        restorePreviewStats = null,
                        restoreJsonText = "",
                        statusMessage = "Backup restored successfully"
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Restore failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    // Google Drive
    fun setGoogleDriveSync(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(googleDriveSyncEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun openGoogleDriveClientIdDialog(show: Boolean) {
        _uiState.update {
            it.copy(
                showGoogleDriveClientIdDialog = show,
                googleDriveClientIdDraft = if (show) it.preferences.googleDriveClientId else ""
            )
        }
    }

    fun setGoogleDriveClientIdDraft(id: String) {
        _uiState.update { it.copy(googleDriveClientIdDraft = id) }
    }

    fun saveGoogleDriveClientId(id: String) {
        val updated = _uiState.value.preferences.copy(googleDriveClientId = id.trim())
        _uiState.update { it.copy(preferences = updated, showGoogleDriveClientIdDialog = false) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun backupToGoogleDrive() {
        _uiState.update { it.copy(isSyncing = true, statusMessage = "Backing up to Google Drive...") }
        scope.launch {
            val res = repository.cloudSyncManager.backupSnapshot(repository.snapshot.value)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = if (res is SyncResult.Success) "Google Drive backup complete" else "Backup failed: $res"
                )
            }
        }
    }

    fun restoreFromGoogleDrive() {
        _uiState.update { it.copy(isSyncing = true, statusMessage = "Restoring from Google Drive...") }
        scope.launch {
            val snap = repository.cloudSyncManager.restoreSnapshot()
            if (snap != null) {
                repository.commitSnapshot(snap, immediate = true)
                _uiState.update { it.copy(isSyncing = false, statusMessage = "Restored from Google Drive") }
            } else {
                _uiState.update { it.copy(isSyncing = false, statusMessage = "Restore failed: No Google Drive backup found") }
            }
        }
    }

    fun disconnectGoogleDrive() {
        scope.launch {
            repository.cloudSyncManager.disconnect()
            val updated = _uiState.value.preferences.copy(googleDriveSyncEnabled = false)
            _uiState.update { it.copy(preferences = updated, statusMessage = "Disconnected from Google Drive") }
            repository.updatePreferences(updated)
        }
    }

    // Migration
    fun openMigrateDialog(show: Boolean) {
        _uiState.update { it.copy(showMigrateDialog = show, migrateMessage = null) }
    }

    fun setMigrateServerUrl(url: String) {
        _uiState.update { it.copy(migrateServerUrl = url) }
    }

    fun executeMigration(url: String) {
        _uiState.update { it.copy(isMigrating = true, migrateMessage = "Migrating workspace to server...") }
        scope.launch {
            val res = repository.migrateLocalWorkspaceToSelfHosted(url.trim())
            if (res.isSuccess) {
                _uiState.update { it.copy(isMigrating = false, migrateMessage = "Migration completed successfully!", showMigrateDialog = false) }
            } else {
                _uiState.update { it.copy(isMigrating = false, migrateMessage = "Migration failed: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    // Walkthrough & Reset
    fun replayWalkthrough(onNavigateToOnboarding: () -> Unit) {
        scope.launch {
            val updated = _uiState.value.preferences.copy(onboardingCompleted = false)
            repository.updatePreferences(updated)
            onNavigateToOnboarding()
        }
    }

    fun openResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    fun closeResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    fun resetToDemoData() {
        scope.launch {
            repository.loadDemoData(OrbitWorkspaceSnapshot.empty())
            _uiState.update { it.copy(showResetConfirmation = false, statusMessage = "Reset to clean workspace") }
        }
    }

    fun setShowDonateDialog(show: Boolean) {
        _uiState.update { it.copy(showDonateDialog = show) }
    }
}
