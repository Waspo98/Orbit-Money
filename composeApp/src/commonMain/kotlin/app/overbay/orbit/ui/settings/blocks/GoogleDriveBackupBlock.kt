package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow

@Composable
fun GoogleDriveBackupBlock(
    isDriveSyncEnabled: Boolean,
    lastSyncAt: String?,
    clientId: String = "",
    connectedEmail: String? = null,
    onToggleDriveSync: (Boolean) -> Unit,
    onOpenClientIdDialog: () -> Unit = {},
    onBackupNow: () -> Unit = {},
    onRestoreFromDrive: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onExportJsonBackup: (() -> Unit)? = null,
    onRestoreJsonBackup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showRestoreConfirm by remember { mutableStateOf(false) }

    if (showRestoreConfirm) {
        OrbitConfirmationDialog(
            title = "Restore from Google Drive?",
            message = "Restore your database from Google Drive? This will replace your current workspace data with the cloud backup.",
            confirmText = "Restore",
            isDestructive = true,
            onConfirm = {
                showRestoreConfirm = false
                onRestoreFromDrive()
            },
            onDismiss = { showRestoreConfirm = false }
        )
    }

    OrbitSectionCard(
        title = "Google Drive Cloud Backup",
        subtitle = "Private backups stored in your personal Google Drive",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OrbitSwitchRow(
                title = "Google Drive Sync",
                subtitle = if (isDriveSyncEnabled) {
                    "Private backup active ${if (lastSyncAt != null) "• Last backup: $lastSyncAt" else ""}"
                } else {
                    "Automatically back up your workspace to your personal Google Drive"
                },
                checked = isDriveSyncEnabled,
                onCheckedChange = onToggleDriveSync
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OrbitMiniStatRow(
                        label = "Account Status",
                        value = connectedEmail ?: if (isDriveSyncEnabled) "Connected" else "Not Connected",
                        valueColor = if (isDriveSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Google Sign-In Setup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onOpenClientIdDialog) {
                            Text(if (clientId.isNotBlank()) "Configured" else "Configure")
                        }
                    }
                }
            }

            OrbitAccordionVisibility(visible = isDriveSyncEnabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onBackupNow,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back Up Now")
                        }
                        OutlinedButton(
                            onClick = { showRestoreConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore Drive")
                        }
                    }

                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disconnect Drive")
                    }
                }
            }
        }
    }
}
