package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Scroll state for [MorphingPageHero] implementing ExitUntilCollapsed physics,
 * smooth spring snapping to collapsed or expanded state, and scroll-direction FAB visibility.
 */
class HeroScrollState(
    initialMaxHeightPx: Float,
    val minHeightPx: Float,
    private val coroutineScope: CoroutineScope
) {
    var maxHeightPx by mutableFloatStateOf(initialMaxHeightPx)
    var scrollOffsetPx by mutableFloatStateOf(0f)
    var isFabVisible by mutableStateOf(true)

    val maxCollapsePx: Float
        get() = (maxHeightPx - minHeightPx).coerceAtLeast(0f)

    val collapseFraction: Float
        get() = if (maxCollapsePx > 0f) {
            (scrollOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
        } else 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -2f) {
                isFabVisible = false
            } else if (available.y > 2f) {
                isFabVisible = true
            }

            // Scrolling down (finger moves up, available.y < 0) collapses the header first
            if (available.y < 0f && scrollOffsetPx < maxCollapsePx) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Scrolling up (finger moves down, available.y > 0) expands only when list is at top
            if (available.y > 0f && scrollOffsetPx > 0f) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                if (scrollOffsetPx <= 1f) isFabVisible = true
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (collapseFraction in 0.01f..0.99f) {
                val target = if (collapseFraction < 0.5f) 0f else maxCollapsePx
                coroutineScope.launch { animateTo(target) }
            }
            return super.onPostFling(consumed, available)
        }
    }

    suspend fun animateTo(target: Float) {
        Animatable(scrollOffsetPx).animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        ) { scrollOffsetPx = value }
    }

    suspend fun expand() {
        isFabVisible = true
        animateTo(0f)
    }

    suspend fun collapse() = animateTo(maxCollapsePx)

    fun updateMaxHeight(newMaxPx: Float) {
        val coerced = newMaxPx.coerceAtLeast(minHeightPx)
        if (abs(coerced - maxHeightPx) > 1f) {
            maxHeightPx = coerced
            scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxCollapsePx)
        }
    }
}

@Composable
fun rememberHeroScrollState(
    maxHeight: Dp = 104.dp,
    minHeight: Dp = 56.dp
): HeroScrollState {
    val density = LocalDensity.current
    val maxPx = with(density) { maxHeight.toPx() }
    val minPx = with(density) { minHeight.toPx() }
    val scope = rememberCoroutineScope()
    return remember(minPx) { HeroScrollState(maxPx, minPx, scope) }
}

/**
 * Edge-to-edge flat emerald canopy header that draws seamlessly behind the system status bar.
 * Features a razor-sharp 1dp bottom hairline border when expanded and a subtle, diffused
 * elevation shadow that fades in as the user scrolls and the header pins into a 56dp compact bar.
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
    expandedToolbar: (@Composable ColumnScope.() -> Unit)? = null,
    collapsedActions: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    heroContent: (@Composable () -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val fraction = scrollState?.collapseFraction ?: 0f

    val canopyColors = OrbitTheme.colors
    val scrollShadowElevation = (fraction * 6f).dp

    val currentHeightDp = if (scrollState != null) {
        with(density) {
            val px = scrollState.maxHeightPx - (scrollState.maxHeightPx - scrollState.minHeightPx) * fraction
            px.toDp()
        }
    } else {
        if (expandedToolbar != null || heroContent != null) 148.dp else 96.dp
    }

    val minBarHeightDp = if (scrollState != null) {
        with(density) { scrollState.minHeightPx.toDp() }
    } else 56.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = scrollShadowElevation,
                shape = RectangleShape,
                clip = false
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(canopyColors.canopyStart, canopyColors.canopyMid, canopyColors.canopyEnd),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(canopyColors.canopyGlow, Color.Transparent),
                        center = Offset(w * 0.92f, 0f),
                        radius = (w * 0.7f).coerceAtLeast(100f)
                    )
                )
                // Razor-sharp 1dp bottom hairline border
                val strokePx = 1.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(0f, h - strokePx / 2f),
                    end = Offset(w, h - strokePx / 2f),
                    strokeWidth = strokePx
                )
            }
            .statusBarsPadding()
            .height(currentHeightDp)
            .clipToBounds()
    ) {
        val expandedAlpha = (1f - fraction * 1.65f).coerceIn(0f, 1f)
        val collapsedAlpha = if (scrollState != null) {
            ((fraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
        } else 0f

        // Expanded Content (Unbounded height measurement so maxHeightPx always matches exact content)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true, align = Alignment.Top)
                .onGloballyPositioned { coordinates ->
                    val measuredHeightPx = coordinates.size.height.toFloat()
                    if (scrollState != null && measuredHeightPx > 0f) {
                        scrollState.updateMaxHeight(measuredHeightPx)
                    }
                }
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = expandedAlpha
                    translationY = -fraction * 20f
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canNavigateBack) {
                    IconButton(
                        onClick = {
                            if (collapsedAlpha < 0.5f) {
                                haptics.tick()
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (kicker != null) {
                        Text(
                            text = kicker.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    if (heroContent != null) {
                        heroContent()
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayMedium,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()
                }
            }

            if (expandedToolbar != null) {
                Spacer(modifier = Modifier.height(10.dp))
                expandedToolbar()
            }
        }

        // Pinned Collapsed Top Bar (56dp sticky tier, only composed when fading in)
        if (collapsedAlpha > 0.02f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minBarHeightDp)
                    .padding(horizontal = 8.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = collapsedAlpha
                    },
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
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (collapsedActions != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        collapsedActions()
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()
                }
            }
        }
    }
}
