package app.overbay.orbit.ui.settings.dialogs

import androidx.compose.runtime.Composable
import app.overbay.orbit.ui.components.OrbitTextInputDialog

@Composable
fun GoogleDriveClientIdDialog(
    initialClientId: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    OrbitTextInputDialog(
        title = "Google Drive Web Client ID",
        description = "Configure your Google Cloud OAuth 2.0 Web Client ID to enable private appDataFolder sync in Web PWA browsers.",
        initialValue = initialClientId,
        label = "Web Client ID",
        placeholder = "123456789-abc.apps.googleusercontent.com",
        confirmText = "Save Client ID",
        onConfirm = onSave,
        onDismiss = onDismiss
    )
}
