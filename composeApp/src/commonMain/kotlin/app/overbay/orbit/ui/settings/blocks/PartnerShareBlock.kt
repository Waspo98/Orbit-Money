package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun PartnerShareBlock(
    sharingState: HouseholdSharingState?,
    isLoading: Boolean,
    onOpenInviteDialog: () -> Unit,
    onUpdateAccess: (shareId: Long, accessLevel: String) -> Unit,
    onRevokeShare: (shareId: Long) -> Unit,
    onRemoveUser: (userId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Household Partner Sharing",
        subtitle = "Invite partners to view or collaborate on family finances",
        modifier = modifier
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Loading sharing privileges...", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val users = sharingState?.users ?: emptyList()
            val shares = sharingState?.shares ?: emptyList()

            // Active Household Users
            Text(
                text = "ACTIVE HOUSEHOLD MEMBERS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (users.isEmpty()) {
                    OrbitCompactListRow(
                        title = "Current User (Workspace Owner)",
                        leadingContent = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                } else {
                    users.forEach { user ->
                        OrbitCompactListRow(
                            title = user.displayName.ifBlank { user.email ?: user.username ?: "Household Member" },
                            subtitle = "Role: ${user.role} · Access: ${user.accessLevel}",
                            trailingContent = if (sharingState?.canRemoveUsers == true && user.id != sharingState.currentUserId) {
                                {
                                    IconButton(onClick = { onRemoveUser(user.id) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            } else null
                        )
                    }
                }
            }

            // Pending Partner Invites
            if (shares.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "PENDING EMAIL INVITATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shares.forEach { share ->
                        OrbitCompactListRow(
                            title = share.invitedEmail,
                            subtitle = "Invited as ${share.accessLevel}",
                            trailingContent = {
                                IconButton(onClick = { onRevokeShare(share.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Revoke Invite", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onOpenInviteDialog,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Share With Partner")
            }
        }
    }
}
