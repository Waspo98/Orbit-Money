package app.overbay.orbit.ui.settings.components

import androidx.compose.runtime.Composable
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.OrbitAlertDialog
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.settings.NotificationScheduleType
import app.overbay.orbit.ui.settings.SettingsDialogState
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.ui.settings.SettingsViewModel
import app.overbay.orbit.ui.settings.sheets.BackupExportSheet
import app.overbay.orbit.ui.settings.sheets.BackupRestorePasteSheet
import app.overbay.orbit.ui.settings.sheets.GoogleDriveClientIdDialog
import app.overbay.orbit.ui.settings.sheets.MigrateWorkspaceSheet
import app.overbay.orbit.ui.settings.sheets.NotificationScheduleSheet
import app.overbay.orbit.ui.settings.sheets.PartnerInviteSheet
import app.overbay.orbit.ui.settings.sheets.RestorePreviewSheet
import app.overbay.orbit.ui.transactions.sheets.TransactionActionsCustomizeSheet

/**
 * Exhaustive modal/dialog host driven by [SettingsUiState.activeDialog].
 */
@Composable
fun SettingsDialogsHost(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigate: (OrbitNavKey) -> Unit
) {
    when (val dialog = uiState.activeDialog) {
        SettingsDialogState.None -> Unit
        is SettingsDialogState.BackupExport -> BackupExportSheet(
            jsonText = dialog.jsonText,
            onDismiss = viewModel::dismissDialog
        )
        is SettingsDialogState.BackupRestorePaste -> BackupRestorePasteSheet(
            jsonText = dialog.draftJson,
            errorMessage = dialog.errorMessage,
            onTextChange = viewModel::updateRestorePasteDraft,
            onPreview = viewModel::previewBackupForRestore,
            onDismiss = viewModel::dismissDialog
        )

        is SettingsDialogState.RestorePreview -> RestorePreviewSheet(
            stats = dialog.stats,
            isRestoring = dialog.isRestoring,
            errorMessage = dialog.errorMessage,
            onConfirm = viewModel::confirmRestoreBackup,
            onDismiss = viewModel::dismissDialog
        )

        is SettingsDialogState.MigrateWorkspace -> MigrateWorkspaceSheet(
            initialServerUrl = dialog.serverUrl,
            isMigrating = dialog.isMigrating,
            message = dialog.message,
            onMigrate = viewModel::executeMigration,
            onDismiss = viewModel::dismissDialog
        )

        is SettingsDialogState.PartnerInvite -> PartnerInviteSheet(
            isSending = dialog.isSending,
            errorMessage = dialog.errorMessage,
            onSendInvite = viewModel::inviteHouseholdPartner,
            onDismiss = viewModel::dismissDialog
        )

        is SettingsDialogState.ConfirmRemovePartnerUser -> {
            val memberLabel = dialog.user.displayName.ifBlank { dialog.user.email ?: "this member" }
            ConfirmActionDialog(
                title = "Remove Member?",
                message = "Remove $memberLabel from this household?",
                confirmText = "Remove",
                onConfirm = { viewModel.removeHouseholdUser(dialog.user.id); viewModel.dismissDialog() },
                onDismiss = viewModel::dismissDialog
            )
        }

        is SettingsDialogState.ConfirmRevokePartnerShare -> ConfirmActionDialog(
            title = "Revoke Invitation?",
            message = "Stop sharing with ${dialog.share.invitedEmail}?",
            confirmText = "Stop Sharing",
            onConfirm = { viewModel.revokePartnerShare(dialog.share.id); viewModel.dismissDialog() },
            onDismiss = viewModel::dismissDialog
        )

        is SettingsDialogState.GoogleDriveClientId -> GoogleDriveClientIdDialog(
            initialClientId = dialog.draftClientId,
            onSave = viewModel::saveGoogleDriveClientId,
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmRestoreGoogleDrive -> ConfirmActionDialog(
            title = "Restore from Google Drive?",
            message = "Restore your database from Google Drive? This will replace your current workspace data with the cloud backup.",
            confirmText = "Restore",
            onConfirm = { viewModel.dismissDialog(); viewModel.restoreFromGoogleDrive() },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmDisconnectGoogleDrive -> ConfirmActionDialog(
            title = "Disconnect Google Drive?",
            message = "Disconnect Google Drive? Existing backups in your Drive stay untouched, but automatic cloud sync will stop.",
            confirmText = "Disconnect",
            onConfirm = { viewModel.dismissDialog(); viewModel.disconnectGoogleDrive() },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmDisconnectSimpleFin -> ConfirmActionDialog(
            title = "Disconnect SimpleFIN?",
            message = "Your existing transactions stay, but bank sync will stop until you reconnect.",
            confirmText = "Disconnect",
            onConfirm = { viewModel.dismissDialog(); viewModel.disconnectSimpleFin() },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmReapplyRules -> ConfirmActionDialog(
            title = "Reapply All Rules?",
            message = "Reapply all enabled rules across historical transactions? Manual edits stay protected, while rule-owned edits may update.",
            confirmText = "Reapply Rules",
            isDestructive = false,
            onConfirm = { viewModel.dismissDialog(); viewModel.reapplyAllRules() },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmLoadSampleData -> ConfirmActionDialog(
            title = "Load Sample Household?",
            message = "This will replace your current local accounts, transactions, budgets, and goals with realistic sample household data. This cannot be undone.",
            confirmText = "Load Sample Data",
            onConfirm = { viewModel.loadSampleDemoData { onNavigate(OrbitNavKey.Dashboard) } },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmResetWorkspace -> ConfirmActionDialog(
            title = "Reset Workspace?",
            message = "This will erase all accounts, transactions, budgets, and rules on this device and reset to an empty state. This cannot be undone.",
            confirmText = "Reset Everything",
            onConfirm = viewModel::resetToDemoData,
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmSignOut -> ConfirmActionDialog(
            title = "Sign Out?",
            message = "Sign out of your self-hosted server on this device?",
            confirmText = "Sign Out",
            onConfirm = { viewModel.dismissDialog(); viewModel.logout(); onNavigate(OrbitNavKey.Login) },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.ConfirmSwitchAccount -> ConfirmActionDialog(
            title = "Switch Account?",
            message = "This will sign out your current session on this device and take you to the sign-in screen.",
            confirmText = "Switch Account",
            onConfirm = { viewModel.dismissDialog(); viewModel.logout(); onNavigate(OrbitNavKey.Login) },
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.SupportDevelopment -> OrbitAlertDialog(
            title = "Support Development",
            message = "Orbit Money is free, private, and open source. Thank you for supporting independent household finance software!",
            confirmText = "OK",
            onDismiss = viewModel::dismissDialog
        )

        SettingsDialogState.TransactionQuickActions -> TransactionActionsCustomizeSheet(
            pinnedIds = uiState.preferences.transactionPinnedActions,
            orderIds = uiState.preferences.transactionActionOrder,
            mhaEnabled = uiState.preferences.mhaTrackerEnabled,
            onSave = { pinned, ordered -> viewModel.saveTransactionQuickActions(pinned, ordered) },
            onDismiss = viewModel::dismissDialog
        )

        is SettingsDialogState.NotificationSchedule -> {
            val notifPrefs = uiState.preferences.notificationPreferences
            val currentSchedule = when (dialog.type) {
                NotificationScheduleType.WEEKLY_SNAPSHOT -> notifPrefs.weeklySnapshot
                NotificationScheduleType.INCOME -> notifPrefs.income
                NotificationScheduleType.ACCOUNT_SNAPSHOTS -> notifPrefs.accountSnapshots
            }
            NotificationScheduleSheet(
                notificationType = dialog.type,
                schedule = currentSchedule,
                accounts = uiState.accounts,
                onSave = { updated ->
                    val nextConfig = when (dialog.type) {
                        NotificationScheduleType.WEEKLY_SNAPSHOT -> notifPrefs.copy(weeklySnapshot = updated)
                        NotificationScheduleType.INCOME -> notifPrefs.copy(income = updated)
                        NotificationScheduleType.ACCOUNT_SNAPSHOTS -> notifPrefs.copy(accountSnapshots = updated)
                    }
                    viewModel.updateNotificationPreferences(nextConfig)
                    viewModel.dismissDialog()
                },
                onDismiss = viewModel::dismissDialog
            )
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
) {
    OrbitConfirmationDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        isDestructive = isDestructive,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
