package app.overbay.orbit.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
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
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.*
import app.overbay.orbit.ui.settings.blocks.AboutSettingsBlock
import app.overbay.orbit.ui.settings.dialogs.*
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()

    androidx.compose.runtime.LaunchedEffect(uiState.activeSubpage) {
        heroScrollState.expand()
    }

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
                scrollState = heroScrollState,
                actions = {
                    if (!isHub) {
                        Button(
                            onClick = { viewModel.setSubpage(null) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Settings",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back to Settings", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                canopyBar = { _, isCompact, barHeight ->
                    if (!isHub) {
                        CanopyActionPillButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "Back to Settings",
                            compactLabel = "Back",
                            isCompact = isCompact,
                            buttonSize = barHeight,
                            onClick = { viewModel.setSubpage(null) }
                        )
                    }
                    val isLocal = uiState.preferences.storageMode == StorageMode.LOCAL
                    val modeLabel = if (isLocal) "Local Offline Mode · Ready" else "Self-Hosted Server · Active"
                    val shortMode = if (isLocal) "Local Mode" else "Server Sync"
                    CanopySurfacePill(
                        expandedText = if (uiState.isSyncing) "Syncing workspace..." else modeLabel,
                        compactText = if (uiState.isSyncing) "Syncing..." else shortMode,
                        isCompact = isCompact,
                        leadingIcon = OrbitIcons.Settings,
                        onClick = if (!isHub) { { viewModel.setSubpage(null) } } else null,
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

            AnimatedContent(
                targetState = uiState.activeSubpage,
                transitionSpec = {
                    val entering = targetState != null && initialState == null
                    val dir = if (entering) 1 else -1
                    (slideInHorizontally(OrbitSprings.IntOffsetSpatialDefault) { dir * it / 4 } +
                        fadeIn(OrbitSprings.FloatEffectsDefault)) togetherWith
                        (slideOutHorizontally(OrbitSprings.IntOffsetSpatialDefault) { -dir * it / 4 } +
                            fadeOut(OrbitSprings.FloatEffectsFast))
                },
                modifier = Modifier.fillMaxSize(),
                label = "settingsSubpageTransition"
            ) { subpage ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (subpage == null) {
                        // Settings Hub: Two Rich Subpage Cards + About Block
                        item(key = "hub_preferences") {
                            SettingsHubCard(
                                title = "Preferences",
                                subtitle = "Theme & AMOLED pure black, push notifications, navigation customization, and biometric security",
                                icon = OrbitIcons.Categories,
                                onClick = { viewModel.setSubpage("preferences") }
                            )
                        }

                        item(key = "hub_data") {
                            SettingsHubCard(
                                title = "Data Management",
                                subtitle = "Storage mode & server sync, SimpleFIN bank feeds, partner sharing, rules maintenance, and cloud backups",
                                icon = OrbitIcons.Upcoming,
                                onClick = { viewModel.setSubpage("data") }
                            )
                        }

                        item(key = "hub_about") {
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
                    } else if (subpage == "preferences") {
                        item(key = "subpage_preferences") {
                            PreferencesSubpageContent(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    } else if (subpage == "data") {
                        item(key = "subpage_data") {
                            DataManagementSubpageContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                onNavigate = onNavigate
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
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
        BackupExportDialog(
            jsonText = uiState.backupExportJson ?: "",
            onDismiss = viewModel::closeBackupExportDialog
        )
    }

    // Restore text paste dialog
    if (uiState.showRestoreDialog) {
        BackupRestoreDialog(
            jsonText = uiState.restoreJsonText,
            onTextChange = viewModel::setRestoreJsonText,
            onPreview = viewModel::previewBackupForRestore,
            onDismiss = viewModel::closeRestoreDialog
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
        modifier = Modifier
            .fillMaxWidth()
            .orbitTactilePress()
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
