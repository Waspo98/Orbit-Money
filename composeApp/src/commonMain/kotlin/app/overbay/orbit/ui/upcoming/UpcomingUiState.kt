package app.overbay.orbit.ui.upcoming

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.ProjectedOccurrence
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.domain.Cents

enum class UpcomingFilterOption {
    ALL,
    BILLS,
    INCOME
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
    val editingItem: UpcomingItem? = null,
    val showEditorSheet: Boolean = false,
    val reconcilingItem: UpcomingItem? = null,
    val showReconcileDialog: Boolean = false,
    val isLoading: Boolean = false
) {
    val activeItems get() = items.filter { it.status == app.overbay.orbit.data.model.UpcomingStatus.ACTIVE }
}
