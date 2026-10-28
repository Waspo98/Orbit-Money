package app.overbay.orbit.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.ui.budgets.components.BudgetCategoryGroupList
import app.overbay.orbit.ui.budgets.components.BudgetDetailInspectorPane
import app.overbay.orbit.ui.budgets.components.BudgetSummaryHeroCard
import app.overbay.orbit.ui.budgets.dialogs.BudgetAmountEditorSheet
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
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
                var sortMenuExpanded by remember { mutableStateOf(false) }

                MorphingPageHero(
                    kicker = "BUDGET PLANNING",
                    title = "Budgets",
                    subtitle = "${state.monthLabel} · ${state.daysRemaining} days left",
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, barHeight ->
                        val shortMonth = if (state.monthLabel.length > 8) {
                            state.monthLabel.take(3) + " " + state.selectedMonth.take(4)
                        } else {
                            state.monthLabel
                        }
                        PeriodNavigatorPill(
                            label = state.monthLabel,
                            compactLabel = shortMonth,
                            isCompact = isCompact,
                            onPrevious = { viewModel.onPreviousMonth() },
                            onNext = { viewModel.onNextMonth() },
                            canGoNext = state.canGoNextMonth,
                            onClickLabel = { viewModel.onResetToCurrentMonth() },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        Box {
                            CanopyIconButton(
                                icon = OrbitIcons.Sort,
                                contentDescription = "Sort Budgets",
                                onClick = { sortMenuExpanded = true },
                                buttonSize = barHeight,
                                isActive = state.sortOption != CategorySortOption.OVER_BUDGET_FIRST
                            )

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                val sortOptions = listOf(
                                    CategorySortOption.OVER_BUDGET_FIRST to "Over Budget First",
                                    CategorySortOption.LEAST_ROOM_LEFT to "Least Room Left",
                                    CategorySortOption.MOST_SPENT to "Most Spent",
                                    CategorySortOption.NAME_A_TO_Z to "Name A-Z"
                                )
                                sortOptions.forEach { (option, label) ->
                                    val selected = state.sortOption == option
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
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
                                            viewModel.setSortOption(option)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
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
