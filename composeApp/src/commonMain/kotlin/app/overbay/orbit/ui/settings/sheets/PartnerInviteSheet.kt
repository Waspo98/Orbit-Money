package app.overbay.orbit.ui.settings.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner

/**
 * Modal bottom sheet for inviting a household partner or spouse with selectable access permissions.
 */
@Composable
fun PartnerInviteSheet(
    isSending: Boolean = false,
    errorMessage: String? = null,
    onSendInvite: (email: String, role: String, accessLevel: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var accessLevel by remember { mutableStateOf("write") }
    val trimmedEmail = email.trim()
    val isEmailValid = trimmedEmail.contains("@") && trimmedEmail.contains(".")

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Send Invite",
        onConfirm = { onSendInvite(trimmedEmail, "member", accessLevel) },
        confirmEnabled = isEmailValid && !isSending,
        isSaving = isSending,
        errorBannerText = errorMessage,
        modifier = modifier,
        title = "Share With Partner",
        subtitle = "Invite a household partner or spouse to collaborate on budgeting, accounts, and financial goals.",
        bodySpacing = 14.dp
    ) {
        OrbitTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Partner Email") },
            placeholder = { Text("partner@example.com") },
            singleLine = true,
            enabled = !isSending,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OrbitFieldLabel(text = "Permission Level")
            OrbitSegmentedTabRow(
                items = listOf("write", "read"),
                selected = accessLevel,
                onSelect = { accessLevel = it },
                label = { level -> if (level == "write") "Read & Write" else "Read Only" }
            )
        }

        SettingsCalloutBanner(
            icon = OrbitIcons.InfoCircle,
            text = if (accessLevel == "write") {
                "Read & Write allows your partner to add and edit transactions, budgets, categories, and goals."
            } else {
                "Read Only allows your partner to view balances, budgets, and reports without modifying data."
            }
        )
    }
}
