package app.overbay.orbit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

private data class NavTabItem(
    val key: OrbitNavKey,
    val title: String,
    val icon: ImageVector
)

/**
 * Root navigation shell with a persistent 5-tab bottom NavigationBar across all device postures.
 */
@Composable
fun AdaptiveNavigationShell(
    currentKey: OrbitNavKey,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {},
    mhaEnabled: Boolean = true,
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
            OrbitNavKey.Onboarding,
            OrbitNavKey.Dashboard,
            is OrbitNavKey.Transactions,
            OrbitNavKey.Budgets,
            is OrbitNavKey.Accounts,
            OrbitNavKey.Settings -> false
            else -> true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = OrbitTheme.colors.cardBorder
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    primaryTabs.forEach { tab ->
                        val selected = when (tab.key) {
                            is OrbitNavKey.Transactions -> currentKey is OrbitNavKey.Transactions
                            is OrbitNavKey.Accounts -> currentKey is OrbitNavKey.Accounts
                            else -> currentKey == tab.key
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                haptics.tick()
                                onNavigate(tab.key)
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
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

                    // 5th 'More' tab (active if currentKey is any secondary tool)
                    NavigationBarItem(
                        selected = isSecondaryRoute,
                        onClick = {
                            haptics.tick()
                            showMoreSheet = true
                        },
                        icon = {
                            Icon(
                                imageVector = OrbitIcons.MoreHoriz,
                                contentDescription = "More",
                                modifier = Modifier.size(24.dp)
                            )
                        },
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            content()
        }
    }

    if (showMoreSheet) {
        MoreDestinationsSheet(
            currentKey = currentKey,
            onNavigate = onNavigate,
            onDismiss = { showMoreSheet = false },
            mhaEnabled = mhaEnabled
        )
    }
}
