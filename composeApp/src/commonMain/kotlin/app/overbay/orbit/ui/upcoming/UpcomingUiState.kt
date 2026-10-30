package app.overbay.orbit.ui.upcoming

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.ProjectedOccurrence
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MatchedTransactionHistory
import app.overbay.orbit.domain.ProjectedAmountResult
import app.overbay.orbit.domain.UpcomingCashFlowSummary

enum class UpcomingFilterOption {
    ALL,
    BILLS,
    INCOME
}

enum class UpcomingCashFlowPeriod {
    REST_OF_MONTH,
    NEXT_30_DAYS
}

data class UpcomingUiState(
    val items: List<UpcomingItem> = emptyList(),
    val occurrences: List<ProjectedOccurrence> = emptyList(),
    val candidates: List<RecurringCandidate> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categoryMap: Map<Long, Category> = emptyMap(),
    val accountMap: Map<Long, Account> = emptyMap(),
    val filterOption: UpcomingFilterOption = UpcomingFilterOption.ALL,
    val selectedDateFilter: String? = null,
    val totalUpcomingExpenseCents: Cents = 0L,
    val totalUpcomingIncomeCents: Cents = 0L,
    val netCashflowProjectionCents: Cents = 0L,
    val cashFlowSummary: UpcomingCashFlowSummary = UpcomingCashFlowSummary(),
    val cashFlowPeriod: UpcomingCashFlowPeriod = UpcomingCashFlowPeriod.REST_OF_MONTH,
    val projectedAmounts: Map<Long, ProjectedAmountResult> = emptyMap(),
    val inspectingItemId: Long? = null,
    val editingItem: UpcomingItem? = null,
    val showEditorSheet: Boolean = false,
    val reconcilingItem: UpcomingItem? = null,
    val showReconcileDialog: Boolean = false,
    val historyItem: UpcomingItem? = null,
    val showHistoryModal: Boolean = false,
    val matchingHistory: List<MatchedTransactionHistory> = emptyList(),
    val showAddFromTxModal: Boolean = false,
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false
) {
    val activeItems get() = items.filter { it.status == UpcomingStatus.ACTIVE }
    val inspectingItem: UpcomingItem?
        get() = items.find { it.id == inspectingItemId } ?: editingItem
}
