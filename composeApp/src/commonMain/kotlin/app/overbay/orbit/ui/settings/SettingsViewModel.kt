package app.overbay.orbit.ui.settings

import androidx.lifecycle.ViewModel
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
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
    }

    fun setStorageMode(mode: StorageMode) {
        val currentUrl = _uiState.value.preferences.selfHostedServerUrl
        val updated = _uiState.value.preferences.copy(storageMode = mode)
        _uiState.update { it.copy(preferences = updated, storageMode = mode) }
        scope.launch {
            repository.setStorageMode(mode, currentUrl)
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

    fun setThemePreset(preset: OrbitThemePreset) {
        val updated = _uiState.value.preferences.copy(themePreset = preset)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setThemeMode(mode: OrbitThemeMode) {
        val updated = _uiState.value.preferences.copy(themeMode = mode)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(dynamicColor = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(biometricLockEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(hapticsEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setMhaTrackerEnabled(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(mhaTrackerEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setSimpleFinToken(token: String?) {
        val updated = _uiState.value.preferences.copy(simpleFinToken = token?.trim())
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setGoogleDriveSync(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(googleDriveSyncEnabled = enabled)
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

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
        _uiState.update { it.copy(showRestoreDialog = false) }
    }

    fun restoreBackup(jsonString: String) {
        scope.launch {
            val result = repository.restoreBackupJson(jsonString)
            if (result.isSuccess) {
                _uiState.update { it.copy(showRestoreDialog = false, statusMessage = "Backup restored successfully") }
            } else {
                _uiState.update { it.copy(statusMessage = "Restore failed: ${result.exceptionOrNull()?.message}") }
            }
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
}
