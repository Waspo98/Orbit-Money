package app.overbay.orbit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.settings.blocks.GoogleDriveBackupBlock
import app.overbay.orbit.ui.settings.blocks.SecurityBiometricsBlock
import app.overbay.orbit.ui.settings.blocks.SimpleFinBankSyncBlock
import app.overbay.orbit.ui.settings.blocks.StorageSyncModeBlock
import app.overbay.orbit.ui.settings.blocks.ThemeAppearanceBlock
import app.overbay.orbit.util.LocalFileImportExportHelper

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val fileHelper = LocalFileImportExportHelper.current

    var restoreJsonText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        MorphingPageHero(
            title = "Settings",
            subtitle = "Data storage, bank feeds, cloud backup & personalization"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                onOpenLoginScreen = { onNavigate(OrbitNavKey.Login) },
                onLogout = {
                    viewModel.logout()
                    onNavigate(OrbitNavKey.Login)
                }
            )

            // 2. SimpleFIN Bank Feeds
            SimpleFinBankSyncBlock(
                token = uiState.preferences.simpleFinToken,
                onTokenChange = viewModel::setSimpleFinToken,
                onTriggerSync = viewModel::triggerSync
            )

            // 3. Google Drive & File Backup
            GoogleDriveBackupBlock(
                isDriveSyncEnabled = uiState.preferences.googleDriveSyncEnabled,
                lastSyncAt = uiState.preferences.lastGoogleDriveSyncAt,
                onToggleDriveSync = viewModel::setGoogleDriveSync,
                onExportJsonBackup = {
                    val json = viewModel.exportBackup()
                    fileHelper.exportFile("orbit-backup.orbit.json", json, "application/json")
                },
                onRestoreJsonBackup = viewModel::openRestoreDialog
            )

            // 4. Appearance & Theme
            ThemeAppearanceBlock(
                preferences = uiState.preferences,
                onPresetChange = viewModel::setThemePreset,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor
            )

            // 5. Security & Biometrics
            SecurityBiometricsBlock(
                preferences = uiState.preferences,
                onBiometricLockChange = viewModel::setBiometricLock,
                onHapticsChange = viewModel::setHapticsEnabled,
                onMhaTrackerChange = viewModel::setMhaTrackerEnabled,
                onResetData = viewModel::openResetConfirmation
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Export Dialog
    if (uiState.showBackupExportDialog && uiState.backupExportJson != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeBackupExportDialog,
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
                Button(onClick = viewModel::closeBackupExportDialog) {
                    Text("Done")
                }
            }
        )
    }

    // Restore Dialog
    if (uiState.showRestoreDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeRestoreDialog,
            title = { Text("Restore Workspace Backup") },
            text = {
                Column {
                    Text(
                        text = "Select a .orbit.json backup file or paste a snapshot below to replace local data:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            fileHelper.pickAndImportFile("application/json") { content ->
                                restoreJsonText = content
                            }
                        }
                    ) {
                        Text("Choose .orbit.json File")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        placeholder = { Text("Paste JSON here...") },
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.restoreBackup(restoreJsonText) },
                    enabled = restoreJsonText.isNotBlank()
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeRestoreDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (uiState.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::closeResetConfirmation,
            title = { Text("Reset Workspace?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will erase all accounts, transactions, budgets and rules on this device and reset to a clean empty state. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::resetToDemoData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeResetConfirmation) {
                    Text("Cancel")
                }
            }
        )
    }
}
