package app.overbay.orbit.ui.settings

import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.StorageMode

data class SettingsUiState(
    val preferences: OrbitPreferences = OrbitPreferences(),
    val storageMode: StorageMode = StorageMode.LOCAL,
    val syncStatus: SyncResult = SyncResult.NoOp,
    val isSyncing: Boolean = false,
    val backupExportJson: String? = null,
    val showBackupExportDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val statusMessage: String? = null
)
