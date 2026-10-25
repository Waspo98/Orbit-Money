package app.overbay.orbit.ui.transactions.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.transactions.TransactionsUiState
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.util.LocalOrbitHaptics

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

    Column(modifier = modifier.fillMaxSize()) {
        // Canopy Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.filteredTransactions.size} transactions • Net: ${MoneyUtils.formatCents(state.netCashflowCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onOpenCsv) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Import CSV",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Search & Filter Bar
        TransactionSearchFilterBar(
            searchQuery = state.searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) },
            activeQuickFilter = state.quickFilter,
            onQuickFilterSelected = { viewModel.setQuickFilter(it) },
            hasActiveFilters = state.hasActiveFilters,
            onOpenFilterSheet = { viewModel.openFilterSheet() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Transactions LazyColumn (60fps optimized)
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    title = "No Transactions Found",
                    description = if (state.hasActiveFilters) "Try adjusting your filters or search terms." else "Tap + to add your first transaction or import a CSV.",
                    actionButtonText = if (state.hasActiveFilters) "Clear Filters" else "Import CSV",
                    onActionClick = {
                        if (state.hasActiveFilters) {
                            viewModel.clearFilters()
                        } else {
                            onOpenCsv()
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.filteredTransactions,
                    key = { it.id }
                ) { txn ->
                    val isSelected = state.selectedTransactionIds.contains(txn.id)
                    val category = state.categoryMap[txn.effectiveCategoryId]
                    val account = state.accountMap[txn.accountId]

                    SwipeableTransactionRow(
                        transaction = txn,
                        category = category,
                        accountName = account?.name ?: txn.accountName,
                        isSelected = isSelected,
                        isSelectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) {
                                viewModel.toggleTransactionSelection(txn.id)
                            } else {
                                onSelectForInspect(txn.id)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleTransactionSelection(txn.id)
                        },
                        onQuickDelete = {
                            viewModel.deleteTransaction(txn.id)
                        },
                        onQuickTransfer = {
                            viewModel.saveTransaction(
                                id = txn.id,
                                merchant = txn.effectiveMerchant,
                                amountCents = txn.effectiveAmountCents,
                                date = txn.effectiveDate,
                                categoryId = txn.effectiveCategoryId,
                                notes = txn.notes,
                                isTransfer = !txn.effectiveIsTransfer,
                                isIgnored = txn.effectiveIsIgnored,
                                mhaEligible = txn.effectiveMhaEligible
                            )
                        }
                    )
                }
            }
        }
    }
}
