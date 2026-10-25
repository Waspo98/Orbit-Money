package app.overbay.orbit.ui.budgets

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.budgets.components.BudgetCategoryGroupList
import app.overbay.orbit.ui.budgets.components.BudgetSummaryHeroCard
import app.overbay.orbit.ui.budgets.dialogs.BudgetAmountEditorSheet

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Canopy
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Budgets",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Envelope allocations and pacing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Hero Summary Card
                item {
                    BudgetSummaryHeroCard(
                        overview = state.overview,
                        monthLabel = state.monthLabel,
                        canGoNextMonth = state.canGoNextMonth,
                        onPreviousMonth = { viewModel.onPreviousMonth() },
                        onNextMonth = { viewModel.onNextMonth() },
                        onResetToCurrentMonth = { viewModel.onResetToCurrentMonth() }
                    )
                }

                // Category List Section
                item {
                    BudgetCategoryGroupList(
                        budgeted = state.budgetedItems,
                        unbudgeted = state.unbudgetedItems,
                        inactive = state.inactiveItems,
                        activeSort = state.sortOption,
                        onSortSelected = { viewModel.setSortOption(it) },
                        onItemClick = { viewModel.openBudgetEditor(it) }
                    )
                }
            }
        }
    }

    // Modal Editor Sheet
    val editingItem = state.editingItem
    if (state.showEditorSheet && editingItem != null) {
        val avgSpend = viewModel.calculate3MonthAverageSpend(editingItem.category.id)
        BudgetAmountEditorSheet(
            item = editingItem,
            threeMonthAvgCents = avgSpend,
            onSave = { categoryId, amountCents, rollover ->
                viewModel.saveBudget(categoryId, amountCents, rollover)
            },
            onDelete = { budgetId ->
                viewModel.deleteBudget(budgetId)
            },
            onDismiss = { viewModel.closeBudgetEditor() }
        )
    }
}
