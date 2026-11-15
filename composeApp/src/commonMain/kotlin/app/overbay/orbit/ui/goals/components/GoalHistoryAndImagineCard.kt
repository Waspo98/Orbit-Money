package app.overbay.orbit.ui.goals.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.GoalAllocationEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Interactive historical balance chart and What-If monthly boost simulator for a goal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalHistoryAndImagineCard(
    goal: Goal,
    selectedMonths: Int,
    onTimeframeChanged: (Int) -> Unit,
    whatIfBoostCents: Cents,
    onWhatIfBoostChanged: (Cents) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val haptics = LocalOrbitHaptics.current
    val baselinePace = goal.monthlyPaceCents ?: 0L
    val isStalled = !goal.isCompleted && baselinePace <= 0L
    val isPastTarget = !goal.isCompleted && goal.isProjectedPastTargetDate()

    val (badgeLabel, badgeColor) = when {
        goal.isCompleted -> "Reached" to colors.income
        isStalled -> "Pace Stalled" to colors.warning
        isPastTarget -> "Behind Target" to colors.warning
        else -> "On Track" to colors.income
    }

    OrbitSectionCard(
        modifier = modifier,
        title = "Growth & What-If Planner",
        headerBadge = {
            OrbitBadge(
                text = badgeLabel,
                containerColor = badgeColor.copy(alpha = 0.14f),
                contentColor = badgeColor
            )
        }
    ) {
        OrbitSegmentedTabRow(
            items = listOf(6, 12, 24),
            selected = selectedMonths,
            onSelect = { months ->
                haptics.tick()
                onTimeframeChanged(months)
            },
            label = { "${it}M" }
        )

        Spacer(Modifier.height(12.dp))

        val visiblePoints = remember(goal.history, selectedMonths) {
            goal.history.takeLast(selectedMonths)
        }
        GoalHistoryChart(
            goalId = goal.id,
            points = visiblePoints,
            targetAmountCents = goal.targetAmountCents
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current Trajectory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val landingColor = if (isStalled || isPastTarget) colors.warning else colors.income
            Text(
                text = goal.projectedLandingLabel(),
                style = MaterialTheme.typography.labelLarge.merge(TabularNumStyle),
                fontWeight = FontWeight.SemiBold,
                color = landingColor
            )
        }

        if (!goal.isCompleted) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.cardBorder)
            Spacer(Modifier.height(14.dp))

            GoalWhatIfPlannerSection(
                goal = goal,
                baselinePace = baselinePace,
                whatIfBoostCents = whatIfBoostCents,
                onWhatIfBoostChanged = onWhatIfBoostChanged
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalWhatIfPlannerSection(
    goal: Goal,
    baselinePace: Cents,
    whatIfBoostCents: Cents,
    onWhatIfBoostChanged: (Cents) -> Unit
) {
    val colors = OrbitTheme.colors
    val haptics = LocalOrbitHaptics.current
    val remainingCents = (goal.targetAmountCents - goal.currentAmountCents).coerceAtLeast(0L)
    val targetMonths = remember(goal.targetDate) { goal.monthsUntilTargetDate() }
    val requiredMonthlyCents = if (targetMonths != null && targetMonths > 0 && remainingCents > 0L) {
        (remainingCents + targetMonths - 1) / targetMonths
    } else {
        0L
    }
    val matchTargetBoostCents = remember(requiredMonthlyCents, baselinePace) {
        val raw = (requiredMonthlyCents - baselinePace.coerceAtLeast(0L)).coerceAtLeast(0L)
        if (raw <= 0L) 0L else ((raw + 99L) / 100L) * 100L
    }
    val maxSliderDollars = remember(matchTargetBoostCents) {
        val suggested = (matchTargetBoostCents / 100L).toFloat() * 1.5f
        maxOf(2500f, suggested).coerceAtMost(25000f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "What-If Monthly Boost",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "+${formatCurrencySmart(whatIfBoostCents)}/mo",
            style = MaterialTheme.typography.labelLarge.merge(TabularNumStyle),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(Modifier.height(10.dp))

    CurrencyTextField(
        cents = whatIfBoostCents,
        onCentsChange = { onWhatIfBoostChanged(it.coerceAtLeast(0L)) },
        label = "Additional Monthly Contribution",
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(6.dp))

    Slider(
        value = (whatIfBoostCents / 100f).coerceIn(0f, maxSliderDollars),
        onValueChange = { dollars ->
            val snappedDollars = (dollars / 25f).roundToInt() * 25L
            onWhatIfBoostChanged((snappedDollars * 100L).coerceAtLeast(0L))
        },
        valueRange = 0f..maxSliderDollars,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = Modifier.fillMaxWidth()
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (matchTargetBoostCents > 0L) {
            OrbitActionChip(
                label = "Match Target (+${formatCurrencySmart(matchTargetBoostCents)})",
                onClick = {
                    haptics.tick()
                    onWhatIfBoostChanged(matchTargetBoostCents)
                }
            )
        }
        listOf(100_00L, 250_00L, 500_00L).forEach { presetCents ->
            OrbitActionChip(
                label = "+${formatCurrencySmart(presetCents)}",
                onClick = {
                    haptics.tick()
                    onWhatIfBoostChanged(presetCents)
                }
            )
        }
        if (whatIfBoostCents > 0L) {
            OrbitActionChip(
                label = "Reset",
                onClick = {
                    haptics.tick()
                    onWhatIfBoostChanged(0L)
                }
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    val simulatedEta = remember(goal.currentAmountCents, goal.targetAmountCents, baselinePace, whatIfBoostCents) {
        GoalAllocationEngine.simulateWhatIf(
            currentAmountCents = goal.currentAmountCents,
            targetAmountCents = goal.targetAmountCents,
            baselinePaceCents = baselinePace,
            additionalMonthlyCents = whatIfBoostCents
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        AnimatedContent(
            targetState = Triple(simulatedEta.date, simulatedEta.months, targetMonths),
            transitionSpec = { orbitMorphContentTransform() },
            label = "what_if_projection",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) { (simDate, simMonths, desiredMonths) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val primaryLine = when {
                    simMonths != null && simMonths > 240 -> "> 20 years at this pace"
                    simDate != null && simMonths != null -> "Projected $simDate ($simMonths mo)"
                    else -> "Add a monthly boost to project a completion date"
                }
                Text(
                    text = primaryLine,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumStyle),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val targetIso = goal.targetDate
                if (!targetIso.isNullOrBlank() && desiredMonths != null && simMonths != null && simMonths <= 240) {
                    val targetLabel = LocalDateUtils.formatMonthShortLabel(targetIso.take(7))
                    val diff = simMonths - desiredMonths
                    val (comparisonText, toneColor) = when {
                        diff <= 0 -> "On time for $targetLabel goal" to colors.income
                        else -> "$diff mo after $targetLabel goal" to colors.warning
                    }
                    Text(
                        text = comparisonText,
                        style = MaterialTheme.typography.labelSmall.merge(TabularNumStyle),
                        color = toneColor
                    )
                }
            }
        }
    }
}
