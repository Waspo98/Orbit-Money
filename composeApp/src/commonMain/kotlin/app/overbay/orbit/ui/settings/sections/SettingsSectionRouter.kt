package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.lazy.LazyListScope
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.settings.SettingsDialogState
import app.overbay.orbit.ui.settings.SettingsSection
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.ui.settings.SettingsViewModel

/**
 * Routes a [SettingsSection] to its modular [LazyListScope] section items.
 */
fun LazyListScope.settingsSectionItems(
    section: SettingsSection,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigate: (OrbitNavKey) -> Unit
) {
    when (section) {
        SettingsSection.APPEARANCE -> appearanceSectionItems(
            preferences = uiState.preferences,
            onThemeModeChange = viewModel::setThemeMode,
            onAmoledDarkChange = viewModel::setAmoledDark,
            onPresetChange = viewModel::setThemePreset,
            onDynamicColorChange = viewModel::setDynamicColor,
            onHapticsChange = viewModel::setHapticsEnabled
        )
        SettingsSection.NOTIFICATIONS -> notificationsSectionItems(
            preferences = uiState.preferences,
            pushConfig = uiState.pushConfig,
            isTesting = uiState.isPushTesting,
            onSendTestNotification = viewModel::sendTestPush,
            onUpdateNotificationPreferences = viewModel::updateNotificationPreferences,
            onOpenScheduleSheet = { viewModel.showDialog(SettingsDialogState.NotificationSchedule(it)) }
        )
        SettingsSection.WORKSPACE_NAVIGATION -> workspaceNavigationSectionItems(
            preferences = uiState.preferences,
            onMhaTrackerChange = viewModel::setMhaTrackerEnabled,
            onOpenTransactionActions = { viewModel.showDialog(SettingsDialogState.TransactionQuickActions) },
            onToggleRouteHidden = viewModel::toggleRouteHidden,
            onMoveRoute = viewModel::moveRouteOrder
        )
        SettingsSection.SYNC_AND_SHARING -> syncAndSharingSectionItems(
            uiState = uiState,
            onBiometricLockChange = viewModel::setBiometricLock,
            onBiometricTimeoutChange = viewModel::setBiometricTimeoutSeconds,
            onStorageModeChange = viewModel::setStorageMode,
            onServerUrlDraftChange = viewModel::updateServerUrlDraft,
            onCommitServerUrl = viewModel::setServerUrl,
            onToggleReauthOptions = viewModel::setShowReauthOptions,
            onTogglePasswordAuth = viewModel::setShowServerPasswordLogin,
            onUsernameDraftChange = viewModel::updateServerUsernameDraft,
            onPasswordDraftChange = viewModel::updateServerPasswordDraft,
            onTriggerSync = viewModel::triggerSync,
            onAuthenticateServer = viewModel::loginToSelfHostedServer,
            onOpenMigrateWizard = viewModel::openMigrateDialog,
            onRequestSwitchAccount = { viewModel.showDialog(SettingsDialogState.ConfirmSwitchAccount) },
            onRequestSignOut = { viewModel.showDialog(SettingsDialogState.ConfirmSignOut) },
            onOpenPartnerInvite = { viewModel.showDialog(SettingsDialogState.PartnerInvite()) },
            onUpdatePartnerAccess = viewModel::updatePartnerAccess,
            onUpdateMemberAccess = viewModel::updateHouseholdMemberAccess,
            onRequestRemoveUser = { viewModel.showDialog(SettingsDialogState.ConfirmRemovePartnerUser(it)) },
            onRequestRevokeShare = { viewModel.showDialog(SettingsDialogState.ConfirmRevokePartnerShare(it)) }
        )
        SettingsSection.BANK_FEEDS -> bankFeedsSectionItems(
            uiState = uiState,
            onTokenDraftChange = viewModel::updateSimpleFinTokenDraft,
            onCutoverDateDraftChange = viewModel::updateSimpleFinCutoverDateDraft,
            onToggleReconnectEditor = viewModel::setShowSimpleFinReconnectEditor,
            onSetupBridge = viewModel::setupSimpleFinBridge,
            onTriggerSync = viewModel::triggerSync,
            onRequestDisconnect = { viewModel.showDialog(SettingsDialogState.ConfirmDisconnectSimpleFin) },
            onToggleLogsExpanded = viewModel::toggleSimpleFinLogsExpanded,
            onUnmuteAccount = viewModel::unmuteSimpleFinAccount
        )
        SettingsSection.DATA_AND_BACKUPS -> dataBackupsSectionItems(
            preferences = uiState.preferences,
            storageMode = uiState.storageMode,
            isSyncing = uiState.isSyncing,
            onToggleDriveSync = viewModel::setGoogleDriveSync,
            onOpenClientIdDialog = viewModel::openGoogleDriveClientIdDialog,
            onBackupToDrive = viewModel::backupToGoogleDrive,
            onRequestRestoreFromDrive = { viewModel.showDialog(SettingsDialogState.ConfirmRestoreGoogleDrive) },
            onRequestDisconnectDrive = { viewModel.showDialog(SettingsDialogState.ConfirmDisconnectGoogleDrive) },
            onExportCsv = viewModel::exportCsv,
            onExportBudgetingJson = viewModel::exportBudgetingJson,
            onExportBackupToFile = viewModel::exportBackupToFile,
            onOpenBackupExportJsonDialog = { viewModel.openBackupExportJsonDialog() },
            onPickRestoreFile = viewModel::previewBackupForRestore,
            onOpenRestorePasteDialog = viewModel::openRestorePasteDialog,
            onRequestReapplyRules = { viewModel.showDialog(SettingsDialogState.ConfirmReapplyRules) },
            onRequestLoadSampleData = { viewModel.showDialog(SettingsDialogState.ConfirmLoadSampleData) },
            onRequestResetWorkspace = { viewModel.showDialog(SettingsDialogState.ConfirmResetWorkspace) }
        )
        SettingsSection.ABOUT -> aboutSectionItems(
            preferences = uiState.preferences,
            onReplayWalkthrough = {
                viewModel.replayWalkthrough { onNavigate(OrbitNavKey.Onboarding(isReplay = true)) }
            },
            onSupportClick = { viewModel.showDialog(SettingsDialogState.SupportDevelopment) },
            onRequestSignOut = { viewModel.showDialog(SettingsDialogState.ConfirmSignOut) }
        )
    }
}
