package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.charts.RetirementProjectionChart
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RetirementProjectionHeroCard(
    uiState: RetirementUiState,
    onAgeScrubbed: (Double?, Cents?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scenario = uiState.scenario
    val isFunded = scenario.readinessPercent >= 100.0
    val readinessColor = when {
        scenario.readinessPercent >= 100.0 -> MaterialTheme.colorScheme.primary
        scenario.readinessPercent >= 75.0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    OrbitSectionCard(
        title = "Retirement Trajectory & Nest Egg",
        subtitle = "Live accumulation path across conservative, balanced, and aggressive scenarios",
        modifier = modifier
    ) {
        // Hero Top Metrics
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (uiState.scrubbedAge != null) {
                                "SCRUBBED BALANCE (AGE ${uiState.scrubbedAge.roundToInt()})"
                            } else {
                                "AT AGE ${uiState.retirementAge.toInt()}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RollingTickerCents(
                            cents = uiState.scrubbedBalanceCents ?: scenario.projectedBalanceCents,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            animate = uiState.scrubbedBalanceCents == null
                        )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = readinessColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${scenario.readinessPercent.roundToInt()}% FUNDED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = readinessColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target: ${MoneyUtils.formatCompactCents(scenario.targetNestEggCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val gap = uiState.surplusOrGapCents
                    Text(
                        text = if (gap >= 0L) {
                            "+${MoneyUtils.formatCompactCents(gap)} ahead of target"
                        } else {
                            "-${MoneyUtils.formatCompactCents(abs(gap))} below target"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (gap >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive 3-Scenario Projection Chart with Scrubbing
        RetirementProjectionChart(
            currentAge = uiState.currentAge,
            retirementAge = uiState.retirementAge,
            currentBalanceCents = uiState.currentBalanceCents,
            targetNestEggCents = scenario.targetNestEggCents,
            scenarios = uiState.scenarios,
            selectedPreset = uiState.preset,
            actualHistoryPoints = uiState.actualHistoryPoints,
            hsaBridge = scenario.hsaBridge,
            onAgeScrubbed = onAgeScrubbed,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Exact Old-PWA Metrics List
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitMiniStatRow(
                    label = "Retirement Target",
                    value = MoneyUtils.formatCents(scenario.targetNestEggCents, showCents = false)
                )

                val reqLabel = if (scenario.monthsToRetirement > 0) "Required Monthly Savings" else "Required Today"
                val reqValue = if (scenario.monthsToRetirement > 0) {
                    "${MoneyUtils.formatCents(scenario.requiredMonthlyContributionCents, showCents = false)} / mo"
                } else {
                    MoneyUtils.formatCents(scenario.fundingGapCents, showCents = false)
                }
                OrbitMiniStatRow(
                    label = reqLabel,
                    value = reqValue,
                    valueColor = if (scenario.monthlySavingsGapCents > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                val earliestText = if (scenario.earliestRetirementAge != null) {
                    "Age ${scenario.earliestRetirementAge.roundToInt()}"
                } else {
                    "After Age ${uiState.runwayEndAge.toInt()}"
                }
                OrbitMiniStatRow(
                    label = "Earliest Target Age",
                    value = earliestText,
                    valueColor = if (scenario.earliestRetirementAge != null && scenario.earliestRetirementAge <= uiState.retirementAge) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurface
                )

                val savingsRateVal = "${uiState.savingsRatePercent.roundToInt()}% of gross income"
                OrbitMiniStatRow(
                    label = "Savings Rate",
                    value = savingsRateVal,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
