package app.overbay.orbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

private data class NavTabItem(val key: OrbitNavKey, val title: String, val icon: ImageVector)

/**
 * Adaptive navigation shell supporting phone bottom bar, tablet rail, and desktop sidebar.
 */
@Composable
fun AdaptiveNavigationShell(
    currentKey: OrbitNavKey,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {},
    mhaEnabled: Boolean = true,
    hiddenRouteNames: List<String> = emptyList(),
    moreRouteOrder: List<String> = emptyList(),
    content: @Composable () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var showMoreSheet by remember { mutableStateOf(false) }

    val primaryTabs = remember {
        listOf(
            NavTabItem(OrbitNavKey.Dashboard, "Dashboard", OrbitIcons.Dashboard),
            NavTabItem(OrbitNavKey.Transactions(), "Transactions", OrbitIcons.Transactions),
            NavTabItem(OrbitNavKey.Budgets, "Budgets", OrbitIcons.Budgets),
            NavTabItem(OrbitNavKey.Accounts(), "Accounts", OrbitIcons.Accounts)
        )
    }

    val isSecondaryRoute = remember(currentKey) {
        when (currentKey) {
            OrbitNavKey.Onboarding, OrbitNavKey.Dashboard, is OrbitNavKey.Transactions,
            OrbitNavKey.Budgets, is OrbitNavKey.Accounts, OrbitNavKey.Settings -> false
            else -> true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        when {
            maxWidth >= 900.dp -> {
                // Desktop Sidebar (>= 900.dp)
                Row(modifier = Modifier.fillMaxSize()) {
                    DesktopSidebar(
                        primaryTabs = primaryTabs,
                        currentKey = currentKey,
                        onNavigate = { haptics.tick(); onNavigate(it) },
                        mhaEnabled = mhaEnabled,
                        hiddenRouteNames = hiddenRouteNames,
                        moreRouteOrder = moreRouteOrder
                    )
                    VerticalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            }
            maxWidth >= 600.dp -> {
                // Tablet NavigationRail (600.dp..899.dp)
                Row(modifier = Modifier.fillMaxSize()) {
                    TabletNavRail(
                        primaryTabs = primaryTabs,
                        currentKey = currentKey,
                        isSecondaryRoute = isSecondaryRoute,
                        onTabSelected = { haptics.tick(); onNavigate(it) },
                        onOpenMore = { haptics.tick(); showMoreSheet = true },
                        onOpenSettings = { haptics.tick(); onNavigate(OrbitNavKey.Settings) }
                    )
                    VerticalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            }
            else -> {
                // Compact Phone Bottom NavigationBar (< 600.dp)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        PhoneBottomBar(
                            primaryTabs = primaryTabs,
                            currentKey = currentKey,
                            isSecondaryRoute = isSecondaryRoute,
                            onTabSelected = { haptics.tick(); onNavigate(it) },
                            onOpenMore = { haptics.tick(); showMoreSheet = true }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                        content()
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        MoreDestinationsSheet(
            currentKey = currentKey,
            onNavigate = onNavigate,
            onDismiss = { showMoreSheet = false },
            mhaEnabled = mhaEnabled,
            hiddenRouteNames = hiddenRouteNames,
            moreRouteOrder = moreRouteOrder
        )
    }
}

@Composable
private fun DesktopSidebar(
    primaryTabs: List<NavTabItem>,
    currentKey: OrbitNavKey,
    onNavigate: (OrbitNavKey) -> Unit,
    mhaEnabled: Boolean,
    hiddenRouteNames: List<String>,
    moreRouteOrder: List<String>
) {
    val allSecondary = remember(mhaEnabled) {
        buildList {
            add(NavTabItem(OrbitNavKey.Upcoming, "Upcoming", OrbitIcons.Upcoming))
            add(NavTabItem(OrbitNavKey.Goals, "Goals", OrbitIcons.Goals))
            add(NavTabItem(OrbitNavKey.Categories, "Categories", OrbitIcons.Categories))
            add(NavTabItem(OrbitNavKey.Rules, "Rules", OrbitIcons.Rules))
            add(NavTabItem(OrbitNavKey.SpendingTrends, "Spending Trends", OrbitIcons.SpendingTrends))
            add(NavTabItem(OrbitNavKey.NetWorth, "Net Worth", OrbitIcons.NetWorth))
            if (mhaEnabled) add(NavTabItem(OrbitNavKey.MhaTracker, "MHA Tracker", OrbitIcons.MhaTracker))
            add(NavTabItem(OrbitNavKey.HousingCalculator, "Housing Calculator", OrbitIcons.HousingCalculator))
            add(NavTabItem(OrbitNavKey.RetirementCalculator, "Retirement Calculator", OrbitIcons.RetirementCalculator))
            add(NavTabItem(OrbitNavKey.Household, "Household", OrbitIcons.Household))
        }
    }

    val visibleSecondary = allSecondary.filter { !hiddenRouteNames.contains(it.key.routeName) }
    val orderedSecondary = if (moreRouteOrder.isNotEmpty()) {
        val orderMap = moreRouteOrder.withIndex().associate { it.value to it.index }
        visibleSecondary.sortedBy { orderMap[it.key.routeName] ?: 999 }
    } else visibleSecondary

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(240.dp).fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = OrbitIcons.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Orbit Money", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Household Finance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "MAIN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                primaryTabs.forEach { tab ->
                    SidebarRow(tab.title, tab.icon, isTabSelected(tab.key, currentKey)) { onNavigate(tab.key) }
                }

                if (orderedSecondary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "MORE TOOLS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    orderedSecondary.forEach { item ->
                        SidebarRow(item.title, item.icon, currentKey == item.key) { onNavigate(item.key) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            SidebarRow("Settings", OrbitIcons.Settings, currentKey == OrbitNavKey.Settings) { onNavigate(OrbitNavKey.Settings) }
        }
    }
}

@Composable
private fun SidebarRow(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = bg, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = title, tint = fg, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TabletNavRail(
    primaryTabs: List<NavTabItem>,
    currentKey: OrbitNavKey,
    isSecondaryRoute: Boolean,
    onTabSelected: (OrbitNavKey) -> Unit,
    onOpenMore: () -> Unit,
    onOpenSettings: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Icon(imageVector = OrbitIcons.Dashboard, contentDescription = "Orbit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp).padding(vertical = 4.dp))
        }
    ) {
        primaryTabs.forEach { tab ->
            NavigationRailItem(
                selected = isTabSelected(tab.key, currentKey),
                onClick = { onTabSelected(tab.key) },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.title, modifier = Modifier.size(24.dp)) },
                label = { Text(tab.title.uppercase(), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) }
            )
        }
        NavigationRailItem(
            selected = isSecondaryRoute,
            onClick = onOpenMore,
            icon = { Icon(imageVector = OrbitIcons.MoreHoriz, contentDescription = "More", modifier = Modifier.size(24.dp)) },
            label = { Text("MORE", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) }
        )
        Spacer(modifier = Modifier.weight(1f))
        NavigationRailItem(
            selected = currentKey == OrbitNavKey.Settings,
            onClick = onOpenSettings,
            icon = { Icon(imageVector = OrbitIcons.Settings, contentDescription = "Settings", modifier = Modifier.size(24.dp)) },
            label = { Text("SETTINGS", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun PhoneBottomBar(
    primaryTabs: List<NavTabItem>,
    currentKey: OrbitNavKey,
    isSecondaryRoute: Boolean,
    onTabSelected: (OrbitNavKey) -> Unit,
    onOpenMore: () -> Unit
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            primaryTabs.forEach { tab ->
                val selected = isTabSelected(tab.key, currentKey)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab.key) },
                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.title, modifier = Modifier.size(24.dp)) },
                    label = {
                        Text(
                            text = tab.title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    },
                    colors = itemColors
                )
            }
            NavigationBarItem(
                selected = isSecondaryRoute,
                onClick = onOpenMore,
                icon = { Icon(imageVector = OrbitIcons.MoreHoriz, contentDescription = "More", modifier = Modifier.size(24.dp)) },
                label = {
                    Text(
                        text = "MORE",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSecondaryRoute) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                },
                colors = itemColors
            )
        }
    }
}

private fun isTabSelected(tabKey: OrbitNavKey, currentKey: OrbitNavKey): Boolean = when (tabKey) {
    is OrbitNavKey.Transactions -> currentKey is OrbitNavKey.Transactions
    is OrbitNavKey.Accounts -> currentKey is OrbitNavKey.Accounts
    else -> currentKey == tabKey
}
