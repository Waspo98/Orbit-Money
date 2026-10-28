package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.ReviewQueueCandidate

/**
 * Enumeration of all 16 customizable dashboard cards from original Orbit Money.
 * Maps directly to [app.overbay.orbit.data.model.DashboardWidgetConfig].
 */
enum class DashboardCardType(
    val widgetId: String,
    val defaultTitle: String,
    val defaultDescription: String,
    val defaultSortOrder: Int,
    val defaultWideSpan: Int = 1
) {
    ACCOUNTS_OVERVIEW("accounts", "Accounts", "Net worth and account balance groups.", 0),
    BUDGET_PULSE("budget_pulse", "Budget Pulse", "Budget usage, pace, and categories to watch.", 1),
    TOP_SPENDING("top_spending", "Top Spending", "Largest spending categories this month.", 2),
    CASHFLOW_HERO("this_month", "This Month", "Income, expenses, and net cash flow.", 3),
    BIGGEST_TRANSACTIONS("biggest_transactions", "Biggest Monthly Transactions", "Top 5 current-month expense transactions with dashboard-only hiding.", 4),
    SUBSCRIPTIONS("subscriptions", "Subscriptions / Recurring", "Saved subscription items from Upcoming.", 5),
    UNCATEGORIZED("uncategorized", "Uncategorized Transactions", "Recent transactions that need a category.", 6),
    CATEGORIZE_RECENT("categorize_recent", "Categorize Recent Transactions", "Swipe through transactions that need a quick category check.", 7),
    MONTH_COMPARISON("month_comparison", "Month vs Last Month", "Spending, income, and net change.", 8),
    GOALS_PROGRESS("goals_progress", "Goals Progress", "Overall progress across active goals.", 9),
    GOAL_FOCUS("goal_focus", "Goal Focus", "One goal that deserves attention.", 10),
    RETIREMENT_SNAPSHOT("retirement", "Retirement Snapshot", "Linked retirement balance and contributions.", 11),
    MHA_SUMMARY("mha", "MHA Tracker Summary", "Year-to-date MHA eligible spending.", 12),
    UPCOMING_BILLS("upcoming_bills", "Upcoming: Bills and Income", "Saved bills, subscriptions, and income.", 13),
    MORTGAGE_SNAPSHOT("mortgage", "Mortgage Snapshot", "Home value, balance, and equity.", 14),
    RECENT_TRANSACTIONS("recent_transactions", "Recent Transactions", "Latest activity with full transaction actions.", 15, defaultWideSpan = 2);

    companion object {
        fun fromWidgetId(widgetId: String): DashboardCardType? {
            val normalized = widgetId.lowercase().replace("-", "_")
            return entries.firstOrNull { entry ->
                entry.widgetId.equals(widgetId, ignoreCase = true) ||
                entry.widgetId.replace("-", "_").equals(normalized, ignoreCase = true) ||
                when (normalized) {
                    "accounts", "accounts_overview" -> entry == ACCOUNTS_OVERVIEW
                    "budget_pulse" -> entry == BUDGET_PULSE
                    "top_spending" -> entry == TOP_SPENDING
                    "this_month", "cashflow_hero" -> entry == CASHFLOW_HERO
                    "biggest_transactions" -> entry == BIGGEST_TRANSACTIONS
                    "subscriptions", "recurring" -> entry == SUBSCRIPTIONS
                    "uncategorized" -> entry == UNCATEGORIZED
                    "categorize_recent" -> entry == CATEGORIZE_RECENT
                    "month_comparison" -> entry == MONTH_COMPARISON
                    "goals_progress" -> entry == GOALS_PROGRESS
                    "goal_focus" -> entry == GOAL_FOCUS
                    "retirement", "retirement_snapshot" -> entry == RETIREMENT_SNAPSHOT
                    "mha", "mha_summary" -> entry == MHA_SUMMARY
                    "upcoming", "upcoming_bills" -> entry == UPCOMING_BILLS
                    "mortgage", "mortgage_snapshot" -> entry == MORTGAGE_SNAPSHOT
                    "recent_activity", "recent_transactions" -> entry == RECENT_TRANSACTIONS
                    else -> false
                }
            }
        }
    }
}

