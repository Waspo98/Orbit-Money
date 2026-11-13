package app.overbay.orbit.ui.household.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.ui.household.HouseholdCalculations
import kotlin.math.roundToInt

@Composable
fun HouseholdMembersCard(
    members: List<HouseholdMember>,
    totalGrossIncomeCents: Cents,
    onAddMember: () -> Unit,
    onEditMember: (HouseholdMember) -> Unit,
    onDeleteMember: (HouseholdMember) -> Unit,
    inspectingMemberId: Long? = null,
    onInspectMember: ((HouseholdMember) -> Unit)? = null,
    includeHsaInRetirementRate: Boolean = true,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Household Members",
        subtitle = "Profiles, income earners & dependent planning",
        action = {
            Button(
                onClick = onAddMember,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Add Member", style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = modifier
    ) {
        // Income summary header
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Household Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = MoneyUtils.formatCents(totalGrossIncomeCents, showCents = false) + " / yr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${members.size} Member${if (members.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = members.isEmpty(),
            transitionSpec = { orbitFadeContentTransform() },
            label = "householdMembersEmpty"
        ) { isEmpty ->
            if (isEmpty) {
                EmptyStateCard(
                    title = "No Household Members",
                    description = "Add partners, children, or dependents to track household allowances."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    members.forEach { member ->
                        val isInspected = inspectingMemberId == member.id
                        MemberSummaryCard(
                            member = member,
                            isInspected = isInspected,
                            includeHsaInRetirementRate = includeHsaInRetirementRate,
                            onClick = {
                                if (onInspectMember != null) {
                                    onInspectMember(member)
                                } else {
                                    onEditMember(member)
                                }
                            },
                            onEdit = { onEditMember(member) },
                            onDelete = { onDeleteMember(member) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberSummaryCard(
    member: HouseholdMember,
    isInspected: Boolean,
    includeHsaInRetirementRate: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val netPay = HouseholdCalculations.memberNetPayAnnual(member)
    val match = HouseholdCalculations.memberEmployerMatchAnnual(member)
    val rate = HouseholdCalculations.memberRetirementRatePercent(member, includeHsaInRetirementRate)
    val age = LocalDateUtils.ageFromBirthDate(member.birthDate)

    val empLabel = when (member.employmentStatus.lowercase()) {
        "employed" -> "Employed"
        "self_employed" -> "Self-Employed"
        "stay_at_home" -> "Stay at Home"
        "student" -> "Student"
        "retired" -> "Retired"
        "unemployed" -> "Unemployed"
        else -> member.employmentStatus.replaceFirstChar { it.uppercase() }
    }

    val subtitle = buildString {
        append(empLabel)
        if (!member.employer.isNullOrBlank()) {
            append(" at ${member.employer}")
        }
        if (age != null) {
            append(" | Age $age")
        }
    }

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

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isInspected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Name, Subtitle & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit member",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete member",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 4-Metric Summary Grid: Gross, Net Pay, Employer Match, Retirement Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MemberMetricTile(
                    label = "Gross",
                    value = MoneyUtils.formatCents(member.grossIncomeAnnualCents, showCents = false),
                    modifier = Modifier.weight(1f)
                )
                MemberMetricTile(
                    label = "Net Pay",
                    value = MoneyUtils.formatCents(netPay, showCents = false),
                    modifier = Modifier.weight(1f)
                )
                MemberMetricTile(
                    label = "Employer Match",
                    value = MoneyUtils.formatCents(match, showCents = false),
                    modifier = Modifier.weight(1f)
                )
                MemberMetricTile(
                    label = "Retirement Rate",
                    value = "${rate.roundToInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }

            // Meta Chips: Role, Accounts, Pay Frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitBadge(
                    text = roleLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                OrbitBadge(
                    text = accountsLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OrbitBadge(
                    text = payFreqLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemberMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
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
