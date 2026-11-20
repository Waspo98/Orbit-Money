package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme

internal data class NavTabItem(val key: OrbitNavKey, val title: String, val icon: ImageVector)

/**
 * Symmetric 5-slot phone/tablet bottom navigation bar with equal 1/5th tab widths,
 * centered uppercase labels (even for wide labels like TRANSACTIONS), and balanced
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
    // Bias tabs slightly lower above system navigation insets while keeping balanced vertical centering without insets.
    val topPadding = if (hasBottomInset) 18.dp else 11.dp
    val bottomPadding = if (hasBottomInset) 5.dp else 11.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Equal 1/5th weight slots avoid M3 NavigationBar's spacedBy(8.dp) outer edge shift and label width loss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(navInsets)
                    .defaultMinSize(minHeight = 72.dp)
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
                    onClick = onOpenMore,
                    modifier = Modifier.weight(1f)
                )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
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
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        letterSpacing = 0.2.sp,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 72.dp)
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
            modifier = Modifier.size(width = 64.dp, height = 32.dp),
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
                    .indication(interactionSource, LocalIndication.current)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Unbounded center wrap ensures long labels (e.g. TRANSACTIONS) stay centered under their icon even under font scaling.
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

internal fun isTabSelected(tabKey: OrbitNavKey, currentKey: OrbitNavKey): Boolean = when (tabKey) {
    is OrbitNavKey.Transactions -> currentKey is OrbitNavKey.Transactions
    is OrbitNavKey.Accounts -> currentKey is OrbitNavKey.Accounts
    is OrbitNavKey.CardPerks -> currentKey is OrbitNavKey.CardPerks
    else -> currentKey == tabKey
}
