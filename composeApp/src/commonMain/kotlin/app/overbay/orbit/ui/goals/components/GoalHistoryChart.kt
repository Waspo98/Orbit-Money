package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.GoalMonthlyPoint
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.charts.ChartScrubberHeader
import app.overbay.orbit.ui.components.charts.DeltaTone
import app.overbay.orbit.ui.components.charts.chartHorizontalScrub
import app.overbay.orbit.ui.components.charts.rememberAnimatedFloatSeries
import app.overbay.orbit.ui.components.charts.rememberSmoothScrubPosition
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Interactive historical balance chart for a single savings goal with touch scrubbing,
 * dashed reference gridlines, an optional target threshold marker, and a compact
 * empty state when all historical balances in the selected window are zero.
 */
@Composable
fun GoalHistoryChart(
    goalId: Long,
    points: List<GoalMonthlyPoint>,
    targetAmountCents: Cents,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val haptics = LocalOrbitHaptics.current

    if (points.isEmpty()) return

    val hasAnyBalance = remember(points) { points.any { it.amountCents > 0L } }
    if (!hasAnyBalance) {
        Surface(
            modifier = modifier.fillMaxWidth().height(92.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No funding balance recorded across this ${points.size}M window yet. Use the What-If planner below to model monthly contributions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var scrubFraction by remember(goalId, points.size) { mutableStateOf<Float?>(null) }
    val scrubIndex = scrubFraction?.let { fraction ->
        if (points.size <= 1) 0 else (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
    }

    LaunchedEffect(scrubIndex) {
        if (scrubIndex != null) {
            haptics.tick()
        }
    }

    val startPoint = points.first()
    val endPoint = points.last()
    val displayPoint = scrubIndex?.let { points[it] } ?: endPoint
    val windowDelta = displayPoint.amountCents - startPoint.amountCents
    val windowDeltaText = when {
        windowDelta > 0L -> "+${formatCurrencySmart(windowDelta)} vs ${LocalDateUtils.formatMonthShortLabel(startPoint.month)}"
        windowDelta < 0L -> "-${formatCurrencySmart(-windowDelta)} vs ${LocalDateUtils.formatMonthShortLabel(startPoint.month)}"
        else -> "Flat vs ${LocalDateUtils.formatMonthShortLabel(startPoint.month)}"
    }
    val deltaTone = when {
        windowDelta > 0L -> DeltaTone.POSITIVE
        windowDelta < 0L -> DeltaTone.NEGATIVE
        else -> DeltaTone.NEUTRAL
    }

    val maxSeries = remember(points) { points.maxOf { it.amountCents }.coerceAtLeast(100L) }
    val showTargetLine = targetAmountCents > 0L && targetAmountCents <= (maxSeries * 1.45).toLong()
    val yMaxCents = remember(maxSeries, targetAmountCents, showTargetLine) {
        val ceilSource = if (showTargetLine) maxOf(maxSeries, targetAmountCents) else maxSeries
        (ceilSource * 1.1f).toLong().coerceAtLeast(100L)
    }
    val animatedRatios = rememberAnimatedFloatSeries(
        targets = points.map { (it.amountCents.toFloat() / yMaxCents.toFloat()).coerceIn(0f, 1f) }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        ChartScrubberHeader(
            title = if (scrubIndex != null) "Snapshot Balance" else "Allocated Balance",
            formattedValue = formatCurrencySmart(displayPoint.amountCents),
            deltaText = windowDeltaText,
            deltaTone = deltaTone,
            isScrubbing = scrubIndex != null,
            scrubDateText = LocalDateUtils.formatMonthShortLabel(displayPoint.month)
        )

        Spacer(Modifier.height(8.dp))

        val targetScrubOffset = remember(scrubIndex, animatedRatios, points.size) {
            scrubIndex?.let { idx ->
                val xFrac = if (points.size <= 1) 0.5f else idx.toFloat() / (points.size - 1).toFloat()
                val yFrac = 1f - animatedRatios.getOrElse(idx) { 0f }
                Offset(xFrac, yFrac)
            }
        }
        val smoothScrubNormalized = rememberSmoothScrubPosition(targetScrubOffset)

        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .chartHorizontalScrub(
                    key = Pair(goalId, points.size),
                    onFractionChanged = { scrubFraction = it }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                val w = size.width
                val h = size.height
                val stepX = if (points.size <= 1) w else w / (points.size - 1).toFloat()

                // 3 horizontal reference gridlines
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val y = h * frac
                    drawLine(
                        color = outlineVariant.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                // Target threshold line when near the plotted range
                if (showTargetLine) {
                    val targetRatio = (targetAmountCents.toFloat() / yMaxCents.toFloat()).coerceIn(0f, 1f)
                    val targetY = h * (1f - targetRatio)
                    drawLine(
                        color = colors.income.copy(alpha = 0.55f),
                        start = Offset(0f, targetY),
                        end = Offset(w, targetY),
                        strokeWidth = 1.25.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                val coords = animatedRatios.indices.map { i ->
                    Offset(
                        x = if (points.size <= 1) w / 2f else i * stepX,
                        y = h * (1f - animatedRatios[i])
                    )
                }

                if (coords.size >= 2) {
                    val strokePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.size - 1) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(coords.last().x, h)
                        lineTo(coords.first().x, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(colors.income.copy(alpha = 0.24f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )
                    drawPath(
                        path = strokePath,
                        color = colors.income,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // End marker at rest, or smooth scrubbing crosshair + halo
                if (smoothScrubNormalized != null) {
                    val sx = smoothScrubNormalized.x * w
                    val sy = smoothScrubNormalized.y * h
                    drawLine(
                        color = outlineVariant.copy(alpha = 0.7f),
                        start = Offset(sx, 0f),
                        end = Offset(sx, h),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(
                        color = colors.income.copy(alpha = 0.22f),
                        radius = 10.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.5.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                    drawCircle(
                        color = colors.income,
                        radius = 4.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                } else {
                    val last = coords.last()
                    drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = last)
                    drawCircle(color = colors.income, radius = 3.5.dp.toPx(), center = last)
                }
            }

            if (showTargetLine) {
                Text(
                    text = "Goal ${MoneyUtils.formatCompactCents(targetAmountCents)}",
                    style = MaterialTheme.typography.labelSmall.merge(TabularNumStyle),
                    fontWeight = FontWeight.Medium,
                    color = colors.income,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = LocalDateUtils.formatMonthShortLabel(startPoint.month),
                style = MaterialTheme.typography.labelSmall.merge(TabularNumStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = LocalDateUtils.formatMonthShortLabel(endPoint.month),
                style = MaterialTheme.typography.labelSmall.merge(TabularNumStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
