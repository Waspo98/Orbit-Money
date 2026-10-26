package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdRole

@Composable
fun InviteMemberDialog(
    onDismiss: () -> Unit,
    onInvite: (email: String, role: HouseholdRole) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(HouseholdRole.MEMBER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Invite Household Member",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Send an invite to grant access to view and collaborate on this household's financial plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Member Email Address") },
                    placeholder = { Text("partner@example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Permission Role",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == HouseholdRole.MEMBER,
                        onClick = { selectedRole = HouseholdRole.MEMBER },
                        label = { Text("Member (Edit)") }
                    )
                    FilterChip(
                        selected = selectedRole == HouseholdRole.ADMIN,
                        onClick = { selectedRole = HouseholdRole.ADMIN },
                        label = { Text("Admin (Full)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        onInvite(email, selectedRole)
                    }
                },
                enabled = email.contains("@") && email.contains(".")
            ) {
                Text("Send Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
