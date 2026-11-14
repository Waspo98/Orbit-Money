package app.overbay.orbit.ui.budgets

import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.data.model.MonthlyBudgetOverview
import app.overbay.orbit.data.model.Transaction

data class BudgetsUiState(
    val selectedMonth: String = "",
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val canGoNextMonth: Boolean = false,
    val canGoPreviousMonth: Boolean = true,
    val daysRemaining: Int = 0,
    val monthProgressFraction: Float? = null,
    val overview: MonthlyBudgetOverview? = null,
    val activeMonths: List<String> = emptyList(),
    val sortOption: CategorySortOption = CategorySortOption.OVER_BUDGET_FIRST,
    val expandedCategoryId: Long? = null,
    val showUnbudgeted: Boolean = false,
    val showInactive: Boolean = false,
    val categoryTransactions: Map<Long, List<Transaction>> = emptyMap(),
    val editingItem: CategoryBudgetOverviewItem? = null,
    val showEditorSheet: Boolean = false,
    val showAddCategorySheet: Boolean = false,
    val initialAddCategoryId: Long? = null,
    val isLoading: Boolean = false
) {
    val totalBudgetedCents get() = overview?.totalBudgetedCents ?: 0L
    val totalSpentCents get() = overview?.totalSpentCents ?: 0L
    val totalRemainingCents get() = overview?.totalRemainingCents ?: 0L
    val budgetedRemainingCents get() = overview?.budgetedRemainingCents ?: 0L
    val lastMonthMtdSpentCents get() = overview?.lastMonthMtdSpentCents ?: 0L
    val lastMonthLabel get() = overview?.lastMonthLabel ?: ""
    val dailySafeSpend get() = overview?.dailySafeSpend
    val budgetedDailySafeSpendCents get() = overview?.budgetedDailySafeSpendCents ?: 0L
    val budgetedItems get() = overview?.budgeted ?: emptyList()
    val unbudgetedItems get() = overview?.unbudgeted ?: emptyList()
    val inactiveItems get() = overview?.inactive ?: emptyList()
    val inspectingItem: CategoryBudgetOverviewItem?
        get() = (budgetedItems + unbudgetedItems + inactiveItems).find { it.category.id == expandedCategoryId } ?: editingItem
}
