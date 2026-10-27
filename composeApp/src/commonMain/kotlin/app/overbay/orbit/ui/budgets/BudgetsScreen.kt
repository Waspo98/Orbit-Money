package app.overbay.orbit.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.budgets.components.BudgetCategoryGroupList
import app.overbay.orbit.ui.budgets.components.BudgetSummaryHeroCard
import app.overbay.orbit.ui.budgets.dialogs.BudgetAmountEditorSheet
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.PeriodNavigatorPill
import app.overbay.orbit.ui.components.rememberHeroScrollState

/**
 * Editorial Budgets screen featuring flat emerald canopy hero with period navigation,
 * 2x2 snapshot metrics panel, spending mix strip, inline expandable category rows, and scroll-reactive FAB.
 * Conforms strictly to <= 6 parameters and < 400-line ceiling.
 */
@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!state.isLoading) {
                OrbitFloatingActionButton(
                    visible = heroScrollState.isFabVisible,
                    onClick = { viewModel.openAddBudget() },
                    contentDescription = "Add Budget"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            MorphingPageHero(
                kicker = "BUDGET PLANNING",
                title = "Budgets",
                subtitle = "${state.monthLabel} · ${state.daysRemaining} days left",
                scrollState = heroScrollState,
                expandedToolbar = {
                    PeriodNavigatorPill(
                        label = state.monthLabel,
                        onPrevious = { viewModel.onPreviousMonth() },
                        onNext = { viewModel.onNextMonth() },
                        canGoNext = state.canGoNextMonth,
                        onClickLabel = { viewModel.onResetToCurrentMonth() }
                    )
                }
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Scrollable Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero Snapshot Card
                    item {
                        BudgetSummaryHeroCard(
                            overview = state.overview,
                            monthLabel = state.monthLabel,
                            daysRemaining = state.daysRemaining
                        )
                    }

                    // Category List Section
                    item {
                        BudgetCategoryGroupList(
                            budgeted = state.budgetedItems,
                            unbudgeted = state.unbudgetedItems,
                            inactive = state.inactiveItems,
                            activeSort = state.sortOption,
                            expandedCategoryId = state.expandedCategoryId,
                            categoryTransactions = state.categoryTransactions,
                            onSortSelected = { viewModel.setSortOption(it) },
                            onToggleExpand = { viewModel.toggleExpandCategory(it) },
                            onEditBudget = { viewModel.openBudgetEditor(it) },
                            onDeleteBudget = { viewModel.deleteBudget(it) },
                            onViewTransactions = { categoryId ->
                                onNavigate(OrbitNavKey.Transactions(categoryId = categoryId))
                            }
                        )
                    }
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
