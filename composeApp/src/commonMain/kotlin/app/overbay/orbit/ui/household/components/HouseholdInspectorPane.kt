package app.overbay.orbit.ui.household.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRole
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.household.HouseholdUiState
import app.overbay.orbit.ui.household.dialogs.HouseholdMemberEditorContent
import app.overbay.orbit.ui.household.dialogs.InviteMemberContent
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun HouseholdInspectorPane(
    state: HouseholdUiState,
    onOpenEditMember: (HouseholdMember) -> Unit,
    onSaveMember: (HouseholdMember) -> Unit,
    onCloseEditMember: () -> Unit,
    onDeleteMember: (Long) -> Unit,
    onInviteMember: (email: String, role: HouseholdRole) -> Unit,
    onCloseInvite: () -> Unit,
    onDismissPane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = when {
        state.showInviteDialog -> "INVITE"
        state.showMemberEditor -> "MEMBER_EDIT"
        state.inspectingMember != null -> "MEMBER_DETAIL"
        else -> "NONE"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = activeMode,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessLow))
            },
            label = "HouseholdInspectorModeTransition"
        ) { mode ->
            when (mode) {
                "INVITE" -> {
                    InviteMemberContent(
                        onDismiss = {
                            if (state.inspectingMember != null) {
                                onCloseInvite()
                            } else {
                                onDismissPane()
                            }
                        },
                        onInvite = onInviteMember
                    )
                }
                "MEMBER_EDIT" -> {
                    HouseholdMemberEditorContent(
                        member = state.editingMember,
                        onDismiss = {
                            if (state.inspectingMember != null) {
                                onCloseEditMember()
                            } else {
                                onDismissPane()
                            }
                        },
                        onSave = onSaveMember
                    )
                }
                "MEMBER_DETAIL" -> {
                    val member = state.inspectingMember
                    if (member != null) {
                        HouseholdMemberDetailView(
                            member = member,
                            onOpenEdit = { onOpenEditMember(member) },
                            onDelete = { onDeleteMember(member.id) },
                            onDismissPane = onDismissPane
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun HouseholdMemberDetailView(
    member: HouseholdMember,
    onOpenEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissPane: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = member.role.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(onClick = onDismissPane) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Inspector",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Compensation Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "COMPENSATION & INCOME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = MoneyUtils.formatCents(member.grossIncomeAnnualCents) + " / yr",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Monthly Gross: ${MoneyUtils.formatCents(member.grossIncomeAnnualCents / 12)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Employment & Benefits Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PROFILE & RETIREMENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                MemberDetailRow("Employer", member.employer ?: "Not specified")
                MemberDetailRow("Job Title", member.jobTitle ?: "Not specified")
                MemberDetailRow("Retirement Plan", member.retirementAccountType ?: "401k")
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    haptics.tick()
                    onOpenEdit()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Member")
            }

            OutlinedButton(
                onClick = {
                    haptics.reject()
                    onDelete()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
private fun MemberDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
