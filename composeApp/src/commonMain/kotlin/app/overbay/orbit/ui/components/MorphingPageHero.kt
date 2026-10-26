package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Scroll state for [MorphingPageHero] implementing ExitUntilCollapsed physics
 * and smooth spring snapping to collapsed or expanded state.
 */
class HeroScrollState(
    initialMaxHeightPx: Float,
    val minHeightPx: Float,
    private val coroutineScope: CoroutineScope
) {
    var maxHeightPx by mutableFloatStateOf(initialMaxHeightPx)
    var scrollOffsetPx by mutableFloatStateOf(0f)

    val maxCollapsePx: Float
        get() = (maxHeightPx - minHeightPx).coerceAtLeast(0f)

    val collapseFraction: Float
        get() = if (maxCollapsePx > 0f) {
            (scrollOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
        } else 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Scrolling down (finger moves up, available.y < 0) collapses the header first
            if (available.y < 0f && scrollOffsetPx < maxCollapsePx) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // Scrolling up (finger moves down, available.y > 0) expands only when list is at top
            if (available.y > 0f && scrollOffsetPx > 0f) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (collapseFraction in 0.01f..0.99f) {
                val target = if (collapseFraction < 0.5f) 0f else maxCollapsePx
                coroutineScope.launch {
                    animateTo(target)
                }
            }
            return super.onPostFling(consumed, available)
        }
    }

    suspend fun animateTo(target: Float) {
        Animatable(scrollOffsetPx).animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) {
            scrollOffsetPx = value
        }
    }

    suspend fun expand() {
        animateTo(0f)
    }

    suspend fun collapse() {
        animateTo(maxCollapsePx)
    }

    fun updateMaxHeight(newMaxPx: Float) {
        if (newMaxPx > minHeightPx && newMaxPx != maxHeightPx) {
            maxHeightPx = newMaxPx
        }
    }
}

@Composable
fun rememberHeroScrollState(
    maxHeight: Dp = 210.dp,
    minHeight: Dp = 64.dp
): HeroScrollState {
    val density = LocalDensity.current
    val maxPx = with(density) { maxHeight.toPx() }
    val minPx = with(density) { minHeight.toPx() }
    val scope = rememberCoroutineScope()
    return remember(maxPx, minPx) { HeroScrollState(maxPx, minPx, scope) }
}

enum class HeroStatTone { DEFAULT, GOOD, WARN, CAUTION }

data class HeroStatItem(
    val label: String,
    val value: String,
    val tone: HeroStatTone = HeroStatTone.DEFAULT
)

/**
 * Authentic edge-to-edge curved emerald canopy header with 1:1 internal morphing.
 * Bleeds full-width behind status bar with 135° 3-stop emerald gradient + cyan glow.
 * Morphs seamlessly into a 64dp pinned sticky bar with spring-snap release physics.
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
    stats: List<HeroStatItem> = emptyList(),
    expandedToolbar: (@Composable ColumnScope.() -> Unit)? = null,
    collapsedActions: (@Composable RowScope.() -> Unit)? = null,
    onPrimaryAddClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    heroContent: (@Composable () -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val fraction = scrollState?.collapseFraction ?: 0f

    val canopyColors = OrbitTheme.colors
    val bottomRadius = lerp(28.dp, 0.dp, fraction)
    val canopyShape = RoundedCornerShape(bottomStart = bottomRadius, bottomEnd = bottomRadius)

    val currentHeightDp = if (scrollState != null) {
        with(density) {
            val px = scrollState.maxHeightPx - (scrollState.maxHeightPx - scrollState.minHeightPx) * fraction
            px.toDp()
        }
    } else {
        if (stats.isNotEmpty() || expandedToolbar != null || heroContent != null) 210.dp else 130.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(canopyShape)
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
            }
            .statusBarsPadding()
            .height(currentHeightDp)
    ) {
        // Expanded Content (Fades out and translates slightly upward as collapsed)
        val expandedAlpha = (1f - fraction * 1.65f).coerceIn(0f, 1f)
        if (expandedAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        alpha = expandedAlpha
                        translationY = -fraction * 28f
                    }
                    .onGloballyPositioned { coordinates ->
                        val measuredHeightPx = coordinates.size.height.toFloat()
                        val targetMaxPx = measuredHeightPx + with(density) { 76.dp.toPx() }
                        if (scrollState != null && targetMaxPx > scrollState.maxHeightPx) {
                            scrollState.updateMaxHeight(targetMaxPx)
                        }
                    }
            ) {
                if (kicker != null) {
                    Text(
                        text = kicker.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
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
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (stats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HeroBentoStatsGrid(stats = stats)
                }

                if (expandedToolbar != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    expandedToolbar()
                }
            }
        }

        // Pinned Collapsed Top Bar (64dp sticky tier)
        val collapsedAlpha = if (scrollState != null) {
            ((fraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
        } else 0f

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
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Collapsed Single Title (Only visible when collapsed!)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = collapsedAlpha
                        scaleX = 0.9f + 0.1f * collapsedAlpha
                        scaleY = 0.9f + 0.1f * collapsedAlpha
                    }
            )

            // Collapsed compact action slot
            if (collapsedActions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = collapsedAlpha }
                ) {
                    collapsedActions()
                }
            }

            // Sticky Header '+' Icon Button
            if (onPrimaryAddClick != null) {
                val plusAlpha = ((fraction - 0.65f) / 0.35f).coerceIn(0f, 1f)
                IconButton(
                    onClick = {
                        haptics.tick()
                        onPrimaryAddClick()
                    },
                    modifier = Modifier.graphicsLayer {
                        alpha = plusAlpha
                        scaleX = 0.85f + 0.15f * plusAlpha
                        scaleY = 0.85f + 0.15f * plusAlpha
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color.White
                    )
                }
            }

            // Backward compatibility for existing actions slot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun HeroBentoStatsGrid(
    stats: List<HeroStatItem>,
    modifier: Modifier = Modifier
) {
    val chunked = stats.chunked(2)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chunked.forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowStats.forEach { stat ->
                    val toneColor = when (stat.tone) {
                        HeroStatTone.DEFAULT -> Color.White
                        HeroStatTone.GOOD -> OrbitTheme.colors.heroStatGood
                        HeroStatTone.WARN -> OrbitTheme.colors.heroStatWarn
                        HeroStatTone.CAUTION -> OrbitTheme.colors.heroStatCaution
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = stat.label.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stat.value,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = toneColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowStats.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
