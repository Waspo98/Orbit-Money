package app.overbay.orbit.ui.settings.subpages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.ui.settings.SettingsViewModel
import app.overbay.orbit.ui.settings.blocks.DataImportExportBlock
import app.overbay.orbit.ui.settings.blocks.GoogleDriveBackupBlock
import app.overbay.orbit.ui.settings.blocks.PartnerShareBlock
import app.overbay.orbit.ui.settings.blocks.SimpleFinBankSyncBlock
import app.overbay.orbit.ui.settings.blocks.StorageSyncModeBlock

@Composable
fun DataManagementSubpageContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Storage & Server Sync
        StorageSyncModeBlock(
            preferences = uiState.preferences,
            isSyncing = uiState.isSyncing,
            syncStatus = uiState.syncStatus,
            onStorageModeChange = viewModel::setStorageMode,
            onServerUrlChange = viewModel::setServerUrl,
            onTriggerSync = viewModel::triggerSync,
            onAuthenticateServer = viewModel::loginToSelfHostedServer,
            onOpenLoginScreen = {
                viewModel.logout()
                onNavigate(OrbitNavKey.Login)
            },
            onLogout = {
                viewModel.logout()
                onNavigate(OrbitNavKey.Login)
            },
            onOpenMigrateWizard = {
                viewModel.setMigrateServerUrl(uiState.preferences.selfHostedServerUrl)
                viewModel.openMigrateDialog(true)
            }
        )

        // 2. SimpleFIN Bank Feeds
        SimpleFinBankSyncBlock(
            token = uiState.preferences.simpleFinToken,
            cutoverDate = uiState.preferences.simpleFinCutoverDate,
            syncLogs = uiState.simpleFinLogs,
            isLogsExpanded = uiState.isSimpleFinLogsExpanded,
            mutedAccounts = uiState.preferences.mutedSimpleFinAccounts,
            onUnmuteAccount = viewModel::unmuteSimpleFinAccount,
            onTokenChange = viewModel::setSimpleFinToken,
            onSetupBridge = viewModel::setupSimpleFinBridge,
            onTriggerSync = viewModel::triggerSync,
            onDisconnect = viewModel::disconnectSimpleFin,
            onToggleLogsExpanded = viewModel::toggleSimpleFinLogsExpanded
        )

        // 3. Household Partner Sharing
        PartnerShareBlock(
            sharingState = uiState.householdSharing,
            isLoading = uiState.isLoadingSharing,
            onOpenInviteDialog = { viewModel.openInvitePartnerDialog(true) },
            onUpdateAccess = viewModel::updatePartnerAccess,
            onRevokeShare = viewModel::revokePartnerShare,
            onRemoveUser = viewModel::removeHouseholdUser
        )

        // 4. Data Import, Export & Rules
        DataImportExportBlock(
            onReapplyAllRules = viewModel::reapplyAllRules,
            onExportCsv = viewModel::exportCsv,
            onExportBudgetingJson = viewModel::exportBudgetingJson,
            onExportBackupJson = viewModel::exportBackup,
            onPickRestoreFile = viewModel::previewBackupForRestore,
            onOpenRestoreTextDialog = viewModel::openRestoreDialog
        )

        // 5. Google Drive Cloud Backup
        GoogleDriveBackupBlock(
            isDriveSyncEnabled = uiState.preferences.googleDriveSyncEnabled,
            lastSyncAt = uiState.preferences.lastGoogleDriveSyncAt,
            clientId = uiState.preferences.googleDriveClientId,
            onToggleDriveSync = viewModel::setGoogleDriveSync,
            onOpenClientIdDialog = { viewModel.openGoogleDriveClientIdDialog(true) },
            onBackupNow = viewModel::backupToGoogleDrive,
            onRestoreFromDrive = viewModel::restoreFromGoogleDrive,
            onDisconnect = viewModel::disconnectGoogleDrive
        )
    }
}
