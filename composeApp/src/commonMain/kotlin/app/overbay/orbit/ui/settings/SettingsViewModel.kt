package app.overbay.orbit.ui.settings

import androidx.lifecycle.ViewModel
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
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
        val updated = _uiState.value.preferences.copy(storageMode = mode)
        _uiState.update { it.copy(preferences = updated, storageMode = mode) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun setServerUrl(url: String) {
        val updated = _uiState.value.preferences.copy(selfHostedServerUrl = url.trim())
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
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
