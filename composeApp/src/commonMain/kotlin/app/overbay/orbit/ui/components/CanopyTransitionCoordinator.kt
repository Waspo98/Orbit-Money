package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Coordinates a stationary screen-space canopy gradient and synchronized smooth vertical
 * height glide across [MorphingPageHero] instances during horizontal page transitions.
 * Preserves vertical velocity on rapid mid-flight tab switches and caches GPU gradient brushes
 * once per container width (zero per-frame Shader allocations).
 */
@Stable
class CanopyTransitionCoordinator(initialKeyId: Any) {
    private val knownNaturalHeights = HashMap<Any, Int>()
    // Use a sub-pixel visibility threshold (~0.5px on a 1000px screen) so the canopy
    // coordinator doesn't terminate ~100ms ahead of AnimatedContent's 1px IntOffset slide.
    private val transitionAnimatable = Animatable(1f, visibilityThreshold = CANOPY_TRANSITION_THRESHOLD)
    private var interruptedHeightVelocityPxPerSec: Float = 0f

    companion object {
        internal const val CANOPY_TRANSITION_THRESHOLD = 0.0005f
    }

    var activeKeyId: Any = initialKeyId
        private set

    var containerRootX by mutableFloatStateOf(0f)
    var containerWidthPx by mutableFloatStateOf(0f)

    var startHeightPx by mutableIntStateOf(0)
        private set
    var targetHeightPx by mutableIntStateOf(0)
        private set
    var transitionFraction by mutableFloatStateOf(1f)
        private set

    val isTransitioning: Boolean
        get() = transitionFraction < 0.999f

    val sharedDisplayHeightPx: Int
        get() {
            val start = if (startHeightPx > 0) startHeightPx else targetHeightPx
            val target = if (targetHeightPx > 0) targetHeightPx else start
            return lerp(start, target, transitionFraction).coerceAtLeast(0)
        }

    fun onTargetKeyChanged(newKeyId: Any) {
        if (newKeyId != activeKeyId) {
            val prevDelta = (targetHeightPx - startHeightPx).toFloat()
            interruptedHeightVelocityPxPerSec = prevDelta * transitionAnimatable.velocity
            val currentDisplayed = sharedDisplayHeightPx
            if (currentDisplayed > 0) {
                startHeightPx = currentDisplayed
            }
            activeKeyId = newKeyId
            knownNaturalHeights[newKeyId]?.let { known ->
                targetHeightPx = known
            }
            transitionFraction = 0f
        }
    }

    fun updateTransitionFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        transitionFraction = clamped
        if (clamped >= 1f) {
            startHeightPx = targetHeightPx
            interruptedHeightVelocityPxPerSec = 0f
        }
    }

    suspend fun animateToTarget() {
        if (transitionFraction < 1f) {
            val delta = (targetHeightPx - startHeightPx).toFloat()
            val normalizedVelocity = if (kotlin.math.abs(delta) > 1f) {
                (interruptedHeightVelocityPxPerSec / delta).coerceIn(-8f, 8f)
            } else {
                0f
            }
            interruptedHeightVelocityPxPerSec = 0f
            transitionAnimatable.snapTo(0f)
            transitionAnimatable.animateTo(
                targetValue = 1f,
                initialVelocity = normalizedVelocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 650f,
                    visibilityThreshold = CANOPY_TRANSITION_THRESHOLD
                )
            ) {
                updateTransitionFraction(value)
            }
            updateTransitionFraction(1f)
        }
    }

    fun resolveDisplayHeightPx(screenKeyId: Any?, naturalHeightPx: Int): Int {
        if (naturalHeightPx <= 0) return sharedDisplayHeightPx
        val keyId = screenKeyId ?: activeKeyId
        knownNaturalHeights[keyId] = naturalHeightPx

        if (keyId == activeKeyId) {
            if (targetHeightPx != naturalHeightPx) {
                targetHeightPx = naturalHeightPx
            }
            if (!isTransitioning && startHeightPx != naturalHeightPx) {
                startHeightPx = naturalHeightPx
            }
        } else if (startHeightPx == 0) {
            startHeightPx = naturalHeightPx
        }

        val resolved = sharedDisplayHeightPx
        return if (resolved > 0) resolved else naturalHeightPx
    }
}

val LocalCanopyTransitionCoordinator = compositionLocalOf<CanopyTransitionCoordinator?> { null }
val LocalCanopyScreenKeyId = compositionLocalOf<Any?> { null }

