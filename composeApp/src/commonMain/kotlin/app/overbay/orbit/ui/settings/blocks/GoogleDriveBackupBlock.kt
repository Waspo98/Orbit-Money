package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun GoogleDriveBackupBlock(
    isDriveSyncEnabled: Boolean,
    lastSyncAt: String?,
    onToggleDriveSync: (Boolean) -> Unit,
    onExportJsonBackup: () -> Unit,
    onRestoreJsonBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Cloud & File Backup",
        subtitle = "Google Drive appDataFolder sync and portable .orbit.json workspace snapshots",
        modifier = modifier
    ) {
        // Google Drive Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Drive Private Sync",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isDriveSyncEnabled) {
                        "Backs up to hidden appDataFolder ${if (lastSyncAt != null) "• Last synced: $lastSyncAt" else ""}"
                    } else {
                        "Store encrypted snapshot in personal Google Drive"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isDriveSyncEnabled,
                onCheckedChange = onToggleDriveSync
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export / Import buttons
        Text(
            text = "Portable Workspace Snapshots",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onExportJsonBackup,
                modifier = Modifier.weight(1f)
            ) {
                Text("Export JSON")
            }
            Button(
                onClick = onRestoreJsonBackup,
                modifier = Modifier.weight(1f)
            ) {
                Text("Restore JSON")
            }
        }
    }
}
