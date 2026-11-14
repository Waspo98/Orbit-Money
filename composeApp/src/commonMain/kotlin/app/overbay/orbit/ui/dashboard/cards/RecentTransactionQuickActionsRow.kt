package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.dashboard.DashboardRecentTransactionItem
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Inline expandable quick actions row displayed when a Recent Transaction chevron is tapped.
 */
@Composable
fun RecentTransactionQuickActionsRow(
    transaction: DashboardRecentTransactionItem,
    mhaEnabled: Boolean,
    categories: List<Category> = emptyList(),
    onQuickCategorize: (Long) -> Unit,
    onToggleTransfer: (Boolean) -> Unit,
    onToggleIgnore: (Boolean) -> Unit,
    onToggleMha: (Boolean) -> Unit,
    onMarkRecurring: () -> Unit,
    onResetEdits: () -> Unit,
    onInspectDetails: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OrbitAccordionVisibility(visible = categories.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    categories.take(5).forEach { cat ->
                        val isCurrent = transaction.categoryId == cat.id
                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                haptics.tick()
                                onQuickCategorize(cat.id)
                            },
                            label = { Text(cat.name, fontSize = 11.5.sp) },
                            leadingIcon = if (isCurrent) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                            } else null,
                            shape = CircleShape,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = transaction.isTransfer,
                onClick = {
                    haptics.tick()
                    onToggleTransfer(!transaction.isTransfer)
                },
                leadingIcon = {
                    Icon(OrbitIcons.Transactions, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text("Transfer", fontSize = 11.5.sp) },
                shape = CircleShape,
                modifier = Modifier.height(32.dp)
            )

            FilterChip(
                selected = transaction.isIgnored,
                onClick = {
                    haptics.tick()
                    onToggleIgnore(!transaction.isIgnored)
                },
                leadingIcon = {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text("Ignore", fontSize = 11.5.sp) },
                shape = CircleShape,
                modifier = Modifier.height(32.dp)
            )

            OrbitScaleBadgeVisibility(visible = mhaEnabled) {
                FilterChip(
                    selected = transaction.isMhaEligible,
                    onClick = {
                        haptics.tick()
                        onToggleMha(!transaction.isMhaEligible)
                    },
                    label = { Text("MHA Eligible", fontSize = 11.5.sp) },
                    shape = CircleShape,
                    modifier = Modifier.height(32.dp)
                )
            }

            OrbitActionChip(
                label = "Mark Recurring",
                icon = OrbitIcons.Upcoming,
                onClick = {
                    haptics.confirm()
                    onMarkRecurring()
                },
                modifier = Modifier.height(32.dp)
            )

            OrbitScaleBadgeVisibility(visible = transaction.hasEdits) {
                OrbitActionChip(
                    label = "Reset Edits",
                    icon = Icons.Default.Refresh,
                    onClick = onResetEdits,
                    modifier = Modifier.height(32.dp)
                )
            }

            OrbitActionChip(
                label = "Details",
                icon = Icons.Default.Info,
                onClick = onInspectDetails,
                modifier = Modifier.height(32.dp)
            )

            OrbitActionChip(
                label = "Delete",
                icon = Icons.Default.Delete,
                isDestructive = true,
                onClick = {
                    haptics.confirm()
                    onDelete()
                },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}
