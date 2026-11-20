package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.HouseholdUserAccess
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner

@Composable
fun PartnerSharingCard(
    storageMode: StorageMode,
    sharingState: HouseholdSharingState?,
    isLoading: Boolean,
    onOpenInviteDialog: () -> Unit,
    onUpdateAccess: (shareId: Long, accessLevel: String) -> Unit,
    onUpdateUserAccess: (userId: Long, accessLevel: String) -> Unit,
    onRequestRemoveUser: (HouseholdUserAccess) -> Unit,
    onRequestRevokeShare: (HouseholdShare) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Household Sharing",
        subtitle = "Invite family members to view or collaborate on finances",
        modifier = modifier
    ) {
        when {
            storageMode == StorageMode.LOCAL -> {
                SettingsCalloutBanner(
                    icon = OrbitIcons.InfoCircle,
                    text = "Household partner sharing and multi-user permissions require a self-hosted Orbit Money server."
                )
            }

            isLoading && sharingState == null -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Loading household members...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val users = sharingState?.users ?: emptyList()
                val shares = sharingState?.shares ?: emptyList()
                val canManageSharing = sharingState?.canManageSharing == true
                val canRemoveUsers = sharingState?.canRemoveUsers == true

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Active Members",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (users.isEmpty()) {
                        OrbitCompactListRow(
                            title = "Current User (Workspace Owner)",
                            showTopDivider = false,
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            users.forEachIndexed { index, user ->
                                if (index > 0) HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                                val isCurrentUser = user.id == sharingState?.currentUserId
                                val isOwner = user.role.equals("owner", ignoreCase = true)
                                val displayLabel = user.displayName.ifBlank { user.email ?: user.username ?: "Household Member" }
                                val roleSummary = if (isOwner) "Owner · Full Access" else "Member · ${if (user.accessLevel == "write") "Read & Write" else "Read Only"}"

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(displayLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(roleSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        if (canRemoveUsers && !isCurrentUser && !isOwner) {
                                            IconButton(onClick = { onRequestRemoveUser(user) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove Member", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    if (!isOwner && canManageSharing) {
                                        OrbitSegmentedTabRow(
                                            items = listOf("write", "read"),
                                            selected = user.accessLevel,
                                            onSelect = { onUpdateUserAccess(user.id, it) },
                                            label = { if (it == "write") "Read & Write" else "Read Only" }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (shares.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                        Text("Pending Invitations", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            shares.forEachIndexed { index, share ->
                                if (index > 0) HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(share.invitedEmail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Invited as ${if (share.accessLevel == "write") "Read & Write" else "Read Only"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (canManageSharing) {
                                            IconButton(onClick = { onRequestRevokeShare(share) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Revoke Invitation", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    if (canManageSharing) {
                                        OrbitSegmentedTabRow(
                                            items = listOf("write", "read"),
                                            selected = share.accessLevel,
                                            onSelect = { onUpdateAccess(share.id, it) },
                                            label = { if (it == "write") "Read & Write" else "Read Only" }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (canManageSharing) {
                        Spacer(modifier = Modifier.size(2.dp))
                        Button(
                            onClick = onOpenInviteDialog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Share With Partner", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
