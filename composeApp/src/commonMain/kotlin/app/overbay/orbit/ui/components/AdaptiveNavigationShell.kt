package app.overbay.orbit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PostureMode

private data class NavTabItem(
    val key: OrbitNavKey,
    val title: String,
    val icon: ImageVector
)

/**
 * Root adaptive navigation shell.
 * Switches smoothly between a 5-tab NavigationBar on compact phones (< 720dp)
 * and a NavigationRail + wide content container on foldables, tablets, and wide screens (>= 720dp).
 */
@Composable
fun AdaptiveNavigationShell(
    currentKey: OrbitNavKey,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val posture = LocalDevicePosture.current
    val haptics = LocalOrbitHaptics.current
    var showMoreSheet by remember { mutableStateOf(false) }

    val primaryTabs = remember {
        listOf(
            NavTabItem(OrbitNavKey.Dashboard, "Dashboard", Icons.Default.Home),
            NavTabItem(OrbitNavKey.Transactions(), "Transactions", Icons.AutoMirrored.Filled.List),
            NavTabItem(OrbitNavKey.Budgets, "Budgets", Icons.Default.AccountBox),
            NavTabItem(OrbitNavKey.Accounts(), "Accounts", Icons.Default.AccountCircle)
        )
    }

    val isSecondaryRoute = remember(currentKey) {
        when (currentKey) {
            OrbitNavKey.Onboarding,
            OrbitNavKey.Dashboard,
            is OrbitNavKey.Transactions,
            OrbitNavKey.Budgets,
            is OrbitNavKey.Accounts -> false
            else -> true
        }
    }

    if (posture.mode == PostureMode.COMPACT_PHONE) {
        // Compact Phone Layout (< 720dp)
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }

                    // 5th 'More' tab (active if currentKey is any secondary tool)
                    NavigationBarItem(
                        selected = isSecondaryRoute,
                        onClick = {
                            haptics.tick()
                            showMoreSheet = true
                        },
                        icon = { Icon(Icons.Default.Menu, contentDescription = "More") },
                        label = { Text("More") }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                content()
            }
        }
    } else {
        // Foldable / Tablet / Desktop Layout (>= 720dp)
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                header = {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "O",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxHeight()
            ) {
                primaryTabs.forEach { tab ->
                    val selected = when (tab.key) {
                        is OrbitNavKey.Transactions -> currentKey is OrbitNavKey.Transactions
                        is OrbitNavKey.Accounts -> currentKey is OrbitNavKey.Accounts
                        else -> currentKey == tab.key
                    }
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            onNavigate(tab.key)
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }

                NavigationRailItem(
                    selected = isSecondaryRoute,
                    onClick = {
                        haptics.tick()
                        showMoreSheet = true
                    },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "More") },
                    label = { Text("More") }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content()
            }
        }
    }

    if (showMoreSheet) {
        MoreDestinationsSheet(
            currentKey = currentKey,
            onNavigate = onNavigate,
            onDismiss = { showMoreSheet = false },
            mhaEnabled = true
        )
    }
}
