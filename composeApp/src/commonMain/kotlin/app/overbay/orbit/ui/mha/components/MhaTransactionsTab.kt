package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.mha.MhaTransactionItem
import app.overbay.orbit.ui.mha.MhaTransactionTabFilter
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MhaTransactionsTab(
    transactions: List<MhaTransactionItem>,
    selectedTab: MhaTransactionTabFilter,
    eligibleCount: Int,
    reviewQueueCount: Int,
    allCount: Int,
    searchQuery: String,
    onTabChange: (MhaTransactionTabFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleEligible: (Long, Boolean) -> Unit,
    onResetEligible: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "Housing Transactions Queue",
        subtitle = "Review qualifying expenditures and toggle eligibility",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search housing merchant or notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented Chips
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    Triple(MhaTransactionTabFilter.ELIGIBLE, "Eligible", eligibleCount),
                    Triple(MhaTransactionTabFilter.REVIEW_QUEUE, "Review Queue", reviewQueueCount),
                    Triple(MhaTransactionTabFilter.ALL, "All", allCount)
                )
                tabs.forEachIndexed { index, (tab, label, count) ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = {
                            haptics.tick()
                            onTabChange(tab)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        icon = {},
                        label = { Text("$label ($count)", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction List
            if (transactions.isEmpty()) {
                EmptyStateCard(
                    title = "No Transactions Found",
                    description = if (searchQuery.isNotEmpty()) "No transactions match '$searchQuery'" else "No transactions in this queue."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transactions.forEach { item ->
                        MhaTransactionRow(
                            item = item,
                            onToggle = {
                                haptics.tick()
                                onToggleEligible(item.id, item.isEligible)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MhaTransactionRow(
    item: MhaTransactionItem,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (item.isEligible) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Checkbox + Merchant + Category/Account Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Checkbox(
                    checked = item.isEligible,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.isExplicitOverride) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "USER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.date} • ${item.category?.labelWithEmoji ?: "❓ Uncategorized"} • ${item.account?.name ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Tabular Amount
            Text(
                text = MoneyUtils.formatCents(abs(item.amountCents)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.isEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
