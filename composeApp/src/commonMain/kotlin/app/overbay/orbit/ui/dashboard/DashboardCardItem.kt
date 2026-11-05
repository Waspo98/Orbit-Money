package app.overbay.orbit.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.dashboard.cards.AccountsOverviewCard
import app.overbay.orbit.ui.dashboard.cards.BiggestTransactionsCard
import app.overbay.orbit.ui.dashboard.cards.BudgetPulseCard
import app.overbay.orbit.ui.dashboard.cards.CategorizeRecentCard
import app.overbay.orbit.ui.dashboard.cards.GoalFocusCard
import app.overbay.orbit.ui.dashboard.cards.GoalsProgressCard
import app.overbay.orbit.ui.dashboard.cards.MhaSummaryCard
import app.overbay.orbit.ui.dashboard.cards.MonthComparisonCard
import app.overbay.orbit.ui.dashboard.cards.MortgageSnapshotCard
import app.overbay.orbit.ui.dashboard.cards.NetCashflowHeroCard
import app.overbay.orbit.ui.dashboard.cards.RecentTransactionsCard
import app.overbay.orbit.ui.dashboard.cards.RetirementSnapshotCard
import app.overbay.orbit.ui.dashboard.cards.SubscriptionsCard
import app.overbay.orbit.ui.dashboard.cards.TopSpendingCard
import app.overbay.orbit.ui.dashboard.cards.UncategorizedCard
import app.overbay.orbit.ui.dashboard.cards.UpcomingBillsCard

data class DashboardGridRow(
    val leftCard: DashboardCardType,
    val rightCard: DashboardCardType? = null,
    val isFullSpan: Boolean = false
)

fun buildDashboardGridRows(visibleConfigs: List<DashboardCardConfigItem>): List<DashboardGridRow> {
    val rows = mutableListOf<DashboardGridRow>()
    var pendingLeft: DashboardCardType? = null

    for (config in visibleConfigs) {
        if (config.wideSpan == 2) {
            if (pendingLeft != null) {
                rows.add(DashboardGridRow(leftCard = pendingLeft, rightCard = null, isFullSpan = false))
                pendingLeft = null
            }
            rows.add(DashboardGridRow(leftCard = config.type, rightCard = null, isFullSpan = true))
        } else {
            if (pendingLeft == null) {
                pendingLeft = config.type
            } else {
                rows.add(DashboardGridRow(leftCard = pendingLeft, rightCard = config.type, isFullSpan = false))
                pendingLeft = null
            }
        }
    }

    if (pendingLeft != null) {
        rows.add(DashboardGridRow(leftCard = pendingLeft, rightCard = null, isFullSpan = false))
    }

    return rows
}

