package app.overbay.orbit.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.ProjectedOccurrence
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.ProjectedAmountResult
import app.overbay.orbit.domain.UpcomingCashFlowSummary
import app.overbay.orbit.domain.UpcomingEngine
import app.overbay.orbit.domain.UpcomingScheduleHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class UpcomingViewModel(
    private val repository: OrbitRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _filterOption = MutableStateFlow(UpcomingFilter.ALL)
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

    private val computedCoreFlow = combine(
        dataFlow,
        _dismissedKeys
    ) { data, localDismissed ->
        val todayIso = LocalDateUtils.todayIsoDate()
        val categoryMap = data.categories.associateBy { it.id }
        val accountMap = data.accounts.associateBy { it.id }

        val reconciliation = UpcomingEngine.reconcileOccurrences(data.items, data.transactions, todayIso)
        val repoOccurrences = repository.snapshot.value.upcomingOccurrences
        val effectiveOccurrences = if (repoOccurrences.isNotEmpty()) repoOccurrences else reconciliation.occurrences

        val prefDismissed = repository.preferences.value.dismissedRecurringKeys
        val allDismissed = prefDismissed + localDismissed
        val existingMerchants = data.items.mapNotNull { it.merchant?.lowercase() }.toSet() + allDismissed

        val detected = UpcomingEngine.detectRecurringCandidates(
            transactions = data.transactions,
            existingMerchants = existingMerchants,
            todayDate = todayIso,
            categoryMap = categoryMap
        )

        val snapshotSuggestions = repository.snapshot.value.upcomingSuggestions.filter {
            it.merchant !in allDismissed && it.merchant.lowercase() !in allDismissed
        }
        val mergedSuggestions = (snapshotSuggestions + detected)
            .distinctBy { it.merchant.lowercase().trim() }
            .sortedByDescending { it.confidenceScore }

        val projectedAmounts = data.items.associate { item ->
            item.id to UpcomingScheduleHelper.computeProjectedAmount(item, data.transactions, todayIso)
        }

        var totalExpense = 0L
        var totalIncome = 0L
        var incomeCents = 0L
        var givingCents = 0L
        var subscriptionsCents = 0L
        var billsCents = 0L

        for (item in data.items.filter { it.status == UpcomingStatus.ACTIVE }) {
            val amount = projectedAmounts[item.id]?.amountCents ?: item.amountCents
            val absAmount = abs(amount)
            val cat = categoryMap[item.categoryId]
            val isInc = item.direction.lowercase() == "income" || item.kind == UpcomingKind.INCOME
            if (isInc) {
                totalIncome += absAmount
                incomeCents += absAmount
            } else {
                totalExpense += absAmount
                val text = "${item.name} ${item.merchant ?: ""} ${item.notes ?: ""} ${cat?.name ?: ""}".lowercase()
                val isGiving = item.kind == UpcomingKind.GIVING || UpcomingScheduleHelper.GIVING_HINTS.any { text.contains(it) }
                if (isGiving) {
                    givingCents += absAmount
                } else if (item.kind == UpcomingKind.SUBSCRIPTION) {
                    subscriptionsCents += absAmount
                } else {
                    billsCents += absAmount
                }
            }
        }

        val cashFlow = UpcomingScheduleHelper.calculateCashFlow(data.items, data.transactions, todayIso)
        val recentTxns = data.transactions
            .filter { !it.effectiveIsIgnored && !it.effectiveIsTransfer }
            .sortedByDescending { it.effectiveDate }
            .take(60)

        UpcomingComputedCore(
            data = data,
            categoryMap = categoryMap,
            accountMap = accountMap,
            effectiveOccurrences = effectiveOccurrences,
            mergedSuggestions = mergedSuggestions,
            projectedAmounts = projectedAmounts,
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            incomeCents = incomeCents,
            givingCents = givingCents,
            subscriptionsCents = subscriptionsCents,
            billsCents = billsCents,
            cashFlow = cashFlow,
            recentTxns = recentTxns
        )
    }.flowOn(computeDispatcher)

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
        computedCoreFlow,
        controlFlow,
        _inspectingItemId,
        _showReconcileDialog,
        _cashFlowPeriod,
        _showHistoryModal,
        _historyItem,
        _showAddFromTxModal
    ) { args ->
        val core = args[0] as UpcomingComputedCore
        val control = args[1] as UpcomingControlBundle
        val inspectingId = args[2] as Long?
        val showReconcile = args[3] as Boolean
        val cfPeriod = args[4] as UpcomingCashFlowPeriod
        val showHistory = args[5] as Boolean
        val histItem = args[6] as UpcomingItem?
        val showAddFromTx = args[7] as Boolean

        val data = core.data

        val filtered = data.items.filter { item ->
            val cat = core.categoryMap[item.categoryId]
            val text = "${item.name} ${item.merchant ?: ""} ${item.notes ?: ""} ${cat?.name ?: ""}".lowercase()
            val isInc = item.direction.lowercase() == "income" || item.kind == UpcomingKind.INCOME
            val isGiving = !isInc && (item.kind == UpcomingKind.GIVING || UpcomingScheduleHelper.GIVING_HINTS.any { text.contains(it) })
            val isSub = !isInc && !isGiving && item.kind == UpcomingKind.SUBSCRIPTION
            val isBill = !isInc && !isGiving && !isSub

            val matchesFilter = when (control.filter) {
                UpcomingFilter.ALL -> true
                UpcomingFilter.INCOME -> isInc
                UpcomingFilter.GIVING -> isGiving
                UpcomingFilter.SUBSCRIPTIONS -> isSub
                UpcomingFilter.BILLS -> isBill
                UpcomingFilter.SUGGESTIONS -> true
            }
            if (!matchesFilter) return@filter false
            if (control.date != null && item.nextDate != control.date) {
                return@filter false
            }
            true
        }.sortedBy { it.nextDate }

        val matchingHistory = if (histItem != null) {
            UpcomingScheduleHelper.findMatchingTransactions(histItem, data.transactions)
        } else {
            emptyList()
        }

        UpcomingUiState(
            items = filtered,
            occurrences = core.effectiveOccurrences,
            candidates = core.mergedSuggestions,
            categories = data.categories,
            accounts = data.accounts,
            categoryMap = core.categoryMap,
            accountMap = core.accountMap,
            selectedFilter = control.filter,
            selectedDateFilter = control.date,
            upcomingIncomeCents = core.incomeCents,
            upcomingGivingCents = core.givingCents,
            upcomingBillsCents = core.billsCents,
            upcomingSubscriptionsCents = core.subscriptionsCents,
            totalUpcomingExpenseCents = core.totalExpense,
            totalUpcomingIncomeCents = core.totalIncome,
            netCashflowProjectionCents = if (cfPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) {
                core.cashFlow.restOfMonthNetCents
            } else {
                core.cashFlow.next30DaysNetCents
            },
            cashFlowSummary = core.cashFlow,
            cashFlowPeriod = cfPeriod,
            projectedAmounts = core.projectedAmounts,
            inspectingItemId = inspectingId,
            editingItem = control.editing,
            showEditorSheet = control.showSheet,
            reconcilingItem = control.reconciling,
            showReconcileDialog = showReconcile,
            historyItem = histItem,
            showHistoryModal = showHistory,
            matchingHistory = matchingHistory,
            showAddFromTxModal = showAddFromTx,
            recentTransactions = core.recentTxns,
            isLoading = false
        )
    }.flowOn(computeDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UpcomingUiState(isLoading = true)
    )

    fun setFilter(filter: UpcomingFilter) {
        _filterOption.value = filter
    }

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
        viewModelScope.launch(computeDispatcher) {
            repository.saveUpcoming(item)
            closeEditorSheet()
        }
    }

    fun deleteUpcoming(id: Long) {
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
            val occ = uiState.value.occurrences.find { it.upcomingItemId == upcomingItemId } ?: return@launch
            repository.reconcileOccurrence(
                occurrenceId = occ.id,
                upcomingItemId = upcomingItemId,
                status = OccurrenceStatus.PENDING
            )
            closeReconcileDialog()
        }
    }

    fun reviewCandidate(candidate: RecurringCandidate) {
        val detectedKind = UpcomingScheduleHelper.detectKind(candidate.merchant)
        val draftItem = UpcomingItem(
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
        _editingItem.value = draftItem
        _showEditorSheet.value = true
    }

    fun acceptCandidate(candidate: RecurringCandidate) {
        viewModelScope.launch(computeDispatcher) {
            repository.acceptRecurringSuggestion(candidate)
        }
    }

    fun dismissCandidate(key: String) {
        viewModelScope.launch(computeDispatcher) {
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
        val tx = repository.transactions.value.find { it.id == transactionId } ?: return
        val isIncome = tx.effectiveAmountCents > 0L
        val kind = if (isIncome) UpcomingKind.INCOME else UpcomingScheduleHelper.detectKind(tx.effectiveMerchant)
        val draftItem = UpcomingItem(
            id = 0L,
            name = tx.effectiveMerchant,
            merchant = tx.effectiveMerchant,
            amountCents = abs(tx.effectiveAmountCents),
            kind = kind,
            direction = if (isIncome) "income" else "expense",
            accountId = tx.accountId,
            categoryId = tx.effectiveCategoryId,
            nextDate = tx.effectiveDate,
            status = UpcomingStatus.ACTIVE
        )
        _showAddFromTxModal.value = false
        _editingItem.value = draftItem
        _showEditorSheet.value = true
    }
}

private data class UpcomingDataBundle(
    val items: List<UpcomingItem>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val accounts: List<Account>
)

private data class UpcomingComputedCore(
    val data: UpcomingDataBundle,
    val categoryMap: Map<Long, Category>,
    val accountMap: Map<Long, Account>,
    val effectiveOccurrences: List<ProjectedOccurrence>,
    val mergedSuggestions: List<RecurringCandidate>,
    val projectedAmounts: Map<Long, ProjectedAmountResult>,
    val totalExpense: Long,
    val totalIncome: Long,
    val incomeCents: Long,
    val givingCents: Long,
    val subscriptionsCents: Long,
    val billsCents: Long,
    val cashFlow: UpcomingCashFlowSummary,
    val recentTxns: List<Transaction>
)

private data class UpcomingControlBundle(
    val filter: UpcomingFilter,
    val date: String?,
    val editing: UpcomingItem?,
    val showSheet: Boolean,
    val reconciling: UpcomingItem?
)
