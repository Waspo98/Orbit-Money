package app.overbay.orbit.ui.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitTextField

@Composable
fun BackupExportDialog(
    jsonText: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Workspace Backup Export") },
        text = {
            Column {
                Text(
                    text = "Copy the backup data below to store a copy off-device:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OrbitTextField(
                    value = jsonText,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = false,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(jsonText))
                    copied = true
                }
            ) {
                Text(if (copied) "Copied!" else "Copy JSON")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}
