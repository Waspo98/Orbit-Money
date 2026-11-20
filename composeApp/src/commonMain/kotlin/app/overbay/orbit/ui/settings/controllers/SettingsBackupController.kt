package app.overbay.orbit.ui.settings.controllers

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.exportBackupPayload
import app.overbay.orbit.data.exportBudgetingCsv
import app.overbay.orbit.data.exportBudgetingJson
import app.overbay.orbit.data.migrateLocalWorkspaceToSelfHosted
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.previewBackupJson
import app.overbay.orbit.domain.DemoSeedGenerator
import app.overbay.orbit.ui.settings.SettingsDialogState
import app.overbay.orbit.ui.settings.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsBackupController(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>
) {
    fun reapplyAllRules() {
        uiState.update {
            it.copy(activeDialog = SettingsDialogState.None, statusMessage = "Reapplying rules across all transactions...")
        }
        scope.launch {
            val initialTx = repository.transactions.value
            val beforeById = initialTx.associateBy { it.id }
            repository.runRulesOnAllTransactions()
            val updated = repository.transactions.value.count { after -> beforeById[after.id] != after }
            uiState.update { it.copy(statusMessage = "Reapplied rules to ${initialTx.size} transactions ($updated updated)") }
        }
    }

    suspend fun exportCsv(): String = repository.exportBudgetingCsv().getOrDefault("")

    suspend fun exportBudgetingJson(): String = repository.exportBudgetingJson().getOrDefault("")

    fun exportBackupToFile(onReady: (String) -> Unit) {
        if (uiState.value.storageMode == StorageMode.SELF_HOSTED) {
            scope.launch {
                val payload = repository.exportBackupPayload()
                withContext(Dispatchers.Main) { onReady(payload) }
                uiState.update { it.copy(statusMessage = "Backup ready to save") }
            }
        } else {
            val json = repository.exportBackupJson()
            onReady(json)
            uiState.update { it.copy(statusMessage = "Backup ready to save") }
        }
    }

    fun openBackupExportJsonDialog(): String {
        val fallbackJson = repository.exportBackupJson()
        if (uiState.value.storageMode == StorageMode.SELF_HOSTED) {
            scope.launch {
                val payload = repository.exportBackupPayload()
                uiState.update { it.copy(activeDialog = SettingsDialogState.BackupExport(payload)) }
            }
        } else {
            uiState.update { it.copy(activeDialog = SettingsDialogState.BackupExport(fallbackJson)) }
        }
        return fallbackJson
    }

    fun openRestorePasteDialog() {
        uiState.update { it.copy(activeDialog = SettingsDialogState.BackupRestorePaste()) }
    }

    fun updateRestorePasteDraft(text: String) {
        uiState.update { state ->
            val current = state.activeDialog
            if (current is SettingsDialogState.BackupRestorePaste) {
                state.copy(activeDialog = current.copy(draftJson = text, errorMessage = null))
            } else {
                state
            }
        }
    }

    fun previewBackupForRestore(rawJson: String) {
        val preview = repository.previewBackupJson(rawJson)
        val stats = preview.getOrNull()
        if (preview.isSuccess && stats != null) {
            uiState.update { it.copy(activeDialog = SettingsDialogState.RestorePreview(rawJson, stats)) }
        } else {
            val message = "Could not read backup file. Please make sure it is a valid Orbit Money backup."
            uiState.update { state ->
                val current = state.activeDialog
                if (current is SettingsDialogState.BackupRestorePaste) {
                    state.copy(activeDialog = current.copy(errorMessage = message))
                } else {
                    state.copy(statusMessage = message)
                }
            }
        }
    }

    fun confirmRestoreBackup() {
        val dialog = uiState.value.activeDialog as? SettingsDialogState.RestorePreview ?: return
        if (dialog.isRestoring) return
        uiState.update { it.copy(activeDialog = dialog.copy(isRestoring = true, errorMessage = null)) }
        restoreBackup(dialog.rawJson)
    }

    fun restoreBackup(rawJson: String) {
        scope.launch {
            val result = repository.restoreBackupJson(rawJson)
            if (result.isSuccess) {
                uiState.update { it.copy(activeDialog = SettingsDialogState.None, statusMessage = "Backup restored successfully") }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                val message = "Restore failed: $err"
                uiState.update { state ->
                    val current = state.activeDialog
                    if (current is SettingsDialogState.RestorePreview) {
                        state.copy(activeDialog = current.copy(isRestoring = false, errorMessage = message))
                    } else {
                        state.copy(statusMessage = message)
                    }
                }
            }
        }
    }

    fun setGoogleDriveSync(enabled: Boolean) {
        val updated = uiState.value.preferences.copy(googleDriveSyncEnabled = enabled)
        uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun openGoogleDriveClientIdDialog() {
        uiState.update { it.copy(activeDialog = SettingsDialogState.GoogleDriveClientId(it.preferences.googleDriveClientId)) }
    }

    fun saveGoogleDriveClientId(id: String) {
        val updated = uiState.value.preferences.copy(googleDriveClientId = id.trim())
        uiState.update { it.copy(preferences = updated, activeDialog = SettingsDialogState.None) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun backupToGoogleDrive() {
        uiState.update { it.copy(isSyncing = true, statusMessage = "Backing up to Google Drive...") }
        scope.launch {
            val res = repository.cloudSyncManager.backupSnapshot(repository.snapshot.value)
            uiState.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = when (res) {
                        is SyncResult.Success, SyncResult.NoOp -> "Google Drive backup complete"
                        is SyncResult.Error -> "Backup failed: ${res.message}"
                    }
                )
            }
        }
    }

    fun restoreFromGoogleDrive() {
        uiState.update {
            it.copy(activeDialog = SettingsDialogState.None, isSyncing = true, statusMessage = "Restoring from Google Drive...")
        }
        scope.launch {
            val snap = repository.cloudSyncManager.restoreSnapshot()
            if (snap == null) {
                uiState.update { it.copy(isSyncing = false, statusMessage = "Restore failed: No Google Drive backup found") }
                return@launch
            }
            if (uiState.value.storageMode == StorageMode.SELF_HOSTED) {
                val snapshotJson = repository.json.encodeToString(OrbitWorkspaceSnapshot.serializer(), snap)
                val remoteResult = repository.restoreBackupJson(snapshotJson)
                val msg = if (remoteResult.isSuccess) {
                    "Restored from Google Drive"
                } else {
                    "Restore failed: ${remoteResult.exceptionOrNull()?.message ?: "Unknown error"}"
                }
                uiState.update { it.copy(isSyncing = false, statusMessage = msg) }
            } else {
                repository.commitSnapshot(snap, immediate = true)
                uiState.update { it.copy(isSyncing = false, statusMessage = "Restored from Google Drive") }
            }
        }
    }

    fun disconnectGoogleDrive() {
        uiState.update { it.copy(activeDialog = SettingsDialogState.None) }
        scope.launch {
            repository.cloudSyncManager.disconnect()
            val updated = uiState.value.preferences.copy(googleDriveSyncEnabled = false)
            uiState.update { it.copy(preferences = updated, statusMessage = "Disconnected from Google Drive") }
            repository.updatePreferences(updated)
        }
    }

    fun openMigrateDialog() {
        uiState.update {
            it.copy(activeDialog = SettingsDialogState.MigrateWorkspace(serverUrl = it.preferences.selfHostedServerUrl))
        }
    }

    fun executeMigration(url: String) {
        uiState.update {
            it.copy(
                activeDialog = SettingsDialogState.MigrateWorkspace(
                    serverUrl = url,
                    isMigrating = true,
                    message = "Migrating workspace to server..."
                )
            )
        }
        scope.launch {
            val res = repository.migrateLocalWorkspaceToSelfHosted(url.trim())
            if (res.isSuccess) {
                uiState.update {
                    it.copy(activeDialog = SettingsDialogState.None, statusMessage = "Migration completed successfully!")
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                uiState.update {
                    it.copy(
                        activeDialog = SettingsDialogState.MigrateWorkspace(
                            serverUrl = url,
                            isMigrating = false,
                            message = "Migration failed: $err"
                        )
                    )
                }
            }
        }
    }

    fun replayWalkthrough(onNavigateToOnboarding: () -> Unit) {
        scope.launch {
            val updated = uiState.value.preferences.copy(onboardingCompleted = false)
            repository.updatePreferences(updated)
            withContext(Dispatchers.Main) { onNavigateToOnboarding() }
        }
    }

    fun resetToDemoData() {
        scope.launch {
            repository.loadDemoData(OrbitWorkspaceSnapshot.empty())
            uiState.update { it.copy(activeDialog = SettingsDialogState.None, statusMessage = "Reset to clean workspace") }
        }
    }

    fun loadSampleDemoData(onCompleted: () -> Unit = {}) {
        scope.launch {
            repository.loadDemoData(DemoSeedGenerator.generateDemoSnapshot())
            uiState.update {
                it.copy(activeDialog = SettingsDialogState.None, statusMessage = "Loaded sample household workspace")
            }
            withContext(Dispatchers.Main) { onCompleted() }
        }
    }
}