@Composable
fun rememberCanopyTransitionCoordinator(currentKeyId: Any): CanopyTransitionCoordinator {
    val coordinator = remember { CanopyTransitionCoordinator(currentKeyId) }
    Snapshot.withoutReadObservation {
        coordinator.onTargetKeyChanged(currentKeyId)
    }
    LaunchedEffect(currentKeyId) {
        coordinator.animateToTarget()
    }
    return coordinator
}

/**
 * Computes the `topLeft` offset inside a canvas translated by `(-slideOffsetX, 0)` so the drawn
 * backdrop covers local `[0, width]` with 1px horizontal bleed inside `clipRect`.
 */
internal fun canopyBackdropFillTopLeft(slideOffsetX: Float): Offset = Offset(slideOffsetX - 1f, 0f)

/**
 * Computes the fill size paired with [canopyBackdropFillTopLeft] inside `clipRect`.
 */
internal fun canopyBackdropFillSize(width: Float, height: Float): Size = Size(width + 2f, height)

fun Modifier.stationaryCanopyBackdrop(
    canopyStart: Color,
    canopyMid: Color,
    canopyEnd: Color,
    canopyGlow: Color,
    elevationPxProvider: () -> Float,
    coordinator: CanopyTransitionCoordinator?,
    screenKeyId: Any?,
    getHeroCoordinates: () -> LayoutCoordinates?,
    setHeroCoordinates: (LayoutCoordinates) -> Unit
): Modifier = this
    .fillMaxWidth()
    .onGloballyPositioned { coords -> setHeroCoordinates(coords) }
    .graphicsLayer {
        shadowElevation = elevationPxProvider()
        shape = RectangleShape
        clip = false
    }
    .drawWithCache {
        val gradientHeightPx = 180.dp.toPx()
        val strokePx = 1.dp.toPx()
        val borderColor = Color.White.copy(alpha = 0.16f)
        val linearColors = listOf(canopyStart, canopyMid, canopyEnd)
        val radialColors = listOf(canopyGlow, Color.Transparent)
        var cachedTotalW = Float.NaN
        var cachedLinearBrush: Brush? = null
        var cachedRadialBrush: Brush? = null

        onDrawBehind {
            val animTick = coordinator?.transitionFraction ?: 1f
            val currentRootX = getHeroCoordinates()?.takeIf { it.isAttached }?.positionInRoot()?.x ?: 0f
            val slideOffsetX = if (coordinator != null && coordinator.containerWidthPx > 0f && animTick >= 0f) {
                currentRootX - coordinator.containerRootX
            } else {
                0f
            }
            val totalW = if (coordinator != null && coordinator.containerWidthPx > 0f) {
                coordinator.containerWidthPx
            } else {
                size.width
            }
            if (cachedLinearBrush == null || cachedTotalW != totalW) {
                cachedTotalW = totalW
                cachedLinearBrush = Brush.linearGradient(
                    colors = linearColors,
                    start = Offset.Zero,
                    end = Offset(totalW, gradientHeightPx)
                )
                cachedRadialBrush = Brush.radialGradient(
                    colors = radialColors,
                    center = Offset(totalW * 0.92f, 0f),
                    radius = (totalW * 0.7f).coerceAtLeast(100f)
                )
            }
            // Keep the fill anchored to local [0..size.width] (with 1px bleed inside clipRect)
            // inside the translated canvas so only the gradient shader offsets horizontally and
            // neither edge can ever be left unpainted for any slideOffsetX.
            val fillTopLeft = canopyBackdropFillTopLeft(slideOffsetX)
            val fillSize = canopyBackdropFillSize(size.width, size.height)
            clipRect {
                withTransform({ translate(left = -slideOffsetX, top = 0f) }) {
                    drawRect(brush = cachedLinearBrush!!, topLeft = fillTopLeft, size = fillSize)
                    drawRect(brush = cachedRadialBrush!!, topLeft = fillTopLeft, size = fillSize)
                }
            }
            val h = size.height
            drawLine(
                color = borderColor,
                start = Offset(0f, h - strokePx / 2f),
                end = Offset(size.width, h - strokePx / 2f),
                strokeWidth = strokePx
            )
        }
    }
    .statusBarsPadding()
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val displayHeightPx = coordinator?.resolveDisplayHeightPx(screenKeyId, placeable.height) ?: placeable.height
        layout(placeable.width, displayHeightPx) {
            placeable.placeRelative(0, 0)
        }
    }
