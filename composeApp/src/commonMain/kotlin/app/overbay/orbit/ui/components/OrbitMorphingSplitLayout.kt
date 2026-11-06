package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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

    val showDetail = splitProgress > 0.001f || isDetailOpen

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Layout(
            modifier = Modifier
                .widthIn(max = maxContainerWidth)
                .fillMaxSize(),
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val closeOvershoot = (-splitProgress).coerceAtLeast(0f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = 1f + (closeOvershoot * 0.08f)
                        }
                ) {
                    masterContent()
                }

                if (showDetail) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp, bottom = 16.dp)
                            .graphicsLayer {
                                val progress = splitProgress
                                alpha = progress.coerceIn(0f, 1f)
                                translationX = (1f - progress) * size.width * 0.35f
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
        ) { measurables, constraints ->
            val totalWidthPx = constraints.maxWidth
            val totalHeightPx = constraints.maxHeight
            val minPanePx = 320.dp.roundToPx()
            val gutterPx = 20.dp.roundToPx()
            val spacingPx = paneSpacing.roundToPx()
            val progress = splitProgress

            val restingClosedMasterPx = (totalWidthPx - gutterPx).coerceAtLeast(minPanePx).coerceAtMost(totalWidthPx)
            val effectiveOpenPx = (totalWidthPx - spacingPx - gutterPx).coerceAtLeast(minPanePx)
            val openMasterPx = (effectiveOpenPx * masterWeightWhenOpen).toInt().coerceIn(1, totalWidthPx)
            val openDetailPx = (effectiveOpenPx - openMasterPx).coerceAtLeast(1)

            val closeOvershoot = (-progress).coerceAtLeast(0f)
            val gutterOvershootFraction = (closeOvershoot / 0.22f).coerceIn(0f, 1f)
            val currentMasterPx = if (progress >= 0f) {
                lerp(restingClosedMasterPx, openMasterPx, progress)
            } else {
                lerp(restingClosedMasterPx, (totalWidthPx - 2.dp.roundToPx()).coerceAtLeast(1), gutterOvershootFraction)
            }.coerceIn(1, totalWidthPx)

            val masterPlaceable = measurables[0].measure(
                Constraints.fixed(currentMasterPx, totalHeightPx)
            )
            val detailPlaceable = if (measurables.size > 1) {
                measurables[1].measure(
                    Constraints.fixed(openDetailPx, totalHeightPx)
                )
            } else null

            val detailOffsetX = currentMasterPx + (spacingPx * progress.coerceAtLeast(0f)).toInt()

            layout(totalWidthPx, totalHeightPx) {
                masterPlaceable.placeRelative(0, 0)
                detailPlaceable?.placeRelative(detailOffsetX, 0)
            }
        }
    }
}
