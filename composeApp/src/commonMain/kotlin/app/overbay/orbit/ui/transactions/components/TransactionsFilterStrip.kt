package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.transactions.QuickFilter
import app.overbay.orbit.ui.transactions.TransactionSortOption
import app.overbay.orbit.ui.transactions.TransactionsUiState
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Persistent filter/sort/view control row and removable active-filter chips that remain
 * pinned below the collapsed Transactions title and search bar, smoothly collapsing out
 * when active search mode is opened.
 */
@Composable
fun TransactionsPersistentFilterStrip(
    state: TransactionsUiState,
    viewModel: TransactionsViewModel,
    onOpenCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }

    val activeChips = remember(state.quickFilter, state.filterDraft, state.accountMap, state.categoryMap) {
        buildList<Pair<String, () -> Unit>> {
            if (state.quickFilter != QuickFilter.ALL) {
                add("Quick: ${state.quickFilter.label}" to { viewModel.setQuickFilter(QuickFilter.ALL) })
            }
            if (state.filterDraft.type != "all") {
                val typeLabel = when (state.filterDraft.type) {
                    "expense" -> "Expenses"
                    "income" -> "Income"
                    "transfer" -> "Transfers"
                    else -> state.filterDraft.type
                }
                add("Type: $typeLabel" to { viewModel.applyFilterDraft(state.filterDraft.copy(type = "all")) })
            }
            if (state.filterDraft.datePreset != "all") {
                val dateLabel = when (state.filterDraft.datePreset) {
                    "this_month" -> "This Month"
                    "last_month" -> "Last Month"
                    "this_year" -> "This Year"
                    else -> state.filterDraft.datePreset
                }
                add("Date: $dateLabel" to { viewModel.applyFilterDraft(state.filterDraft.copy(datePreset = "all")) })
            }
            state.filterDraft.accountId?.let { accId ->
                val accName = state.accountMap[accId]?.name ?: "Account"
                add("Account: $accName" to { viewModel.applyFilterDraft(state.filterDraft.copy(accountId = null)) })
            }
            state.filterDraft.categoryId?.let { catId ->
                val catName = state.categoryMap[catId]?.name ?: "Category"
                add("Category: $catName" to { viewModel.applyFilterDraft(state.filterDraft.copy(categoryId = null)) })
            }
        }
    }

    AnimatedVisibility(
        visible = !state.isSearchActive,
        enter = expandVertically(OrbitSprings.spatialDefault()) + fadeIn(OrbitSprings.effectsDefault()),
        exit = shrinkVertically(OrbitSprings.effectsDefault()) + fadeOut(OrbitSprings.effectsDefault()),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)
        ) {
            val pillColors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
            val pillBorder = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { haptics.tick(); viewModel.openFilterSheet() },
                    shape = RoundedCornerShape(14.dp),
                    colors = pillColors,
                    border = pillBorder,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (activeChips.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        }
                    ) {
                        Text(
                            text = if (activeChips.isNotEmpty()) "Filter (${activeChips.size})" else "Filter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { haptics.tick(); sortMenuExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = pillColors,
                        border = pillBorder,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Sort", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        TransactionSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { viewModel.setSortOption(option); sortMenuExpanded = false }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { haptics.tick(); viewMenuExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = pillColors,
                        border = pillBorder,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("View", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.isCompactView) "Comfortable View" else "Compact View") },
                            onClick = { viewModel.toggleCompactView(); viewMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Import CSV") },
                            onClick = { viewMenuExpanded = false; onOpenCsv() }
                        )
                    }
                }
            }

            // Removable Active Filter Chips Row
            AnimatedVisibility(
                visible = activeChips.isNotEmpty(),
                enter = expandVertically(OrbitSprings.spatialDefault()) + fadeIn(OrbitSprings.effectsDefault()),
                exit = shrinkVertically(OrbitSprings.effectsDefault()) + fadeOut(OrbitSprings.effectsDefault())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeChips.forEach { (label, onRemove) ->
                        Surface(
                            onClick = {
                                haptics.tick()
                                onRemove()
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $label",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (activeChips.size > 1) {
                        Surface(
                            onClick = {
                                haptics.tick()
                                viewModel.clearFilters()
                            },
                            shape = CircleShape,
                            color = OrbitTheme.colors.onCanopy.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.onCanopy,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
