package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner
import app.overbay.orbit.ui.settings.components.SettingsDangerCard
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.PlatformRuntime
import kotlinx.coroutines.launch

fun LazyListScope.dataBackupsSectionItems(
    preferences: OrbitPreferences,
    storageMode: StorageMode,
    isSyncing: Boolean,
    onToggleDriveSync: (Boolean) -> Unit,
    onOpenClientIdDialog: () -> Unit,
    onBackupToDrive: () -> Unit,
    onRequestRestoreFromDrive: () -> Unit,
    onRequestDisconnectDrive: () -> Unit,
    onExportCsv: suspend () -> String,
    onExportBudgetingJson: suspend () -> String,
    onExportBackupToFile: ((String) -> Unit) -> Unit,
    onOpenBackupExportJsonDialog: () -> Unit,
    onPickRestoreFile: (String) -> Unit,
    onOpenRestorePasteDialog: () -> Unit,
    onRequestReapplyRules: () -> Unit,
    onRequestLoadSampleData: () -> Unit,
    onRequestResetWorkspace: () -> Unit
) {
    val btnShape = RoundedCornerShape(14.dp)

    item(key = "data_google_drive") {
        val isDriveEnabled = preferences.googleDriveSyncEnabled
        val lastSync = preferences.lastGoogleDriveSyncAt

        OrbitSectionCard(
            title = "Google Drive Backup",
            subtitle = "Encrypted snapshots in your personal Google Drive"
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OrbitSwitchRow(
                    title = "Google Drive Sync",
                    subtitle = if (isDriveEnabled && !lastSync.isNullOrBlank()) "Last backup: $lastSync" else "Back up your workspace to your personal Google Drive",
                    checked = isDriveEnabled,
                    onCheckedChange = onToggleDriveSync,
                    showContainer = false
                )
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                OrbitMiniStatRow(
                    label = "Status",
                    value = if (isDriveEnabled) "Connected" else "Not Connected",
                    valueColor = if (isDriveEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (PlatformRuntime.isWeb) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Web Client ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onOpenClientIdDialog) {
                            Text(if (preferences.googleDriveClientId.isNotBlank()) "Configured" else "Configure")
                        }
                    }
                }

                OrbitAccordionVisibility(visible = isDriveEnabled) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onBackupToDrive,
                                enabled = !isSyncing,
                                shape = btnShape,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text("Back Up Now", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onRequestRestoreFromDrive,
                                enabled = !isSyncing,
                                shape = btnShape,
                                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text("Restore Drive", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        OutlinedButton(
                            onClick = onRequestDisconnectDrive,
                            enabled = !isSyncing,
                            shape = btnShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Disconnect Drive", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    item(key = "data_exports_backups") {
        val fileHelper = LocalFileImportExportHelper.current
        val scope = rememberCoroutineScope()

        OrbitSectionCard(
            title = "Exports & Workspace Backups",
            subtitle = "Spreadsheet exports and complete portable workspace snapshots"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Spreadsheet Export",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csv = onExportCsv()
                                fileHelper.exportFile("orbit-transactions.csv", csv, "text/csv")
                            }
                        },
                        shape = btnShape,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Export CSV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = onExportBudgetingJson()
                                fileHelper.exportFile("orbit-budgeting.json", json, "application/json")
                            }
                        },
                        shape = btnShape,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Export JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                Text(
                    text = "Full Workspace Backup",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SettingsCalloutBanner(
                    icon = OrbitIcons.InfoCircle,
                    text = "SimpleFIN bank credentials are not included in backups and must be reconnected after restoring."
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onExportBackupToFile { payload ->
                                fileHelper.exportFile("orbit-backup.orbit.json", payload, "application/json")
                            }
                        },
                        shape = btnShape,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Export Backup", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            fileHelper.pickAndImportFile("application/json") { raw ->
                                onPickRestoreFile(raw)
                            }
                        },
                        shape = btnShape,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Restore File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenBackupExportJsonDialog) {
                        Text("Copy Backup JSON")
                    }
                    TextButton(onClick = onOpenRestorePasteDialog) {
                        Text("Paste Backup JSON")
                    }
                }
            }
        }
    }

    item(key = "data_rules_maintenance") {
        OrbitSectionCard(
            title = "Rules Maintenance",
            subtitle = "Re-evaluate historical transactions against your active rules"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Manual edits remain protected; only rule-owned fields may update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onRequestReapplyRules,
                    shape = btnShape,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Reapply All Rules", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    item(key = "data_danger_zone") {
        SettingsDangerCard(
            title = "Danger Zone",
            subtitle = "Destructive actions that replace or erase workspace records"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (storageMode == StorageMode.LOCAL) {
                    OutlinedButton(
                        onClick = onRequestLoadSampleData,
                        shape = btnShape,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Load Sample Data", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OutlinedButton(
                    onClick = onRequestResetWorkspace,
                    shape = btnShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Reset Workspace", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