@Composable
fun DashboardCardItem(
    cardType: DashboardCardType,
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    when (cardType) {
        DashboardCardType.ACCOUNTS_OVERVIEW -> AccountsOverviewCard(
            accounts = uiState.accounts,
            onClick = { onNavigate(OrbitNavKey.Accounts()) },
            modifier = modifier
        )
        DashboardCardType.BUDGET_PULSE -> BudgetPulseCard(
            summary = uiState.budgetPulse,
            onClick = { onNavigate(OrbitNavKey.Budgets) },
            modifier = modifier
        )
        DashboardCardType.TOP_SPENDING -> TopSpendingCard(
            state = uiState.topSpending,
            onClick = { onNavigate(OrbitNavKey.SpendingTrends) },
            onCategoryClick = { catId -> onNavigate(OrbitNavKey.Transactions(categoryId = catId)) },
            modifier = modifier
        )
        DashboardCardType.CASHFLOW_HERO -> NetCashflowHeroCard(
            cashflow = uiState.cashflow,
            onClick = { onNavigate(OrbitNavKey.SpendingTrends) },
            modifier = modifier
        )
        DashboardCardType.BIGGEST_TRANSACTIONS -> {
            val month = uiState.selectedMonth.ifBlank { LocalDateUtils.currentYearMonth() }
            val (y, m) = LocalDateUtils.parseYearMonth(month) ?: Pair(2026, 1)
            val days = LocalDateUtils.daysInMonth(y, m)
            val monthStart = "$month-01"
            val monthEnd = "$month-${days.toString().padStart(2, '0')}"
            BiggestTransactionsCard(
                state = uiState.biggestTransactions,
                onClick = {
                    onNavigate(
                        OrbitNavKey.Transactions(
                            startDate = monthStart,
                            endDate = monthEnd,
                            sortOption = "ABS_AMOUNT_DESC"
                        )
                    )
                },
                onHideTransaction = { onAction(DashboardAction.HideBiggestTransaction(it)) },
                onResetHidden = { onAction(DashboardAction.ResetHiddenBiggestTransactions) },
                modifier = modifier
            )
        }
        DashboardCardType.SUBSCRIPTIONS -> SubscriptionsCard(
            state = uiState.subscriptions,
            onClick = { onNavigate(OrbitNavKey.Upcoming) },
            modifier = modifier
        )
        DashboardCardType.UNCATEGORIZED -> UncategorizedCard(
            state = uiState.uncategorized,
            onClick = { onNavigate(OrbitNavKey.Transactions(uncategorizedOnly = true)) },
            modifier = modifier
        )
        DashboardCardType.CATEGORIZE_RECENT -> CategorizeRecentCard(
            onOpenReview = { onAction(DashboardAction.OpenCategorizeReview) },
            modifier = modifier
        )
        DashboardCardType.MONTH_COMPARISON -> MonthComparisonCard(
            state = uiState.monthComparison,
            onClick = { onNavigate(OrbitNavKey.SpendingTrends) },
            modifier = modifier
        )
        DashboardCardType.GOALS_PROGRESS -> GoalsProgressCard(
            state = uiState.goals,
            onClick = { onNavigate(OrbitNavKey.Goals) },
            modifier = modifier
        )
        DashboardCardType.GOAL_FOCUS -> GoalFocusCard(
            state = uiState.goalFocus,
            onSelectGoal = { onAction(DashboardAction.SetGoalFocus(it)) },
            onClick = { onNavigate(OrbitNavKey.Goals) },
            modifier = modifier
        )
        DashboardCardType.RETIREMENT_SNAPSHOT -> RetirementSnapshotCard(
            state = uiState.retirement,
            onChangeAge = { onAction(DashboardAction.SetRetirementAge(it)) },
            onChangePreset = { onAction(DashboardAction.SetRetirementPreset(it)) },
            onClick = { onNavigate(OrbitNavKey.RetirementCalculator) },
            modifier = modifier
        )
        DashboardCardType.MHA_SUMMARY -> MhaSummaryCard(
            state = uiState.mha,
            onClick = { onNavigate(OrbitNavKey.MhaTracker) },
            modifier = modifier
        )
        DashboardCardType.UPCOMING_BILLS -> UpcomingBillsCard(
            bills = uiState.upcoming.items,
            onReconcile = { id, date -> onAction(DashboardAction.MarkBillPaid(id, date)) },
            onViewAll = { onNavigate(OrbitNavKey.Upcoming) },
            modifier = modifier
        )
        DashboardCardType.MORTGAGE_SNAPSHOT -> MortgageSnapshotCard(
            state = uiState.mortgage,
            onClick = { onNavigate(OrbitNavKey.Accounts(accountId = uiState.mortgage.mortgageAccountId)) },
            modifier = modifier
        )
        DashboardCardType.RECENT_TRANSACTIONS -> RecentTransactionsCard(
            transactions = uiState.recentTransactions.transactions,
            mhaEnabled = uiState.mhaEnabled,
            categories = uiState.availableCategories,
            onSelectTransaction = { onNavigate(OrbitNavKey.Transactions(transactionId = it)) },
            onViewAll = { onNavigate(OrbitNavKey.Transactions()) },
            onQuickCategorize = { id, catId -> onAction(DashboardAction.ApplyCategory(id, catId)) },
            onToggleTransfer = { id, isTr -> onAction(DashboardAction.ToggleTransactionTransfer(id, isTr)) },
            onToggleIgnore = { id, isIgn -> onAction(DashboardAction.ToggleTransactionIgnored(id, isIgn)) },
            onToggleMha = { id, isMha -> onAction(DashboardAction.ToggleTransactionMha(id, isMha)) },
            onMarkRecurring = { id -> onAction(DashboardAction.MarkTransactionRecurring(id)) },
            onResetEdits = { id -> onAction(DashboardAction.ResetTransactionEdits(id)) },
            onDelete = { id -> onAction(DashboardAction.DeleteTransaction(id)) },
            modifier = modifier
        )
    }
}
