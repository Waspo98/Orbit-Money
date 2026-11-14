package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Interactive Canvas Donut chart for [SpendingMixPanel] supporting tap-to-select slices,
 * spring-animated stroke expansion, dimmed non-selected arcs, and a morphing center readout.
 */
@Composable
internal fun SpendingMixDonut(
    slices: List<SpendingMixSlice>,
    totalSpent: Cents,
    totalCategoryCount: Int,
    activeSlice: SpendingMixSlice?,
    onToggleSlice: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val hasSelection = activeSlice != null
    val totalRawSweep = slices.sumOf {
        ((it.spentCents.toDouble() / totalSpent.toDouble()) * 360.0).coerceAtLeast(3.0)
    }.toFloat()
    val sweepScale = if (totalRawSweep > 0f) 360f / totalRawSweep else 1f

    val sliceAngles = buildList {
        var cursor = 0f
        slices.forEach { slice ->
            val sweep = ((slice.spentCents.toFloat() / totalSpent.toFloat()) * 360f).coerceAtLeast(3f) * sweepScale
            add(Triple(slice, cursor, cursor + sweep))
            cursor += sweep
        }
    }

    val animatedStrokes = slices.map { slice ->
        val target = if (activeSlice?.key == slice.key) 26.dp else 20.dp
        animateDpAsState(targetValue = target, animationSpec = OrbitSprings.DpSpatialFast, label = "donutStroke").value
    }
    val animatedAlphas = slices.map { slice ->
        val target = if (!hasSelection || activeSlice?.key == slice.key) 1f else 0.35f
        animateFloatAsState(targetValue = target, animationSpec = OrbitSprings.FloatEffectsFast, label = "donutAlpha").value
    }

    Box(
        modifier = modifier.size(168.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sliceAngles, hasSelection) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val outerRadius = min(size.width, size.height) / 2f
                        val innerRadius = outerRadius * 0.45f

                        if (dist < innerRadius) {
                            if (hasSelection) {
                                haptics.tick()
                                onClearSelection()
                            }
                            return@detectTapGestures
                        }

                        val rawDeg = (atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)).toFloat()
                        val clockDeg = (rawDeg + 90f + 360f) % 360f
                        val hit = sliceAngles.firstOrNull { (_, start, end) ->
                            clockDeg >= start && clockDeg < end
                        } ?: sliceAngles.lastOrNull()
                        if (hit != null) {
                            haptics.tick()
                            onToggleSlice(hit.first.key)
                        }
                    }
                }
        ) {
            val basePad = 16.dp.toPx()
            val arcDiameter = min(size.width, size.height) - basePad * 2f
            val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
            val arcSize = Size(arcDiameter, arcDiameter)
            val gapDeg = if (slices.size > 1) 2.5f else 0f

            var startDeg = -90f
            sliceAngles.forEachIndexed { index, (slice, start, end) ->
                val rawSweep = end - start
                val drawnSweep = (rawSweep - gapDeg).coerceAtLeast(1f)
                drawArc(
                    color = slice.color.copy(alpha = animatedAlphas[index]),
                    startAngle = startDeg,
                    sweepAngle = drawnSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = animatedStrokes[index].toPx(), cap = StrokeCap.Butt)
                )
                startDeg += rawSweep
            }
        }

        AnimatedContent(
            targetState = activeSlice,
            transitionSpec = { orbitFadeContentTransform() },
            label = "donutCenterReadout"
        ) { selected ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                if (selected == null) {
                    Text(
                        text = "TOTAL SPENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyUtils.formatCents(totalSpent, showCents = false),
                        style = TabularNumStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap slice to inspect",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = selected.item?.category?.name ?: selected.centerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = MoneyUtils.formatCents(selected.spentCents, showCents = false),
                        style = TabularNumStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${selected.percentInt}% of $totalCategoryCount active",
                        style = TabularNumStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                        color = selected.color
                    )
                }
            }
        }
    }
}
