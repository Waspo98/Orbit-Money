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
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.charts.RetirementProjectionChart
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
        subtitle = "Interactive accumulation and drawdown projection",
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
                            text = if (uiState.scrubbedAge != null) "SCRUBBED BALANCE (AGE ${uiState.scrubbedAge.roundToInt()})" else "PROJECTED AT AGE ${uiState.retirementAge.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RollingTickerCents(
                            cents = uiState.scrubbedBalanceCents ?: scenario.projectedBalanceCents,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
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
                        text = "Target Nest Egg: ${MoneyUtils.formatCompactCents(scenario.targetNestEggCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (scenario.fundingGapCents > 0L) "Gap: ${MoneyUtils.formatCompactCents(scenario.fundingGapCents)}" else "Fully Funded!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (scenario.fundingGapCents > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Projection Chart with Scrubbing
        RetirementProjectionChart(
            currentAge = uiState.currentAge,
            retirementAge = uiState.retirementAge,
            runwayEndAge = uiState.runwayEndAge,
            accumulationPoints = scenario.accumulationPoints,
            drawdownPoints = scenario.drawdownPoints,
            targetNestEggCents = scenario.targetNestEggCents,
            coastFireTargetCents = scenario.coastFireTargetCents,
            onAgeScrubbed = onAgeScrubbed,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Coast FIRE & Depletion Summary Pill
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Coast FIRE Target Today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (scenario.isCoastFireReached) "Coast FIRE Reached! 🎉" else MoneyUtils.formatCents(scenario.coastFireTargetCents),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (scenario.isCoastFireReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Portfolio Runway",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val runwayText = if (scenario.depletionAge != null) {
                        "Depletes at Age ${scenario.depletionAge.roundToInt()}"
                    } else {
                        "Sustainable Past Age ${uiState.runwayEndAge.toInt()} ♾️"
                    }
                    Text(
                        text = runwayText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (scenario.depletionAge != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                if (scenario.earliestRetirementAge != null && scenario.earliestRetirementAge < uiState.retirementAge) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Earliest Achievable Retirement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Age ${scenario.earliestRetirementAge.roundToInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}
