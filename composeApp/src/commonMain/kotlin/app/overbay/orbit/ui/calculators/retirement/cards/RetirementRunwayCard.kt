package app.overbay.orbit.ui.calculators.retirement.cards

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RetirementRunwayCard(
    uiState: RetirementUiState,
    onRunwayEndAgeChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val scenario = uiState.scenario
    val density = LocalDensity.current
    val depletionAge = scenario.depletionAge
    val retirementAge = uiState.retirementAge
    val runwayEndAge = uiState.runwayEndAge
    val drawdownPoints = scenario.drawdownPoints

    val curveStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val dashStroke = remember(density) {
        Stroke(width = with(density) { 1.2.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    OrbitSectionCard(
        title = "Retirement Runway: Will It Last?",
        subtitle = "Drawdown trajectory keeping spending in today's dollars across retirement",
        modifier = modifier
    ) {
        // 1. Dedicated Drawdown Curve Canvas
        val maxDrawdownBal = remember(drawdownPoints, scenario.projectedBalanceCents) {
            val peak = drawdownPoints.maxOfOrNull { it.balanceCents } ?: scenario.projectedBalanceCents
            max(peak, 100_000_00L)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val ageSpan = max(1.0, runwayEndAge - retirementAge)

                fun ageToX(age: Double): Float = (((age - retirementAge) / ageSpan) * w).toFloat()
                fun balToY(bal: Cents): Float = h - ((bal.toDouble() / maxDrawdownBal.toDouble()) * h).toFloat()

                // Baseline 0 balance line
                drawLine(
                    color = outlineVariant.copy(alpha = 0.5f),
                    start = Offset(0f, h),
                    end = Offset(w, h),
                    strokeWidth = 1f
                )

                if (drawdownPoints.size >= 2) {
                    val linePath = Path()
                    val pFirst = drawdownPoints.first()
                    linePath.moveTo(ageToX(pFirst.age), balToY(pFirst.balanceCents))

                    for (i in 0 until drawdownPoints.size - 1) {
                        val p0 = if (i > 0) drawdownPoints[i - 1] else drawdownPoints[i]
                        val p1 = drawdownPoints[i]
                        val p2 = drawdownPoints[i + 1]
                        val p3 = if (i + 2 < drawdownPoints.size) drawdownPoints[i + 2] else p2
                        val x1 = ageToX(p1.age)
                        val y1 = balToY(p1.balanceCents)
                        val x2 = ageToX(p2.age)
                        val y2 = balToY(p2.balanceCents)
                        val x0 = ageToX(p0.age)
                        val y0 = balToY(p0.balanceCents)
                        val x3 = ageToX(p3.age)
                        val y3 = balToY(p3.balanceCents)
                        linePath.cubicTo(
                            x1 + (x2 - x0) / 6f, (y1 + (y2 - y0) / 6f).coerceIn(0f, h),
                            x2 - (x3 - x1) / 6f, (y2 - (y3 - y1) / 6f).coerceIn(0f, h),
                            x2, y2
                        )
                    }

                    val curveColor = if (depletionAge != null) errorColor else primaryColor
                    drawPath(linePath, color = curveColor, style = curveStroke)
                }

                // Depletion marker
                if (depletionAge != null) {
                    val dx = ageToX(depletionAge).coerceIn(0f, w)
                    drawLine(
                        color = errorColor.copy(alpha = 0.75f),
                        start = Offset(dx, 0f),
                        end = Offset(dx, h),
                        strokeWidth = dashStroke.width,
                        pathEffect = dashStroke.pathEffect
                    )
                    drawCircle(color = errorColor, radius = with(density) { 5.dp.toPx() }, center = Offset(dx, h))
                } else {
                    // Final Age positive balance marker
                    val fx = ageToX(runwayEndAge)
                    val fy = balToY(scenario.finalBalanceCents)
                    drawCircle(color = primaryColor, radius = with(density) { 5.dp.toPx() }, center = Offset(fx, fy))
                }
            }
        }

        // Drawdown Age Bounds Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Retire (${retirementAge.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
            if (depletionAge != null) {
                Text(
                    text = "Depleted at Age ${depletionAge.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = errorColor
                )
            }
            Text(
                text = "Final Age (${runwayEndAge.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Inline Final Age Slider (retirementAge + 1 to 110)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Final Age / Life Expectancy",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Age ${runwayEndAge.roundToInt()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val minFinalAge = (retirementAge.toFloat() + 1f).coerceAtLeast(70f)
        Slider(
            value = runwayEndAge.toFloat().coerceIn(minFinalAge, 110f),
            onValueChange = { onRunwayEndAgeChange(it.toDouble()) },
            valueRange = minFinalAge..110f,
            steps = max(0, (110 - minFinalAge.toInt()) - 1),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Metrics Details Surface
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Breakeven spending
                val breakeven = scenario.breakevenAnnualSpendingCents
                val spending = uiState.annualSpendingCents
                val isBelowBreakeven = spending <= breakeven
                val breakevenDiff = if (isBelowBreakeven) breakeven - spending else spending - breakeven
                val breakevenDetail = if (isBelowBreakeven) {
                    "${MoneyUtils.formatCompactCents(breakevenDiff)}/yr below static savings pace"
                } else {
                    "${MoneyUtils.formatCompactCents(breakevenDiff)}/yr above static savings pace"
                }

                OrbitMiniStatRow(
                    label = "Breakeven Spending",
                    value = "${MoneyUtils.formatCents(breakeven, showCents = false)} / yr",
                    valueColor = if (isBelowBreakeven) primaryColor else errorColor
                )
                Text(
                    text = breakevenDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBelowBreakeven) primaryColor else onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // At Final Age Balance
                OrbitMiniStatRow(
                    label = "At Age ${runwayEndAge.toInt()}",
                    value = MoneyUtils.formatCents(scenario.finalBalanceCents, showCents = false),
                    valueColor = if (scenario.finalBalanceCents > 0L) primaryColor else errorColor
                )

                // Runway Status
                val runwayText = if (depletionAge != null) {
                    "Depletes at Age ${depletionAge.roundToInt()}"
                } else {
                    "Past Age ${runwayEndAge.toInt()} ♾️"
                }
                OrbitMiniStatRow(
                    label = "Runway",
                    value = runwayText,
                    valueColor = if (depletionAge != null) errorColor else primaryColor
                )
            }
        }
    }
}