data class DashboardCardConfigItem(
    val type: DashboardCardType,
    val title: String,
    val description: String,
    val isVisible: Boolean,
    val sortOrder: Int,
    val wideSpan: Int = 1
)

data class CashflowHeroState(
    val selectedMonth: String = "",
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val canGoNextMonth: Boolean = false,
    val dayOfMonth: Int = 1,
    val totalDaysInMonth: Int = 30,
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
    val percentUsed: Int = 0,
    val dailySafeSpendCents: Cents = 0L,
    val dailySafeSpendDetail: String = "",
    val dailySafeSpendTone: String = "neutral",
    val daysRemaining: Int = 0,
    val isOverBudget: Boolean = false,
    val overBudgetCents: Cents = 0L,
    val paceStatus: BudgetPaceStatus = BudgetPaceStatus.NO_BUDGET,
    val hasBudgets: Boolean = false,
    val topBudgets: List<CategoryBudgetOverviewItem> = emptyList(),
    val attentionCategories: List<CategoryBudgetOverviewItem> = emptyList()
)

data class TopSpendingCategoryItem(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String?,
    val categoryIcon: String?,
    val spentCents: Cents,
    val percentOfTotal: Double
)

data class TopSpendingState(
    val totalSpentCents: Cents = 0L,
    val categories: List<TopSpendingCategoryItem> = emptyList()
)

data class BiggestTransactionItem(
    val id: Long,
    val merchant: String,
    val amountCents: Cents,
    val formattedAmount: String,
    val dateDisplay: String,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val accountName: String = ""
)

data class BiggestTransactionsState(
    val transactions: List<BiggestTransactionItem> = emptyList(),
    val hiddenCount: Int = 0
)

data class SubscriptionItem(
    val id: Long,
    val name: String,
    val merchant: String?,
    val amountCents: Cents,
    val frequencyLabel: String,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val nextDateDisplay: String = ""
)

data class SubscriptionsState(
    val items: List<SubscriptionItem> = emptyList()
)

data class UncategorizedTransactionsState(
    val count: Int = 0,
    val transactions: List<DashboardRecentTransactionItem> = emptyList()
)

data class MonthComparisonRow(
    val metric: String,
    val currentCents: Cents,
    val previousCents: Cents,
    val deltaCents: Cents,
    val isPositiveImprovement: Boolean
)

data class MonthComparisonState(
    val currentMonthLabel: String = "",
    val previousMonthLabel: String = "",
    val rows: List<MonthComparisonRow> = emptyList()
)

data class GoalFocusState(
    val focusedGoal: DashboardGoalItem? = null,
    val availableGoals: List<DashboardGoalItem> = emptyList()
)

data class RetirementSnapshotState(
    val currentBalanceCents: Cents = 0L,
    val monthlyContributionCents: Cents = 0L,
    val projectedAtRetirementCents: Cents = 0L,
    val targetAge: Int = 67,
    val presetKey: String = "balanced"
)

data class MhaSummaryState(
    val eligibleSpendingCents: Cents = 0L,
    val estimatedTaxSavingsCents: Cents = 0L,
    val ytdTransactionCount: Int = 0
)

data class MortgageSnapshotState(
    val hasMortgage: Boolean = false,
    val mortgageAccountId: Long? = null,
    val estimatedHomeValueCents: Cents = 0L,
    val mortgageBalanceCents: Cents = 0L,
    val estimatedEquityCents: Cents = 0L,
    val equityPercent: Double = 0.0
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
    val isPending: Boolean = false,
    val merchantLogoUrl: String? = null
)

data class RecentTransactionsState(
    val transactions: List<DashboardRecentTransactionItem> = emptyList()
)

data class AccountsOverviewState(
    val totalNetWorthCents: Cents = 0L,
    val totalAssetsCents: Cents = 0L,
    val totalLiabilitiesCents: Cents = 0L,
    val cashCents: Cents = 0L,
    val investmentsCents: Cents = 0L,
    val creditCardsOwedCents: Cents = 0L,
    val loansOwedCents: Cents = 0L,
    val realEstateEquityCents: Cents = 0L,
    val otherCents: Cents = 0L,
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
    val targetDate: String? = null,
    val icon: String = "🎯"
) {
    val labelWithEmoji: String
        get() = "$icon $name"
}

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

