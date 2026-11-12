package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HsaBridgeAnalysis
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementScenarioModel
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
private fun rememberScenarioPresetColors(): Map<RetirementScenarioPreset, Color> {
    val conservative = OrbitTheme.colors.transfer
    val balanced = MaterialTheme.colorScheme.primary
    val aggressive = MaterialTheme.colorScheme.tertiary
    return remember(conservative, balanced, aggressive) {
        mapOf(
            RetirementScenarioPreset.CONSERVATIVE to conservative,
            RetirementScenarioPreset.BALANCED to balanced,
            RetirementScenarioPreset.AGGRESSIVE to aggressive
        )
    }
}

@Composable
fun RetirementProjectionChart(
    currentAge: Double,
    retirementAge: Double,
    currentBalanceCents: Cents,
    targetNestEggCents: Cents,
    scenarios: Map<RetirementScenarioPreset, RetirementScenarioModel>,
    selectedPreset: RetirementScenarioPreset,
    actualHistoryPoints: List<ProjectionPoint>,
    hsaBridge: HsaBridgeAnalysis? = null,
    onAgeScrubbed: ((Double?, Cents?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    height: Dp = 250.dp
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current

    var scrubbedAge by remember { mutableStateOf<Double?>(null) }
    var lastHapticAge by remember { mutableStateOf<Int?>(null) }

    val firstActualAge = actualHistoryPoints.firstOrNull()?.age ?: currentAge
    val minAge = min(firstActualAge, currentAge)
    val maxAge = max(retirementAge, currentAge + 1.0)

    val maxBalance = remember(scenarios, actualHistoryPoints, targetNestEggCents, currentBalanceCents) {
        val peakScenarios = scenarios.values.flatMap { it.accumulationPoints }.maxOfOrNull { it.balanceCents } ?: 0L
        val peakActual = actualHistoryPoints.maxOfOrNull { it.balanceCents } ?: 0L
        val peak = maxOf(peakScenarios, peakActual, targetNestEggCents, currentBalanceCents, 100_000_00L)
        (peak.toDouble() * 1.12).toLong()
    }
    val animatedMaxBalance by animateFloatAsState(
        targetValue = maxBalance.toFloat(),
        animationSpec = OrbitSprings.FloatSpatialDefault,
        label = "retirementMaxBalanceScale"
    )

    LaunchedEffect(scrubbedAge, selectedPreset) {
        val age = scrubbedAge
        if (age != null) {
            val intAge = age.roundToInt()
            if (intAge != lastHapticAge) {
                haptics.tick()
                lastHapticAge = intAge
            }
            val activeModel = scenarios[selectedPreset]
            val bal = interpolateBalanceAtAge(activeModel?.accumulationPoints ?: emptyList(), age)
            onAgeScrubbed?.invoke(age, bal)
        } else {
            lastHapticAge = null
            onAgeScrubbed?.invoke(null, null)
        }
    }

    val presetColors = rememberScenarioPresetColors()
    val rawSelectedColor = presetColors[selectedPreset] ?: MaterialTheme.colorScheme.primary
    val selectedColor by animateColorAsState(
        targetValue = rawSelectedColor,
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "retirementSelectedScenarioColor"
    )
    val areaBrush = remember(selectedColor) {
        Brush.verticalGradient(listOf(selectedColor.copy(alpha = 0.22f), selectedColor.copy(alpha = 0.01f)))
    }
    val targetStroke = remember(density) {
        Stroke(width = with(density) { 1.5.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    }
    val markerStroke = remember(density) {
        Stroke(width = with(density) { 1.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
    val thickCurveStroke = remember(density) {
        Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val normalCurveStroke = remember(density) {
        Stroke(width = with(density) { 1.8.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val actualStroke = remember(density) {
        Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val areaPath = remember { Path() }
    val actualPath = remember { Path() }
    val linePath = remember { Path() }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface

    val ageSpan = max(1.0, maxAge - minAge)
    val normalizedScrubTarget = scrubbedAge?.let { age ->
        val xNorm = ((age - minAge) / ageSpan).toFloat().coerceIn(0f, 1f)
        val activeBal = interpolateBalanceAtAge(scenarios[selectedPreset]?.accumulationPoints ?: emptyList(), age)
        val yNorm = (1f - (activeBal.toFloat() / animatedMaxBalance.coerceAtLeast(1f))).coerceIn(0f, 1f)
        Offset(xNorm, yNorm)
    }
    val smoothScrubNorm = rememberSmoothScrubPosition(normalizedScrubTarget)

    Column(modifier = modifier.fillMaxWidth()) {
        RetirementChartReadoutBar(
            scrubbedAge = scrubbedAge,
            scenarios = scenarios,
            selectedPreset = selectedPreset,
            presetColors = presetColors
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .chartHorizontalScrub(
                    key = minAge to maxAge,
                    clearOnRelease = true,
                    onFractionChanged = { fraction ->
                        scrubbedAge = fraction?.let { minAge + it * (maxAge - minAge) }
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val denom = animatedMaxBalance.toDouble().coerceAtLeast(1.0)

                fun ageToX(age: Double): Float = (((age - minAge) / ageSpan) * w).toFloat()
                fun balToY(bal: Cents): Float = (h - ((bal.toDouble() / denom) * h).toFloat()).coerceIn(0f, h)

                // 1. Target Nest Egg Horizontal Line
                val targetY = balToY(targetNestEggCents)
                drawLine(
                    color = tertiaryColor.copy(alpha = 0.7f),
                    start = Offset(0f, targetY),
                    end = Offset(w, targetY),
                    strokeWidth = targetStroke.width,
                    pathEffect = targetStroke.pathEffect
                )

                // 2. Vertical "Today" Marker Line
                val todayX = ageToX(currentAge)
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.6f),
                    start = Offset(todayX, 0f),
                    end = Offset(todayX, h),
                    strokeWidth = markerStroke.width,
                    pathEffect = markerStroke.pathEffect
                )

                // 3. Vertical Retirement Age Marker Line
                val retireX = ageToX(retirementAge)
                drawLine(
                    color = selectedColor.copy(alpha = 0.6f),
                    start = Offset(retireX, 0f),
                    end = Offset(retireX, h),
                    strokeWidth = markerStroke.width,
                    pathEffect = markerStroke.pathEffect
                )

                // 4. Area fill for selected scenario
                val selectedModel = scenarios[selectedPreset]
                if (selectedModel != null && selectedModel.accumulationPoints.size >= 2) {
                    val pts = selectedModel.accumulationPoints
                    areaPath.rewind()
                    areaPath.moveTo(ageToX(pts.first().age), balToY(pts.first().balanceCents))
                    for (i in 1 until pts.size) {
                        areaPath.lineTo(ageToX(pts[i].age), balToY(pts[i].balanceCents))
                    }
                    areaPath.lineTo(ageToX(pts.last().age), h)
                    areaPath.lineTo(ageToX(pts.first().age), h)
                    areaPath.close()
                    drawPath(areaPath, brush = areaBrush)
                }

                // 5. Historical Actual Balances Line (up to currentAge)
                if (actualHistoryPoints.size >= 2) {
                    actualPath.rewind()
                    actualPath.moveTo(ageToX(actualHistoryPoints.first().age), balToY(actualHistoryPoints.first().balanceCents))
                    for (i in 1 until actualHistoryPoints.size) {
                        val pt = actualHistoryPoints[i]
                        actualPath.lineTo(ageToX(pt.age), balToY(pt.balanceCents))
                    }
                    drawPath(actualPath, color = onSurfaceVariant.copy(alpha = 0.65f), style = actualStroke)
                }

                // 6. Dot at Today's Balance
                drawCircle(color = onSurface, radius = 4.5.dp.toPx(), center = Offset(todayX, balToY(currentBalanceCents)))

                // 7. Simultaneous 3 Accumulation Curves
                RetirementScenarioPreset.entries.forEach { preset ->
                    val model = scenarios[preset]
                    if (model != null && model.accumulationPoints.size >= 2) {
                        val color = presetColors[preset] ?: onSurfaceVariant
                        val isSelected = preset == selectedPreset
                        linePath.rewind()
                        val pts = model.accumulationPoints
                        linePath.moveTo(ageToX(pts.first().age), balToY(pts.first().balanceCents))
                        for (i in 0 until pts.size - 1) {
                            val p0 = if (i > 0) pts[i - 1] else pts[i]
                            val p1 = pts[i]
                            val p2 = pts[i + 1]
                            val p3 = if (i + 2 < pts.size) pts[i + 2] else p2
                            val x1 = ageToX(p1.age); val y1 = balToY(p1.balanceCents)
                            val x2 = ageToX(p2.age); val y2 = balToY(p2.balanceCents)
                            val x0 = ageToX(p0.age); val y0 = balToY(p0.balanceCents)
                            val x3 = ageToX(p3.age); val y3 = balToY(p3.balanceCents)
                            linePath.cubicTo(
                                x1 + (x2 - x0) / 6f, (y1 + (y2 - y0) / 6f).coerceIn(0f, h),
                                x2 - (x3 - x1) / 6f, (y2 - (y3 - y1) / 6f).coerceIn(0f, h),
                                x2, y2
                            )
                        }
                        drawPath(
                            linePath,
                            color = if (isSelected) color else color.copy(alpha = 0.65f),
                            style = if (isSelected) thickCurveStroke else normalCurveStroke
                        )
                    }
                }

                // 8. Smoothly gliding scrub line & indicator
                if (smoothScrubNorm != null) {
                    val sx = smoothScrubNorm.x * w
                    val sy = smoothScrubNorm.y * h
                    drawLine(color = onSurface.copy(alpha = 0.8f), start = Offset(sx, 0f), end = Offset(sx, h), strokeWidth = markerStroke.width)
                    drawCircle(color = selectedColor.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = Offset(sx, sy))
                    drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
                    drawCircle(color = selectedColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(width = 2.5.dp.toPx()))
                }
            }

            // 9. On-chart badge for Pre-65 HSA Bridge
            if (retirementAge < 65.0 && hsaBridge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    val statusText = if (hsaBridge.isBridgeCovered) "Non-HSA: Covered" else "Non-HSA: ${MoneyUtils.formatCompactCents(hsaBridge.shortfallCents)} Short"
                    val statusColor = if (hsaBridge.isBridgeCovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // X-Axis Age Milestones Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Today (${currentAge.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
            Text(
                text = "Target: ${MoneyUtils.formatCompactCents(targetNestEggCents)}",
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor
            )
            Text(
                text = "Retire (${retirementAge.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = selectedColor
            )
        }
    }
}
