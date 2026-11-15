package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.charts.RetirementProjectionChart
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RetirementProjectionHeroCard(
    uiState: RetirementUiState,
    onSelectPreset: (RetirementScenarioPreset) -> Unit,
    isDraggingControl: Boolean = false,
    chartHeightDp: Int = 196,
    modifier: Modifier = Modifier
) {
    val scenario = uiState.scenario
    var isScrubbingChart by remember { mutableStateOf(false) }

    val readinessColor = when {
        scenario.readinessPercent >= 100.0 -> OrbitTheme.colors.income
        scenario.readinessPercent >= 75.0 -> OrbitTheme.colors.warning
        else -> OrbitTheme.colors.expense
    }

    OrbitSectionCard(
        title = "Projected Nest Egg",
        subtitle = "Age ${uiState.retirementAge.roundToInt()} · ${uiState.preset.label} (${RetirementCalculatorEngine.formatRatePercent(uiState.preset.nominalAnnualReturn)})",
        modifier = modifier
    ) {
        // 1. Headline Projection Verdict Row (Whole Dollars — zero .39 cents)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                RollingTickerCents(
                    cents = scenario.projectedBalanceCents,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    showCents = false,
                    animate = !isScrubbingChart && !isDraggingControl
                )

                val gap = uiState.surplusOrGapCents
                val targetCompact = MoneyUtils.formatCompactCents(scenario.targetNestEggCents)
                val gapText = if (gap >= 0L) {
                    "+${MoneyUtils.formatCompactCents(gap)} above $targetCompact target"
                } else {
                    "-${MoneyUtils.formatCompactCents(abs(gap))} below $targetCompact target"
                }
                Text(
                    text = gapText,
                    style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    color = if (gap >= 0L) OrbitTheme.colors.income else OrbitTheme.colors.expense
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = readinessColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "${scenario.readinessPercent.roundToInt()}% FUNDED",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                    color = readinessColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Interactive 3-Scenario Accumulation Chart (isolated Draw-phase curves & scrub cursor)
        RetirementProjectionChart(
            currentAge = uiState.currentAge,
            retirementAge = uiState.retirementAge,
            currentBalanceCents = uiState.currentBalanceCents,
            targetNestEggCents = scenario.targetNestEggCents,
            scenarios = uiState.scenarios,
            selectedPreset = uiState.preset,
            actualHistoryPoints = uiState.actualHistoryPoints,
            hsaBridge = scenario.hsaBridge,
            onSelectPreset = onSelectPreset,
            onScrubActiveChanged = { isScrubbingChart = it },
            isDraggingControl = isDraggingControl,
            height = chartHeightDp.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 3-Tile Executive KPI Verdict Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val reqMonthly = if (scenario.monthsToRetirement > 0) {
                MoneyUtils.formatCents(scenario.requiredMonthlyContributionCents, showCents = false)
            } else {
                MoneyUtils.formatCompactCents(scenario.fundingGapCents)
            }
            val paceGapText = if (scenario.monthlySavingsGapCents > 0L) {
                "-${MoneyUtils.formatCompactCents(scenario.monthlySavingsGapCents)}/mo"
            } else {
                "On track"
            }
            OrbitStatTile(
                label = "Need / mo",
                value = reqMonthly,
                subText = paceGapText,
                valueColor = if (scenario.monthlySavingsGapCents > 0L) MaterialTheme.colorScheme.onSurface else OrbitTheme.colors.income,
                subTextColor = if (scenario.monthlySavingsGapCents > 0L) OrbitTheme.colors.expense else OrbitTheme.colors.income,
                modifier = Modifier.weight(1f)
            )

            val earliestVal = scenario.earliestRetirementAge?.let { "Age ${it.roundToInt()}" } ?: "Age ${uiState.runwayEndAge.roundToInt()}+"
            OrbitStatTile(
                label = "FI Readiness",
                value = earliestVal,
                subText = "${uiState.savingsRatePercent.roundToInt()}% gross saved",
                valueColor = if (scenario.earliestRetirementAge != null && scenario.earliestRetirementAge <= uiState.retirementAge) {
                    OrbitTheme.colors.income
                } else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            val depletionAge = scenario.depletionAge
            val runwayVal = if (depletionAge != null) "Age ${depletionAge.roundToInt()}" else "Age ${uiState.runwayEndAge.roundToInt()}+"
            val runwaySub = if (depletionAge != null) "Depletes early" else "Lasts plan"
            OrbitStatTile(
                label = "Runway",
                value = runwayVal,
                subText = runwaySub,
                valueColor = if (depletionAge != null) OrbitTheme.colors.expense else OrbitTheme.colors.income,
                subTextColor = if (depletionAge != null) OrbitTheme.colors.expense else OrbitTheme.colors.income,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
