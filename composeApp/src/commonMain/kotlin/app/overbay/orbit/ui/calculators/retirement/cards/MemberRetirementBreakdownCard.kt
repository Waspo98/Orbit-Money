package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.min
import kotlin.math.roundToLong

@Composable
fun MemberRetirementBreakdownCard(
    members: List<HouseholdMember>,
    accounts: List<Account>,
    modifier: Modifier = Modifier
) {
    val accountMap = accounts.associateBy { it.id }

    OrbitSectionCard(
        title = "Household Member Retirement Contributions",
        subtitle = "Annual & per-paycheck employee savings, employer match, and HSA",
        modifier = modifier
    ) {
        if (members.isEmpty()) {
            EmptyStateCard(
                title = "No Household Members",
                description = "Add household members in the Household tab to see per-earner retirement contribution breakdowns."
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                members.forEach { member ->
                    MemberContributionItem(
                        member = member,
                        accountMap = accountMap
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberContributionItem(
    member: HouseholdMember,
    accountMap: Map<Long, Account>
) {
    // 1. Employee contribution
    val gross = member.grossIncomeAnnualCents
    val empPct = member.employeeContributionPercent ?: 0.0
    val empContribCents = if (member.employeeContributionAnnualCents > 0L) {
        member.employeeContributionAnnualCents
    } else if (empPct > 0.0 && gross > 0L) {
        ((gross.toDouble() * empPct) / 100.0).roundToLong()
    } else 0L

    // 2. Employer match calculation
    val matchPct = member.employerMatchPercent
    val matchLimitPct = member.employerMatchLimitPercent
    val matchCap = member.employerMatchAnnualCapCents
    val eligiblePct = if (matchLimitPct > 0.0) min(empPct, matchLimitPct) else empPct
    val matchRate = if (matchPct > 0.0) matchPct / 100.0 else 1.0
    val uncappedMatchCents = if (eligiblePct > 0.0 && gross > 0L) {
        ((gross.toDouble() * eligiblePct / 100.0) * matchRate).roundToLong()
    } else 0L
    val matchCents = if (matchCap > 0L) min(matchCap, uncappedMatchCents) else uncappedMatchCents

    // 3. HSA contribution
    val hsaCents = member.hsaContributionAnnualCents

    // 4. Totals
    val totalAnnualCents = empContribCents + matchCents + hsaCents
    val payPeriods = if (member.payPeriodsPerYear > 0.0) member.payPeriodsPerYear else 26.0
    val perPaycheckCents = (totalAnnualCents.toDouble() / payPeriods).roundToLong()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Member Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${member.employer ?: "Self-employed / Independent"} • ${member.payFrequency.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${MoneyUtils.formatCents(totalAnnualCents, showCents = false)} / yr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${MoneyUtils.formatCents(perPaycheckCents, showCents = false)} / paycheck",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Breakdown Rows
            ContributionRow("Employee Savings", MoneyUtils.formatCents(empContribCents, showCents = false))
            ContributionRow(
                "Employer Match",
                "${MoneyUtils.formatCents(matchCents, showCents = false)}${if (matchCap > 0L && uncappedMatchCents > matchCap) " (Cap Reached)" else ""}"
            )
            if (hsaCents > 0L) {
                ContributionRow("HSA Annual Contribution", MoneyUtils.formatCents(hsaCents, showCents = false))
            }

            // Linked Accounts Chips
            if (member.retirementAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Linked Accounts:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    member.retirementAccounts.forEach { link ->
                        val acct = accountMap[link.accountId]
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${link.accountKind.uppercase()}: ${acct?.name ?: "Account #${link.accountId}"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributionRow(
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
