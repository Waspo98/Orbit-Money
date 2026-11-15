package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.MemberRetirementSummary
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun MemberRetirementBreakdownCard(
    memberSummaries: List<MemberRetirementSummary>,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Household Member Contributions",
        subtitle = "Annual & per-paycheck employee savings, employer match, and HSA",
        modifier = modifier
    ) {
        if (memberSummaries.isEmpty()) {
            EmptyStateCard(
                title = "No Household Members",
                description = "Add household members in the Household tab to see per-earner retirement contribution breakdowns."
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                memberSummaries.forEach { summary ->
                    MemberContributionItem(summary = summary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberContributionItem(
    summary: MemberRetirementSummary
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = summary.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${summary.employer ?: "Self-employed / Independent"} • ${summary.payFrequencyLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${MoneyUtils.formatCents(summary.annualTotalCents, showCents = false)} / yr",
                        style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${MoneyUtils.formatCents(summary.perPaycheckCents, showCents = false)} / paycheck",
                        style = TabularNumStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = OrbitTheme.colors.cardBorder)

            OrbitMiniStatRow(
                label = "Employee Savings",
                value = MoneyUtils.formatCents(summary.annualEmployeeSavingsCents, showCents = false)
            )
            OrbitMiniStatRow(
                label = "Employer Match",
                value = "${MoneyUtils.formatCents(summary.annualEmployerMatchCents, showCents = false)}${if (summary.matchCapReached) " (Cap)" else ""}"
            )
            if (summary.annualHsaCents > 0L) {
                OrbitMiniStatRow(
                    label = "HSA Annual Contribution",
                    value = MoneyUtils.formatCents(summary.annualHsaCents, showCents = false)
                )
            }

            if (summary.linkedAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Linked Accounts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.linkedAccounts.forEach { chip ->
                        OrbitBadge(
                            text = "${chip.displayKind}: ${chip.name}",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
