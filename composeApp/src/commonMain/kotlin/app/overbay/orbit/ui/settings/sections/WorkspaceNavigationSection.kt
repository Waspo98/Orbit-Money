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
import app.overbay.orbit.data.model.TransactionQuickAction
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.util.LocalOrbitHaptics

private data class MoreRouteItem(val routeName: String, val title: String, val isLocked: Boolean = false)
private data class MoreRouteBucket(val title: String, val items: List<MoreRouteItem>)

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.workspaceNavigationSectionItems(
    preferences: OrbitPreferences,
    onMhaTrackerChange: (Boolean) -> Unit,
    onCardRewardsToolsChange: (Boolean?) -> Unit,
    onOpenTransactionActions: () -> Unit,
    onToggleRouteHidden: (String) -> Unit,
    onMoveRoute: (fromIndex: Int, toIndex: Int, currentList: List<String>) -> Unit
) {
    item(key = "workspace_optional_tools") {
        OrbitSectionCard(
            title = "Optional Tools",
            subtitle = "Enable specialized planning trackers and credit card strategy"
        ) {
            OrbitSwitchRow(
                title = "Ministerial Housing Allowance (MHA)",
                subtitle = "Enable clergy housing allowance tracking and tax estimates",
                checked = preferences.mhaTrackerEnabled,
                onCheckedChange = onMhaTrackerChange,
                showContainer = false
            )
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Credit Card Rewards Strategy",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (preferences.cardRewardsToolsEnabled) {
                    null -> "Auto · Active when any card tracks bonus or opened dates"
                    true -> "Always On · Showing 5/24, bonuses, and earn rates"
                    false -> "Off · Hiding strategy tools across cards"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            OrbitSegmentedTabRow(
                items = listOf<Boolean?>(null, true, false),
                selected = preferences.cardRewardsToolsEnabled,
                onSelect = onCardRewardsToolsChange,
                label = { when (it) { null -> "Auto"; true -> "On"; false -> "Off" } }
            )
        }
    }

    item(key = "workspace_quick_actions") {
        val haptics = LocalOrbitHaptics.current
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
                            text = action.label,
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

    item(key = "workspace_more_menu") {
        val buckets = remember(preferences.moreRouteOrder, preferences.mhaTrackerEnabled) {
            buildOrderedRouteBuckets(preferences.moreRouteOrder, preferences.mhaTrackerEnabled)
        }
        val globalRouteOrder = remember(buckets) {
            buckets.flatMap { bucket -> bucket.items.map { it.routeName } }
        }

        OrbitSectionCard(
            title = "More Menu Destinations",
            subtitle = "Show, hide, and reorder tools inside each More menu group"
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                buckets.forEach { bucket ->
                    MoreMenuBucketGroup(
                        bucket = bucket,
                        hiddenRouteNames = preferences.hiddenRouteNames,
                        onToggleRouteHidden = onToggleRouteHidden,
                        onMoveWithinBucket = { fromIdx, toIdx ->
                            val fromRoute = bucket.items.getOrNull(fromIdx)?.routeName ?: return@MoreMenuBucketGroup
                            val toRoute = bucket.items.getOrNull(toIdx)?.routeName ?: return@MoreMenuBucketGroup
                            val fromGlobal = globalRouteOrder.indexOf(fromRoute)
                            val toGlobal = globalRouteOrder.indexOf(toRoute)
                            if (fromGlobal >= 0 && toGlobal >= 0) onMoveRoute(fromGlobal, toGlobal, globalRouteOrder)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenuBucketGroup(
    bucket: MoreRouteBucket,
    hiddenRouteNames: List<String>,
    onToggleRouteHidden: (String) -> Unit,
    onMoveWithinBucket: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val reorderState = if (bucket.items.size > 1) {
        rememberDragReorderState(
            items = bucket.items,
            keySelector = { it.routeName },
            onMove = onMoveWithinBucket,
            itemSpacingDp = 1.dp
        )
    } else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = bucket.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        bucket.items.forEachIndexed { index, item ->
            key(item.routeName) {
                val isVisible = item.isLocked || !hiddenRouteNames.contains(item.routeName)
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().dragReorderItem(reorderState, item.routeName).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (reorderState != null) {
                        OrbitDragHandle(state = reorderState, key = item.routeName)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.isLocked) {
                        OrbitBadge(text = "Always On")
                    } else {
                        Checkbox(checked = isVisible, onCheckedChange = { haptics.tick(); onToggleRouteHidden(item.routeName) })
                    }
                }
            }
        }
    }
}

private fun buildOrderedRouteBuckets(moreRouteOrder: List<String>, mhaEnabled: Boolean): List<MoreRouteBucket> {
    val orderMap = moreRouteOrder.withIndex().associate { it.value to it.index }
    fun List<MoreRouteItem>.sortedByOrder(): List<MoreRouteItem> =
        if (orderMap.isEmpty()) this else sortedBy { orderMap[it.routeName] ?: 999 }

    val planning = listOf(
        MoreRouteItem("card_perks", "Credit Card Tracker"),
        MoreRouteItem("upcoming", "Upcoming"),
        MoreRouteItem("goals", "Goals"),
        MoreRouteItem("categories", "Category Manager", isLocked = true),
        MoreRouteItem("rules", "Rules", isLocked = true)
    ).sortedByOrder()

    val insights = buildList {
        add(MoreRouteItem("spending_trends", "Spending Trends"))
        add(MoreRouteItem("net_worth", "Net Worth"))
        if (mhaEnabled) add(MoreRouteItem("mha", "MHA Tracker"))
    }.sortedByOrder()

    val calculators = listOf(
        MoreRouteItem("housing_calculator", "Housing Calculator"),
        MoreRouteItem("retirement_calculator", "Retirement Calculator")
    ).sortedByOrder()

    val household = listOf(MoreRouteItem("household", "Household"))

    return listOf(
        MoreRouteBucket("Planning", planning),
        MoreRouteBucket("Insights", insights),
        MoreRouteBucket("Calculators", calculators),
        MoreRouteBucket("Household", household)
    )
}
