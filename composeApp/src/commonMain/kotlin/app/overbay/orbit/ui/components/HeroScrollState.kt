package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Scroll state for [MorphingPageHero] implementing ExitUntilCollapsed physics,
 * velocity-aware bounded spring snapping to collapsed or expanded state,
 * immediate touch cancellation of in-flight snap animations, and hysteresis-filtered FAB visibility.
 */
class HeroScrollState(
    initialMaxHeightPx: Float,
    val minHeightPx: Float,
    private val coroutineScope: CoroutineScope,
    private val fabSlopPx: Float = 36f
) {
    var maxHeightPx: Float = initialMaxHeightPx
    var scrollOffsetPx by mutableFloatStateOf(0f)
    var isFabVisible by mutableStateOf(true)
    var scrollEnabled by mutableStateOf(true)

    private var snapJob: Job? = null
    private var fabScrollDeltaPx: Float = 0f

    val maxCollapsePx: Float
        get() = (maxHeightPx - minHeightPx).coerceAtLeast(0f)

    val collapseFraction: Float
        get() = if (maxCollapsePx > 0f) (scrollOffsetPx / maxCollapsePx).coerceIn(0f, 1f) else 0f

    private fun cancelSnap() {
        snapJob?.cancel()
        snapJob = null
    }

    private fun recordFabScrollDelta(dy: Float) {
        if ((dy > 0f && fabScrollDeltaPx < 0f) || (dy < 0f && fabScrollDeltaPx > 0f)) {
            fabScrollDeltaPx = 0f
        }
        fabScrollDeltaPx += dy
        if (fabScrollDeltaPx <= -fabSlopPx) {
            isFabVisible = false
            fabScrollDeltaPx = 0f
        } else if (fabScrollDeltaPx >= fabSlopPx) {
            isFabVisible = true
            fabScrollDeltaPx = 0f
        }
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!scrollEnabled) return Offset.Zero
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                cancelSnap()
                recordFabScrollDelta(available.y)
            }
            if (available.y < 0f && scrollOffsetPx < maxCollapsePx) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!scrollEnabled) return Offset.Zero
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                cancelSnap()
            }
            if (available.y > 0f && scrollOffsetPx > 0f) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                if (scrollOffsetPx <= 2f) {
                    isFabVisible = true
                    fabScrollDeltaPx = 0f
                }
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (scrollEnabled && collapseFraction in 0.01f..0.99f) {
                val target = if (collapseFraction < 0.5f) 0f else maxCollapsePx
                cancelSnap()
                snapJob = coroutineScope.launch {
                    animateTo(target = target, initialVelocity = -available.y)
                }
            }
            return super.onPostFling(consumed, available)
        }
    }

    suspend fun animateTo(target: Float, initialVelocity: Float = 0f) {
        val upper = maxCollapsePx
        val clampedTarget = target.coerceIn(0f, upper)
        val anim = Animatable(scrollOffsetPx.coerceIn(0f, upper))
        anim.updateBounds(lowerBound = 0f, upperBound = upper)
        anim.animateTo(
            targetValue = clampedTarget,
            initialVelocity = initialVelocity,
            animationSpec = OrbitSprings.FloatSpatialDefault
        ) {
            scrollOffsetPx = value
        }
    }

    suspend fun expand() {
        cancelSnap()
        isFabVisible = true
        fabScrollDeltaPx = 0f
        animateTo(0f)
    }

    suspend fun collapse() {
        cancelSnap()
        animateTo(maxCollapsePx)
    }

    fun updateMaxHeight(newMaxPx: Float) {
        val coerced = newMaxPx.coerceAtLeast(minHeightPx)
        if (abs(coerced - maxHeightPx) > 1f) {
            maxHeightPx = coerced
            scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxCollapsePx)
        }
    }
}

@Composable
fun rememberHeroScrollState(maxHeight: Dp = 112.dp, minHeight: Dp = 56.dp): HeroScrollState {
    val density = LocalDensity.current
    val maxPx = with(density) { maxHeight.toPx() }
    val minPx = with(density) { minHeight.toPx() }
    val slopPx = with(density) { 14.dp.toPx() }
    val scope = rememberCoroutineScope()
    return remember(minPx) { HeroScrollState(maxPx, minPx, scope, slopPx) }
}
