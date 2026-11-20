package app.overbay.orbit.ui.settings.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.OrbitTextInputDialog
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Modal bottom sheet displaying exported workspace JSON with one-tap clipboard copy.
 */
@Composable
fun BackupExportSheet(
    jsonText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalOrbitHaptics.current
    var copied by remember(jsonText) { mutableStateOf(false) }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss,
        dismissText = null,
        modifier = modifier,
        title = "Workspace Backup Export",
        subtitle = "Copy the backup JSON below to store a snapshot off-device.",
        bodySpacing = 12.dp
    ) {
        OrbitTextField(
            value = jsonText,
            onValueChange = {},
            readOnly = true,
            singleLine = false,
            maxLines = 10,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 220.dp)
        )

        OutlinedButton(
            onClick = {
                haptics.confirm()
                clipboard.setText(AnnotatedString(jsonText))
                copied = true
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            if (copied) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (copied) "Copied" else "Copy JSON",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Modal bottom sheet for pasting exported backup JSON before previewing restoration.
 */
@Composable
fun BackupRestorePasteSheet(
    jsonText: String,
    errorMessage: String? = null,
    onTextChange: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Preview Restore",
        onConfirm = { onPreview(jsonText) },
        confirmEnabled = jsonText.isNotBlank(),
        errorBannerText = errorMessage,
        modifier = modifier,
        title = "Restore Workspace Backup",
        subtitle = "Paste your exported backup JSON below to preview and restore.",
        bodySpacing = 12.dp
    ) {
        OrbitTextField(
            value = jsonText,
            onValueChange = onTextChange,
            placeholder = { Text("Paste backup data here...") },
            singleLine = false,
            maxLines = 10,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 220.dp)
        )
    }
}

/**
 * Single-field dialog for configuring the Google Cloud Web Client ID on Web PWA builds.
 */
@Composable
fun GoogleDriveClientIdDialog(
    initialClientId: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    OrbitTextInputDialog(
        title = "Google Drive Web Client ID",
        description = "Enter your Google Cloud Web Client ID to enable Google Drive sign-in in web browsers.",
        initialValue = initialClientId,
        label = "Web Client ID",
        placeholder = "123456789-abc.apps.googleusercontent.com",
        confirmText = "Save Client ID",
        onConfirm = { onSave(it.trim()) },
        onDismiss = onDismiss
    )
}
