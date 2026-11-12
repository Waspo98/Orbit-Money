package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings

/**
 * Tactile M3E press feedback modifier powered by [OrbitSprings.TactilePressSpring].
 * Observes press state via [interactionSource] when provided, or passively inspects pointer down/up
 * events on [PointerEventPass.Initial] without consuming clicks or blocking parent scroll gestures.
 * Scales strictly in the GPU [graphicsLayer] draw phase with zero layout invalidation.
 */
fun Modifier.orbitTactilePress(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    interactionSource: InteractionSource? = null
): Modifier = composed {
    if (!enabled) return@composed this

    val isPressed = if (interactionSource != null) {
        val pressed by interactionSource.collectIsPressedAsState()
        pressed
    } else {
        var pointerPressed by remember { mutableStateOf(false) }
        val passiveTracker = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pointerPressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pointerPressed = false
            }
        }
        val scale by animateFloatAsState(
            targetValue = if (pointerPressed) pressedScale else 1f,
            animationSpec = OrbitSprings.TactilePressSpring,
            label = "orbitPassiveTactilePress"
        )
        return@composed this
            .then(passiveTracker)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = OrbitSprings.TactilePressSpring,
        label = "orbitTactilePress"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Rotating accordion chevron standardized on [OrbitSprings.FloatSpatialFast].
 */
@Composable
fun OrbitExpandableChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = OrbitSprings.FloatSpatialFast,
        label = "orbitChevronRotation"
    )
    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    )
}

/**
 * Standardized M3E vertical accordion container pairing snappy spatial expansion
 * with critically-damped opacity fade (~200ms open/close).
 */
@Composable
fun OrbitAccordionVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = OrbitSprings.IntSizeAccordionFast) +
            fadeIn(animationSpec = OrbitSprings.FloatEffectsFast),
        exit = shrinkVertically(animationSpec = OrbitSprings.IntSizeAccordionFast) +
            fadeOut(animationSpec = OrbitSprings.FloatEffectsFast),
        modifier = modifier,
        content = content
    )
}
