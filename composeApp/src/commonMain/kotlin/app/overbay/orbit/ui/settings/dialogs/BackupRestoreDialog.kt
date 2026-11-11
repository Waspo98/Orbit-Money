package app.overbay.orbit.ui.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitTextField

@Composable
fun BackupRestoreDialog(
    jsonText: String,
    onTextChange: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Restore Workspace Backup") },
        text = {
            Column {
                Text(
                    text = "Paste your exported backup data below to preview and restore:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OrbitTextField(
                    value = jsonText,
                    onValueChange = onTextChange,
                    placeholder = { Text("Paste backup data here...") },
                    singleLine = false,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPreview(jsonText) },
                enabled = jsonText.isNotBlank()
            ) { Text("Preview Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
