package app.overbay.orbit.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private class CoordinatesHolder {
    var value: LayoutCoordinates? = null
    var rootX by mutableFloatStateOf(0f)
}

private class HeroMetricsHolder(
    var textBlockHeightPx: Float,
    var titleTopInColumnPx: Float,
    var titleHeightPx: Float,
    var rawTitleWidthPx: Float
)

/**
 * Edge-to-edge flat emerald canopy header that draws seamlessly behind the system status bar.
 * Isolates scroll-driven morphing strictly to Compose's Layout and Draw/graphicsLayer phases
 * (zero per-frame Composition reads) and resolves exact header geometry on Frame 0.
 */
@Composable
fun MorphingPageHero(
    title: String,
    modifier: Modifier = Modifier,
    collapsedTitle: String? = null,
    kicker: String? = null,
    subtitle: String? = null,
    scrollState: HeroScrollState? = null,
    contentAlpha: Float = 1f,
    activeHeightOverrideDp: Dp? = null,
    actions: @Composable RowScope.() -> Unit = {},
    canopyBar: (@Composable RowScope.(collapseFraction: Float, isCompact: Boolean, barHeight: Dp) -> Unit)? = null,
    morphingContent: (@Composable BoxWithConstraintsScope.(
        collapseFractionProvider: () -> Float,
        collapsedTitleRightDp: Dp,
        expandedTextBottomPxProvider: () -> Float
    ) -> Unit)? = null,
    persistentBottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    heroContent: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    val canopyColors = OrbitTheme.colors
    val textMeasurer = rememberTextMeasurer()

    val minBarHeightPx = scrollState?.minHeightPx ?: with(density) { 56.dp.toPx() }
    val hasCanopyBar = canopyBar != null && morphingContent == null
    val hasMorphingSlot = morphingContent != null

    val collapseDisplayTitle = collapsedTitle ?: title
    val expandedFontSize = if (title.length > 17) 26.sp else 30.sp
    val collapsedTargetSp = when {
        collapseDisplayTitle.length > 15 -> 17.5f
        collapseDisplayTitle.length > 11 -> 19f
        else -> 21f
    }
    val collapsedTitleScale = collapsedTargetSp / expandedFontSize.value

    val titleStyle = MaterialTheme.typography.displayMedium.copy(
        fontSize = expandedFontSize,
        lineHeight = (expandedFontSize.value + 6f).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )
    val collapsedStyle = titleStyle.copy(
        fontSize = collapsedTargetSp.sp,
        lineHeight = (collapsedTargetSp + 4f).sp
    )
    val kickerStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp
    )

    val measuredTitleSize = remember(title, expandedFontSize, density) {
        textMeasurer.measure(text = title, style = titleStyle, maxLines = 1).size
    }
    val measuredCollapsedWidthPx = remember(collapseDisplayTitle, collapsedStyle, density) {
        textMeasurer.measure(text = collapseDisplayTitle, style = collapsedStyle, maxLines = 1).size.width.toFloat()
    }
    val initialTitleTopPx = remember(kicker, density) {
        if (kicker != null) {
            val kh = textMeasurer.measure(text = kicker.uppercase(), style = kickerStyle, maxLines = 1).size.height
            kh + with(density) { 3.dp.toPx() }
        } else {
            0f
        }
    }

    val metrics = remember(title, kicker, expandedFontSize) {
        HeroMetricsHolder(
            textBlockHeightPx = initialTitleTopPx + measuredTitleSize.height + with(density) { 28.dp.toPx() },
            titleTopInColumnPx = initialTitleTopPx,
            titleHeightPx = measuredTitleSize.height.toFloat(),
            rawTitleWidthPx = measuredTitleSize.width.toFloat()
        )
    }

    val isCompact by remember(scrollState) {
        derivedStateOf { (scrollState?.collapseFraction ?: 0f) > 0.45f }
    }
    val animatedBarButtonHeight by animateDpAsState(
        targetValue = if (isCompact) 38.dp else 46.dp,
        animationSpec = OrbitSprings.DpSpatialFast,
        label = "canopyBarButtonHeight"
    )

    val extraMorphSlotPx = if (hasMorphingSlot || hasCanopyBar) with(density) { 58.dp.toPx() } else 0f
    val canopyCoordinator = LocalCanopyTransitionCoordinator.current
    val canopyScreenKeyId = LocalCanopyScreenKeyId.current
    val heroCoordinates = remember { CoordinatesHolder() }
    val maxElevationPx = with(density) { 6.dp.toPx() }

    val topPadDp = 18.dp
    val topPadPx = with(density) { topPadDp.toPx() }
    val bottomPadDp = if (hasMorphingSlot || hasCanopyBar || persistentBottomBar != null) 10.dp else 18.dp
    val backShiftBasePx = with(density) { (-6).dp.toPx() }
    val collapsedTitleWidthDp = with(density) { measuredCollapsedWidthPx.toDp() }
    val collapsedMaxBoundFraction = 0.36f
    val minCollapsedWidthPx = with(density) { 84.dp.toPx() }
    val iconTopOffsetDp = with(density) {
        (initialTitleTopPx + (measuredTitleSize.height - 36.dp.toPx()).coerceAtLeast(0f) / 2f).toDp()
    }

    val headerTextRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = topPadDp, bottom = bottomPadDp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (kicker != null) {
                    val kickerTint = lerp(canopyColors.onCanopy, canopyColors.heroStatGood, 0.32f).copy(alpha = 0.84f)
                    Text(
                        text = kicker.uppercase(),
                        style = kickerStyle,
                        color = kickerTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val f = scrollState?.collapseFraction ?: 0f
                            alpha = ((1f - f * 2.2f).coerceIn(0f, 1f) * contentAlpha).coerceIn(0f, 1f)
                            translationY = -f * 12f
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                val morphTransform: Modifier.((Float) -> Float) -> Modifier = { alphaFn ->
                    this.graphicsLayer {
                        val f = scrollState?.collapseFraction ?: 0f
                        val maxShiftY = (minBarHeightPx / 2f) - (topPadPx + metrics.titleTopInColumnPx + metrics.titleHeightPx / 2f)
                        val scale = lerp(1f, collapsedTitleScale, f)
                        alpha = alphaFn(f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationX = backShiftBasePx * f
                        translationY = maxShiftY * f
                    }
                }
                val placeMetrics = Modifier.onPlaced { coords ->
                    metrics.titleTopInColumnPx = coords.positionInParent().y
                    metrics.titleHeightPx = coords.size.height.toFloat()
                    metrics.rawTitleWidthPx = coords.size.width.toFloat()
                }
                if (heroContent != null) {
                    Box(modifier = placeMetrics.morphTransform { contentAlpha }) { heroContent() }
                } else {
                    val hasSeparateCollapsed = collapsedTitle != null && collapsedTitle != title
                    val limitModifier = if (hasCanopyBar) {
                        Modifier.layout { measurable, constraints ->
                            val f = scrollState?.collapseFraction ?: 0f
                            val scale = lerp(1f, collapsedTitleScale, f)
                            val maxCollapsedW = (constraints.maxWidth * collapsedMaxBoundFraction).coerceAtLeast(minCollapsedWidthPx)
                            val maxUnscaledW = if (f > 0.25f) {
                                lerp(constraints.maxWidth.toFloat(), maxCollapsedW / scale, ((f - 0.25f) / 0.45f).coerceIn(0f, 1f)).roundToInt()
                            } else constraints.maxWidth
                            val p = measurable.measure(constraints.copy(maxWidth = maxUnscaledW.coerceIn(0, constraints.maxWidth)))
                            layout(p.width, p.height) { p.placeRelative(0, 0) }
                        }
                    } else Modifier

                    Box {
                        Text(
                            text = title,
                            style = titleStyle,
                            color = canopyColors.onCanopy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = limitModifier.then(placeMetrics).morphTransform { f ->
                                if (hasSeparateCollapsed) (1f - f * 2.3f).coerceIn(0f, 1f) * contentAlpha else contentAlpha
                            }
                        )
                        if (hasSeparateCollapsed) {
                            Text(
                                text = collapsedTitle!!,
                                style = titleStyle,
                                color = canopyColors.onCanopy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = limitModifier.morphTransform { f ->
                                    ((f - 0.35f) * 2.2f).coerceIn(0f, 1f) * contentAlpha
                                }
                            )
                        }
                    }
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
                        modifier = Modifier.graphicsLayer {
                            val f = scrollState?.collapseFraction ?: 0f
                            alpha = ((1f - f * 2.2f).coerceIn(0f, 1f) * contentAlpha).coerceIn(0f, 1f)
                            translationY = -f * 12f
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(top = iconTopOffsetDp)
                    .graphicsLayer {
                        val f = scrollState?.collapseFraction ?: 0f
                        val maxShiftY = (minBarHeightPx / 2f) - (topPadPx + metrics.titleTopInColumnPx + metrics.titleHeightPx / 2f)
                        alpha = contentAlpha
                        translationY = maxShiftY * f
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) { actions() }
        }
    }

    Column(
        modifier = modifier.stationaryCanopyBackdrop(
            canopyStart = canopyColors.canopyStart,
            canopyMid = canopyColors.canopyMid,
            canopyEnd = canopyColors.canopyEnd,
            canopyGlow = canopyColors.canopyGlow,
            elevationPxProvider = { (scrollState?.collapseFraction ?: 0f) * maxElevationPx },
            coordinator = canopyCoordinator,
            screenKeyId = canopyScreenKeyId,
            getHeroCoordinates = {
                // Subscribe the Draw phase to rootX so late slide settlement invalidates draw
                // without triggering recomposition.
                @Suppress("UNUSED_VARIABLE")
                val trackedRootX = heroCoordinates.rootX
                heroCoordinates.value
            },
            setHeroCoordinates = { coords ->
                heroCoordinates.value = coords
                val rootX = coords.positionInRoot().x
                if (abs(rootX - heroCoordinates.rootX) > 0.01f) {
                    heroCoordinates.rootX = rootX
                }
            }
        )
    ) {
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            content = {
                headerTextRow()

                if (morphingContent != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val maxCollapsedTitleWidthDp = (maxWidth * 0.46f).coerceAtLeast(100.dp)
                        val clampedTitleW = collapsedTitleWidthDp.coerceIn(52.dp, maxCollapsedTitleWidthDp)
                        morphingContent(
                            { scrollState?.collapseFraction ?: 0f },
                            20.dp + clampedTitleW,
                            { metrics.textBlockHeightPx }
                        )
                    }
                } else if (canopyBar != null) {
                    Row(
                        modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        canopyBar(if (isCompact) 1f else 0f, isCompact, animatedBarButtonHeight)
                    }
                }
            }
        ) { measurables, constraints ->
            val headerPlaceable = measurables[0].measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )
            val textBlockH = headerPlaceable.height.toFloat()
            metrics.textBlockHeightPx = textBlockH

            val expandedMorphHeightPx = (textBlockH + extraMorphSlotPx).coerceAtLeast(minBarHeightPx)
            scrollState?.updateMaxHeight(expandedMorphHeightPx)

            val f = scrollState?.collapseFraction ?: 0f
            val restingPx = lerp(expandedMorphHeightPx, minBarHeightPx, f)
            val zoneHeightPx = (activeHeightOverrideDp?.roundToPx() ?: restingPx.roundToInt()).coerceAtLeast(0)
            val totalWidthPx = constraints.maxWidth.toFloat()

            var secondPlaceableX = 0
            var secondPlaceableY = 0
            val secondPlaceable = if (measurables.size > 1) {
                if (hasMorphingSlot) {
                    measurables[1].measure(Constraints.fixed(constraints.maxWidth, zoneHeightPx))
                } else {
                    val maxCollapsedTitleW = (totalWidthPx * collapsedMaxBoundFraction).coerceAtLeast(minCollapsedWidthPx)
                    val targetCollapsedW = if (collapsedTitle != null) measuredCollapsedWidthPx else (metrics.rawTitleWidthPx * collapsedTitleScale)
                    val clampedTitleW = targetCollapsedW.coerceIn(52.dp.toPx(), maxCollapsedTitleW)
                    val collapsedRowLeftPx = 20.dp.toPx() + clampedTitleW + 10.dp.toPx()
                    val barHeightPx = lerp(46.dp.toPx(), 38.dp.toPx(), f)
                    val barLeftPx = lerp(20.dp.toPx(), collapsedRowLeftPx, f)
                    val barTopPx = lerp(textBlockH, (minBarHeightPx - barHeightPx) / 2f, f)
                    val barRightMarginPx = lerp(20.dp.toPx(), 16.dp.toPx(), f)
                    val barWidthPx = (totalWidthPx - barRightMarginPx - barLeftPx)
                        .coerceAtLeast(120.dp.toPx())
                        .roundToInt()

                    secondPlaceableX = barLeftPx.roundToInt()
                    secondPlaceableY = barTopPx.roundToInt()
                    measurables[1].measure(
                        Constraints.fixed(barWidthPx, barHeightPx.roundToInt().coerceAtLeast(1))
                    )
                }
            } else null

            layout(constraints.maxWidth, zoneHeightPx) {
                headerPlaceable.placeRelative(0, 0)
                secondPlaceable?.placeRelative(secondPlaceableX, secondPlaceableY)
            }
        }

        if (persistentBottomBar != null) {
            persistentBottomBar()
        }
    }
}
