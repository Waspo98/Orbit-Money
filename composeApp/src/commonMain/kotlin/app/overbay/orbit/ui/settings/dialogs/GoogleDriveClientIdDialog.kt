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
        description = "Enter your Google Cloud Web Client ID to enable Google Drive sign-in in web browsers.",
        initialValue = initialClientId,
        label = "Web Client ID",
        placeholder = "123456789-abc.apps.googleusercontent.com",
        confirmText = "Save Client ID",
        onConfirm = onSave,
        onDismiss = onDismiss
    )
}
