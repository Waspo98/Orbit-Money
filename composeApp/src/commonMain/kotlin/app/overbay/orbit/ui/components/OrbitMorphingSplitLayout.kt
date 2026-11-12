package app.overbay.orbit.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Reusable morphing master-detail split layout template for unfolded phones, tablets, and desktops.
 *
 * - Uses [OrbitSprings.FloatSpatialDefault] (`DampingRatioLowBouncy = 0.85f, StiffnessLow = 200f`)
 *   without horizontal text distortion or out-of-bounds width overshoot on the master column.
 * - Preserves expressive GPU spring glide and uniform subtle scale on the floating detail card.
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
    val haptics = LocalOrbitHaptics.current

    BackHandler(enabled = isDetailOpen) {
        haptics.tick()
        onDismissDetail()
    }

    val splitProgress by animateFloatAsState(
        targetValue = if (isDetailOpen) 1f else 0f,
        animationSpec = OrbitSprings.FloatSpatialDefault,
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
                Box(modifier = Modifier.fillMaxSize()) {
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
                                val uniformScale = lerp(0.96f, 1f, progress.coerceIn(0f, 1.05f))
                                scaleX = uniformScale
                                scaleY = uniformScale
                                translationX = (1f - progress) * (size.width * 0.35f)
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
            val clampedProgress = splitProgress.coerceIn(0f, 1f)

            val restingClosedMasterPx = (totalWidthPx - gutterPx).coerceAtLeast(minPanePx).coerceAtMost(totalWidthPx)
            val effectiveOpenPx = (totalWidthPx - spacingPx - gutterPx).coerceAtLeast(minPanePx)
            val openMasterPx = (effectiveOpenPx * masterWeightWhenOpen).toInt().coerceIn(1, totalWidthPx)
            val openDetailPx = (effectiveOpenPx - openMasterPx).coerceAtLeast(1)

            val currentMasterPx = lerp(restingClosedMasterPx, openMasterPx, clampedProgress).coerceIn(1, totalWidthPx)

            val masterPlaceable = measurables[0].measure(
                Constraints.fixed(currentMasterPx, totalHeightPx)
            )
            val detailPlaceable = if (measurables.size > 1) {
                measurables[1].measure(
                    Constraints.fixed(openDetailPx, totalHeightPx)
                )
            } else null

            val detailOffsetX = currentMasterPx + (spacingPx * clampedProgress).toInt()

            layout(totalWidthPx, totalHeightPx) {
                masterPlaceable.placeRelative(0, 0)
                detailPlaceable?.placeRelative(detailOffsetX, 0)
            }
        }
    }
}
