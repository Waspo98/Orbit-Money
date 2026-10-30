package app.overbay.orbit.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.RuleMatcherEngine
import app.overbay.orbit.ui.components.TransactionFilterDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _quickFilter = MutableStateFlow(QuickFilter.ALL)
    private val _filterDraft = MutableStateFlow(TransactionFilterDraft())
    private val _sortOption = MutableStateFlow(TransactionSortOption.DATE_DESC)
    private val _isCompactView = MutableStateFlow(false)
    private val _pageSize = MutableStateFlow(50)
    private val _currentPage = MutableStateFlow(1)
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
    private val _ruleDraftTransactionId = MutableStateFlow<Long?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncToastMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repository.transactions,
        repository.categories,
        repository.accounts,
        repository.rules
    ) { txns, categories, accounts, rules ->
        val nextPriority = (rules.maxOfOrNull { it.priority } ?: 0) + 10
        TransactionsFilterHelper.RepoSnapshot(txns, categories, accounts, nextPriority)
    }.distinctUntilChanged()

    private val filterFlow = combine(
        _searchQuery,
        _quickFilter,
        _filterDraft,
        _sortOption,
        _isCompactView
    ) { query, quick, draft, sort, compact ->
        TransactionsFilterHelper.FilterParams(query, quick, draft, sort, compact)
    }.distinctUntilChanged()

    private val paginationFlow = combine(
        _pageSize,
        _currentPage
    ) { size, page ->
        Pair(size, page)
    }.distinctUntilChanged()

    val uiState: StateFlow<TransactionsUiState> = combine(
        dataFlow,
        filterFlow,
        paginationFlow
    ) { (txns, categories, accounts, nextPriority), filterParams, (pageSize, page) ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val filteredRaw = TransactionsFilterHelper.filterTransactions(
            transactions = txns,
            query = filterParams.query,
            quick = filterParams.quick,
            draft = filterParams.draft,
            categoryMap = categoryMap
        )

        val sorted = TransactionsFilterHelper.sortTransactions(filteredRaw, filterParams.sort)

        val paginated = TransactionsFilterHelper.paginateTransactions(sorted, page, pageSize)

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

        // Group active paginated transactions by month (or all sorted if page size is 0 / All)
        val displayList = if (pageSize <= 0) sorted else paginated
        val grouped = displayList.groupBy { it.effectiveDate.take(7) }.map { (monthKey, items) ->
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
            paginatedTransactions = paginated,
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
            pageSize = pageSize,
            currentPage = page,
            monthlyTransactionCount = monthlyCount,
            nextRulePriority = nextPriority,
            totalExpenseCents = totalExpense,
            totalIncomeCents = totalIncome,
            netCashflowCents = totalIncome + totalExpense
        )
    }.combine(_isSearchActive) { state, v -> state.copy(isSearchActive = v) }
        .combine(_selectedIds) { state, v -> state.copy(selectedTransactionIds = v) }
        .combine(_inspectingId) { state, v -> state.copy(inspectingTransactionId = v) }
        .combine(_expandedId) { state, v -> state.copy(expandedTransactionId = v) }
        .combine(_showEditorSheet) { state, v -> state.copy(showEditorSheet = v) }
        .combine(_showSplitSheet) { state, v -> state.copy(showSplitSheet = v) }
        .combine(_showCsvImportSheet) { state, v -> state.copy(showCsvImportSheet = v) }
        .combine(_showFilterSheet) { state, v -> state.copy(showFilterSheet = v) }
        .combine(_showBatchDeleteConfirm) { state, v -> state.copy(showBatchDeleteConfirm = v) }
        .combine(_singleDeleteConfirmId) { state, v -> state.copy(singleDeleteConfirmId = v) }
        .combine(_showCategoryBatchPicker) { state, v -> state.copy(showCategoryBatchPicker = v) }
        .combine(_logoFinderTransactionId) { state, v -> state.copy(logoFinderTransactionId = v) }
        .combine(_ruleDraftTransactionId) { state, v -> state.copy(ruleDraftTransactionId = v) }
        .combine(_isSyncing) { state, v -> state.copy(isSyncing = v) }
        .combine(_syncToastMessage) { state, v -> state.copy(syncToastMessage = v) }
        .stateIn(
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

    fun setPageSize(size: Int) {
        _pageSize.value = size
        _currentPage.value = 1
    }

    fun setCurrentPage(page: Int) {
        val maxPages = uiState.value.totalPages
        _currentPage.value = page.coerceIn(1, maxPages)
    }

    fun nextPage() {
        val next = _currentPage.value + 1
        if (next <= uiState.value.totalPages) _currentPage.value = next
    }

    fun prevPage() {
        val prev = _currentPage.value - 1
        if (prev >= 1) _currentPage.value = prev
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _quickFilter.value = QuickFilter.ALL
        _filterDraft.value = TransactionFilterDraft()
        _sortOption.value = TransactionSortOption.DATE_DESC
        _currentPage.value = 1
    }

    fun applyDeepLink(
        transactionId: Long? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        uncategorizedOnly: Boolean = false,
        startDate: String? = null,
        endDate: String? = null,
        sortOption: String? = null,
        openAddSheet: Boolean = false,
        includeTransfers: Boolean = true,
        includeIgnored: Boolean = true
    ) {
        val hasDeepLink = transactionId != null || accountId != null || categoryId != null ||
                uncategorizedOnly || startDate != null || endDate != null ||
                sortOption != null || openAddSheet || !includeTransfers || !includeIgnored
        if (!hasDeepLink) return

        _searchQuery.value = ""
        _isSearchActive.value = false
        _quickFilter.value = if (uncategorizedOnly) QuickFilter.UNCATEGORIZED else QuickFilter.ALL

        val resolvedSort = sortOption?.let { TransactionsFilterHelper.parseSortOption(it) }
            ?: TransactionSortOption.DATE_DESC
        _sortOption.value = resolvedSort

        _filterDraft.value = TransactionFilterDraft(
            accountId = accountId,
            categoryId = categoryId,
            accountIds = if (accountId != null) setOf(accountId) else emptySet(),
            categoryIds = if (categoryId != null) setOf(categoryId) else emptySet(),
            uncategorizedOnly = uncategorizedOnly,
            startDate = startDate,
            endDate = endDate,
            includeTransfers = includeTransfers,
            includeIgnored = includeIgnored
        )

        _inspectingId.value = transactionId
        _expandedId.value = transactionId
        _showEditorSheet.value = openAddSheet
        _showSplitSheet.value = false
        _currentPage.value = 1
    }

    fun toggleTransactionSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectAll() {
        val visibleIds = uiState.value.filteredTransactions.map { it.id }.toSet()
        _selectedIds.value = visibleIds
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

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

    fun closeTransactionEditor() { _showEditorSheet.value = false }
    fun openSplitSheet(id: Long) {
        _inspectingId.value = id
        _showSplitSheet.value = true
    }
    fun closeSplitSheet() { _showSplitSheet.value = false }
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
            repository.setTransactionIgnored(id, !txn.effectiveIsIgnored)
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

    fun resetMerchant(id: Long) = resetField(id, "merchant")
    fun resetCategory(id: Long) = resetField(id, "category")
    fun resetField(id: Long, field: String) { viewModelScope.launch { repository.resetTransactionFields(id, listOf(field)) } }
    fun resetTransaction(id: Long) { viewModelScope.launch { repository.resetTransaction(id); closeTransactionEditor() } }
    fun requestOpenRuleEditor(ruleId: Long?) = repository.requestOpenRuleEditor(ruleId)
    fun requestOpenBudgetMonth(month: String?) = repository.requestOpenBudgetMonth(month)

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            if (_inspectingId.value == id) _inspectingId.value = null
            if (_expandedId.value == id) _expandedId.value = null
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

    fun getImportBatches(): List<app.overbay.orbit.data.model.ImportBatchRecord> =
        repository.snapshot.value.importBatches

    fun importCsvTransactions(
        transactions: List<Transaction>,
        rules: List<TransactionRule> = emptyList()
    ) {
        viewModelScope.launch {
            repository.importTransactions(transactions, rules)
            closeCsvImportSheet()
        }
    }

    fun importCsvBatch(
        fileName: String,
        rawCsvContent: String,
        transactions: List<Transaction>,
        rules: List<TransactionRule> = emptyList()
    ) {
        viewModelScope.launch {
            repository.importTransactionsWithBatch(
                fileName = fileName,
                rawCsvContent = rawCsvContent,
                newTransactions = transactions,
                newAccounts = emptyList(),
                newCategories = emptyList(),
                newRules = rules
            )
            closeCsvImportSheet()
        }
    }

    fun undoImportBatch(batchId: Long) {
        viewModelScope.launch {
            repository.undoImportBatch(batchId)
        }
    }

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

    fun openCreateRuleModal(transactionId: Long) {
        _showEditorSheet.value = false
        _ruleDraftTransactionId.value = transactionId
    }

    fun closeCreateRuleModal() { _ruleDraftTransactionId.value = null }

    fun saveRuleFromTransaction(rule: TransactionRule) {
        _ruleDraftTransactionId.value = null
        viewModelScope.launch { repository.saveRule(rule) }
    }

    fun previewRuleMatchCount(conditions: List<RuleCondition>): Int {
        if (conditions.isEmpty()) return 0
        return RuleMatcherEngine.countMatches(repository.transactions.value, conditions)
    }

    fun previewRuleMatches(conditions: List<RuleCondition>): List<Transaction> =
        RuleMatcherEngine.matchingTransactions(repository.transactions.value, conditions)

    fun markTransactionRecurring(transaction: Transaction) {
        viewModelScope.launch {
            repository.createUpcomingFromTransaction(transaction.id)
        }
    }
}
