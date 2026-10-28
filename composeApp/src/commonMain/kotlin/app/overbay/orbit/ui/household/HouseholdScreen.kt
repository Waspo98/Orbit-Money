package app.overbay.orbit.ui.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.household.components.HouseholdInspectorPane
import app.overbay.orbit.ui.household.components.HouseholdMembersCard
import app.overbay.orbit.ui.household.dialogs.HouseholdMemberEditorSheet
import app.overbay.orbit.ui.household.dialogs.InviteMemberDialog
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun HouseholdScreen(
    viewModel: HouseholdViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()
    var editingName by remember(uiState.householdInfo.name) { mutableStateOf(uiState.householdInfo.name) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (
            uiState.inspectingMember != null ||
                uiState.showMemberEditor ||
                uiState.showInviteDialog
            )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                OrbitFloatingActionButton(
                    visible = heroScrollState.isFabVisible,
                    onClick = { viewModel.openMemberEditor(null) },
                    contentDescription = "New Member"
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                MorphingPageHero(
                    kicker = "INCOME AND BENEFITS",
                    title = "Household",
                    subtitle = "Manage household members, paychecks, benefits, and tax filing profile.",
                    canNavigateBack = true,
                    onBack = onBack,
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, barHeight ->
                        val grossYearly = MoneyUtils.formatCents(uiState.totalHouseholdGrossIncomeCents, showCents = false)
                        CanopySurfacePill(
                            expandedText = "${uiState.members.size} Members · $grossYearly/yr Gross",
                            compactText = "$grossYearly/yr",
                            isCompact = isCompact,
                            leadingIcon = OrbitIcons.Household,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        CanopyIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "New Member",
                            onClick = { viewModel.openMemberEditor(null) },
                            buttonSize = barHeight
                        )

                        CanopySettingsButton(
                            buttonSize = barHeight,
                            onClick = { onNavigate(OrbitNavKey.Settings) }
                        )
                    }
                )

                OrbitMorphingSplitLayout(
                    isDetailOpen = isDetailOpen,
                    onDismissDetail = viewModel::dismissInspectorPane,
                    masterWeightWhenOpen = 0.56f,
                    modifier = Modifier.fillMaxSize(),
                    masterContent = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
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
                                    inspectingMemberId = if (isTwoPane) uiState.inspectingMemberId else null,
                                    onInspectMember = if (isTwoPane) {
                                        { member -> viewModel.selectMemberForInspect(member.id) }
                                    } else {
                                        null
                                    },
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
                    },
                    detailContent = {
                        HouseholdInspectorPane(
                            state = uiState,
                            onOpenEditMember = { member -> viewModel.openMemberEditor(member) },
                            onSaveMember = viewModel::saveMember,
                            onCloseEditMember = viewModel::closeMemberEditor,
                            onDeleteMember = viewModel::deleteMember,
                            onInviteMember = viewModel::inviteMember,
                            onCloseInvite = viewModel::closeInviteDialog,
                            onDismissPane = viewModel::dismissInspectorPane
                        )
                    }
                )
            }
        }

        if (!isTwoPane && uiState.showInviteDialog) {
            InviteMemberDialog(
                onDismiss = viewModel::closeInviteDialog,
                onInvite = viewModel::inviteMember
            )
        }

        if (!isTwoPane && uiState.showMemberEditor) {
            HouseholdMemberEditorSheet(
                member = uiState.editingMember,
                onDismiss = viewModel::closeMemberEditor,
                onSave = viewModel::saveMember
            )
        }
    }
}
