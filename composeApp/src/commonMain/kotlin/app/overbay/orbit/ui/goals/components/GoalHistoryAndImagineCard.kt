package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalMonthlyPoint
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.GoalAllocationEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

/**
 * Historical 6-24 month balance trend canvas chart paired with 7-month trailing pace,
 * ETA pill, and interactive "Imagine" What-If monthly boost simulator.
 * Strictly <= 390 lines.
 */
@Composable
fun GoalHistoryAndImagineCard(
    goal: Goal,
    onTimeframeChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMonths by remember { mutableIntStateOf(12) }
    var boostDollars by remember { mutableFloatStateOf(100f) }
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val history = remember(goal.history, selectedMonths) {
        if (goal.history.isEmpty()) emptyList()
        else goal.history.takeLast(selectedMonths)
    }

    val baselinePace = goal.monthlyPaceCents
    val additionalCents = (boostDollars.toLong() * 100L)
    val simulatedEta = remember(goal, baselinePace, additionalCents) {
        GoalAllocationEngine.simulateWhatIf(
            currentAmountCents = goal.currentAmountCents,
            targetAmountCents = goal.targetAmountCents,
            baselinePaceCents = baselinePace,
            additionalMonthlyCents = additionalCents
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Timeframe Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL GROWTH & ETA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (baselinePace > 0L) {
                            "+${MoneyUtils.formatCents(baselinePace, showCents = false)}/mo avg pace"
                        } else {
                            "Pace stalled"
                        },
                        style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = if (baselinePace > 0L) colors.income else colors.warning
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 12, 24).forEach { m ->
                        FilterChip(
                            selected = selectedMonths == m,
                            onClick = {
                                haptics.tick()
                                selectedMonths = m
                                onTimeframeChanged(m)
                            },
                            label = { Text("${m}M", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Canvas Line & Area Chart
            if (history.size >= 2) {
                GoalHistoryCanvasChart(
                    points = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Historical balances will chart here as account syncs accumulate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // ETA Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Projected Landing:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val etaText = when (goal.eta?.status) {
                    "complete" -> "Goal Reached! 🎉"
                    "stalled" -> "Pace Stalled"
                    else -> "${goal.eta?.date ?: "Unknown"} (${goal.eta?.months ?: 0} mos left)"
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = etaText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)

            // "Imagine" What-If Monthly Simulator
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨ \"IMAGINE\" SIMULATOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "+$${boostDollars.toInt()}/mo",
                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = boostDollars,
                    onValueChange = {
                        boostDollars = it
                    },
                    valueRange = 0f..1500f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth()
                )

                val baselineMonths = goal.eta?.months ?: 999
                val simMonths = simulatedEta.months ?: 0
                val monthsSaved = max(0, baselineMonths - simMonths)

                val simulationSummary = if (simulatedEta.date != null) {
                    if (monthsSaved > 0) {
                        "Lands in ${simulatedEta.date} ($simMonths mos · Saves $monthsSaved months! 🚀)"
                    } else {
                        "Lands in ${simulatedEta.date} ($simMonths months)"
                    }
                } else if (goal.isCompleted) {
                    "Goal already reached! Extra funds accelerate your next priority."
                } else {
                    "Add monthly deposits to project landing date."
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = simulationSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (monthsSaved > 0) colors.income else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalHistoryCanvasChart(
    points: List<GoalMonthlyPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val values = remember(points) { points.map { it.amountCents.toFloat() } }
    val maxVal = remember(values) { max(1f, values.maxOrNull() ?: 1f) }
    val minVal = remember(values) { values.minOrNull() ?: 0f }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1).coerceAtLeast(1)
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { i, pt ->
            val x = i * stepX
            val norm = (pt.amountCents - minVal) / range
            val y = h - (norm * (h - 18f)) - 8f

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (i == points.size - 1) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        // Draw gradient area
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        // Draw stroke line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw dot at latest point
        val lastX = (points.size - 1) * stepX
        val lastNorm = (points.last().amountCents - minVal) / range
        val lastY = h - (lastNorm * (h - 18f)) - 8f
        drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
    }
}
