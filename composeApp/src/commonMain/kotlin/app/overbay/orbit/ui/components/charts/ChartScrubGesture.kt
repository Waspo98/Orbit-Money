package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.lerp
import app.overbay.orbit.theme.OrbitSprings

/**
 * Horizontal-only scrub & tap gesture modifier for interactive Canvas charts inside vertical
 * [androidx.compose.foundation.lazy.LazyColumn]s. Uses [detectHorizontalDragGestures] so vertical
 * thumb drags pass straight through to page scrolling without being hijacked by the chart.
 */
@Composable
fun Modifier.chartHorizontalScrub(
    key: Any?,
    clearOnRelease: Boolean = true,
    onFractionChanged: (Float?) -> Unit,
    onTapFraction: ((Float) -> Unit)? = null
): Modifier {
    val currentOnFraction by rememberUpdatedState(onFractionChanged)
    val currentOnTap by rememberUpdatedState(onTapFraction)

    return this
        .pointerInput(key, clearOnRelease) {
            detectTapGestures(
                onPress = { offset ->
                    val tapHandler = currentOnTap
                    if (tapHandler == null && size.width > 0) {
                        currentOnFraction((offset.x / size.width).coerceIn(0f, 1f))
                        tryAwaitRelease()
                        if (clearOnRelease) {
                            currentOnFraction(null)
                        }
                    }
                },
                onTap = { offset ->
                    val tapHandler = currentOnTap
                    if (tapHandler != null && size.width > 0) {
                        tapHandler((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
            )
        }
        .pointerInput(key, clearOnRelease) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    if (size.width > 0) {
                        currentOnFraction((offset.x / size.width).coerceIn(0f, 1f))
                    }
                },
                onDragEnd = {
                    if (clearOnRelease) currentOnFraction(null)
                },
                onDragCancel = {
                    if (clearOnRelease) currentOnFraction(null)
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        currentOnFraction((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
            )
        }
}

/**
 * Snaps immediately to [targetPoint] on initial touch-down, then smoothly glides `(x, y)`
 * across discrete data points with [OrbitSprings.spatialFast] while scrubbing.
 */
@Composable
fun rememberSmoothScrubPosition(targetPoint: Offset?): Offset? {
    val anim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var wasActive by remember { mutableStateOf(false) }

    LaunchedEffect(targetPoint) {
        if (targetPoint == null) {
            wasActive = false
        } else if (!wasActive) {
            wasActive = true
            anim.snapTo(targetPoint)
        } else {
            anim.animateTo(targetPoint, OrbitSprings.spatialFast())
        }
    }

    return if (targetPoint == null) null else if (!wasActive) targetPoint else anim.value
}

/**
 * Smoothly interpolates an array of normalized `[0f..1f]` chart values across dataset/period
 * transitions using a single shared [Animatable] (zero per-point coroutine allocation).
 */
@Composable
fun rememberAnimatedFloatSeries(targets: List<Float>): FloatArray {
    var fromSeries by remember { mutableStateOf(targets.toFloatArray()) }
    var toSeries by remember { mutableStateOf(targets.toFloatArray()) }
    var progress by remember { mutableFloatStateOf(1f) }
    val anim = remember { Animatable(1f) }

    LaunchedEffect(targets) {
        val next = targets.toFloatArray()
        if (!next.contentEquals(toSeries)) {
            val currentSnapshot = sampleInterpolatedSeries(fromSeries, toSeries, progress, next.size)
            fromSeries = currentSnapshot
            toSeries = next
            progress = 0f
            anim.snapTo(0f)
            anim.animateTo(1f, OrbitSprings.FloatSpatialDefault) {
                progress = value
            }
            progress = 1f
        }
    }

    return sampleInterpolatedSeries(fromSeries, toSeries, progress, targets.size)
}

private fun sampleInterpolatedSeries(
    from: FloatArray,
    to: FloatArray,
    progress: Float,
    count: Int
): FloatArray {
    if (count == 0) return FloatArray(0)
    if (to.isEmpty()) return FloatArray(count)
    val out = FloatArray(count)
    for (i in 0 until count) {
        val targetVal = to[i.coerceAtMost(to.lastIndex)]
        val startVal = if (from.isEmpty()) {
            0f
        } else if (from.size == count) {
            from[i]
        } else if (count == 1) {
            from[0]
        } else {
            val pos = (i.toFloat() / (count - 1).toFloat()) * (from.size - 1).toFloat()
            val lo = pos.toInt().coerceIn(0, from.lastIndex)
            val hi = (lo + 1).coerceIn(0, from.lastIndex)
            lerp(from[lo], from[hi], pos - lo.toFloat())
        }
        out[i] = lerp(startVal, targetVal, progress).coerceAtLeast(0f)
    }
    return out
}
