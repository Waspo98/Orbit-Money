package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

@OptIn(ExperimentalMaterial3Api::class)
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
            InviteMemberFormFields(
                email = email,
                onEmailChange = { email = it },
                selectedRole = selectedRole,
                onRoleChange = { selectedRole = it }
            )
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

@Composable
fun InviteMemberContent(
    onDismiss: () -> Unit,
    onInvite: (email: String, role: HouseholdRole) -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(HouseholdRole.MEMBER) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = "Invite Household Member",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        InviteMemberFormFields(
            email = email,
            onEmailChange = { email = it },
            selectedRole = selectedRole,
            onRoleChange = { selectedRole = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        onInvite(email, selectedRole)
                    }
                },
                enabled = email.contains("@") && email.contains("."),
                modifier = Modifier.weight(1f)
            ) {
                Text("Send Invite")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMemberFormFields(
    email: String,
    onEmailChange: (String) -> Unit,
    selectedRole: HouseholdRole,
    onRoleChange: (HouseholdRole) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Send an invite to grant access to view and collaborate on this household's financial plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
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
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val roles = listOf(
                HouseholdRole.MEMBER to "Member (Edit)",
                HouseholdRole.ADMIN to "Admin (Full)"
            )
            roles.forEachIndexed { index, (role, label) ->
                SegmentedButton(
                    selected = selectedRole == role,
                    onClick = { onRoleChange(role) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size),
                    icon = {},
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}