data class CategorizeReviewState(
    val isOpen: Boolean = false,
    val funMode: Boolean = false,
    val items: List<ReviewQueueCandidate> = emptyList(),
    val currentIndex: Int = 0,
    val batchReviewed: Int = 0,
    val allDone: Boolean = false,
    val isCategoryPickerOpen: Boolean = false,
    val pendingConfirmCategoryId: Long? = null,
    val ruleDraftTransaction: ReviewQueueCandidate? = null,
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val allTransactions: List<Transaction> = emptyList()
) {
    val currentTxn: ReviewQueueCandidate? get() = items.getOrNull(currentIndex)
    val isBatchComplete: Boolean get() = batchReviewed >= 10 || (currentTxn == null && items.isNotEmpty())
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dateLabel: String = "",
    val greeting: String = "",
    val userName: String = "",
    val dayOfMonth: Int = 1,
    val totalDaysInMonth: Int = 30,
    val daysRemaining: Int = 0,
    val selectedMonth: String = "",
    val mhaEnabled: Boolean = false,
    val isReviewModalOpen: Boolean = false,
    val categorizeReview: CategorizeReviewState = CategorizeReviewState(),
    val cashflow: CashflowHeroState = CashflowHeroState(),
    val budgetPulse: BudgetPulseState = BudgetPulseState(),
    val topSpending: TopSpendingState = TopSpendingState(),
    val biggestTransactions: BiggestTransactionsState = BiggestTransactionsState(),
    val subscriptions: SubscriptionsState = SubscriptionsState(),
    val uncategorized: UncategorizedTransactionsState = UncategorizedTransactionsState(),
    val monthComparison: MonthComparisonState = MonthComparisonState(),
    val goals: GoalsProgressState = GoalsProgressState(),
    val goalFocus: GoalFocusState = GoalFocusState(),
    val retirement: RetirementSnapshotState = RetirementSnapshotState(),
    val mha: MhaSummaryState = MhaSummaryState(),
    val upcoming: UpcomingBillsState = UpcomingBillsState(),
    val mortgage: MortgageSnapshotState = MortgageSnapshotState(),
    val recentTransactions: RecentTransactionsState = RecentTransactionsState(),
    val accounts: AccountsOverviewState = AccountsOverviewState(),
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
    data class SetCardWideSpan(val cardType: DashboardCardType, val wideSpan: Int) : DashboardAction
    data class MoveCard(val fromIndex: Int, val toIndex: Int) : DashboardAction
    data object ResetCardsToDefault : DashboardAction
    data class MarkBillPaid(val upcomingItemId: Long, val dueDateIso: String) : DashboardAction
    data class HideBiggestTransaction(val transactionId: Long) : DashboardAction
    data object ResetHiddenBiggestTransactions : DashboardAction
    data class SetGoalFocus(val goalId: Long) : DashboardAction
    data class SetRetirementAge(val age: Int) : DashboardAction
    data class SetRetirementPreset(val presetKey: String) : DashboardAction
    data object OpenCategorizeReview : DashboardAction
    data object CloseCategorizeReview : DashboardAction
    data class ApplyCategory(val transactionId: Long, val categoryId: Long) : DashboardAction
    data object ApproveReviewTransaction : DashboardAction
    data object RejectReviewTransaction : DashboardAction
    data object CloseReviewCategoryPicker : DashboardAction
    data class RequestConfirmReviewCategory(val categoryId: Long) : DashboardAction
    data object CancelConfirmReviewCategory : DashboardAction
    data object ConfirmReviewCategory : DashboardAction
    data object SkipReviewTransaction : DashboardAction
    data object GoBackReviewTransaction : DashboardAction
    data object LoadMoreReviewTransactions : DashboardAction
    data object StartFunReviewMode : DashboardAction
    data object OpenReviewRuleCreator : DashboardAction
    data object CloseReviewRuleCreator : DashboardAction
    data class SaveReviewRule(val rule: TransactionRule) : DashboardAction
}
