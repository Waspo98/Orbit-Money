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
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.household.HouseholdCalculations
import app.overbay.orbit.ui.household.HouseholdUiState
import app.overbay.orbit.ui.household.dialogs.HouseholdMemberEditorContent
import app.overbay.orbit.ui.household.dialogs.InviteMemberContent
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

@Composable
fun HouseholdInspectorPane(
    state: HouseholdUiState,
    onOpenEditMember: (HouseholdMember) -> Unit,
    onSaveMember: (HouseholdMember, String?) -> Unit,
    onCloseEditMember: () -> Unit,
    onDeleteMember: (HouseholdMember) -> Unit,
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
                        availableAccounts = state.availableAccounts,
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
                            includeHsaInRetirementRate = state.includeHsaInRetirementRate,
                            onOpenEdit = { onOpenEditMember(member) },
                            onDelete = { onDeleteMember(member) },
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
    includeHsaInRetirementRate: Boolean,
    onOpenEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissPane: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val netPay = HouseholdCalculations.memberNetPayAnnual(member)
    val match = HouseholdCalculations.memberEmployerMatchAnnual(member)
    val rate = HouseholdCalculations.memberRetirementRatePercent(member, includeHsaInRetirementRate)
    val age = LocalDateUtils.ageFromBirthDate(member.birthDate)

    val roleLabel = when (member.role.lowercase()) {
        "child" -> "Child"
        else -> "Adult"
    }

    val payFreqLabel = when (member.payFrequency.lowercase()) {
        "weekly" -> "Weekly"
        "biweekly" -> "Biweekly"
        "semimonthly" -> "Semimonthly"
        "monthly" -> "Monthly"
        "annual" -> "Annual"
        "none" -> "No Cadence"
        else -> member.payFrequency.replaceFirstChar { it.uppercase() }
    }

    val accountsLabel = if (member.retirementAccounts.isNotEmpty()) {
        "${member.retirementAccounts.size} linked ${if (member.retirementAccounts.size == 1) "account" else "accounts"}"
    } else {
        member.retirementAccountType?.uppercase() ?: "401K"
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrbitBadge(
                        text = roleLabel,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (age != null) {
                        OrbitBadge(
                            text = "Age $age",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

        // 4-Metric Summary Grid
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ANNUAL COMPENSATION METRICS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InspectorMetricTile(
                        label = "Gross",
                        value = MoneyUtils.formatCents(member.grossIncomeAnnualCents, showCents = false),
                        modifier = Modifier.weight(1f)
                    )
                    InspectorMetricTile(
                        label = "Net Pay",
                        value = MoneyUtils.formatCents(netPay, showCents = false),
                        modifier = Modifier.weight(1f)
                    )
                    InspectorMetricTile(
                        label = "Employer Match",
                        value = MoneyUtils.formatCents(match, showCents = false),
                        modifier = Modifier.weight(1f)
                    )
                    InspectorMetricTile(
                        label = "Retirement Rate",
                        value = "${rate.roundToInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Employment & Benefits Card with Meta Chips
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
                MemberDetailRow("Pay Cadence", payFreqLabel)
                MemberDetailRow("Retirement Plan", accountsLabel)
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
private fun InspectorMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MemberDetailRow(
    label: String,
    value: String
) {
    OrbitMiniStatRow(label = label, value = value)
}
