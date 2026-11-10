package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.HouseholdUserAccess
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun PartnerShareBlock(
    sharingState: HouseholdSharingState?,
    isLoading: Boolean,
    onOpenInviteDialog: () -> Unit,
    onUpdateAccess: (shareId: Long, accessLevel: String) -> Unit,
    onRevokeShare: (shareId: Long) -> Unit,
    onRemoveUser: (userId: Long) -> Unit,
    storageMode: StorageMode = StorageMode.LOCAL,
    onUpdateUserAccess: ((userId: Long, accessLevel: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var userToRemove by remember { mutableStateOf<HouseholdUserAccess?>(null) }
    var shareToRevoke by remember { mutableStateOf<HouseholdShare?>(null) }

    userToRemove?.let { user ->
        OrbitConfirmationDialog(
            title = "Remove Member?",
            message = "Remove ${user.displayName.ifBlank { user.email ?: "this member" }} from this household?",
            confirmText = "Remove",
            isDestructive = true,
            onConfirm = {
                onRemoveUser(user.id)
                userToRemove = null
            },
            onDismiss = { userToRemove = null }
        )
    }

    shareToRevoke?.let { share ->
        OrbitConfirmationDialog(
            title = "Revoke Invitation?",
            message = "Stop sharing with ${share.invitedEmail}?",
            confirmText = "Stop Sharing",
            isDestructive = true,
            onConfirm = {
                onRevokeShare(share.id)
                shareToRevoke = null
            },
            onDismiss = { shareToRevoke = null }
        )
    }

    OrbitSectionCard(
        title = "Household Partner Sharing",
        subtitle = "Invite partners to view or collaborate on family finances",
        modifier = modifier
    ) {
        if (storageMode == StorageMode.LOCAL) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = OrbitIcons.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Self-Hosted Feature — Household sharing, partner access, and multi-user permissions require a self-hosted Orbit Money server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (isLoading) {
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
                        val isCurrentUser = user.id == sharingState?.currentUserId
                        val isOwner = user.role.equals("owner", ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = user.displayName.ifBlank { user.email ?: user.username ?: "Household Member" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isOwner) "Owner · Full Access" else "Member · Access: ${if (user.accessLevel == "write") "Read & Write" else "Read Only"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (sharingState?.canRemoveUsers == true && !isCurrentUser && !isOwner) {
                                        IconButton(onClick = { userToRemove = user }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                if (!isOwner && sharingState?.canManageSharing == true && onUpdateUserAccess != null) {
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = user.accessLevel == "write",
                                            onClick = { onUpdateUserAccess(user.id, "write") },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                            icon = {},
                                            label = { Text("Read & Write", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        SegmentedButton(
                                            selected = user.accessLevel == "read",
                                            onClick = { onUpdateUserAccess(user.id, "read") },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                            icon = {},
                                            label = { Text("Read Only", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = share.invitedEmail,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Invited as ${if (share.accessLevel == "write") "Read & Write" else "Read Only"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { shareToRevoke = share }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Revoke Invite",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                if (sharingState?.canManageSharing == true) {
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = share.accessLevel == "write",
                                            onClick = { onUpdateAccess(share.id, "write") },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                            icon = {},
                                            label = { Text("Read & Write", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        SegmentedButton(
                                            selected = share.accessLevel == "read",
                                            onClick = { onUpdateAccess(share.id, "read") },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                            icon = {},
                                            label = { Text("Read Only", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
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
