package app.overbay.orbit.ui.settings.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner

/**
 * Modal bottom sheet for migrating local workspace data to a self-hosted Orbit Money server.
 */
@Composable
fun MigrateWorkspaceSheet(
    initialServerUrl: String,
    isMigrating: Boolean,
    message: String?,
    onMigrate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl by remember(initialServerUrl) { mutableStateOf(initialServerUrl) }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Start Migration",
        onConfirm = { onMigrate(serverUrl.trim()) },
        confirmEnabled = !isMigrating && serverUrl.isNotBlank(),
        isSaving = isMigrating,
        modifier = modifier,
        title = "Migrate Local Workspace",
        subtitle = "Upload all local accounts, transactions, categories, budgets, and rules to your self-hosted Orbit Money instance.",
        bodySpacing = 14.dp
    ) {
        OrbitTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server Base URL") },
            placeholder = { Text("https://orbit.yourdomain.com") },
            singleLine = true,
            enabled = !isMigrating,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (!message.isNullOrBlank()) {
            val isErrorLike = message.contains("fail", ignoreCase = true) ||
                message.contains("error", ignoreCase = true) ||
                message.contains("invalid", ignoreCase = true)
            SettingsCalloutBanner(
                icon = OrbitIcons.InfoCircle,
                text = message,
                isWarning = isErrorLike
            )
        } else {
            SettingsCalloutBanner(
                icon = OrbitIcons.InfoCircle,
                text = "You will be prompted to authenticate with your server if you are not already signed in."
            )
        }
    }
}
