package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.BackHandler

/**
 * Reusable morphing master-detail split layout template for unfolded phones, tablets, and desktops.
 *
 * - When [isDetailOpen] is false, [masterContent] spans 100% of the container width.
 * - When [isDetailOpen] is true, [masterContent] smoothly morphs to the left column ([masterWeightWhenOpen])
 *   while [detailContent] glides into the right column as a floating bordered card.
 * - Pressing the system Back button while [isDetailOpen] is true invokes [onDismissDetail],
 *   closing the right pane and re-stretching [masterContent] back to full width.
 */
@Composable
fun OrbitMorphingSplitLayout(
    isDetailOpen: Boolean,
    onDismissDetail: () -> Unit,
    modifier: Modifier = Modifier,
    masterWeightWhenOpen: Float = 0.55f,
    paneSpacing: Dp = 16.dp,
    maxContainerWidth: Dp = 1440.dp,
    masterContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit
) {
    val haptics = app.overbay.orbit.util.LocalOrbitHaptics.current

    BackHandler(enabled = isDetailOpen) {
        haptics.tick()
        onDismissDetail()
    }

    val splitProgress by animateFloatAsState(
        targetValue = if (isDetailOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "orbitMorphingSplitProgress"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = maxContainerWidth)
                .fillMaxSize()
        ) {
            val totalWidth = maxWidth
            val rightEdgePadding = 16.dp * splitProgress
            val effectiveOpenWidth = (totalWidth - paneSpacing - rightEdgePadding).coerceAtLeast(320.dp)
            val openMasterWidth = effectiveOpenWidth * masterWeightWhenOpen
            val openDetailWidth = effectiveOpenWidth * (1f - masterWeightWhenOpen)

            val currentMasterWidth = lerp(totalWidth, openMasterWidth, splitProgress)
            val detailOffsetX = currentMasterWidth + (paneSpacing * splitProgress)

            // Left Master Pane (morphs smoothly between 100% width and 55% width)
            Box(
                modifier = Modifier
                    .width(currentMasterWidth)
                    .fillMaxHeight()
            ) {
                masterContent()
            }

            // Right Floating Detail Pane (slides and fades in when open)
            if (splitProgress > 0.005f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(detailOffsetX.roundToPx(), 0) }
                        .width(openDetailWidth)
                        .fillMaxHeight()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .graphicsLayer {
                            alpha = splitProgress.coerceIn(0f, 1f)
                            translationX = (1f - splitProgress) * openDetailWidth.toPx() * 0.35f
                        }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        tonalElevation = 2.dp
                    ) {
                        detailContent()
                    }
                }
            }
        }
    }
}
