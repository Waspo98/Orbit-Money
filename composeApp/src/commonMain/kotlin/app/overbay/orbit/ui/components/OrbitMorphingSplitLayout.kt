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
import androidx.compose.ui.graphics.TransformOrigin
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
 * - When [isDetailOpen] is false, [masterContent] spans 100% of the container width (with a 20dp right gutter).
 * - When [isDetailOpen] is true, [masterContent] smoothly morphs to the left column ([masterWeightWhenOpen])
 *   while [detailContent] glides into the right column as a floating bordered card.
 * - Both opening (0f -> 1f) and closing (1f -> 0f) preserve full spring overshoot and rebound.
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
            dampingRatio = 0.48f,
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
            val closedRightGutter = 20.dp
            val openRightGutter = 20.dp

            val restingClosedMasterWidth = (totalWidth - closedRightGutter).coerceAtLeast(320.dp)
            val effectiveOpenWidth = (totalWidth - paneSpacing - openRightGutter).coerceAtLeast(320.dp)
            val openMasterWidth = effectiveOpenWidth * masterWeightWhenOpen
            val openDetailWidth = effectiveOpenWidth * (1f - masterWeightWhenOpen)

            // When splitProgress >= 0f (including > 1f open bounce), interpolate layout width directly.
            // When splitProgress < 0f (closing overshoot past 0f), expand into the right gutter AND
            // apply GPU-accelerated left-anchored elastic stretch so BoxWithConstraints never clips the bounce!
            val closeOvershoot = (-splitProgress).coerceAtLeast(0f)
            val gutterOvershootFraction = (closeOvershoot / 0.22f).coerceIn(0f, 1f)
            val currentMasterWidth = if (splitProgress >= 0f) {
                lerp(restingClosedMasterWidth, openMasterWidth, splitProgress)
            } else {
                lerp(restingClosedMasterWidth, totalWidth - 2.dp, gutterOvershootFraction)
            }

            val detailOffsetX = currentMasterWidth + (paneSpacing * splitProgress.coerceAtLeast(0f))

            // Left Master Pane (bounces on both open and close)
            Box(
                modifier = Modifier
                    .width(currentMasterWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = 1f + (closeOvershoot * 0.08f)
                    }
            ) {
                masterContent()
            }

            // Right Floating Detail Pane (stays mounted with cached content until completely off-screen)
            if (splitProgress > 0.001f || isDetailOpen) {
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
