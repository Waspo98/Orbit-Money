package app.overbay.orbit.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitAlertDialog
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.settings.blocks.AboutSettingsBlock
import app.overbay.orbit.ui.settings.dialogs.GoogleDriveClientIdDialog
import app.overbay.orbit.ui.settings.dialogs.MigrateWorkspaceDialog
import app.overbay.orbit.ui.settings.dialogs.PartnerInviteDialog
import app.overbay.orbit.ui.settings.dialogs.RestorePreviewDialog
import app.overbay.orbit.ui.settings.subpages.DataManagementSubpageContent
import app.overbay.orbit.ui.settings.subpages.PreferencesSubpageContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()

    // Handle back button returning from subpage to Hub
    BackHandler(enabled = uiState.activeSubpage != null) {
        viewModel.setSubpage(null)
    }

    val pageTitle = when (uiState.activeSubpage) {
        "preferences" -> "Preferences"
        "data" -> "Data Management"
        else -> "Settings"
    }

    val pageSubtitle = when (uiState.activeSubpage) {
        "preferences" -> "Theme, AMOLED dark, notifications, navigation & biometrics"
        "data" -> "Storage, SimpleFIN bank feeds, partner sharing & backups"
        else -> "Preferences, sync connections, backups, and appearance."
    }

    val isHub = uiState.activeSubpage == null
    val effectiveOnBack = if (isHub) onBack else { { viewModel.setSubpage(null) } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            // Edge-to-Edge Curved Green Canopy Hero
            MorphingPageHero(
                kicker = if (isHub) "CONTROL CENTER" else "SETTINGS HUB",
                title = pageTitle,
                subtitle = pageSubtitle,
                canNavigateBack = true,
                onBack = effectiveOnBack,
                scrollState = heroScrollState,
                canopyBar = { _, isCompact, barHeight ->
                    val isLocal = uiState.preferences.storageMode == StorageMode.LOCAL
                    val modeLabel = if (isLocal) "Local Offline Mode · Ready" else "Self-Hosted Server · Active"
                    val shortMode = if (isLocal) "Local Mode" else "Server Sync"
                    CanopySurfacePill(
                        expandedText = if (uiState.isSyncing) "Syncing workspace..." else modeLabel,
                        compactText = if (uiState.isSyncing) "Syncing..." else shortMode,
                        isCompact = isCompact,
                        leadingIcon = OrbitIcons.Settings,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CanopyIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Sync Now",
                        onClick = { viewModel.triggerSync() },
                        buttonSize = barHeight,
                        isActive = uiState.isSyncing,
                        enabled = !uiState.isSyncing
                    )

                    CanopySettingsButton(
                        buttonSize = barHeight,
                        isActive = isHub,
                        onClick = { if (!isHub) viewModel.setSubpage(null) }
                    )
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isHub) {
                    // Settings Hub: Two Rich Subpage Cards + About Block
                    item {
                        SettingsHubCard(
                            title = "Preferences",
                            subtitle = "Theme & AMOLED pure black, push notifications, navigation customization, and biometric security",
                            icon = OrbitIcons.Categories,
                            onClick = { viewModel.setSubpage("preferences") }
                        )
                    }

                    item {
                        SettingsHubCard(
                            title = "Data Management",
                            subtitle = "Storage mode & server sync, SimpleFIN bank feeds, partner sharing, rules maintenance, and cloud backups",
                            icon = OrbitIcons.Upcoming,
                            onClick = { viewModel.setSubpage("data") }
                        )
                    }

                    item {
                        AboutSettingsBlock(
                            preferences = uiState.preferences,
                            onReplayWalkthrough = {
                                viewModel.replayWalkthrough { onNavigate(OrbitNavKey.Onboarding) }
                            },
                            onDonateClick = { viewModel.setShowDonateDialog(true) },
                            onSignOut = {
                                viewModel.logout()
                                onNavigate(OrbitNavKey.Login)
                            }
                        )
                    }
                } else if (uiState.activeSubpage == "preferences") {
                    item {
                        PreferencesSubpageContent(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                } else if (uiState.activeSubpage == "data") {
                    item {
                        DataManagementSubpageContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigate = onNavigate
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Restore Preview Stat Grid Dialog
    if (uiState.showRestorePreviewDialog && uiState.restorePreviewStats != null) {
        RestorePreviewDialog(
            stats = uiState.restorePreviewStats!!,
            onConfirm = viewModel::confirmRestoreBackup,
            onDismiss = viewModel::closeRestorePreviewDialog
        )
    }

    // Migrate Workspace Wizard Dialog
    if (uiState.showMigrateDialog) {
        MigrateWorkspaceDialog(
            initialServerUrl = uiState.migrateServerUrl,
            isMigrating = uiState.isMigrating,
            message = uiState.migrateMessage,
            onMigrate = viewModel::executeMigration,
            onDismiss = { viewModel.openMigrateDialog(false) }
        )
    }

    // Partner Share Dialog
    if (uiState.showInvitePartnerDialog) {
        PartnerInviteDialog(
            onSendInvite = viewModel::inviteHouseholdPartner,
            onDismiss = { viewModel.openInvitePartnerDialog(false) }
        )
    }

    // Google Drive Client ID Dialog
    if (uiState.showGoogleDriveClientIdDialog) {
        GoogleDriveClientIdDialog(
            initialClientId = uiState.googleDriveClientIdDraft,
            onSave = viewModel::saveGoogleDriveClientId,
            onDismiss = { viewModel.openGoogleDriveClientIdDialog(false) }
        )
    }

    // Export Dialog
    if (uiState.showBackupExportDialog && uiState.backupExportJson != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeBackupExportDialog,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Workspace Backup Export") },
            text = {
                Column {
                    Text(
                        text = "Backup snapshot generated. Copy the JSON below to safely store off-device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.backupExportJson ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::closeBackupExportDialog) { Text("Done") }
            }
        )
    }

    // Restore text paste dialog
    if (uiState.showRestoreDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeRestoreDialog,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Restore Workspace Backup") },
            text = {
                Column {
                    Text(
                        text = "Paste JSON snapshot below to preview and restore:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.restoreJsonText,
                        onValueChange = viewModel::setRestoreJsonText,
                        placeholder = { Text("Paste JSON here...") },
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.previewBackupForRestore(uiState.restoreJsonText) },
                    enabled = uiState.restoreJsonText.isNotBlank()
                ) { Text("Preview Restore") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeRestoreDialog) { Text("Cancel") }
            }
        )
    }

    if (uiState.showResetConfirmation) {
        OrbitConfirmationDialog(
            title = "Reset Workspace?",
            message = "This will erase all accounts, transactions, budgets and rules on this device and reset to a clean empty state. This cannot be undone.",
            confirmText = "Reset Everything",
            isDestructive = true,
            onConfirm = viewModel::resetToDemoData,
            onDismiss = viewModel::closeResetConfirmation
        )
    }

    if (uiState.showDonateDialog) {
        OrbitAlertDialog(
            title = "☕ Support Development",
            message = "Orbit Money is free, private, and open source. Thank you for supporting independent financial software!",
            confirmText = "OK",
            onDismiss = { viewModel.setShowDonateDialog(false) }
        )
    }
}

@Composable
private fun SettingsHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
