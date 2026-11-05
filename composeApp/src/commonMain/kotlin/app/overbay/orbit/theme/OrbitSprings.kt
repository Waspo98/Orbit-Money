package app.overbay.orbit.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Material 3 Expressive spring physics tokens matching Orbit Money's kinetic design system.
 * Eliminates stiff linear transitions in favor of natural momentum and bouncy recovery.
 */
object OrbitSprings {

    /**
     * Fast spatial transitions: button press scale pop, chip selection thumb glide, tab indicator.
     * Equivalent to CSS --spring-spatial-fast (~320ms, bouncy recovery).
     */
    fun <T> spatialFast(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.75f
        stiffness = Spring.StiffnessMediumLow,          // 400f
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Default spatial transitions: bottom sheet open/close, card modal entrance, canopy height morph.
     * Equivalent to CSS --spring-spatial-default (~420ms, subtle bounce).
     */
    fun <T> spatialDefault(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,    // 0.85f
        stiffness = Spring.StiffnessLow,                // 200f
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Slow spatial transitions: grand sheet expansions, wide two-pane reorder.
     * Equivalent to CSS --spring-spatial-slow (~540ms).
     */
    fun <T> spatialSlow(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 150f,
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Fast effects transitions: opacity, icon tints, badge fades.
     * Critically damped (zero overshoot, 150ms).
     */
    fun <T> effectsFast(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0f
        stiffness = Spring.StiffnessMedium,             // 1500f
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Default effects transitions: surface color morphing, backdrop scrim blur fades.
     * Critically damped (zero overshoot, 240ms).
     */
    fun <T> effectsDefault(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0f
        stiffness = Spring.StiffnessMediumLow,          // 400f
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Tabular numeric reel spring used by RollingTickerText odometer character reels.
     */
    val TickerSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 600f
    )

    /**
     * IntOffset animation spec for vertical odometer reel slides.
     */
    val TickerOffsetSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.72f,
        stiffness = 600f
    )

    /**
     * Active touch press scaling spring (scale down to 0.975 -> spring bounce back).
     */
    val TactilePressSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Pre-allocated static specs to eliminate allocation thrashing during high-frequency animations
    val FloatSpatialFast: SpringSpec<Float> = spatialFast()
    val FloatSpatialDefault: SpringSpec<Float> = spatialDefault()
    val DpSpatialDefault: SpringSpec<Dp> = spatialDefault()
    val IntOffsetSpatialDefault: SpringSpec<IntOffset> = spatialDefault()
    val FloatEffectsFast: SpringSpec<Float> = effectsFast()
    val ColorEffectsFast: SpringSpec<Color> = effectsFast()
}
