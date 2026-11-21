package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal data class NavTabItem(val key: OrbitNavKey, val title: String, val icon: ImageVector)

internal data class BottomBarVerticalPadding(val top: Dp, val bottom: Dp)

internal val BOTTOM_BAR_MIN_HEIGHT = 60.dp

internal fun bottomBarVerticalPadding(hasBottomInset: Boolean): BottomBarVerticalPadding =
    if (hasBottomInset) {
        // Keep a compact 8dp top margin under the divider and 4dp above system nav insets so touch bounds hug the tabs.
        BottomBarVerticalPadding(top = 8.dp, bottom = 4.dp)
    } else {
        BottomBarVerticalPadding(top = 8.dp, bottom = 8.dp)
    }

/**
 * Symmetric 5-slot phone/tablet bottom navigation bar with equal 1/5th tab widths,
 * centered uppercase labels (even for wide labels like TRANSACTIONS), and compact
 * vertical seating above system navigation insets.
 */
@Composable
internal fun PhoneBottomBar(
    primaryTabs: List<NavTabItem>,
    currentKey: OrbitNavKey,
    isSecondaryRoute: Boolean,
    onTabSelected: (OrbitNavKey) -> Unit,
    onOpenMore: () -> Unit
) {
    val navInsets = NavigationBarDefaults.windowInsets
    val hasBottomInset = navInsets.getBottom(LocalDensity.current) > 0
    val verticalPadding = bottomBarVerticalPadding(hasBottomInset)
    val topPadding = verticalPadding.top
    val bottomPadding = verticalPadding.bottom

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Equal 1/5th weight slots avoid M3 NavigationBar's spacedBy(8.dp) outer edge shift and label width loss.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(navInsets)
            ) {
                val labelWidthScale = navLabelWidthScale(maxWidth.value)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = BOTTOM_BAR_MIN_HEIGHT)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryTabs.forEach { tab ->
                        BottomNavTabItem(
                            title = tab.title,
                            icon = tab.icon,
                            selected = isTabSelected(tab.key, currentKey),
                            topPadding = topPadding,
                            bottomPadding = bottomPadding,
                            labelWidthScale = labelWidthScale,
                            onClick = { onTabSelected(tab.key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    BottomNavTabItem(
                        title = "More",
                        icon = OrbitIcons.MoreHoriz,
                        selected = isSecondaryRoute,
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                        labelWidthScale = labelWidthScale,
                        onClick = onOpenMore,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavTabItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    labelWidthScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tabOrigin = remember { OffsetRef() }
    val pillOrigin = remember { OffsetRef() }
    val pillInteractionSource = remember(interactionSource) {
        MappedInteractionSource(interactionSource) { pillOrigin.value - tabOrigin.value }
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = OrbitSprings.FloatEffectsFast,
        label = "bottomNavIndicatorAlpha"
    )
    val indicatorScaleX by animateFloatAsState(
        targetValue = if (selected) 1f else 0.4f,
        animationSpec = OrbitSprings.FloatSpatialFast,
        label = "bottomNavIndicatorScaleX"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "bottomNavContentColor"
    )
    val fontScale = LocalDensity.current.fontScale
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = cappedNavLabelSp(10f * labelWidthScale, fontScale),
        lineHeight = cappedNavLabelSp(13f * labelWidthScale, fontScale),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        letterSpacing = cappedNavLabelSp(0.2f * labelWidthScale, fontScale),
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = BOTTOM_BAR_MIN_HEIGHT)
            .onGloballyPositioned { tabOrigin.value = it.positionInRoot() }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
            .orbitTactilePress(pressedScale = 0.96f, interactionSource = interactionSource)
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 32.dp)
                .onGloballyPositioned { pillOrigin.value = it.positionInRoot() },
            contentAlignment = Alignment.Center
        ) {
            if (indicatorAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = indicatorScaleX.coerceAtLeast(0f)
                            alpha = indicatorAlpha.coerceIn(0f, 1f)
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .indication(pillInteractionSource, LocalIndication.current)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Unbounded center wrap keeps wide labels (e.g. TRANSACTIONS) centered while font-scale capping prevents neighbor overlap.
        Text(
            text = title.uppercase(),
            style = labelStyle,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true)
        )
    }
}

private class OffsetRef(var value: Offset = Offset.Zero)

/**
 * Translates [PressInteraction] coordinates from the full-slot tab container into the
 * centered indicator pill's local coordinate space so ripples originate under the tap.
 */
internal class MappedInteractionSource(
    private val underlying: InteractionSource,
    private val deltaProvider: () -> Offset
) : InteractionSource {
    override val interactions: Flow<Interaction> = flow {
        val mappedPresses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()
        underlying.interactions.collect { interaction ->
            emit(
                when (interaction) {
                    is PressInteraction.Press -> {
                        val mapped = PressInteraction.Press(interaction.pressPosition - deltaProvider())
                        mappedPresses[interaction] = mapped
                        mapped
                    }
                    is PressInteraction.Release -> {
                        mappedPresses.remove(interaction.press)?.let { PressInteraction.Release(it) } ?: interaction
                    }
                    is PressInteraction.Cancel -> {
                        mappedPresses.remove(interaction.press)?.let { PressInteraction.Cancel(it) } ?: interaction
                    }
                    else -> interaction
                }
            )
        }
    }
}

/**
 * Scales bottom-bar label typography down slightly on ultra-narrow viewports (< 360dp,
 * e.g. split-screen or narrow foldable cover screens) so wide adjacent labels never collide.
 */
internal fun navLabelWidthScale(
    barWidthDp: Float,
    standardMinWidthDp: Float = 360f,
    minScale: Float = 0.82f
): Float {
    if (barWidthDp <= 0f || barWidthDp >= standardMinWidthDp) return 1f
    return (barWidthDp / standardMinWidthDp).coerceIn(minScale, 1f)
}

/**
 * Caps bottom-bar label font scaling so wide uppercase labels stay legible without overlapping adjacent tabs.
 */
internal fun cappedNavLabelSp(
    baseSp: Float,
    fontScale: Float,
    maxFontScale: Float = 1.15f
): TextUnit {
    val safeScale = if (fontScale > 0f) fontScale else 1f
    val effectiveScale = safeScale.coerceAtMost(maxFontScale)
    return (baseSp * (effectiveScale / safeScale)).sp
}

internal fun isTabSelected(tabKey: OrbitNavKey, currentKey: OrbitNavKey): Boolean = when (tabKey) {
    is OrbitNavKey.Transactions -> currentKey is OrbitNavKey.Transactions
    is OrbitNavKey.Accounts -> currentKey is OrbitNavKey.Accounts
    is OrbitNavKey.CardPerks -> currentKey is OrbitNavKey.CardPerks
    else -> currentKey == tabKey
}
