package app.overbay.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

class HeroScrollState(val maxHeightPx: Float, val minHeightPx: Float) {
    var scrollOffsetPx by mutableFloatStateOf(0f)
    val collapseFraction: Float
        get() = if (maxHeightPx > minHeightPx) {
            (scrollOffsetPx / (maxHeightPx - minHeightPx)).coerceIn(0f, 1f)
        } else 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            val newOffset = (scrollOffsetPx - delta).coerceIn(0f, maxHeightPx - minHeightPx)
            val consumed = scrollOffsetPx - newOffset
            scrollOffsetPx = newOffset
            return Offset(0f, -consumed)
        }
    }
}

@Composable
fun rememberHeroScrollState(
    maxHeight: Dp = 190.dp,
    minHeight: Dp = 64.dp
): HeroScrollState {
    val density = LocalDensity.current
    val maxPx = with(density) { maxHeight.toPx() }
    val minPx = with(density) { minHeight.toPx() }
    return remember(maxPx, minPx) { HeroScrollState(maxPx, minPx) }
}

/**
 * Two-tier collapsible sticky canopy header.
 * Uses [NestedScrollConnection] and hardware graphics layers to eliminate per-frame layout reflow loops.
 */
@Composable
fun MorphingPageHero(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {},
    scrollState: HeroScrollState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    heroContent: (@Composable () -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val fraction = scrollState?.collapseFraction ?: 0f

    val canopyColors = OrbitTheme.colors
    val canopyBrush = remember(canopyColors.canopyStart, canopyColors.canopyEnd) {
        Brush.verticalGradient(listOf(canopyColors.canopyStart, canopyColors.canopyEnd))
    }

    val currentHeightDp = if (scrollState != null) {
        with(density) {
            val px = scrollState.maxHeightPx - (scrollState.maxHeightPx - scrollState.minHeightPx) * fraction
            px.toDp()
        }
    } else {
        if (heroContent != null) 190.dp else 64.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(canopyBrush)
            .statusBarsPadding()
            .height(currentHeightDp)
    ) {
        // Expanded Tier 2 Content (fades out as scrolled)
        if (heroContent != null || kicker != null || subtitle != null) {
            val tier2Alpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
            if (tier2Alpha > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.BottomStart)
                        .graphicsLayer { alpha = tier2Alpha }
                ) {
                    if (kicker != null) {
                        Text(
                            text = kicker.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = canopyColors.onCanopy.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (heroContent != null) {
                        heroContent()
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = canopyColors.onCanopy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = canopyColors.onCanopy.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Pinned Tier 1 Top Bar (Sticky 64dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canNavigateBack) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = canopyColors.onCanopy
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Pinned Collapsed Title (Fades in when collapsed)
            val titleAlpha = if (heroContent != null) {
                (fraction - 0.4f).coerceIn(0f, 1f) / 0.6f
            } else 1f

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = canopyColors.onCanopy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = titleAlpha }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                actions()
            }
        }
    }
}
