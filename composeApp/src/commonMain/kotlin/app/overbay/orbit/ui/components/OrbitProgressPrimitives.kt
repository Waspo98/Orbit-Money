package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.charts.rememberAnimatedFloatSeries

/**
 * Slim pill-shaped progress bar for budget pulse, goal funding, and category limits.
 * Animates fill ratio via [OrbitSprings.FloatSpatialDefault] and status color transitions
 * via [OrbitSprings.ColorEffectsDefault].
 */
@Composable
fun OrbitPillProgressBar(
    percent: Float,
    modifier: Modifier = Modifier,
    overBudget: Boolean = false,
    height: Dp = 10.dp,
    customFillColor: Color? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    paceMarkerFraction: Float? = null
) {
    val clampedPercent = percent.coerceIn(0f, 1f)
    val animatedPercent by animateFloatAsState(
        targetValue = clampedPercent,
        animationSpec = OrbitSprings.FloatSpatialDefault,
        label = "orbitPillProgress"
    )
    val targetFillColor = customFillColor ?: when {
        overBudget -> OrbitTheme.colors.expense
        percent >= 0.85f -> OrbitTheme.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedFillColor by animateColorAsState(
        targetValue = targetFillColor,
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "orbitPillProgressColor"
    )
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        LinearProgressIndicator(
            progress = { animatedPercent },
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            color = animatedFillColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        if (paceMarkerFraction != null) {
            val clampedPace = paceMarkerFraction.coerceIn(0.02f, 0.98f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = clampedPace * size.width
                val strokeWidth = 2.dp.toPx()
                drawLine(
                    color = markerColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Immutable
data class OrbitBarSegment(
    val key: Any,
    val fraction: Float,
    val color: Color
)

/**
 * Proportional multi-segment horizontal bar drawn in a single GPU [Canvas] with
 * spring-animated slice widths ([OrbitSprings.FloatSpatialDefault]).
 */
@Composable
fun OrbitStackedSegmentBar(
    segments: List<OrbitBarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    cornerRadius: Dp = 999.dp,
    normalizeToFullWidth: Boolean = false,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val rawFractions = if (normalizeToFullWidth) {
        val total = segments.sumOf { it.fraction.coerceAtLeast(0f).toDouble() }.toFloat()
        if (total > 0f) segments.map { it.fraction.coerceAtLeast(0f) / total } else segments.map { 0f }
    } else {
        segments.map { it.fraction.coerceIn(0f, 1f) }
    }
    val animatedFractions = rememberAnimatedFloatSeries(targets = rawFractions)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        val w = size.width
        val h = size.height
        drawRect(color = trackColor, size = size)

        var currentX = 0f
        segments.forEachIndexed { index, segment ->
            val frac = animatedFractions.getOrElse(index) { segment.fraction }.coerceAtLeast(0f)
            val sliceWidth = (frac * w).coerceAtMost((w - currentX).coerceAtLeast(0f))
            if (sliceWidth > 0.5f) {
                drawRect(
                    color = segment.color,
                    topLeft = Offset(currentX, 0f),
                    size = Size(sliceWidth, h)
                )
                currentX += sliceWidth
            }
        }
    }
}
