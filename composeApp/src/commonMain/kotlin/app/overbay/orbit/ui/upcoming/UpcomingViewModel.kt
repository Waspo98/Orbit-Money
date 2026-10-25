package app.overbay.orbit.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingDirection
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.UpcomingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class UpcomingViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _filterOption = MutableStateFlow(UpcomingFilterOption.ALL)
    private val _selectedDateFilter = MutableStateFlow<String?>(null)
    private val _editingItem = MutableStateFlow<UpcomingItem?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _reconcilingItem = MutableStateFlow<UpcomingItem?>(null)
    private val _showReconcileDialog = MutableStateFlow(false)
    private val _dismissedMerchants = MutableStateFlow<Set<String>>(emptySet())

    private val dataFlow = combine(
        repository.upcomingItems,
        repository.transactions,
        repository.categories,
        repository.accounts
    ) { items, transactions, categories, accounts ->
        UpcomingDataBundle(items, transactions, categories, accounts)
    }

    private val controlFlow = combine(
        _filterOption,
        _selectedDateFilter,
        _editingItem,
        _showEditorSheet,
        _reconcilingItem
    ) { filter, date, editing, showSheet, reconciling ->
        UpcomingControlBundle(filter, date, editing, showSheet, reconciling)
    }

    val uiState: StateFlow<UpcomingUiState> = combine(
        dataFlow,
        controlFlow,
        _showReconcileDialog,
        _dismissedMerchants
    ) { data, control, showReconcile, dismissed ->
        val todayIso = LocalDateUtils.todayIsoDate()
        val categoryMap = data.categories.associateBy { it.id }
        val accountMap = data.accounts.associateBy { it.id }

        // Reconcile against posted transactions
        val reconciliation = UpcomingEngine.reconcileOccurrences(data.items, data.transactions, todayIso)

        // Detect recurring candidates from history
        val existingMerchants = data.items.mapNotNull { it.merchant?.lowercase() }.toSet() + dismissed
        val detected = UpcomingEngine.detectRecurringCandidates(
            transactions = data.transactions,
            existingMerchants = existingMerchants,
            todayDate = todayIso
        )

        // Filter items
        val filtered = data.items.filter { item ->
            // Direction filter
            val matchesFilter = when (control.filter) {
                UpcomingFilterOption.ALL -> true
                UpcomingFilterOption.BILLS -> item.direction.lowercase() != "income"
                UpcomingFilterOption.INCOME -> item.direction.lowercase() == "income"
            }
            if (!matchesFilter) return@filter false

            // Date strip filter
            if (control.date != null && item.nextDate != control.date) {
                return@filter false
            }

            true
        }.sortedBy { it.nextDate }

        // Totals for next 30 days
        var totalExpense = 0L
        var totalIncome = 0L
        for (item in data.items.filter { it.status == UpcomingStatus.ACTIVE }) {
            if (item.direction.lowercase() == "income") {
                totalIncome += abs(item.amountCents)
            } else {
                totalExpense += abs(item.amountCents)
            }
        }

        UpcomingUiState(
            items = filtered,
            occurrences = reconciliation.occurrences,
            candidates = detected,
            categories = data.categories,
            accounts = data.accounts,
            categoryMap = categoryMap,
            accountMap = accountMap,
            filterOption = control.filter,
            selectedDateFilter = control.date,
            totalUpcomingExpenseCents = totalExpense,
            totalUpcomingIncomeCents = totalIncome,
            netCashflowProjectionCents = totalIncome - totalExpense,
            editingItem = control.editing,
            showEditorSheet = control.showSheet,
            reconcilingItem = control.reconciling,
            showReconcileDialog = showReconcile,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UpcomingUiState(isLoading = true)
    )

    fun setFilterOption(option: UpcomingFilterOption) {
        _filterOption.value = option
    }

    fun setSelectedDateFilter(date: String?) {
        _selectedDateFilter.value = if (_selectedDateFilter.value == date) null else date
    }

    fun openAddUpcoming(kind: UpcomingKind = UpcomingKind.BILL) {
        val today = LocalDateUtils.todayIsoDate()
        val isIncome = kind == UpcomingKind.INCOME
        _editingItem.value = UpcomingItem(
            id = 0L,
            name = "",
            amountCents = 0L,
            kind = kind,
            direction = if (isIncome) "income" else "expense",
            nextDate = today,
            status = UpcomingStatus.ACTIVE
        )
        _showEditorSheet.value = true
    }

    fun openEditUpcoming(item: UpcomingItem) {
        _editingItem.value = item
        _showEditorSheet.value = true
    }

    fun closeEditorSheet() {
        _showEditorSheet.value = false
        _editingItem.value = null
    }

    fun saveUpcoming(item: UpcomingItem) {
        viewModelScope.launch {
            repository.saveUpcoming(item)
            closeEditorSheet()
        }
    }

    fun deleteUpcoming(id: Long) {
        viewModelScope.launch {
            repository.deleteUpcoming(id)
            closeEditorSheet()
        }
    }

    fun openReconcileDialog(item: UpcomingItem) {
        _reconcilingItem.value = item
        _showReconcileDialog.value = true
    }

    fun closeReconcileDialog() {
        _showReconcileDialog.value = false
        _reconcilingItem.value = null
    }

    fun reconcileItemPaid(upcomingItemId: Long, occurrenceDate: String) {
        viewModelScope.launch {
            val item = repository.upcomingItems.value.find { it.id == upcomingItemId } ?: return@launch
            val isExpense = item.direction.lowercase() != "income"
            val signedAmount = if (isExpense) -abs(item.amountCents) else abs(item.amountCents)

            val paidTxn = Transaction(
                accountId = item.accountId ?: repository.preferences.value.defaultAccountId ?: 1L,
                originalDate = occurrenceDate,
                originalMerchant = item.merchant ?: item.name,
                originalAmountCents = signedAmount,
                originalCategoryId = item.categoryId,
                source = "upcoming_reconcile",
                editSource = EditSource.SYSTEM
            )
            repository.saveTransaction(paidTxn)

            val nextDate = UpcomingEngine.advanceInterval(item, occurrenceDate)
            repository.saveUpcoming(item.copy(nextDate = nextDate))
            closeReconcileDialog()
        }
    }

    fun acceptCandidate(candidate: RecurringCandidate) {
        viewModelScope.launch {
            val newItem = UpcomingItem(
                id = 0L,
                name = candidate.merchant,
                merchant = candidate.merchant,
                amountCents = candidate.amountCents,
                kind = UpcomingKind.BILL,
                direction = "expense",
                frequencyType = candidate.frequency,
                nextDate = candidate.nextProjectedDate,
                categoryId = candidate.suggestedCategoryId,
                accountId = candidate.suggestedAccountId,
                status = UpcomingStatus.ACTIVE
            )
            repository.saveUpcoming(newItem)
        }
    }

    fun dismissCandidate(merchant: String) {
        _dismissedMerchants.value = _dismissedMerchants.value + merchant.lowercase()
    }
}

private data class UpcomingDataBundle(
    val items: List<UpcomingItem>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val accounts: List<Account>
)

private data class UpcomingControlBundle(
    val filter: UpcomingFilterOption,
    val date: String?,
    val editing: UpcomingItem?,
    val showSheet: Boolean,
    val reconciling: UpcomingItem?
)
