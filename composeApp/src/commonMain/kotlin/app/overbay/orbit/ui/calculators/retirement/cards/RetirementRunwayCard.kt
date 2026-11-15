package app.overbay.orbit.ui.calculators.retirement.cards

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.calculators.retirement.components.OrbitSmoothSlider
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.charts.chartHorizontalScrub
import app.overbay.orbit.ui.components.charts.interpolateBalanceAtAge
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RetirementRunwayCard(
    uiState: RetirementUiState,
    onRunwayEndAgeChange: (Double) -> Unit,
    onDragStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scenario = uiState.scenario
    val density = LocalDensity.current
    val haptics = LocalOrbitHaptics.current
    val textMeasurer = rememberTextMeasurer()

    val depletionAge = scenario.depletionAge
    val retirementAge = uiState.retirementAge
    val runwayEndAge = uiState.runwayEndAge
    val drawdownPoints = scenario.drawdownPoints

    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var lastHapticAge by remember { mutableStateOf<Int?>(null) }

    val curveStroke = remember(density) {
        Stroke(width = with(density) { 2.6.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val dashStroke = remember(density) {
        Stroke(width = with(density) { 1.2.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }

    val primaryColor = OrbitTheme.colors.income
    val errorColor = OrbitTheme.colors.expense
    val gridColor = OrbitTheme.colors.cardBorder
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val pillBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val curveColor = if (depletionAge != null) errorColor else primaryColor
    val areaBrush = remember(curveColor) {
        Brush.verticalGradient(listOf(curveColor.copy(alpha = 0.2f), curveColor.copy(alpha = 0.01f)))
    }
    val tabularBase = TabularNumStyle
    val axisStyle = remember(tabularBase, onSurfaceVariant) {
        tabularBase.copy(fontSize = 9.5.sp, color = onSurfaceVariant)
    }
    val depletionPillStyle = remember(tabularBase, errorColor) {
        tabularBase.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = errorColor)
    }

    val maxDrawdownBal = remember(drawdownPoints, scenario.projectedBalanceCents) {
        val peak = drawdownPoints.maxOfOrNull { it.balanceCents } ?: scenario.projectedBalanceCents
        max(peak, 100_000_00L)
    }
    val ageSpan = max(1.0, runwayEndAge - retirementAge)
    val scrubbedAge = scrubFraction?.let { retirementAge + it * ageSpan }
    val scrubbedBalance = scrubbedAge?.let { interpolateBalanceAtAge(drawdownPoints, it) }

    OrbitSectionCard(
        title = "Retirement Runway",
        subtitle = "Inflation-adjusted drawdown path across retirement",
        modifier = modifier
    ) {
        // Constant-height interactive readout strip above drawdown curve
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = pillBgColor.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, gridColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .height(34.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (scrubbedAge != null) "Age ${scrubbedAge.roundToInt()}" else "Retire ${retirementAge.toInt()} -> Age ${runwayEndAge.toInt()}",
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = onSurface
                )
                Text(
                    text = if (scrubbedBalance != null) {
                        MoneyUtils.formatCents(scrubbedBalance, showCents = false)
                    } else if (depletionAge != null) {
                        "Depletes Age ${depletionAge.roundToInt()}"
                    } else {
                        "${MoneyUtils.formatCompactCents(scenario.finalBalanceCents)} at ${runwayEndAge.toInt()}"
                    },
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = if (scrubbedBalance == null && depletionAge != null) errorColor else primaryColor
                )
            }
        }

        // 1. Dedicated Drawdown Curve Canvas with Y-axis + Proportional Depletion Pin
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .chartHorizontalScrub(
                    key = retirementAge to runwayEndAge,
                    clearOnRelease = true,
                    onFractionChanged = { fraction ->
                        if (fraction != null) {
                            val intAge = (retirementAge + fraction * ageSpan).roundToInt()
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val axisGutterPx = 46.dp.toPx()
                val topPadPx = 8.dp.toPx()
                val bottomPadPx = 4.dp.toPx()
                val plotW = (size.width - axisGutterPx).coerceAtLeast(1f)
                val plotH = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)

                fun ageToX(age: Double): Float = (((age - retirementAge) / ageSpan) * plotW).toFloat().coerceIn(0f, plotW)
                fun balToY(bal: Cents): Float = (topPadPx + plotH - ((bal.toDouble() / maxDrawdownBal.toDouble()) * plotH).toFloat()).coerceIn(topPadPx, topPadPx + plotH)

                listOf(1f to maxDrawdownBal, 0.5f to (maxDrawdownBal / 2L), 0f to 0L).forEach { (ratio, cents) ->
                    val y = topPadPx + plotH * (1f - ratio)
                    drawLine(color = gridColor.copy(alpha = 0.5f), start = Offset(0f, y), end = Offset(plotW, y), strokeWidth = 1.dp.toPx())
                    val label = textMeasurer.measure(MoneyUtils.formatCompactCents(cents), style = axisStyle, maxLines = 1)
                    val labelY = (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
                    drawText(label, topLeft = Offset(plotW + 6.dp.toPx(), labelY))
                }

                if (drawdownPoints.size >= 2) {
                    linePath.rewind()
                    fillPath.rewind()
                    val first = drawdownPoints.first()
                    val firstX = ageToX(first.age)
                    val firstY = balToY(first.balanceCents)
                    linePath.moveTo(firstX, firstY)
                    fillPath.moveTo(firstX, firstY)

                    for (i in 0 until drawdownPoints.size - 1) {
                        val p0 = if (i > 0) drawdownPoints[i - 1] else drawdownPoints[i]
                        val p1 = drawdownPoints[i]
                        val p2 = drawdownPoints[i + 1]
                        val p3 = if (i + 2 < drawdownPoints.size) drawdownPoints[i + 2] else p2
                        val x1 = ageToX(p1.age); val y1 = balToY(p1.balanceCents)
                        val x2 = ageToX(p2.age); val y2 = balToY(p2.balanceCents)
                        val x0 = ageToX(p0.age); val y0 = balToY(p0.balanceCents)
                        val x3 = ageToX(p3.age); val y3 = balToY(p3.balanceCents)
                        val cx1 = x1 + (x2 - x0) / 6f
                        val cy1 = (y1 + (y2 - y0) / 6f).coerceIn(topPadPx, topPadPx + plotH)
                        val cx2 = x2 - (x3 - x1) / 6f
                        val cy2 = (y2 - (y3 - y1) / 6f).coerceIn(topPadPx, topPadPx + plotH)
                        linePath.cubicTo(cx1, cy1, cx2, cy2, x2, y2)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, x2, y2)
                    }
                    fillPath.lineTo(ageToX(drawdownPoints.last().age), topPadPx + plotH)
                    fillPath.lineTo(firstX, topPadPx + plotH)
                    fillPath.close()

                    drawPath(fillPath, brush = areaBrush)
                    drawPath(linePath, color = curveColor, style = curveStroke)
                }

                // Proportional Depletion Pin OR Ending Marker
                if (depletionAge != null) {
                    val dx = ageToX(depletionAge).coerceIn(0f, plotW)
                    drawLine(
                        color = errorColor.copy(alpha = 0.78f),
                        start = Offset(dx, topPadPx),
                        end = Offset(dx, topPadPx + plotH),
                        strokeWidth = dashStroke.width,
                        pathEffect = dashStroke.pathEffect
                    )
                    drawCircle(color = errorColor, radius = 4.5.dp.toPx(), center = Offset(dx, topPadPx + plotH))

                    val pinText = textMeasurer.measure("Depleted Age ${depletionAge.roundToInt()}", style = depletionPillStyle, maxLines = 1)
                    val pillW = pinText.size.width + 12.dp.toPx()
                    val pillH = pinText.size.height + 6.dp.toPx()
                    val pillX = (dx - pillW / 2f).coerceIn(4.dp.toPx(), (plotW - pillW - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                    drawRoundRect(
                        color = pillBgColor.copy(alpha = 0.94f),
                        topLeft = Offset(pillX, topPadPx + 4.dp.toPx()),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    drawText(pinText, topLeft = Offset(pillX + 6.dp.toPx(), topPadPx + 7.dp.toPx()))
                } else {
                    val fx = ageToX(runwayEndAge)
                    val fy = balToY(scenario.finalBalanceCents)
                    drawCircle(color = primaryColor, radius = 4.5.dp.toPx(), center = Offset(fx, fy))
                }

                // Scrub Hairline Readout Overlay
                val frac = scrubFraction
                if (frac != null && scrubbedBalance != null) {
                    val sx = (frac * plotW).coerceIn(0f, plotW)
                    val sy = balToY(scrubbedBalance)
                    drawLine(color = onSurface.copy(alpha = 0.75f), start = Offset(sx, topPadPx), end = Offset(sx, topPadPx + plotH), strokeWidth = 1.dp.toPx())
                    drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
                    drawCircle(color = curveColor, radius = 5.dp.toPx(), center = Offset(sx, sy), style = Stroke(width = 2.2.dp.toPx()))
                }
            }
        }

        // X-Axis Bounds aligned with plotW
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Retire ${retirementAge.toInt()}", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant)
            val midAge = ((retirementAge + runwayEndAge) / 2.0).roundToInt()
            Text(text = "Age $midAge", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant.copy(alpha = 0.75f))
            Text(text = "Age ${runwayEndAge.toInt()}", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Smooth Plan Horizon Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Plan Through Age",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onSurface
            )
            Text(
                text = "Age ${runwayEndAge.roundToInt()}",
                style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        val minFinalAge = (retirementAge.toFloat() + 1f).coerceAtLeast(75f)
        val maxFinalAge = max(110f, minFinalAge + 5f)
        OrbitSmoothSlider(
            value = runwayEndAge.toFloat().coerceIn(minFinalAge, maxFinalAge),
            valueRange = minFinalAge..maxFinalAge,
            onDragStateChanged = onDragStateChanged,
            onValueChange = { raw ->
                val quantized = raw.roundToInt().toDouble()
                if (quantized != runwayEndAge) {
                    haptics.tick()
                    onRunwayEndAgeChange(quantized)
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Clear Financial Summary Metrics
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, gridColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val breakeven = scenario.breakevenAnnualSpendingCents
                val spending = uiState.annualSpendingCents
                val isSustainable = spending <= breakeven
                val diffCents = if (isSustainable) breakeven - spending else spending - breakeven
                val diffDetail = if (isSustainable) {
                    "${MoneyUtils.formatCompactCents(diffCents)}/yr cushion below target budget"
                } else {
                    "${MoneyUtils.formatCompactCents(diffCents)}/yr below desired target budget"
                }

                OrbitMiniStatRow(
                    label = "Principal-Intact Income",
                    detail = diffDetail,
                    value = "${MoneyUtils.formatCents(breakeven, showCents = false)} / yr",
                    valueColor = if (isSustainable) primaryColor else errorColor
                )

                OrbitMiniStatRow(
                    label = "Projected at Age ${runwayEndAge.toInt()}",
                    value = MoneyUtils.formatCents(scenario.finalBalanceCents, showCents = false),
                    valueColor = if (scenario.finalBalanceCents > 0L) primaryColor else errorColor,
                    showTopDivider = true
                )

                OrbitMiniStatRow(
                    label = "Runway Longevity",
                    value = if (depletionAge != null) "Depletes at Age ${depletionAge.roundToInt()}" else "Lasts Past Age ${runwayEndAge.toInt()}",
                    valueColor = if (depletionAge != null) errorColor else primaryColor,
                    showTopDivider = true
                )
            }
        }
    }
}
