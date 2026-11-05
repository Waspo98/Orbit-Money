package app.overbay.orbit.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Multi-phase retirement trajectory chart (accumulation + drawdown).
 * Features Catmull-Rom growth curves, target nest egg threshold line, retirement milestone marker,
 * and touch scrubbing with haptic ticks on age transitions.
 */
@Composable
fun RetirementProjectionChart(
    currentAge: Double,
    retirementAge: Double,
    runwayEndAge: Double = 95.0,
    accumulationPoints: List<ProjectionPoint>,
    drawdownPoints: List<ProjectionPoint>,
    targetNestEggCents: Cents,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    coastFireTargetCents: Cents? = null,
    onAgeScrubbed: ((Double?, Cents?) -> Unit)? = null
) {
    val allPoints = remember(accumulationPoints, drawdownPoints) {
        val list = mutableListOf<ProjectionPoint>()
        list.addAll(accumulationPoints)
        if (drawdownPoints.isNotEmpty()) {
            val lastAge = accumulationPoints.lastOrNull()?.age
            val filteredDrawdown = drawdownPoints.filter { it.age > (lastAge ?: 0.0) }
            list.addAll(filteredDrawdown)
        }
        list
    }

    if (allPoints.isEmpty()) return

    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current

    var scrubbedAge by remember { mutableStateOf<Double?>(null) }
    var lastHapticAge by remember { mutableStateOf<Int?>(null) }

    // Pre-allocated paths (Zero allocation in Canvas onDraw)
    val accumLinePath = remember { Path() }
    val accumAreaPath = remember { Path() }
    val drawdownLinePath = remember { Path() }

    // Pre-allocated strokes and brushes
    val curveStroke = remember(density) {
        Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val targetStroke = remember(density) {
        Stroke(width = with(density) { 1.5.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    }
    val milestoneStroke = remember(density) {
        Stroke(width = with(density) { 1.5.dp.toPx() }, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
    val indicatorStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() })
    }
    val haloRadiusPx = remember(density) { with(density) { 12.dp.toPx() } }
    val dotRadiusPx = remember(density) { with(density) { 5.dp.toPx() } }

    val primaryColor = MaterialTheme.colorScheme.primary
    val areaBrush = remember(primaryColor) {
        Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.28f), primaryColor.copy(alpha = 0.0f)))
    }

    val targetColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Maximum balance for Y-axis scale
    val maxBalance = remember(allPoints, targetNestEggCents) {
        val peak = allPoints.maxOfOrNull { it.balanceCents } ?: targetNestEggCents
        (max(peak, targetNestEggCents) * 1.15).toLong()
    }

    // Trigger haptics when scrubbed integer age changes
    LaunchedEffect(scrubbedAge) {
        val age = scrubbedAge
        if (age != null) {
            val intAge = age.roundToInt()
            if (intAge != lastHapticAge) {
                haptics.tick()
                lastHapticAge = intAge
                val pt = allPoints.minByOrNull { kotlin.math.abs(it.age - age) }
                onAgeScrubbed?.invoke(age, pt?.balanceCents)
            }
        } else {
            lastHapticAge = null
            onAgeScrubbed?.invoke(null, null)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(allPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubbedAge = currentAge + fraction * (runwayEndAge - currentAge)
                            tryAwaitRelease()
                            scrubbedAge = null
                        }
                    )
                }
                .pointerInput(allPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubbedAge = currentAge + fraction * (runwayEndAge - currentAge)
                        },
                        onDragEnd = { scrubbedAge = null },
                        onDragCancel = { scrubbedAge = null },
                        onDrag = { change, _ ->
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            scrubbedAge = currentAge + fraction * (runwayEndAge - currentAge)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val ageSpan = runwayEndAge - currentAge

                fun ageToX(age: Double): Float = ((age - currentAge) / ageSpan).toFloat() * w
                fun balToY(bal: Cents): Float = h - ((bal.toDouble() / maxBalance.toDouble()) * h).toFloat()

                // 1. Draw Target Nest Egg Line
                val targetY = balToY(targetNestEggCents)
                drawLine(
                    color = targetColor.copy(alpha = 0.75f),
                    start = Offset(0f, targetY),
                    end = Offset(w, targetY),
                    strokeWidth = targetStroke.width,
                    pathEffect = targetStroke.pathEffect
                )

                // 2. Draw Retirement Milestone Line
                val retireX = ageToX(retirementAge)
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.5f),
                    start = Offset(retireX, 0f),
                    end = Offset(retireX, h),
                    strokeWidth = milestoneStroke.width,
                    pathEffect = milestoneStroke.pathEffect
                )

                // 3. Accumulation Curve & Area
                accumLinePath.rewind()
                accumAreaPath.rewind()
                val accumCoords = accumulationPoints.map { Offset(ageToX(it.age), balToY(it.balanceCents)) }
                if (accumCoords.size >= 2) {
                    accumLinePath.moveTo(accumCoords[0].x, accumCoords[0].y)
                    for (i in 0 until accumCoords.size - 1) {
                        val p0 = if (i > 0) accumCoords[i - 1] else accumCoords[i]
                        val p1 = accumCoords[i]
                        val p2 = accumCoords[i + 1]
                        val p3 = if (i + 2 < accumCoords.size) accumCoords[i + 2] else p2
                        accumLinePath.cubicTo(
                            p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(0f, h),
                            p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(0f, h),
                            p2.x, p2.y
                        )
                    }
                    accumAreaPath.addPath(accumLinePath)
                    accumAreaPath.lineTo(accumCoords.last().x, h)
                    accumAreaPath.lineTo(accumCoords.first().x, h)
                    accumAreaPath.close()

                    drawPath(accumAreaPath, brush = areaBrush)
                    drawPath(accumLinePath, color = primaryColor, style = curveStroke)
                }

                // 4. Drawdown Curve
                drawdownLinePath.rewind()
                val drawdownCoords = drawdownPoints.map { Offset(ageToX(it.age), balToY(it.balanceCents)) }
                if (drawdownCoords.size >= 2) {
                    drawdownLinePath.moveTo(drawdownCoords[0].x, drawdownCoords[0].y)
                    for (i in 0 until drawdownCoords.size - 1) {
                        val p0 = if (i > 0) drawdownCoords[i - 1] else drawdownCoords[i]
                        val p1 = drawdownCoords[i]
                        val p2 = drawdownCoords[i + 1]
                        val p3 = if (i + 2 < drawdownCoords.size) drawdownCoords[i + 2] else p2
                        drawdownLinePath.cubicTo(
                            p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(0f, h),
                            p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(0f, h),
                            p2.x, p2.y
                        )
                    }
                    drawPath(drawdownLinePath, color = onSurfaceVariant.copy(alpha = 0.75f), style = curveStroke)
                }

                // 5. Active Scrubbing Indicator
                val activeAge = scrubbedAge
                if (activeAge != null) {
                    val scrubX = ageToX(activeAge)
                    val pt = allPoints.minByOrNull { kotlin.math.abs(it.age - activeAge) }
                    val scrubY = if (pt != null) balToY(pt.balanceCents) else h / 2f

                    drawLine(
                        color = onSurfaceVariant.copy(alpha = 0.6f),
                        start = Offset(scrubX, 0f),
                        end = Offset(scrubX, h),
                        strokeWidth = milestoneStroke.width,
                        pathEffect = milestoneStroke.pathEffect
                    )

                    drawCircle(color = primaryColor.copy(alpha = 0.22f), radius = haloRadiusPx, center = Offset(scrubX, scrubY))
                    drawCircle(color = surfaceColor, radius = dotRadiusPx, center = Offset(scrubX, scrubY))
                    drawCircle(color = primaryColor, radius = dotRadiusPx, center = Offset(scrubX, scrubY), style = indicatorStroke)
                }
            }
        }

        // 6. X-Axis Age Milestones
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Text(
                text = "Age ${currentAge.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Retire (${retirementAge.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Age ${runwayEndAge.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
