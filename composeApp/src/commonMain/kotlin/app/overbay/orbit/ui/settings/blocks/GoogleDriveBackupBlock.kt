package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    OrbitSectionCard(
        title = "Google Drive Cloud Backup",
        subtitle = "Encrypted snapshots stored inside private Google Drive appDataFolder",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Google Drive Switch
            OrbitSwitchRow(
                title = "Private Google Drive Sync",
                subtitle = if (isDriveSyncEnabled) {
                    "Backs up to hidden appDataFolder ${if (lastSyncAt != null) "• Last: $lastSyncAt" else ""}"
                } else {
                    "Store encrypted snapshot in personal Google Drive"
                },
                checked = isDriveSyncEnabled,
                onCheckedChange = onToggleDriveSync
            )

            // Connection status and OAuth Client ID
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account Status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = connectedEmail ?: if (isDriveSyncEnabled) "Connected" else "Not Connected",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDriveSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Web Client ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onOpenClientIdDialog) {
                            Text(if (clientId.isNotBlank()) "Configured" else "Configure")
                        }
                    }
                }
            }

            // Action buttons
            if (isDriveSyncEnabled) {
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
                        onClick = onRestoreFromDrive,
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
