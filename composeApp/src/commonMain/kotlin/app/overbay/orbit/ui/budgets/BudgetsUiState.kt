package app.overbay.orbit.ui.budgets

import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.data.model.MonthlyBudgetOverview

data class BudgetsUiState(
    val selectedMonth: String = "",
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val canGoNextMonth: Boolean = false,
    val overview: MonthlyBudgetOverview? = null,
    val sortOption: CategorySortOption = CategorySortOption.OVER_BUDGET_FIRST,
    val editingItem: CategoryBudgetOverviewItem? = null,
    val showEditorSheet: Boolean = false,
    val isLoading: Boolean = false
) {
    val totalBudgetedCents get() = overview?.totalBudgetedCents ?: 0L
    val totalSpentCents get() = overview?.totalSpentCents ?: 0L
    val totalRemainingCents get() = overview?.totalRemainingCents ?: 0L
    val dailySafeSpend get() = overview?.dailySafeSpend
    val budgetedItems get() = overview?.budgeted ?: emptyList()
    val unbudgetedItems get() = overview?.unbudgeted ?: emptyList()
    val inactiveItems get() = overview?.inactive ?: emptyList()
}
