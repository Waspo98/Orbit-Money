package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
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
    var maxHeightPx: Float = initialMaxHeightPx
    var scrollOffsetPx by mutableFloatStateOf(0f)
    var isFabVisible by mutableStateOf(true)
    var scrollEnabled by mutableStateOf(true)

    val maxCollapsePx: Float get() = (maxHeightPx - minHeightPx).coerceAtLeast(0f)
    val collapseFraction: Float get() = if (maxCollapsePx > 0f) (scrollOffsetPx / maxCollapsePx).coerceIn(0f, 1f) else 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!scrollEnabled) return Offset.Zero
            if (available.y < -2f) isFabVisible = false else if (available.y > 2f) isFabVisible = true
            if (available.y < 0f && scrollOffsetPx < maxCollapsePx) {
                val newOffset = (scrollOffsetPx - available.y).coerceIn(0f, maxCollapsePx)
                val consumedY = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!scrollEnabled) return Offset.Zero
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
            if (scrollEnabled && collapseFraction in 0.01f..0.99f) {
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

    suspend fun expand() { isFabVisible = true; animateTo(0f) }
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
fun rememberHeroScrollState(maxHeight: Dp = 112.dp, minHeight: Dp = 56.dp): HeroScrollState {
    val density = LocalDensity.current
    val maxPx = with(density) { maxHeight.toPx() }
    val minPx = with(density) { minHeight.toPx() }
    val scope = rememberCoroutineScope()
    return remember(minPx) { HeroScrollState(maxPx, minPx, scope) }
}

/**
 * Edge-to-edge flat emerald canopy header that draws seamlessly behind the system status bar.
 * Features a single continuous morphing title (30sp expanded -> 17.5–21sp left-anchored collapsed)
 * that scales and glides into the 56dp compact bar on GPU graphicsLayer, plus 2-axis morphing toolbars.
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
    contentAlpha: Float = 1f,
    activeHeightOverrideDp: Dp? = null,
    actions: @Composable RowScope.() -> Unit = {},
    canopyBar: (@Composable RowScope.(collapseFraction: Float, isCompact: Boolean, barHeight: Dp) -> Unit)? = null,
    morphingContent: (@Composable BoxWithConstraintsScope.(collapseFraction: Float, collapsedTitleRightDp: Dp, expandedTextBottomDp: Dp) -> Unit)? = null,
    persistentBottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    heroContent: (@Composable () -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val fraction = scrollState?.collapseFraction ?: 0f
    val canopyColors = OrbitTheme.colors

    val minBarHeightPx = scrollState?.minHeightPx ?: with(density) { 56.dp.toPx() }
    val minBarHeightDp = with(density) { minBarHeightPx.toDp() }
    val hasCanopyBar = canopyBar != null && morphingContent == null
    val hasMorphingSlot = morphingContent != null

    var textBlockHeightPx by remember { mutableFloatStateOf(with(density) { 104.dp.toPx() }) }
    var titleTopInColumnPx by remember { mutableFloatStateOf(with(density) { if (kicker != null) 16.dp.toPx() else 0.dp.toPx() }) }
    var titleHeightPx by remember { mutableFloatStateOf(with(density) { 38.dp.toPx() }) }
    var rawTitleWidthPx by remember { mutableFloatStateOf(with(density) { 180.dp.toPx() }) }

    val expandedFontSize = if (title.length > 17) 26.sp else 30.sp
    val collapsedTargetSp = when {
        title.length > 15 -> 17.5f
        title.length > 11 -> 19f
        else -> 21f
    }
    val collapsedTitleScale = collapsedTargetSp / expandedFontSize.value
    val titleScale = lerp(1f, collapsedTitleScale, fraction)

    val extraMorphSlotPx = when {
        hasMorphingSlot || hasCanopyBar -> with(density) { 58.dp.toPx() }
        else -> 0f
    }
    val expandedMorphHeightPx = (textBlockHeightPx + extraMorphSlotPx).coerceAtLeast(minBarHeightPx)
    scrollState?.updateMaxHeight(expandedMorphHeightPx)

    val restingZoneHeightDp = lerp(with(density) { expandedMorphHeightPx.toDp() }, minBarHeightDp, fraction)
    val currentZoneHeightDp = activeHeightOverrideDp ?: restingZoneHeightDp
    val canopyCoordinator = LocalCanopyTransitionCoordinator.current
    val canopyScreenKeyId = LocalCanopyScreenKeyId.current
    var heroCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier = modifier.stationaryCanopyBackdrop(
            canopyStart = canopyColors.canopyStart,
            canopyMid = canopyColors.canopyMid,
            canopyEnd = canopyColors.canopyEnd,
            canopyGlow = canopyColors.canopyGlow,
            elevationDp = (fraction * 6f).dp,
            coordinator = canopyCoordinator,
            screenKeyId = canopyScreenKeyId,
            getHeroCoordinates = { heroCoordinates },
            setHeroCoordinates = { heroCoordinates = it }
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentZoneHeightDp)
                .clipToBounds()
        ) {
            val totalWidth = maxWidth
            val secondaryAlpha = ((1f - fraction * 2.2f).coerceIn(0f, 1f) * contentAlpha).coerceIn(0f, 1f)
            val topPadDp = 18.dp
            val topPadPx = with(density) { topPadDp.toPx() }
            val bottomPadDp = if (hasMorphingSlot || hasCanopyBar || persistentBottomBar != null) 10.dp else 18.dp

            val expandedTitleCenterY = topPadPx + titleTopInColumnPx + (titleHeightPx / 2f)
            val titleTranslationY = ((minBarHeightPx / 2f) - expandedTitleCenterY) * fraction
            val backShiftXPx = with(density) { (-6).dp.toPx() } * fraction

            val collapsedTitleWidthPx = rawTitleWidthPx * collapsedTitleScale
            val titleTranslationX = backShiftXPx

            val maxCollapsedTitleWidthDp = (totalWidth * 0.46f).coerceAtLeast(100.dp)
            val collapsedTitleWidthDp = with(density) { collapsedTitleWidthPx.toDp() }
                .coerceIn(52.dp, maxCollapsedTitleWidthDp)
            val collapsedTitleRightDp = (20.dp + if (canNavigateBack) 36.dp else 0.dp) + collapsedTitleWidthDp
            val expandedTextBottomDp = with(density) { textBlockHeightPx.toDp() }

            // 1. Single Unconfined Header Text & Actions Block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        textBlockHeightPx = placeable.height.toFloat()
                        layout(placeable.width, placeable.height.coerceAtMost(constraints.maxHeight)) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .padding(start = 20.dp, end = 20.dp, top = topPadDp, bottom = bottomPadDp),
                verticalAlignment = Alignment.Top
            ) {
                val iconTopOffsetDp = with(density) {
                    (titleTopInColumnPx + (titleHeightPx - 36.dp.toPx()).coerceAtLeast(0f) / 2f).toDp()
                }

                if (canNavigateBack) {
                    IconButton(
                        onClick = { if (contentAlpha > 0.5f) { haptics.tick(); onBack() } },
                        modifier = Modifier
                            .padding(top = iconTopOffsetDp, end = 6.dp)
                            .size(36.dp)
                            .graphicsLayer {
                                alpha = contentAlpha
                                translationX = backShiftXPx
                                translationY = titleTranslationY
                            }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = canopyColors.onCanopy)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (kicker != null) {
                        val kickerTint = lerp(canopyColors.onCanopy, canopyColors.heroStatGood, 0.32f).copy(alpha = 0.84f)
                        Text(
                            text = kicker.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            color = kickerTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = secondaryAlpha; translationY = -fraction * 12f }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (heroContent != null) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = contentAlpha
                                scaleX = titleScale
                                scaleY = titleScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = titleTranslationX
                                translationY = titleTranslationY
                            }
                        ) { heroContent() }
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayMedium,
                            fontSize = expandedFontSize,
                            lineHeight = (expandedFontSize.value + 6f).sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            color = canopyColors.onCanopy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .onPlaced { coordinates ->
                                    titleTopInColumnPx = coordinates.positionInParent().y
                                    titleHeightPx = coordinates.size.height.toFloat()
                                    rawTitleWidthPx = coordinates.size.width.toFloat()
                                }
                                .graphicsLayer {
                                    alpha = contentAlpha
                                    scaleX = titleScale
                                    scaleY = titleScale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    translationX = titleTranslationX
                                    translationY = titleTranslationY
                                }
                        )
                    }

                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp,
                            color = canopyColors.onCanopy.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = secondaryAlpha; translationY = -fraction * 12f }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(top = iconTopOffsetDp)
                        .graphicsLayer { alpha = contentAlpha; translationY = titleTranslationY },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) { actions() }
            }

            // 2. Standardized 2-Axis Morphing Canopy Bar (46dp expanded -> 38dp collapsed)
            if (canopyBar != null && morphingContent == null) {
                val barHeight = lerp(46.dp, 38.dp, fraction)
                val collapsedRowLeft = collapsedTitleRightDp + 10.dp
                val barLeft = lerp(20.dp, collapsedRowLeft, fraction)
                val barTop = lerp(expandedTextBottomDp, (minBarHeightDp - barHeight) / 2, fraction)
                val barRightMargin = lerp(20.dp, 16.dp, fraction)
                val barWidth = (totalWidth - barRightMargin - barLeft).coerceAtLeast(120.dp)
                val isCompact = fraction > 0.45f

                Row(
                    modifier = Modifier
                        .offset(x = barLeft, y = barTop)
                        .width(barWidth)
                        .height(barHeight)
                        .graphicsLayer { alpha = contentAlpha },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    canopyBar(fraction, isCompact, barHeight)
                }
            }

            // 3. Custom 2-Axis Morphing Slot (Transactions Search Bar)
            if (morphingContent != null) {
                morphingContent(fraction, collapsedTitleRightDp, expandedTextBottomDp)
            }
        }

        if (persistentBottomBar != null) {
            persistentBottomBar()
        }
    }
}
