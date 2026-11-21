package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.TaxTrackingLabels
import app.overbay.orbit.data.model.TransactionQuickAction
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.LocalTaxTrackingMode
import app.overbay.orbit.util.currentTaxTrackingLabels

private data class MoreRouteItem(
    val routeName: String,
    val title: String,
    val subtitle: String? = null,
    val isLocked: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.workspaceNavigationSectionItems(
    preferences: OrbitPreferences,
    onMhaTrackerChange: (Boolean) -> Unit,
    onOpenTransactionActions: () -> Unit,
    onToggleRouteHidden: (String) -> Unit,
    onMoveRoute: (fromIndex: Int, toIndex: Int, currentList: List<String>) -> Unit
) {
    item(key = "workspace_more_menu") {
        val taxLabels = currentTaxTrackingLabels()
        val orderedRoutes = remember(preferences.moreRouteOrder, taxLabels) {
            buildOrderedMoreRoutes(preferences.moreRouteOrder, taxLabels)
        }
        val routeOrderKeys = remember(orderedRoutes) {
            orderedRoutes.map { it.routeName }
        }

        OrbitSectionCard(
            title = "More Menu Destinations",
            subtitle = "Turn features on or off and drag to match your More menu order"
        ) {
            MoreMenuDestinationList(
                items = orderedRoutes,
                hiddenRouteNames = preferences.hiddenRouteNames,
                mhaTrackerEnabled = preferences.mhaTrackerEnabled,
                onToggleItem = { routeName ->
                    if (routeName == "mha") {
                        onMhaTrackerChange(!preferences.mhaTrackerEnabled)
                    } else {
                        onToggleRouteHidden(routeName)
                    }
                },
                onMoveItem = { fromIdx, toIdx ->
                    if (fromIdx in routeOrderKeys.indices && toIdx in routeOrderKeys.indices) {
                        onMoveRoute(fromIdx, toIdx, routeOrderKeys)
                    }
                }
            )
        }
    }

    item(key = "workspace_quick_actions") {
        val haptics = LocalOrbitHaptics.current
        val taxTrackingMode = LocalTaxTrackingMode.current
        val pinnedActions = remember(
            preferences.transactionPinnedActions,
            preferences.transactionActionOrder,
            preferences.mhaTrackerEnabled
        ) {
            TransactionQuickAction.resolveActions(
                pinnedIds = preferences.transactionPinnedActions,
                orderIds = preferences.transactionActionOrder,
                mhaEnabled = preferences.mhaTrackerEnabled
            ).first
        }

        OrbitSectionCard(
            title = "Transaction Quick Actions",
            subtitle = "Choose and reorder buttons shown when expanding a transaction"
        ) {
            if (pinnedActions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pinnedActions.forEach { action ->
                        OrbitBadge(
                            text = action.displayLabel(taxTrackingMode),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { haptics.tick(); onOpenTransactionActions() },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(imageVector = OrbitIcons.ViewSliders, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Customize Buttons")
            }
        }
    }
}

@Composable
private fun MoreMenuDestinationList(
    items: List<MoreRouteItem>,
    hiddenRouteNames: List<String>,
    mhaTrackerEnabled: Boolean,
    onToggleItem: (String) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val reorderState = rememberDragReorderState(
        items = items,
        keySelector = { it.routeName },
        onMove = onMoveItem,
        itemSpacingDp = 1.dp
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            key(item.routeName) {
                val isVisible = when {
                    item.isLocked -> true
                    item.routeName == "mha" -> mhaTrackerEnabled
                    else -> !hiddenRouteNames.contains(item.routeName)
                }
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().dragReorderItem(reorderState, item.routeName).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbitDragHandle(state = reorderState, key = item.routeName)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (item.isLocked) {
                        OrbitBadge(text = "Always On")
                    } else {
                        Checkbox(checked = isVisible, onCheckedChange = { haptics.tick(); onToggleItem(item.routeName) })
                    }
                }
            }
        }
    }
}

private fun buildOrderedMoreRoutes(
    moreRouteOrder: List<String>,
    taxLabels: TaxTrackingLabels
): List<MoreRouteItem> {
    val defaultItems = listOf(
        MoreRouteItem("card_perks", "Credit Card Tracker"),
        MoreRouteItem("upcoming", "Upcoming"),
        MoreRouteItem("goals", "Goals"),
        MoreRouteItem("categories", "Categories", isLocked = true),
        MoreRouteItem("rules", "Rules", isLocked = true),
        MoreRouteItem("spending_trends", "Spending Trends"),
        MoreRouteItem("net_worth", "Net Worth"),
        MoreRouteItem(
            routeName = "mha",
            title = taxLabels.navTitle,
            subtitle = taxLabels.settingsSubtitle
        ),
        MoreRouteItem("housing_calculator", "Housing Calculator"),
        MoreRouteItem("retirement_calculator", "Retirement Calculator"),
        MoreRouteItem("household", "Household")
    )
    if (moreRouteOrder.isEmpty()) return defaultItems
    val orderMap = moreRouteOrder.withIndex().associate { it.value to it.index }
    return defaultItems.sortedBy { orderMap[it.routeName] ?: 999 }
}
