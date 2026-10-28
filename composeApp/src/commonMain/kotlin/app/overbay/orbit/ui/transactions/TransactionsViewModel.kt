package app.overbay.orbit.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.TransactionFilterDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class TransactionsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _quickFilter = MutableStateFlow(QuickFilter.ALL)
    private val _filterDraft = MutableStateFlow(TransactionFilterDraft())
    private val _sortOption = MutableStateFlow(TransactionSortOption.DATE_DESC)
    private val _isCompactView = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _inspectingId = MutableStateFlow<Long?>(null)
    private val _expandedId = MutableStateFlow<Long?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _showSplitSheet = MutableStateFlow(false)
    private val _showCsvImportSheet = MutableStateFlow(false)
    private val _showFilterSheet = MutableStateFlow(false)
    private val _showBatchDeleteConfirm = MutableStateFlow(false)
    private val _singleDeleteConfirmId = MutableStateFlow<Long?>(null)
    private val _showCategoryBatchPicker = MutableStateFlow(false)
    private val _logoFinderTransactionId = MutableStateFlow<Long?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncToastMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repository.transactions,
        repository.categories,
        repository.accounts
    ) { txns, categories, accounts ->
        Triple(txns, categories, accounts)
    }

    private val filterFlow = combine(
        _searchQuery,
        _quickFilter,
        _filterDraft,
        _sortOption,
        _isCompactView
    ) { query, quick, draft, sort, compact ->
        FilterParams(query, quick, draft, sort, compact)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        dataFlow,
        filterFlow
    ) { (txns, categories, accounts), filterParams ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val filteredRaw = filterTransactions(
            transactions = txns,
            query = filterParams.query,
            quick = filterParams.quick,
            draft = filterParams.draft,
            categoryMap = categoryMap
        )

        val sorted = when (filterParams.sort) {
            TransactionSortOption.DATE_DESC -> filteredRaw.sortedWith(compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id })
            TransactionSortOption.DATE_ASC -> filteredRaw.sortedWith(compareBy<Transaction> { it.effectiveDate }.thenBy { it.id })
            TransactionSortOption.AMOUNT_DESC -> filteredRaw.sortedByDescending { abs(it.effectiveAmountCents) }
            TransactionSortOption.AMOUNT_ASC -> filteredRaw.sortedBy { abs(it.effectiveAmountCents) }
            TransactionSortOption.MERCHANT_ASC -> filteredRaw.sortedBy { it.effectiveMerchant.lowercase() }
        }

        val currentMonthKey = LocalDateUtils.currentYearMonth()
        val monthlyCount = txns.count { it.effectiveDate.take(7) == currentMonthKey && !it.effectiveIsIgnored }

        var totalExpense = 0L
        var totalIncome = 0L
        for (txn in sorted) {
            if (!txn.effectiveIsTransfer && !txn.effectiveIsIgnored) {
                if (txn.effectiveAmountCents < 0) {
                    totalExpense += txn.effectiveAmountCents
                } else {
                    totalIncome += txn.effectiveAmountCents
                }
            }
        }

        // Group by month
        val grouped = sorted.groupBy { it.effectiveDate.take(7) }.map { (monthKey, items) ->
            MonthTransactionGroup(
                monthKey = monthKey,
                monthLabel = LocalDateUtils.formatMonthLabel(monthKey),
                count = items.size,
                transactions = items
            )
        }

        TransactionsUiState(
            transactions = txns,
            filteredTransactions = sorted,
            groupedTransactions = grouped,
            categories = categories,
            accounts = accounts,
            categoryMap = categoryMap,
            accountMap = accountMap,
            searchQuery = filterParams.query,
            quickFilter = filterParams.quick,
            filterDraft = filterParams.draft,
            sortOption = filterParams.sort,
            isCompactView = filterParams.isCompactView,
            monthlyTransactionCount = monthlyCount,
            totalExpenseCents = totalExpense,
            totalIncomeCents = totalIncome,
            netCashflowCents = totalIncome + totalExpense
        )
    }.combine(_isSearchActive) { state, searchActive ->
        state.copy(isSearchActive = searchActive)
    }.combine(_selectedIds) { state, selected ->
        state.copy(selectedTransactionIds = selected)
    }.combine(_inspectingId) { state, inspecting ->
        state.copy(inspectingTransactionId = inspecting)
    }.combine(_expandedId) { state, expanded ->
        state.copy(expandedTransactionId = expanded)
    }.combine(_showEditorSheet) { state, showEditor ->
        state.copy(showEditorSheet = showEditor)
    }.combine(_showSplitSheet) { state, showSplit ->
        state.copy(showSplitSheet = showSplit)
    }.combine(_showCsvImportSheet) { state, showCsv ->
        state.copy(showCsvImportSheet = showCsv)
    }.combine(_showFilterSheet) { state, showFilter ->
        state.copy(showFilterSheet = showFilter)
    }.combine(_showBatchDeleteConfirm) { state, showBatchDel ->
        state.copy(showBatchDeleteConfirm = showBatchDel)
    }.combine(_singleDeleteConfirmId) { state, singleDelId ->
        state.copy(singleDeleteConfirmId = singleDelId)
    }.combine(_showCategoryBatchPicker) { state, showCatPicker ->
        state.copy(showCategoryBatchPicker = showCatPicker)
    }.combine(_logoFinderTransactionId) { state, logoFinderId ->
        state.copy(logoFinderTransactionId = logoFinderId)
    }.combine(_isSyncing) { state, isSyncing ->
        state.copy(isSyncing = isSyncing)
    }.combine(_syncToastMessage) { state, toastMsg ->
        state.copy(syncToastMessage = toastMsg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }
    fun setQuickFilter(filter: QuickFilter) { _quickFilter.value = filter }
    fun setSortOption(sort: TransactionSortOption) { _sortOption.value = sort }
    fun toggleCompactView() { _isCompactView.value = !_isCompactView.value }
    fun applyFilterDraft(draft: TransactionFilterDraft) { _filterDraft.value = draft }
    fun setShowBatchDeleteConfirm(show: Boolean) { _showBatchDeleteConfirm.value = show }
    fun setSingleDeleteConfirmId(id: Long?) { _singleDeleteConfirmId.value = id }
    fun setShowCategoryBatchPicker(show: Boolean) { _showCategoryBatchPicker.value = show }

    fun clearFilters() {
        _searchQuery.value = ""
        _quickFilter.value = QuickFilter.ALL
        _filterDraft.value = TransactionFilterDraft()
    }

    fun applyDeepLink(
        transactionId: Long? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        uncategorizedOnly: Boolean = false
    ) {
        val hasDeepLink = transactionId != null || accountId != null || categoryId != null || uncategorizedOnly
        if (!hasDeepLink) return
        _searchQuery.value = ""
        _isSearchActive.value = false
        _quickFilter.value = if (uncategorizedOnly) QuickFilter.UNCATEGORIZED else QuickFilter.ALL
        _filterDraft.value = TransactionFilterDraft(
            accountId = accountId,
            categoryId = categoryId
        )
        _inspectingId.value = transactionId
        _expandedId.value = transactionId
        _showEditorSheet.value = false
        _showSplitSheet.value = false
    }

    fun toggleTransactionSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectAll() {
        val visibleIds = uiState.value.filteredTransactions.map { it.id }.toSet()
        _selectedIds.value = visibleIds
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun toggleExpandTransaction(id: Long) {
        _expandedId.value = if (_expandedId.value == id) null else id
    }

    fun selectTransactionForInspect(id: Long?) {
        if (id != null && _inspectingId.value == id && !_showEditorSheet.value && !_showSplitSheet.value) {
            _inspectingId.value = null
        } else {
            _inspectingId.value = id
            _showEditorSheet.value = false
            _showSplitSheet.value = false
        }
    }

    fun dismissInspectorPane() {
        _inspectingId.value = null
        _showEditorSheet.value = false
        _showSplitSheet.value = false
    }

    fun openTransactionEditor(id: Long? = null) {
        _inspectingId.value = id
        _showEditorSheet.value = true
    }

    fun closeTransactionEditor() {
        _showEditorSheet.value = false
    }

    fun openSplitSheet(id: Long) {
        _inspectingId.value = id
        _showSplitSheet.value = true
    }

    fun closeSplitSheet() {
        _showSplitSheet.value = false
    }

    fun openCsvImportSheet() { _showCsvImportSheet.value = true }
    fun closeCsvImportSheet() { _showCsvImportSheet.value = false }
    fun openFilterSheet() { _showFilterSheet.value = true }
    fun closeFilterSheet() { _showFilterSheet.value = false }
    fun openLogoFinder(transactionId: Long) { _logoFinderTransactionId.value = transactionId }
    fun closeLogoFinder() { _logoFinderTransactionId.value = null }

    fun reportMerchantLogoStatus(merchantKey: String, status: String) {
        viewModelScope.launch { repository.reportMerchantLogoStatus(merchantKey, status) }
    }

    fun useCategoryIconForTransaction(transactionId: Long) {
        viewModelScope.launch { repository.overrideMerchantLogo(transactionId, useCategoryIcon = true) }
    }

    suspend fun searchMerchantLogos(transactionId: Long, query: String): Result<MerchantLogoSearchResponseDto> =
        repository.searchMerchantLogos(transactionId, query)

    suspend fun saveMerchantLogoOverride(transactionId: Long, logoUrl: String): Result<Unit> =
        repository.overrideMerchantLogo(transactionId, logoUrl = logoUrl, useCategoryIcon = false).map {}

    suspend fun useCategoryIconSuspend(transactionId: Long): Result<Unit> =
        repository.overrideMerchantLogo(transactionId, useCategoryIcon = true).map {}

    fun syncSimpleFin() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncToastMessage.value = "Syncing transactions with SimpleFIN..."
            val result = repository.sync(force = true)
            _isSyncing.value = false
            _syncToastMessage.value = when (result) {
                is SyncResult.Success -> result.message.ifBlank { "SimpleFIN sync complete" }
                is SyncResult.Error -> result.message
                SyncResult.NoOp -> "Transactions up to date"
            }
            delay(2600L)
            _syncToastMessage.value = null
        }
    }

    fun saveTransaction(
        id: Long,
        merchant: String,
        amountCents: Cents,
        date: String,
        categoryId: Long?,
        notes: String?,
        isTransfer: Boolean,
        isIgnored: Boolean,
        mhaEligible: Boolean?,
        accountId: Long? = null
    ) {
        viewModelScope.launch {
            val existing = uiState.value.transactions.find { it.id == id }
            if (existing != null) {
                val updated = existing.copy(
                    editedMerchant = merchant,
                    editedMerchantSource = "user",
                    editedAmountCents = amountCents,
                    editedDate = date,
                    editedCategoryId = categoryId,
                    editedCategoryIdSource = "user",
                    editedIsTransfer = isTransfer,
                    editedIsTransferSource = "user",
                    editedIsIgnored = isIgnored,
                    editedIsIgnoredSource = "user",
                    editedMhaEligible = mhaEligible,
                    editedMhaEligibleSource = "user",
                    notes = notes,
                    editSource = EditSource.USER
                )
                repository.saveTransaction(updated)
            } else {
                val resolvedAccountId = accountId ?: uiState.value.accounts.firstOrNull()?.id ?: 1L
                val accountName = uiState.value.accounts.find { it.id == resolvedAccountId }?.name ?: ""
                val newTxn = Transaction(
                    id = 0L,
                    accountId = resolvedAccountId,
                    accountName = accountName,
                    originalDate = date,
                    originalMerchant = merchant,
                    originalAmountCents = amountCents,
                    originalCategoryId = categoryId,
                    notes = notes,
                    isTransfer = isTransfer,
                    isIgnored = isIgnored,
                    source = "manual"
                )
                repository.saveTransaction(newTxn)
            }
            closeTransactionEditor()
        }
    }

    fun toggleTransfer(id: Long) {
        viewModelScope.launch {
            val txn = uiState.value.transactions.find { it.id == id } ?: return@launch
            val updated = txn.copy(
                editedIsTransfer = !txn.effectiveIsTransfer,
                editedIsTransferSource = "user",
                editSource = EditSource.USER
            )
            repository.saveTransaction(updated)
        }
    }

    fun toggleIgnore(id: Long) {
        viewModelScope.launch {
            val txn = uiState.value.transactions.find { it.id == id } ?: return@launch
            val updated = txn.copy(
                editedIsIgnored = !txn.effectiveIsIgnored,
                editedIsIgnoredSource = "user",
                editSource = EditSource.USER
            )
            repository.saveTransaction(updated)
        }
    }

    fun toggleMha(id: Long) {
        viewModelScope.launch {
            val txn = uiState.value.transactions.find { it.id == id } ?: return@launch
            val isEligible = txn.effectiveMhaEligible == true
            val updated = txn.copy(
                editedMhaEligible = !isEligible,
                editedMhaEligibleSource = "user",
                editSource = EditSource.USER
            )
            repository.saveTransaction(updated)
        }
    }

    fun resetMerchant(id: Long) {
        viewModelScope.launch {
            val txn = uiState.value.transactions.find { it.id == id } ?: return@launch
            val updated = txn.copy(
                editedMerchant = null,
                editedMerchantSource = null
            )
            repository.saveTransaction(updated)
        }
    }

    fun resetCategory(id: Long) {
        viewModelScope.launch {
            val txn = uiState.value.transactions.find { it.id == id } ?: return@launch
            val updated = txn.copy(
                editedCategoryId = null,
                editedCategoryIdSource = null
            )
            repository.saveTransaction(updated)
        }
    }

    fun resetTransaction(id: Long) {
        viewModelScope.launch {
            repository.resetTransaction(id)
            closeTransactionEditor()
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            if (_inspectingId.value == id) {
                _inspectingId.value = null
            }
            if (_expandedId.value == id) {
                _expandedId.value = null
            }
            closeTransactionEditor()
            closeSplitSheet()
        }
    }

    fun splitTransaction(parentId: Long, splits: List<Pair<Cents, Long?>>) {
        viewModelScope.launch {
            repository.splitTransaction(parentId, splits)
            closeSplitSheet()
        }
    }

    fun importCsvTransactions(
        transactions: List<Transaction>,
        rules: List<TransactionRule> = emptyList()
    ) {
        viewModelScope.launch {
            repository.importTransactions(transactions, rules)
            closeCsvImportSheet()
        }
    }

    // -------------------------------------------------------------------------
    // Batch Operations
    // -------------------------------------------------------------------------
    fun batchCategorize(categoryId: Long) {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.batchEditCategory(selected, categoryId)
            clearSelection()
        }
    }

    fun batchMarkTransfer(isTransfer: Boolean) {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.batchMarkTransfer(selected, isTransfer)
            clearSelection()
        }
    }

    fun batchToggleMha() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val txns = uiState.value.transactions.filter { it.id in selected }
            val allEligible = txns.all { it.effectiveMhaEligible == true }
            repository.batchSetMhaEligible(selected, !allEligible)
            clearSelection()
        }
    }

    fun batchMarkIgnored(isIgnored: Boolean) {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.batchSetIgnored(selected, isIgnored)
            clearSelection()
        }
    }

    fun batchDelete() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.batchDeleteTransactions(selected)
            clearSelection()
        }
    }

    fun createRuleFromTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val actions = buildList {
                transaction.effectiveCategoryId?.let { add(RuleAction(RuleActionType.SET_CATEGORY, it.toString())) }
                if (transaction.effectiveIsTransfer) add(RuleAction(RuleActionType.MARK_TRANSFER, "true"))
            }
            val rule = TransactionRule(
                name = "${transaction.effectiveMerchant} Rule",
                conditions = listOf(RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, transaction.originalMerchant.ifBlank { transaction.effectiveMerchant })),
                actions = actions
            )
            repository.saveRule(rule)
        }
    }

    fun markTransactionRecurring(transaction: Transaction) {
        viewModelScope.launch {
            val item = UpcomingItem(
                name = transaction.effectiveMerchant,
                merchant = transaction.effectiveMerchant,
                amountCents = abs(transaction.effectiveAmountCents),
                kind = if (transaction.isExpense) UpcomingKind.BILL else UpcomingKind.INCOME,
                direction = if (transaction.isExpense) "expense" else "income",
                frequencyType = RecurrenceFrequency.MONTHLY,
                nextDate = transaction.effectiveDate,
                categoryId = transaction.effectiveCategoryId,
                accountId = transaction.accountId,
                status = UpcomingStatus.ACTIVE
            )
            repository.saveUpcoming(item)
        }
    }

    private fun filterTransactions(
        transactions: List<Transaction>,
        query: String,
        quick: QuickFilter,
        draft: TransactionFilterDraft,
        categoryMap: Map<Long, app.overbay.orbit.data.model.Category>
    ): List<Transaction> {
        return transactions.filter { txn ->
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val matchMerchant = txn.effectiveMerchant.lowercase().contains(q)
                val matchDesc = txn.originalDescription?.lowercase()?.contains(q) ?: false
                val matchNotes = txn.notes?.lowercase()?.contains(q) ?: false
                val matchCategory = txn.effectiveCategoryId?.let { categoryMap[it]?.name?.lowercase()?.contains(q) } ?: false
                if (!matchMerchant && !matchDesc && !matchNotes && !matchCategory) return@filter false
            }

            when (quick) {
                QuickFilter.ALL -> {}
                QuickFilter.EXPENSES -> if (!txn.isExpense || txn.effectiveIsTransfer) return@filter false
                QuickFilter.INCOME -> if (!txn.isIncome || txn.effectiveIsTransfer) return@filter false
                QuickFilter.TRANSFERS -> if (!txn.effectiveIsTransfer) return@filter false
                QuickFilter.UNCATEGORIZED -> if (txn.effectiveCategoryId != null || txn.effectiveIsTransfer) return@filter false
                QuickFilter.PENDING -> if (!txn.isPending) return@filter false
                QuickFilter.MHA -> if (txn.effectiveMhaEligible != true) return@filter false
            }

            if (draft.type == "expenses" && (!txn.isExpense || txn.effectiveIsTransfer)) return@filter false
            if (draft.type == "income" && (!txn.isIncome || txn.effectiveIsTransfer)) return@filter false
            if (draft.type == "transfers" && !txn.effectiveIsTransfer) return@filter false
            if (draft.accountId != null && txn.accountId != draft.accountId) return@filter false
            if (draft.categoryId != null && txn.effectiveCategoryId != draft.categoryId) return@filter false

            true
        }
    }

    private data class FilterParams(
        val query: String,
        val quick: QuickFilter,
        val draft: TransactionFilterDraft,
        val sort: TransactionSortOption,
        val isCompactView: Boolean
    )
}
