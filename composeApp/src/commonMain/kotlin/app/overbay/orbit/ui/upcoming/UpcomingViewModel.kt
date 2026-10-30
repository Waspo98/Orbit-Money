package app.overbay.orbit.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.ProjectedOccurrence
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MatchedTransactionHistory
import app.overbay.orbit.domain.ProjectedAmountResult
import app.overbay.orbit.domain.UpcomingCashFlowSummary
import app.overbay.orbit.domain.UpcomingEngine
import app.overbay.orbit.domain.UpcomingScheduleHelper
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
    private val _inspectingItemId = MutableStateFlow<Long?>(null)
    private val _editingItem = MutableStateFlow<UpcomingItem?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _reconcilingItem = MutableStateFlow<UpcomingItem?>(null)
    private val _showReconcileDialog = MutableStateFlow(false)
    private val _dismissedKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _cashFlowPeriod = MutableStateFlow(UpcomingCashFlowPeriod.REST_OF_MONTH)
    private val _showHistoryModal = MutableStateFlow(false)
    private val _historyItem = MutableStateFlow<UpcomingItem?>(null)
    private val _showAddFromTxModal = MutableStateFlow(false)

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
        _inspectingItemId,
        _showReconcileDialog,
        _dismissedKeys,
        _cashFlowPeriod,
        _showHistoryModal,
        _historyItem,
        _showAddFromTxModal
    ) { args ->
        val data = args[0] as UpcomingDataBundle
        val control = args[1] as UpcomingControlBundle
        val inspectingId = args[2] as Long?
        val showReconcile = args[3] as Boolean
        val localDismissed = args[4] as Set<String>
        val cfPeriod = args[5] as UpcomingCashFlowPeriod
        val showHistory = args[6] as Boolean
        val histItem = args[7] as UpcomingItem?
        val showAddFromTx = args[8] as Boolean

        val todayIso = LocalDateUtils.todayIsoDate()
        val categoryMap = data.categories.associateBy { it.id }
        val accountMap = data.accounts.associateBy { it.id }

        // Reconcile occurrences against posted transactions
        val reconciliation = UpcomingEngine.reconcileOccurrences(data.items, data.transactions, todayIso)
        val repoOccurrences = repository.snapshot.value.upcomingOccurrences
        val effectiveOccurrences = if (repoOccurrences.isNotEmpty()) repoOccurrences else reconciliation.occurrences

        // Suggestions: merge snapshot suggestions with detected history candidates
        val prefDismissed = repository.preferences.value.dismissedRecurringKeys
        val allDismissed = prefDismissed + localDismissed
        val existingMerchants = data.items.mapNotNull { it.merchant?.lowercase() }.toSet() + allDismissed

        val detected = UpcomingEngine.detectRecurringCandidates(
            transactions = data.transactions,
            existingMerchants = existingMerchants,
            todayDate = todayIso
        )

        val snapshotSuggestions = repository.snapshot.value.upcomingSuggestions.filter {
            it.merchant !in allDismissed && it.merchant.lowercase() !in allDismissed
        }
        val mergedSuggestions = (snapshotSuggestions + detected)
            .distinctBy { it.merchant.lowercase().trim() }
            .sortedByDescending { it.confidenceScore }

        // Filter upcoming items
        val filtered = data.items.filter { item ->
            val matchesFilter = when (control.filter) {
                UpcomingFilterOption.ALL -> true
                UpcomingFilterOption.BILLS -> item.direction.lowercase() != "income"
                UpcomingFilterOption.INCOME -> item.direction.lowercase() == "income"
            }
            if (!matchesFilter) return@filter false
            if (control.date != null && item.nextDate != control.date) {
                return@filter false
            }
            true
        }.sortedBy { it.nextDate }

        // Projected variable amounts per item
        val projectedAmounts = data.items.associate { item ->
            item.id to UpcomingScheduleHelper.computeProjectedAmount(item, data.transactions, todayIso)
        }

        // Totals for next 30 days
        var totalExpense = 0L
        var totalIncome = 0L
        for (item in data.items.filter { it.status == UpcomingStatus.ACTIVE }) {
            val amount = projectedAmounts[item.id]?.amountCents ?: item.amountCents
            if (item.direction.lowercase() == "income") {
                totalIncome += abs(amount)
            } else {
                totalExpense += abs(amount)
            }
        }

        // Cash flow summary calculation
        val cashFlow = UpcomingScheduleHelper.calculateCashFlow(data.items, data.transactions, todayIso)

        // Matching transaction history for active history item
        val matchingHistory = if (histItem != null) {
            UpcomingScheduleHelper.findMatchingTransactions(histItem, data.transactions)
        } else {
            emptyList()
        }

        val recentTxns = data.transactions
            .filter { !it.effectiveIsIgnored && !it.effectiveIsTransfer }
            .sortedByDescending { it.effectiveDate }
            .take(60)

        UpcomingUiState(
            items = filtered,
            occurrences = effectiveOccurrences,
            candidates = mergedSuggestions,
            categories = data.categories,
            accounts = data.accounts,
            categoryMap = categoryMap,
            accountMap = accountMap,
            filterOption = control.filter,
            selectedDateFilter = control.date,
            totalUpcomingExpenseCents = totalExpense,
            totalUpcomingIncomeCents = totalIncome,
            netCashflowProjectionCents = totalIncome - totalExpense,
            cashFlowSummary = cashFlow,
            cashFlowPeriod = cfPeriod,
            projectedAmounts = projectedAmounts,
            inspectingItemId = inspectingId,
            editingItem = control.editing,
            showEditorSheet = control.showSheet,
            reconcilingItem = control.reconciling,
            showReconcileDialog = showReconcile,
            historyItem = histItem,
            showHistoryModal = showHistory,
            matchingHistory = matchingHistory,
            showAddFromTxModal = showAddFromTx,
            recentTransactions = recentTxns,
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

    fun setCashFlowPeriod(period: UpcomingCashFlowPeriod) {
        _cashFlowPeriod.value = period
    }

    fun selectUpcomingForInspect(id: Long?) {
        if (id != null && _inspectingItemId.value == id && !_showEditorSheet.value) {
            _inspectingItemId.value = null
        } else {
            _inspectingItemId.value = id
            _showEditorSheet.value = false
            _editingItem.value = null
        }
    }

    fun dismissInspectorPane() {
        _inspectingItemId.value = null
        _showEditorSheet.value = false
        _editingItem.value = null
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
        _inspectingItemId.value = item.id
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
            if (_inspectingItemId.value == id) {
                _inspectingItemId.value = null
            }
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
        reconcileOccurrencePaid(upcomingItemId, occurrenceDate, null)
    }

    fun reconcileOccurrencePaid(
        upcomingItemId: Long,
        occurrenceDate: String,
        matchedTxnId: Long? = null
    ) {
        viewModelScope.launch {
            val item = repository.upcomingItems.value.find { it.id == upcomingItemId } ?: return@launch
            val nextDate = UpcomingScheduleHelper.advanceRecurrence(item, occurrenceDate)

            if (matchedTxnId == null) {
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
            }

            val occ = uiState.value.occurrences.find { it.upcomingItemId == upcomingItemId }
            val occId = occ?.id ?: 0L

            repository.reconcileOccurrence(
                occurrenceId = occId,
                upcomingItemId = upcomingItemId,
                status = OccurrenceStatus.MATCHED,
                matchedTransactionId = matchedTxnId,
                advanceNextDateTo = nextDate
            )
            repository.saveUpcoming(item.copy(nextDate = nextDate))
            closeReconcileDialog()
        }
    }

    fun reconcileOccurrenceMissed(upcomingItemId: Long, occurrenceDate: String) {
        viewModelScope.launch {
            val item = repository.upcomingItems.value.find { it.id == upcomingItemId } ?: return@launch
            val nextDate = UpcomingScheduleHelper.advanceRecurrence(item, occurrenceDate)
            val occ = uiState.value.occurrences.find { it.upcomingItemId == upcomingItemId }
            val occId = occ?.id ?: 0L

            repository.reconcileOccurrence(
                occurrenceId = occId,
                upcomingItemId = upcomingItemId,
                status = OccurrenceStatus.MISSED,
                advanceNextDateTo = nextDate
            )
            repository.saveUpcoming(item.copy(nextDate = nextDate))
            closeReconcileDialog()
        }
    }

    fun reconcileOccurrenceReset(upcomingItemId: Long) {
        viewModelScope.launch {
            val occ = uiState.value.occurrences.find { it.upcomingItemId == upcomingItemId } ?: return@launch
            repository.reconcileOccurrence(
                occurrenceId = occ.id,
                upcomingItemId = upcomingItemId,
                status = OccurrenceStatus.PENDING
            )
            closeReconcileDialog()
        }
    }

    fun acceptCandidate(candidate: RecurringCandidate) {
        viewModelScope.launch {
            val res = repository.acceptRecurringSuggestion(candidate)
            if (res.isFailure) {
                val detectedKind = UpcomingScheduleHelper.detectKind(candidate.merchant)
                val newItem = UpcomingItem(
                    id = 0L,
                    name = candidate.merchant,
                    merchant = candidate.merchant,
                    amountCents = candidate.amountCents,
                    kind = detectedKind,
                    direction = if (detectedKind == UpcomingKind.INCOME) "income" else "expense",
                    frequencyType = candidate.frequency,
                    nextDate = candidate.nextProjectedDate,
                    categoryId = candidate.suggestedCategoryId,
                    accountId = candidate.suggestedAccountId,
                    status = UpcomingStatus.ACTIVE
                )
                repository.saveUpcoming(newItem)
            }
        }
    }

    fun dismissCandidate(key: String) {
        viewModelScope.launch {
            repository.dismissRecurringSuggestion(key)
            _dismissedKeys.value = _dismissedKeys.value + key.lowercase().trim()
        }
    }

    fun openHistoryModal(item: UpcomingItem) {
        _historyItem.value = item
        _showHistoryModal.value = true
    }

    fun closeHistoryModal() {
        _showHistoryModal.value = false
        _historyItem.value = null
    }

    fun openAddFromTxModal() {
        _showAddFromTxModal.value = true
    }

    fun closeAddFromTxModal() {
        _showAddFromTxModal.value = false
    }

    fun createFromTransaction(transactionId: Long) {
        viewModelScope.launch {
            val res = repository.createUpcomingFromTransaction(transactionId)
            res.onSuccess { item ->
                _showAddFromTxModal.value = false
                _editingItem.value = item
                _showEditorSheet.value = true
            }
        }
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
