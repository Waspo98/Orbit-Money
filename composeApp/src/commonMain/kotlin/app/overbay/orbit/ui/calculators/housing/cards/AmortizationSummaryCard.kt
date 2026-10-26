package app.overbay.orbit.ui.calculators.housing.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.AmortizationMonth
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun AmortizationSummaryCard(
    loanAmountCents: Cents,
    totalInterestPaidCents: Cents,
    loanTermYears: Int,
    extraMonthlyPrincipalCents: Cents,
    onExtraPrincipalChange: (Cents) -> Unit,
    monthsSaved: Int,
    interestSavedCents: Cents,
    amortizationSchedule: List<AmortizationMonth>,
    modifier: Modifier = Modifier
) {
    val totalCost = loanAmountCents + totalInterestPaidCents

    OrbitSectionCard(
        title = "Amortization & Payoff Acceleration",
        subtitle = "Simulate extra principal payments and view loan lifetime totals",
        modifier = modifier
    ) {
        // Lifetime Totals Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Total Interest",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = MoneyUtils.formatCents(totalInterestPaidCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Total Principal + Interest",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = MoneyUtils.formatCents(totalCost),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Extra Principal Simulator
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Accelerate Payoff (Extra Principal)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                CurrencyTextField(
                    cents = extraMonthlyPrincipalCents,
                    onCentsChange = onExtraPrincipalChange,
                    label = "Extra Monthly Principal",
                    modifier = Modifier.fillMaxWidth()
                )

                if (extraMonthlyPrincipalCents > 0L && monthsSaved > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val yearsSaved = monthsSaved / 12
                    val remMonths = monthsSaved % 12
                    val timeSavedStr = if (yearsSaved > 0) {
                        "$yearsSaved yr${if (yearsSaved > 1) "s" else ""} ${if (remMonths > 0) "$remMonths mo" else ""}"
                    } else {
                        "$monthsSaved month${if (monthsSaved > 1) "s" else ""}"
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Payoff $timeSavedStr sooner!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Saves ${MoneyUtils.formatCents(interestSavedCents)} in lifetime interest",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Milestone Balance Schedule
        Text(
            text = "Loan Balance Milestones",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))

        val milestoneYears = listOf(1, 5, 10, 15, 20, 25, 30).filter { it <= loanTermYears }
        milestoneYears.forEach { yr ->
            val monthIndex = yr * 12
            val entry = amortizationSchedule.find { it.monthNumber == monthIndex }
                ?: if (yr == loanTermYears) amortizationSchedule.lastOrNull() else null

            if (entry != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Year $yr (${yr * 12} mo)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Remaining: ${MoneyUtils.formatCents(entry.remainingBalanceCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
        }
    }
}
