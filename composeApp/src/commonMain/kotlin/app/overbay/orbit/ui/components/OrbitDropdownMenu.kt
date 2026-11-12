package app.overbay.orbit.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.overbay.orbit.theme.OrbitTheme

/**
 * M3E popup menu with symmetric spring open AND close animations anchored to its trigger.
 * Unlike Material 3's stock DropdownMenu (which snaps closed at scale 1.0 over 75ms),
 * [OrbitDropdownMenu] smoothly blooms from and collapses back into the anchor origin.
 */
@Composable
fun OrbitDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded

    if (expandedState.currentState || expandedState.targetState || !expandedState.isIdle) {
        val density = LocalDensity.current
        val transformOriginState = remember { mutableStateOf(TransformOrigin.Center) }
        val positionProvider = remember(density, offset) {
            OrbitDropdownMenuPositionProvider(
                contentOffset = offset,
                density = density,
                onPositionCalculated = { anchorBounds, menuBounds ->
                    transformOriginState.value = calculateMenuTransformOrigin(anchorBounds, menuBounds)
                }
            )
        }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = expanded)
        ) {
            val transition = rememberTransition(expandedState, label = "orbitDropdownMenuTransition")

            val scale by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) {
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = 700f
                        )
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 900f
                        )
                    }
                },
                label = "orbitDropdownScale"
            ) { isOpen ->
                if (isOpen) 1f else 0.82f
            }

            val alpha by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 1200f
                        )
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 900f
                        )
                    }
                },
                label = "orbitDropdownAlpha"
            ) { isOpen ->
                if (isOpen) 1f else 0f
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = transformOriginState.value
                }
            ) {
                Column(
                    modifier = modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 176.dp, max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    content = content
                )
            }
        }
    }
}

private class OrbitDropdownMenuPositionProvider(
    private val contentOffset: DpOffset,
    private val density: Density,
    private val onPositionCalculated: (IntRect, IntRect) -> Unit
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val marginPx = with(density) { 12.dp.roundToPx() }
        val offsetXPx = with(density) {
            contentOffset.x.roundToPx() * (if (layoutDirection == LayoutDirection.Ltr) 1 else -1)
        }
        val offsetYPx = with(density) { contentOffset.y.roundToPx() }

        val startAlignedX = anchorBounds.left + offsetXPx
        val endAlignedX = anchorBounds.right - popupContentSize.width + offsetXPx
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val resolvedX = when {
            layoutDirection == LayoutDirection.Ltr -> {
                if (startAlignedX + popupContentSize.width <= windowSize.width - marginPx) {
                    startAlignedX
                } else {
                    endAlignedX
                }
            }
            else -> {
                if (endAlignedX >= marginPx) endAlignedX else startAlignedX
            }
        }.coerceIn(marginPx, maxX)

        val belowY = anchorBounds.bottom + offsetYPx
        val aboveY = anchorBounds.top - popupContentSize.height - offsetYPx
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val resolvedY = if (belowY + popupContentSize.height <= windowSize.height - marginPx || aboveY < marginPx) {
            belowY.coerceIn(marginPx, maxY)
        } else {
            aboveY.coerceIn(marginPx, maxY)
        }

        val menuBounds = IntRect(offset = IntOffset(resolvedX, resolvedY), size = popupContentSize)
        onPositionCalculated(anchorBounds, menuBounds)
        return IntOffset(resolvedX, resolvedY)
    }
}

private fun calculateMenuTransformOrigin(
    anchorBounds: IntRect,
    menuBounds: IntRect
): TransformOrigin {
    val pivotX = if (menuBounds.width <= 0) {
        1f
    } else {
        ((anchorBounds.center.x - menuBounds.left).toFloat() / menuBounds.width.toFloat()).coerceIn(0f, 1f)
    }
    val pivotY = if (menuBounds.height <= 0) {
        0f
    } else {
        ((anchorBounds.center.y - menuBounds.top).toFloat() / menuBounds.height.toFloat()).coerceIn(0f, 1f)
    }
    return TransformOrigin(pivotX, pivotY)
}
