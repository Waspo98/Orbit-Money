package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.HsaBridgeAnalysis
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementScenarioModel
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    onSelectPreset: (RetirementScenarioPreset) -> Unit = {},
    onScrubActiveChanged: ((Boolean) -> Unit)? = null,
    isDraggingControl: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 196.dp
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var lastHapticAge by remember { mutableStateOf<Int?>(null) }

    val firstActualAge = actualHistoryPoints.firstOrNull()?.age ?: currentAge
    val minAge = min(firstActualAge, currentAge)
    val maxAge = max(retirementAge, currentAge + 1.0)
    val ageSpan = max(1.0, maxAge - minAge)
    val scrubbedAge = scrubFraction?.let { minAge + it * ageSpan }

    val maxBalance = remember(scenarios, actualHistoryPoints, targetNestEggCents, currentBalanceCents) {
        val peakScenarios = scenarios.values.flatMap { it.accumulationPoints }.maxOfOrNull { it.balanceCents } ?: 0L
        val peakActual = actualHistoryPoints.maxOfOrNull { it.balanceCents } ?: 0L
        val peak = maxOf(peakScenarios, peakActual, targetNestEggCents, currentBalanceCents, 100_000_00L)
        (peak.toDouble() * 1.12).toLong()
    }
    val maxBalanceAnim = remember { Animatable(maxBalance.toFloat()) }
    LaunchedEffect(maxBalance, isDraggingControl) {
        val target = maxBalance.toFloat().coerceAtLeast(1f)
        if (isDraggingControl) {
            maxBalanceAnim.snapTo(target)
        } else {
            maxBalanceAnim.animateTo(target, OrbitSprings.FloatSpatialDefault)
        }
    }
    val maxBalState = maxBalanceAnim.asState()

    val animatedSeriesMap = RetirementScenarioPreset.entries.associateWith { preset ->
        val targets = remember(scenarios[preset]) {
            scenarios[preset]?.accumulationPoints?.map { it.balanceCents.toFloat().coerceAtLeast(0f) } ?: emptyList()
        }
        rememberAnimatedFloatSeriesState(targets = targets, animate = !isDraggingControl)
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
        Stroke(width = with(density) { 1.4.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    }
    val markerStroke = remember(density) {
        Stroke(width = with(density) { 1.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
    val thickCurveStroke = remember(density) {
        Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val normalCurveStroke = remember(density) {
        Stroke(width = with(density) { 1.6.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
    val surfaceHighColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val gridColor = OrbitTheme.colors.cardBorder

    val tabularBase = TabularNumStyle
    val axisStyle = remember(tabularBase, onSurfaceVariant) {
        tabularBase.copy(fontSize = 9.5.sp, color = onSurfaceVariant)
    }
    val targetPillStyle = remember(tabularBase, tertiaryColor) {
        tabularBase.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        RetirementChartReadoutBar(
            scrubbedAge = scrubbedAge,
            scenarios = scenarios,
            selectedPreset = selectedPreset,
            presetColors = presetColors,
            onSelectPreset = onSelectPreset
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .chartHorizontalScrub(
                    key = minAge to maxAge,
                    clearOnRelease = true,
                    onFractionChanged = { fraction ->
                        val wasActive = scrubFraction != null
                        val isNowActive = fraction != null
                        if (wasActive != isNowActive) {
                            onScrubActiveChanged?.invoke(isNowActive)
                        }
                        if (fraction != null) {
                            val intAge = (minAge + fraction * ageSpan).roundToInt()
                            if (intAge != lastHapticAge) {
                                haptics.tick()
                                lastHapticAge = intAge
                            }
                        } else {
                            lastHapticAge = null
                        }
                        scrubFraction = fraction
                    }
                )
        ) {
            // Layer 1: Static + Draw-phase spring curves, right Y-axis ticks & inline target marker
            Canvas(modifier = Modifier.fillMaxSize()) {
                val axisGutterPx = 46.dp.toPx()
                val topPadPx = 6.dp.toPx()
                val bottomPadPx = 4.dp.toPx()
                val plotW = (size.width - axisGutterPx).coerceAtLeast(1f)
                val plotH = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
                val denom = maxBalState.value.toDouble().coerceAtLeast(1.0)

                fun ageToX(age: Double): Float = (((age - minAge) / ageSpan) * plotW).toFloat().coerceIn(0f, plotW)
                fun balToY(bal: Cents): Float = (topPadPx + plotH - ((bal.toDouble() / denom) * plotH).toFloat()).coerceIn(topPadPx, topPadPx + plotH)
                fun balFloatToY(bal: Float): Float = (topPadPx + plotH - ((bal.toDouble() / denom) * plotH).toFloat()).coerceIn(topPadPx, topPadPx + plotH)

                drawRetirementGridAndYAxis(
                    textMeasurer = textMeasurer,
                    axisStyle = axisStyle,
                    gridColor = gridColor,
                    plotW = plotW,
                    topPadPx = topPadPx,
                    plotH = plotH,
                    maxBalance = denom.toLong()
                )

                // Target line + inline capsule pill anchored cleanly on the dashed line
                val targetY = balToY(targetNestEggCents)
                drawLine(
                    color = tertiaryColor.copy(alpha = 0.72f),
                    start = Offset(0f, targetY),
                    end = Offset(plotW, targetY),
                    strokeWidth = targetStroke.width,
                    pathEffect = targetStroke.pathEffect
                )
                val targetLabel = "Target ${MoneyUtils.formatCompactCents(targetNestEggCents)}"
                val measuredTarget = textMeasurer.measure(targetLabel, style = targetPillStyle, maxLines = 1)
                val pillW = measuredTarget.size.width + 12.dp.toPx()
                val pillH = measuredTarget.size.height + 4.dp.toPx()
                val pillX = (plotW - pillW - 6.dp.toPx()).coerceAtLeast(4.dp.toPx())
                val pillY = (targetY - pillH / 2f).coerceIn(topPadPx, topPadPx + plotH - pillH)
                drawRoundRect(
                    color = surfaceHighColor.copy(alpha = 0.94f),
                    topLeft = Offset(pillX, pillY),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawText(measuredTarget, topLeft = Offset(pillX + 6.dp.toPx(), pillY + 2.dp.toPx()))

                val todayX = ageToX(currentAge)
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.5f),
                    start = Offset(todayX, topPadPx),
                    end = Offset(todayX, topPadPx + plotH),
                    strokeWidth = markerStroke.width,
                    pathEffect = markerStroke.pathEffect
                )

                val selectedModel = scenarios[selectedPreset]
                if (selectedModel != null && selectedModel.accumulationPoints.size >= 2) {
                    val pts = selectedModel.accumulationPoints
                    val animPts = animatedSeriesMap[selectedPreset]?.sampleInDrawPhase(pts.size)
                    fun ptY(i: Int): Float = balFloatToY(
                        animPts?.getOrElse(i) { pts[i].balanceCents.toFloat() } ?: pts[i].balanceCents.toFloat()
                    )
                    areaPath.rewind()
                    areaPath.moveTo(ageToX(pts.first().age), ptY(0))
                    for (i in 1 until pts.size) {
                        areaPath.lineTo(ageToX(pts[i].age), ptY(i))
                    }
                    areaPath.lineTo(ageToX(pts.last().age), topPadPx + plotH)
                    areaPath.lineTo(ageToX(pts.first().age), topPadPx + plotH)
                    areaPath.close()
                    drawPath(areaPath, brush = areaBrush)
                }

                if (actualHistoryPoints.size >= 2) {
                    actualPath.rewind()
                    actualPath.moveTo(ageToX(actualHistoryPoints.first().age), balToY(actualHistoryPoints.first().balanceCents))
                    for (i in 1 until actualHistoryPoints.size) {
                        val pt = actualHistoryPoints[i]
                        actualPath.lineTo(ageToX(pt.age), balToY(pt.balanceCents))
                    }
                    drawPath(actualPath, color = onSurfaceVariant.copy(alpha = 0.7f), style = actualStroke)
                }

                drawCircle(color = onSurface, radius = 4.dp.toPx(), center = Offset(todayX, balToY(currentBalanceCents)))

                RetirementScenarioPreset.entries.forEach { preset ->
                    val model = scenarios[preset]
                    if (model != null && model.accumulationPoints.size >= 2) {
                        val color = presetColors[preset] ?: onSurfaceVariant
                        val isSelected = preset == selectedPreset
                        val pts = model.accumulationPoints
                        val animPts = animatedSeriesMap[preset]?.sampleInDrawPhase(pts.size)
                        fun ptY(i: Int): Float = balFloatToY(
                            animPts?.getOrElse(i) { pts[i].balanceCents.toFloat() } ?: pts[i].balanceCents.toFloat()
                        )
                        linePath.rewind()
                        linePath.moveTo(ageToX(pts.first().age), ptY(0))
                        for (i in 0 until pts.size - 1) {
                            val i0 = (i - 1).coerceAtLeast(0)
                            val i2 = i + 1
                            val i3 = (i + 2).coerceAtMost(pts.lastIndex)
                            val x1 = ageToX(pts[i].age); val y1 = ptY(i)
                            val x2 = ageToX(pts[i2].age); val y2 = ptY(i2)
                            val x0 = ageToX(pts[i0].age); val y0 = ptY(i0)
                            val x3 = ageToX(pts[i3].age); val y3 = ptY(i3)
                            linePath.cubicTo(
                                x1 + (x2 - x0) / 6f, (y1 + (y2 - y0) / 6f).coerceIn(topPadPx, topPadPx + plotH),
                                x2 - (x3 - x1) / 6f, (y2 - (y3 - y1) / 6f).coerceIn(topPadPx, topPadPx + plotH),
                                x2, y2
                            )
                        }
                        drawPath(
                            linePath,
                            color = if (isSelected) color else color.copy(alpha = 0.55f),
                            style = if (isSelected) thickCurveStroke else normalCurveStroke
                        )
                    }
                }
            }

            // Layer 2: Scrub indicator overlay reading fraction strictly in Draw phase
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frac = scrubFraction ?: return@Canvas
                val axisGutterPx = 46.dp.toPx()
                val topPadPx = 6.dp.toPx()
                val bottomPadPx = 4.dp.toPx()
                val plotW = (size.width - axisGutterPx).coerceAtLeast(1f)
                val plotH = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
                val denom = maxBalState.value.toDouble().coerceAtLeast(1.0)
                val activeAge = minAge + frac * ageSpan
                val activeBal = interpolateBalanceAtAge(scenarios[selectedPreset]?.accumulationPoints ?: emptyList(), activeAge)
                val sx = (frac * plotW).coerceIn(0f, plotW)
                val sy = (topPadPx + plotH - ((activeBal.toDouble() / denom) * plotH).toFloat()).coerceIn(topPadPx, topPadPx + plotH)

                drawLine(color = onSurface.copy(alpha = 0.75f), start = Offset(sx, topPadPx), end = Offset(sx, topPadPx + plotH), strokeWidth = markerStroke.width)
                drawCircle(color = selectedColor.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = selectedColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(width = 2.5.dp.toPx()))
            }

            if (retirementAge < 65.0 && hsaBridge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    val statusText = if (hsaBridge.isBridgeCovered) "Pre-65 Bridge: Covered" else "Bridge Gap: ${MoneyUtils.formatCompactCents(hsaBridge.shortfallCents)}"
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

        // Evenly spaced X-axis milestones aligned to plot width
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val mid1 = (currentAge + (retirementAge - currentAge) / 3.0).roundToInt()
            val mid2 = (currentAge + 2.0 * (retirementAge - currentAge) / 3.0).roundToInt()
            Text(text = "Today ${currentAge.roundToInt()}", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant)
            if (mid2 - mid1 >= 4) {
                Text(text = "$mid1", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant.copy(alpha = 0.78f))
                Text(text = "$mid2", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant.copy(alpha = 0.78f))
            }
            Text(text = "Retire ${retirementAge.roundToInt()}", style = TabularNumStyle.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold, color = selectedColor)
        }
    }
}
