package app.overbay.orbit.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import app.overbay.orbit.ui.budgets.components.BudgetDetailInspectorPane
import app.overbay.orbit.ui.budgets.components.BudgetSummaryHeroCard
import app.overbay.orbit.ui.budgets.dialogs.BudgetAmountEditorSheet
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.PeriodNavigatorPill
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Editorial Budgets screen featuring flat emerald canopy hero with period navigation,
 * 2x2 snapshot metrics panel, spending mix strip, inline expandable category rows on phones,
 * morphing dual-screen inspector pane, and scroll-reactive FAB.
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
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (state.inspectingItem != null || state.showEditorSheet)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = startPad, end = endPad, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                BudgetSummaryHeroCard(
                                    overview = state.overview,
                                    monthLabel = state.monthLabel,
                                    daysRemaining = state.daysRemaining
                                )
                            }

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
                                    },
                                    isWideScreen = isTwoPane
                                )
                            }
                        }
                    }

                    if (isTwoPane) {
                        OrbitMorphingSplitLayout(
                            isDetailOpen = isDetailOpen,
                            onDismissDetail = { viewModel.dismissInspectorPane() },
                            masterContent = masterList,
                            detailContent = {
                                BudgetDetailInspectorPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onNavigate = onNavigate,
                                    onClosePane = { viewModel.dismissInspectorPane() }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        masterList()
                    }
                }
            }
        }

        // Compact Phone Modal Editor Sheet
        val editingItem = state.editingItem
        if (!isTwoPane && state.showEditorSheet && editingItem != null) {
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
}
