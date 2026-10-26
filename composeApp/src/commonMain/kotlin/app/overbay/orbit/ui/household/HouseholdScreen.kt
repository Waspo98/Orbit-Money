package app.overbay.orbit.ui.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.HeroStatItem
import app.overbay.orbit.ui.components.HeroStatTone
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitPrimaryPillButton
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.household.components.HouseholdMembersCard
import app.overbay.orbit.ui.household.dialogs.HouseholdMemberEditorSheet
import app.overbay.orbit.ui.household.dialogs.InviteMemberDialog

@Composable
fun HouseholdScreen(
    viewModel: HouseholdViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState(maxHeight = 220.dp, minHeight = 64.dp)
    var editingName by remember(uiState.householdInfo.name) { mutableStateOf(uiState.householdInfo.name) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(heroScrollState.nestedScrollConnection)
    ) {
        // Edge-to-Edge Curved Green Canopy Hero
        MorphingPageHero(
            kicker = "INCOME AND BENEFITS",
            title = "Household",
            subtitle = "Manage household members, paychecks, benefits, and tax filing profile.",
            canNavigateBack = true,
            onBack = { onNavigate(OrbitNavKey.Dashboard) },
            scrollState = heroScrollState,
            onPrimaryAddClick = { viewModel.openMemberEditor(null) },
            stats = listOf(
                HeroStatItem("MEMBERS", "${uiState.members.size}", HeroStatTone.GOOD),
                HeroStatItem("GROSS INCOME", MoneyUtils.formatCents(uiState.totalHouseholdGrossIncomeCents), HeroStatTone.DEFAULT),
                HeroStatItem("SHARES", "${uiState.shares.size}", HeroStatTone.DEFAULT),
                HeroStatItem("STORAGE", if (uiState.storageMode == StorageMode.LOCAL) "Local" else "Cloud", HeroStatTone.GOOD)
            )
        )

        // Action Row Below Canopy Hero
        OrbitPageActionRow {
            OrbitPrimaryPillButton(
                text = "+ New Member",
                onClick = { viewModel.openMemberEditor(null) }
            )
            OrbitSecondaryPillButton(
                text = "Invite Partner",
                icon = Icons.Default.Email,
                onClick = viewModel::openInviteDialog
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Household Profile Card
            item {
                OrbitSectionCard(
                    title = "Household Profile",
                    subtitle = "Workspace identity and synchronization mode"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = { editingName = it },
                            label = { Text("Household Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = { viewModel.updateHouseholdName(editingName) },
                            enabled = editingName.isNotBlank() && editingName != uiState.householdInfo.name
                        ) {
                            Text("Save")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage Environment",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (uiState.storageMode == StorageMode.LOCAL) "Local Device (Offline)" else "Self-Hosted Multi-User Server",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 2. Household Members List
            item {
                HouseholdMembersCard(
                    members = uiState.members,
                    totalGrossIncomeCents = uiState.totalHouseholdGrossIncomeCents,
                    onAddMember = { viewModel.openMemberEditor(null) },
                    onEditMember = { viewModel.openMemberEditor(it) },
                    onDeleteMember = viewModel::deleteMember
                )
            }

            // 3. Multi-User Collaboration / Shared Invites
            item {
                OrbitSectionCard(
                    title = "Shared Access & Invites",
                    subtitle = "Collaborate with partners or financial advisors",
                    action = {
                        Button(onClick = viewModel::openInviteDialog) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Invite Partner", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                ) {
                    if (uiState.shares.isEmpty()) {
                        Text(
                            text = "No active invites or shared members. Invite a spouse or co-planner to share access to this household.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        uiState.shares.forEach { share ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = share.invitedEmail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Role: ${share.role.name} • Status: ${if (share.acceptedAt != null) "Accepted" else "Pending Invite"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.revokeInvite(share.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Revoke",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (uiState.showInviteDialog) {
        InviteMemberDialog(
            onDismiss = viewModel::closeInviteDialog,
            onInvite = viewModel::inviteMember
        )
    }

    if (uiState.showMemberEditor) {
        HouseholdMemberEditorSheet(
            member = uiState.editingMember,
            onDismiss = viewModel::closeMemberEditor,
            onSave = viewModel::saveMember
        )
    }
}
