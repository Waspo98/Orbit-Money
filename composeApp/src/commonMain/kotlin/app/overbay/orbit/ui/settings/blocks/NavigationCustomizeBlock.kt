package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState

private data class NavCustomItem(val routeName: String, val title: String, val isLocked: Boolean = false)

@Composable
fun NavigationCustomizeBlock(
    preferences: OrbitPreferences,
    onToggleRouteHidden: (String) -> Unit,
    onMoveRoute: (fromIndex: Int, toIndex: Int, currentList: List<String>) -> Unit,
    onMhaTrackerChange: (Boolean) -> Unit = {},
    onCardRewardsToolsChange: (Boolean?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val defaultRoutes = remember {
        listOf(
            NavCustomItem("card_perks", "Credit Card Tracker"),
            NavCustomItem("upcoming", "Upcoming"),
            NavCustomItem("goals", "Goals"),
            NavCustomItem("categories", "Category Manager", isLocked = true),
            NavCustomItem("rules", "Rules", isLocked = true),
            NavCustomItem("spending_trends", "Spending Trends"),
            NavCustomItem("net_worth", "Net Worth"),
            NavCustomItem("housing_calculator", "Housing Calculator"),
            NavCustomItem("retirement_calculator", "Retirement Calculator"),
            NavCustomItem("household", "Household")
        )
    }

    val orderedRoutes = remember(preferences.moreRouteOrder) {
        if (preferences.moreRouteOrder.isNotEmpty()) {
            val orderMap = preferences.moreRouteOrder.withIndex().associate { it.value to it.index }
            defaultRoutes.sortedBy { orderMap[it.routeName] ?: 999 }
        } else {
            defaultRoutes
        }
    }

    val currentRouteStrings = remember(orderedRoutes) { orderedRoutes.map { it.routeName } }
    val reorderState = rememberDragReorderState(
        items = orderedRoutes,
        keySelector = { it.routeName },
        onMove = { from, to -> onMoveRoute(from, to, currentRouteStrings) },
        itemSpacingDp = 1.dp
    )

    OrbitSectionCard(
        title = "Navigation & Optional Features",
        subtitle = "Choose which tools appear in the More menu and reorder them",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OrbitSwitchRow(
                title = "Ministerial Housing Allowance (MHA)",
                subtitle = "Enable clergy housing allowance tracking and tax estimates",
                checked = preferences.mhaTrackerEnabled,
                onCheckedChange = onMhaTrackerChange
            )

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            // Show Rewards Strategy Tools
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Show Rewards Strategy Tools",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (preferences.cardRewardsToolsEnabled) {
                        null -> "Auto · Active when any card tracks bonus or opened dates"
                        true -> "Always On · Showing 5/24, bonuses, and earn rates"
                        false -> "Off · Hiding strategy tools across cards"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf("Auto", "On", "Off")
                    val selectedIndex = when (preferences.cardRewardsToolsEnabled) {
                        null -> 0
                        true -> 1
                        false -> 2
                    }
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedIndex == index,
                            onClick = {
                                val newValue = when (index) {
                                    0 -> null
                                    1 -> true
                                    else -> false
                                }
                                onCardRewardsToolsChange(newValue)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            Text(
                text = "More Menu Destinations",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    orderedRoutes.forEachIndexed { index, item ->
                        key(item.routeName) {
                            val isVisible = item.isLocked || !preferences.hiddenRouteNames.contains(item.routeName)

                            if (index > 0) {
                                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dragReorderItem(reorderState, item.routeName)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OrbitDragHandle(
                                    state = reorderState,
                                    key = item.routeName
                                )

                                Checkbox(
                                    checked = isVisible,
                                    enabled = !item.isLocked,
                                    onCheckedChange = { if (!item.isLocked) onToggleRouteHidden(item.routeName) }
                                )

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )

                                if (item.isLocked) {
                                    OrbitBadge(text = "Locked")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
