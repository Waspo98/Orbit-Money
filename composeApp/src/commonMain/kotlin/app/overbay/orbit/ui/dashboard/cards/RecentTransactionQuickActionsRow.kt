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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import app.overbay.orbit.ui.components.OrbitIcons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.theme.OrbitColorTokens
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
        // Quick Category Suggestions (first 4 categories if available)
        if (categories.isNotEmpty()) {
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
                        label = { Text(cat.name, fontSize = 11.sp) },
                        leadingIcon = if (isCurrent) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                        } else null,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle Transfer
            FilterChip(
                selected = transaction.isTransfer,
                onClick = {
                    haptics.tick()
                    onToggleTransfer(!transaction.isTransfer)
                },
                leadingIcon = {
                    Icon(OrbitIcons.Transactions, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text("Transfer", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp)
            )

            // Toggle Ignore
            FilterChip(
                selected = transaction.isIgnored,
                onClick = {
                    haptics.tick()
                    onToggleIgnore(!transaction.isIgnored)
                },
                leadingIcon = {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text("Ignore", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp)
            )

            // Toggle MHA (if enabled)
            if (mhaEnabled) {
                FilterChip(
                    selected = transaction.isMhaEligible,
                    onClick = {
                        haptics.tick()
                        onToggleMha(!transaction.isMhaEligible)
                    },
                    label = { Text("MHA Eligible", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            // Mark Recurring
            AssistChip(
                onClick = {
                    haptics.confirm()
                    onMarkRecurring()
                },
                leadingIcon = {
                    Icon(OrbitIcons.Upcoming, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text("Mark Recurring", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp)
            )

            // Reset Edits (if edited)
            if (transaction.hasEdits) {
                AssistChip(
                    onClick = {
                        haptics.tick()
                        onResetEdits()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Reset Edits", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            // Inspect Details
            AssistChip(
                onClick = {
                    haptics.tick()
                    onInspectDetails()
                },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text("Details", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp)
            )

            // Delete
            AssistChip(
                onClick = {
                    haptics.confirm()
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = OrbitColorTokens.Red
                    )
                },
                label = { Text("Delete", fontSize = 11.sp, color = OrbitColorTokens.Red) },
                colors = AssistChipDefaults.assistChipColors(labelColor = OrbitColorTokens.Red),
                modifier = Modifier.height(28.dp)
            )
        }
    }
}
