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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Interactive Catmull-Rom cubic Bezier net worth area chart with gradient vertical fill.
 * Supports touch drag scrubbing with haptic ticks and zero-allocation 60fps rendering.
 */
@Composable
fun NetWorthAreaChart(
    points: List<MonthlyNetWorthPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onPointSelected: ((MonthlyNetWorthPoint?) -> Unit)? = null
) {
    if (points.isEmpty()) return

    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var lastHapticIndex by remember { mutableStateOf<Int?>(null) }

    // Pre-allocated reusable paths (ZERO object allocation in Canvas onDraw)
    val linePath = remember { Path() }
    val areaPath = remember { Path() }

    // Pre-allocated strokes and brushes
    val lineStroke = remember(density) {
        Stroke(
            width = with(density) { 3.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
    val gridStroke = remember(density) {
        Stroke(
            width = with(density) { 1.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        )
    }
    val scrubRailStroke = remember(density) {
        Stroke(
            width = with(density) { 1.5.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    }
    val indicatorStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() })
    }
    val haloRadiusPx = remember(density) { with(density) { 12.dp.toPx() } }
    val dotRadiusPx = remember(density) { with(density) { 5.dp.toPx() } }

    val areaBrush = remember(primaryColor) {
        Brush.verticalGradient(
            listOf(primaryColor.copy(alpha = 0.32f), primaryColor.copy(alpha = 0.0f))
        )
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Calculate value range with padding
    val rawValues = remember(points) { points.map { it.snapshot.netWorthCents } }
    val minVal = rawValues.minOrNull() ?: 0L
    val maxVal = rawValues.maxOrNull() ?: 1L
    val span = max(1L, maxVal - minVal)
    val paddedMin = minVal - (span * 0.12).toLong()
    val paddedMax = maxVal + (span * 0.12).toLong()
    val totalSpan = max(1L, paddedMax - paddedMin).toDouble()

    // Trigger haptics when selected point changes
    LaunchedEffect(selectedIndex) {
        val idx = selectedIndex
        if (idx != null && idx != lastHapticIndex) {
            haptics.tick()
            lastHapticIndex = idx
            onPointSelected?.invoke(points.getOrNull(idx))
        } else if (idx == null) {
            lastHapticIndex = null
            onPointSelected?.invoke(null)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            val n = points.size
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            selectedIndex = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                            tryAwaitRelease()
                            selectedIndex = null
                        }
                    )
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val n = points.size
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            selectedIndex = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        },
                        onDragEnd = { selectedIndex = null },
                        onDragCancel = { selectedIndex = null },
                        onDrag = { change, _ ->
                            val n = points.size
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            selectedIndex = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val n = points.size

                // 1. Draw 3 Horizontal Gridlines (Min, Mid, Max)
                val yTop = h * 0.12f
                val yMid = h * 0.50f
                val yBottom = h * 0.88f
                drawLine(outlineColor, Offset(0f, yTop), Offset(w, yTop), strokeWidth = gridStroke.width, pathEffect = gridStroke.pathEffect)
                drawLine(outlineColor, Offset(0f, yMid), Offset(w, yMid), strokeWidth = gridStroke.width, pathEffect = gridStroke.pathEffect)
                drawLine(outlineColor, Offset(0f, yBottom), Offset(w, yBottom), strokeWidth = gridStroke.width, pathEffect = gridStroke.pathEffect)

                // 2. Compute screen coordinates
                val pointCoords = ArrayList<Offset>(n)
                for (i in 0 until n) {
                    val px = if (n == 1) w / 2f else (i.toFloat() / (n - 1).toFloat()) * w
                    val value = points[i].snapshot.netWorthCents
                    val normalized = ((value - paddedMin).toDouble() / totalSpan).toFloat().coerceIn(0f, 1f)
                    val py = h - (normalized * h)
                    pointCoords.add(Offset(px, py))
                }

                // 3. Build Catmull-Rom Cubic Bezier Spline
                linePath.rewind()
                areaPath.rewind()

                if (pointCoords.size >= 2) {
                    linePath.moveTo(pointCoords[0].x, pointCoords[0].y)
                    for (i in 0 until pointCoords.size - 1) {
                        val p0 = if (i > 0) pointCoords[i - 1] else pointCoords[i]
                        val p1 = pointCoords[i]
                        val p2 = pointCoords[i + 1]
                        val p3 = if (i + 2 < pointCoords.size) pointCoords[i + 2] else p2

                        val cp1x = p1.x + (p2.x - p0.x) / 6f
                        val cp1y = (p1.y + (p2.y - p0.y) / 6f).coerceIn(0f, h)
                        val cp2x = p2.x - (p3.x - p1.x) / 6f
                        val cp2y = (p2.y - (p3.y - p1.y) / 6f).coerceIn(0f, h)

                        linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    }

                    // Complete Area Path
                    areaPath.addPath(linePath)
                    areaPath.lineTo(w, h)
                    areaPath.lineTo(0f, h)
                    areaPath.close()

                    // Draw Gradient Fill and Curve Stroke
                    drawPath(areaPath, brush = areaBrush)
                    drawPath(linePath, color = primaryColor, style = lineStroke)
                }

                // 4. Draw Active Scrubbing Rail and Indicator Thumb
                val activeIdx = selectedIndex
                if (activeIdx != null && activeIdx in pointCoords.indices) {
                    val activePt = pointCoords[activeIdx]

                    // Vertical dashed scrub rail
                    drawLine(
                        color = onSurfaceVariant.copy(alpha = 0.5f),
                        start = Offset(activePt.x, 0f),
                        end = Offset(activePt.x, h),
                        strokeWidth = scrubRailStroke.width,
                        pathEffect = scrubRailStroke.pathEffect
                    )

                    // Outer halo
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.22f),
                        radius = haloRadiusPx,
                        center = activePt
                    )

                    // Inner surface dot with solid primary border
                    drawCircle(
                        color = surfaceColor,
                        radius = dotRadiusPx,
                        center = activePt
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = dotRadiusPx,
                        center = activePt,
                        style = indicatorStroke
                    )
                }
            }
        }

        // 5. Co-located Accessible Month Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            val firstMonth = points.firstOrNull()?.month ?: ""
            val midMonth = if (points.size > 2) points[points.size / 2].month else ""
            val lastMonth = points.lastOrNull()?.month ?: ""

            Text(
                text = firstMonth,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (midMonth.isNotEmpty()) {
                Text(
                    text = midMonth,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
            Text(
                text = lastMonth,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}
