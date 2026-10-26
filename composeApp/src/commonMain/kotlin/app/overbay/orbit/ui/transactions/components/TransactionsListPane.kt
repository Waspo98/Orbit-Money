package app.overbay.orbit.ui.transactions.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitPrimaryPillButton
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.transactions.TransactionSortOption
import app.overbay.orbit.ui.transactions.TransactionsUiState
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.launch

/**
 * Restored editorial Transactions list pane featuring MorphingPageHero with embedded
 * white pill search input, 3-button toolbar, OrbitPageActionRow, and month group headers.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun TransactionsListPane(
    state: TransactionsUiState,
    viewModel: TransactionsViewModel,
    onOpenDrawer: () -> Unit,
    onOpenCsv: () -> Unit,
    onSelectForInspect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val coroutineScope = rememberCoroutineScope()
    val heroScrollState = rememberHeroScrollState(maxHeight = 220.dp, minHeight = 64.dp)

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(heroScrollState.nestedScrollConnection)
    ) {
        // Edge-to-edge curved green canopy hero
        MorphingPageHero(
            kicker = "MONEY MOVEMENT",
            title = "Transactions",
            subtitle = "${state.monthlyTransactionCount} transactions this month",
            scrollState = heroScrollState,
            onPrimaryAddClick = { viewModel.openTransactionEditor(null) },
            expandedToolbar = {
                // White Pill Search Input
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "Search merchant, description, notes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color(0xFF1A1F1B),
                        unfocusedTextColor = Color(0xFF1A1F1B)
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3-Column Equal-Width Toolbar Row (Filter, Sort, View)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Filter Button
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            viewModel.openFilterSheet()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1A1F1B)
                        ),
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.hasActiveFilters) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        ) {
                            Text("Filter", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // 2. Sort Button
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                sortMenuExpanded = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1A1F1B)
                            ),
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Sort", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            TransactionSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 3. View Button
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                viewMenuExpanded = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1A1F1B)
                            ),
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("View", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }

                        DropdownMenu(
                            expanded = viewMenuExpanded,
                            onDismissRequest = { viewMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (state.isCompactView) "Comfortable View" else "Compact View") },
                                onClick = {
                                    viewModel.toggleCompactView()
                                    viewMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import CSV") },
                                onClick = {
                                    viewMenuExpanded = false
                                    onOpenCsv()
                                }
                            )
                        }
                    }
                }
            },
            collapsedActions = {
                IconButton(onClick = { coroutineScope.launch { heroScrollState.expand() } }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = OrbitTheme.colors.onCanopy
                    )
                }
                BadgedBox(
                    badge = {
                        if (state.hasActiveFilters) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    IconButton(onClick = { viewModel.openFilterSheet() }) {
                        Icon(
                            imageVector = OrbitIcons.Filter,
                            contentDescription = "Filter",
                            tint = OrbitTheme.colors.onCanopy
                        )
                    }
                }
            }
        )

        // Actions Row Directly Below Hero
        OrbitPageActionRow {
            OrbitSecondaryPillButton(
                text = "SimpleFIN Sync",
                onClick = { viewModel.syncSimpleFin() },
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryPillButton(
                text = "+ Manual Transaction",
                onClick = { viewModel.openTransactionEditor(null) },
                modifier = Modifier.weight(1f)
            )
        }

        // Transactions List or States
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.groupedTransactions.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        title = if (state.searchQuery.isNotBlank() || state.hasActiveFilters) {
                            "No matching transactions"
                        } else {
                            "No transactions recorded"
                        },
                        description = if (state.searchQuery.isNotBlank() || state.hasActiveFilters) {
                            "Try clearing your search query or adjusting your filters."
                        } else {
                            "Sync with SimpleFIN or record your first manual transaction above."
                        },
                        icon = OrbitIcons.Transactions,
                        actionButtonText = if (state.searchQuery.isNotBlank() || state.hasActiveFilters) "Reset Filters" else null,
                        onActionClick = {
                            viewModel.setSearchQuery("")
                            viewModel.clearFilters()
                        }
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.groupedTransactions.forEach { group ->
                        // Sticky / Month Group Header
                        item(key = "header_${group.monthKey}") {
                            MonthGroupHeader(monthLabel = group.monthLabel, count = group.count)
                        }

                        // Transactions in this month
                        items(
                            items = group.transactions,
                            key = { it.id }
                        ) { txn ->
                            val isSelected = state.selectedTransactionIds.contains(txn.id)
                            val isExpanded = state.expandedTransactionId == txn.id
                            val category = state.categoryMap[txn.effectiveCategoryId]
                            val account = state.accountMap[txn.accountId]

                            SwipeableTransactionRow(
                                transaction = txn,
                                category = category,
                                accountName = account?.name ?: txn.accountName,
                                isSelected = isSelected,
                                isSelectionMode = state.isSelectionMode,
                                isExpanded = isExpanded,
                                onClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleTransactionSelection(txn.id)
                                    } else {
                                        viewModel.toggleExpandTransaction(txn.id)
                                    }
                                },
                                onLongClick = { viewModel.toggleTransactionSelection(txn.id) },
                                onEdit = { viewModel.openTransactionEditor(txn.id) },
                                onSplit = { viewModel.openSplitSheet(txn.id) },
                                onCreateRule = { viewModel.createRuleFromTransaction(txn) },
                                onMarkRecurring = { viewModel.markTransactionRecurring(txn) },
                                onToggleTransfer = { viewModel.toggleTransfer(txn.id) },
                                onToggleMha = { viewModel.toggleMha(txn.id) },
                                onToggleIgnore = { viewModel.toggleIgnore(txn.id) },
                                onDelete = { viewModel.deleteTransaction(txn.id) },
                                onResetMerchant = { viewModel.resetMerchant(txn.id) },
                                onResetCategory = { viewModel.resetCategory(txn.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGroupHeader(monthLabel: String, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$count ${if (count == 1) "transaction" else "transactions"}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = OrbitTheme.colors.cardBorder,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
