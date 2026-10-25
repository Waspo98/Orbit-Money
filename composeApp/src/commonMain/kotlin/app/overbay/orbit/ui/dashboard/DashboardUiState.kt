package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.domain.Cents

/**
 * Enumeration of all customizable dashboard cards.
 * Maps directly to [app.overbay.orbit.data.model.DashboardWidgetConfig].
 */
enum class DashboardCardType(
    val widgetId: String,
    val defaultTitle: String,
    val defaultSortOrder: Int
) {
    CASHFLOW_HERO("cashflow_hero", "Net Cashflow", 0),
    BUDGET_PULSE("budget_pulse", "Budget Pulse", 1),
    UPCOMING_BILLS("upcoming_bills", "Upcoming Bills & Income", 2),
    RECENT_TRANSACTIONS("recent_transactions", "Recent Transactions", 3),
    ACCOUNTS_OVERVIEW("accounts_overview", "Accounts Overview", 4),
    GOALS_PROGRESS("goals_progress", "Savings Goals", 5);

    companion object {
        fun fromWidgetId(widgetId: String): DashboardCardType? =
            entries.firstOrNull { it.widgetId.equals(widgetId, ignoreCase = true) }
    }
}

data class DashboardCardConfigItem(
    val type: DashboardCardType,
    val title: String,
    val isVisible: Boolean,
    val sortOrder: Int
)

data class CashflowHeroState(
    val selectedMonth: String = "",
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val canGoNextMonth: Boolean = false,
    val incomeCents: Cents = 0L,
    val expenseCents: Cents = 0L,
    val netCents: Cents = 0L,
    val savingsRatePercent: Double = 0.0,
    val priorMonthNetCents: Cents? = null,
    val netDeltaPercent: Double? = null
) {
    val isNetPositive: Boolean get() = netCents >= 0L
}

data class BudgetPulseState(
    val totalBudgetedCents: Cents = 0L,
    val totalSpentCents: Cents = 0L,
    val remainingCents: Cents = 0L,
    val utilizationRatio: Double = 0.0,
    val dailySafeSpendCents: Cents = 0L,
    val dailySafeSpendDetail: String = "",
    val dailySafeSpendTone: String = "neutral",
    val paceStatus: BudgetPaceStatus = BudgetPaceStatus.NO_BUDGET,
    val hasBudgets: Boolean = false,
    val topBudgets: List<CategoryBudgetOverviewItem> = emptyList()
)

data class UpcomingBillItem(
    val upcomingItemId: Long,
    val name: String,
    val merchant: String?,
    val amountCents: Cents,
    val isExpense: Boolean,
    val dueDateIso: String,
    val dueLabel: String,
    val daysUntilDue: Int,
    val isDueSoon: Boolean,
    val isOverdue: Boolean,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null
)

data class UpcomingBillsState(
    val upcomingCount: Int = 0,
    val totalUpcomingOutflowCents: Cents = 0L,
    val totalUpcomingInflowCents: Cents = 0L,
    val items: List<UpcomingBillItem> = emptyList()
)

data class DashboardRecentTransactionItem(
    val id: Long,
    val merchant: String,
    val amountCents: Cents,
    val isExpense: Boolean,
    val formattedAmount: String,
    val dateIso: String,
    val dateDisplay: String,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val accountName: String = "",
    val isPending: Boolean = false
)

data class RecentTransactionsState(
    val transactions: List<DashboardRecentTransactionItem> = emptyList()
)

data class AccountsOverviewState(
    val totalNetWorthCents: Cents = 0L,
    val totalAssetsCents: Cents = 0L,
    val totalLiabilitiesCents: Cents = 0L,
    val liquidCents: Cents = 0L,
    val investmentsCents: Cents = 0L,
    val propertyCents: Cents = 0L,
    val debtCents: Cents = 0L,
    val accountCount: Int = 0
)

data class DashboardGoalItem(
    val id: Long,
    val name: String,
    val currentAmountCents: Cents,
    val targetAmountCents: Cents,
    val progressRatio: Double,
    val remainingAmountCents: Cents,
    val isCompleted: Boolean,
    val targetDate: String? = null
)

data class GoalsProgressState(
    val activeGoalsCount: Int = 0,
    val completedGoalsCount: Int = 0,
    val totalSavedCents: Cents = 0L,
    val totalTargetCents: Cents = 0L,
    val overallProgressRatio: Double = 0.0,
    val goals: List<DashboardGoalItem> = emptyList()
)

data class DashboardCustomizationState(
    val isCustomizing: Boolean = false,
    val cardConfigs: List<DashboardCardConfigItem> = emptyList()
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedMonth: String = "",
    val cashflow: CashflowHeroState = CashflowHeroState(),
    val budgetPulse: BudgetPulseState = BudgetPulseState(),
    val upcoming: UpcomingBillsState = UpcomingBillsState(),
    val recentTransactions: RecentTransactionsState = RecentTransactionsState(),
    val accounts: AccountsOverviewState = AccountsOverviewState(),
    val goals: GoalsProgressState = GoalsProgressState(),
    val visibleCards: List<DashboardCardType> = emptyList(),
    val customization: DashboardCustomizationState = DashboardCustomizationState(),
    val syncErrorMessage: String? = null
) {
    companion object {
        fun initial(): DashboardUiState = DashboardUiState(
            isLoading = true,
            visibleCards = DashboardCardType.entries.sortedBy { it.defaultSortOrder }
        )
    }
}

sealed interface DashboardAction {
    data object Refresh : DashboardAction
    data object PreviousMonth : DashboardAction
    data object NextMonth : DashboardAction
    data object OpenCustomizeSheet : DashboardAction
    data object CloseCustomizeSheet : DashboardAction
    data class ToggleCardVisibility(val cardType: DashboardCardType) : DashboardAction
    data class MoveCardUp(val cardType: DashboardCardType) : DashboardAction
    data class MoveCardDown(val cardType: DashboardCardType) : DashboardAction
    data object ResetCardsToDefault : DashboardAction
    data class MarkBillPaid(val upcomingItemId: Long, val dueDateIso: String) : DashboardAction
}
