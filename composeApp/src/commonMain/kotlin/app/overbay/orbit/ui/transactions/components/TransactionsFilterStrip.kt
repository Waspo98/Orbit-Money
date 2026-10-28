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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.transactions.QuickFilter
import app.overbay.orbit.ui.transactions.TransactionSortOption
import app.overbay.orbit.ui.transactions.TransactionsUiState
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun rememberActiveFilterChips(
    state: TransactionsUiState,
    viewModel: TransactionsViewModel
): List<Pair<String, () -> Unit>> {
    return remember(state.quickFilter, state.filterDraft, state.accountMap, state.categoryMap) {
        buildList {
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
                val catName = state.categoryMap[catId]?.labelWithEmoji ?: "Category"
                add("Category: $catName" to { viewModel.applyFilterDraft(state.filterDraft.copy(categoryId = null)) })
            }
        }
    }
}

/**
 * Flanking circular Filter and Sort icon buttons positioned immediately to the right
 * of the TransactionsSearchBar. Shrinks in lockstep with the search bar on scroll.
 */
@Composable
fun TransactionsSearchTrailingControls(
    state: TransactionsUiState,
    activeFilterCount: Int,
    onOpenFilter: () -> Unit,
    onSelectSort: (TransactionSortOption) -> Unit,
    buttonSize: Dp,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val hasActiveFilter = activeFilterCount > 0
    val hasCustomSort = state.sortOption != TransactionSortOption.DATE_DESC
    val iconSize = if (buttonSize < 42.dp) 18.dp else 20.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Filter Button with active badge
        Surface(
            onClick = { haptics.tick(); onOpenFilter() },
            shape = CircleShape,
            color = if (hasActiveFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = if (hasActiveFilter) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                }
            ),
            modifier = Modifier.size(buttonSize)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                BadgedBox(
                    badge = {
                        if (hasActiveFilter) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(activeFilterCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = OrbitIcons.Filter,
                        contentDescription = "Filter transactions",
                        tint = if (hasActiveFilter) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        // Circular Sort Button with DropdownMenu
        Box {
            Surface(
                onClick = { haptics.tick(); sortMenuExpanded = true },
                shape = CircleShape,
                color = if (hasCustomSort) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (hasCustomSort) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
                ),
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = OrbitIcons.Sort,
                        contentDescription = "Sort transactions",
                        tint = if (hasCustomSort) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                TransactionSortOption.entries.forEach { option ->
                    val selected = state.sortOption == option
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            haptics.tick()
                            onSelectSort(option)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Auto-expanding removable active filter chip strip shown below the canopy ONLY when
 * one or more filters are active (and hidden during active search mode).
 */
@Composable
fun TransactionsActiveFilterChipsBar(
    isSearchActive: Boolean,
    activeChips: List<Pair<String, () -> Unit>>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    AnimatedVisibility(
        visible = !isSearchActive && activeChips.isNotEmpty(),
        enter = expandVertically(OrbitSprings.spatialDefault()) + fadeIn(OrbitSprings.effectsDefault()),
        exit = shrinkVertically(OrbitSprings.effectsDefault()) + fadeOut(OrbitSprings.effectsDefault()),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)
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
                        onClearAll()
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
