package app.overbay.orbit.ui.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class TransactionsOverlayState(
    val isSearchActive: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val inspectingId: Long? = null,
    val expandedId: Long? = null,
    val showEditorSheet: Boolean = false,
    val showSplitSheet: Boolean = false,
    val showCsvImportSheet: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showBatchDeleteConfirm: Boolean = false,
    val singleDeleteConfirmId: Long? = null,
    val showCategoryBatchPicker: Boolean = false,
    val logoFinderTransactionId: Long? = null,
    val ruleDraftTransactionId: Long? = null,
    val categoryPickerTransactionId: Long? = null,
    val showCustomizeActionsSheet: Boolean = false
)

class TransactionsViewModel(
    private val repository: OrbitRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    var searchQueryInput by mutableStateOf("")
        private set
    private val _quickFilter = MutableStateFlow(QuickFilter.ALL)
    private val _filterDraft = MutableStateFlow(TransactionFilterDraft())
    private val _sortOption = MutableStateFlow(TransactionSortOption.DATE_DESC)
    private val _isCompactView = MutableStateFlow(false)
    private val _pageSize = MutableStateFlow(50)
    private val _currentPage = MutableStateFlow(1)
    private val _overlays = MutableStateFlow(TransactionsOverlayState())
    private val _isSyncing = MutableStateFlow(false)
    private val _syncToastMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repository.transactions,
        repository.categories,
        repository.accounts,
        repository.rules,
        repository.preferences
    ) { txns, categories, accounts, rules, preferences ->
        val nextPriority = (rules.maxOfOrNull { it.priority } ?: 0) + 10
        TransactionsFilterHelper.RepoSnapshot(txns, categories, accounts, nextPriority, preferences)
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

    private val baseStateFlow = combine(
        dataFlow,
        filterFlow,
        paginationFlow
    ) { (txns, categories, accounts, nextPriority, preferences), filterParams, (pageSize, page) ->
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
            netCashflowCents = totalIncome + totalExpense,
            mhaEnabled = preferences.mhaTrackerEnabled,
            transactionPinnedActions = preferences.transactionPinnedActions,
            transactionActionOrder = preferences.transactionActionOrder
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<TransactionsUiState> = combine(
        baseStateFlow,
        _overlays,
        _isSyncing,
        _syncToastMessage
    ) { base, overlays, isSyncing, syncToast ->
        base.copy(
            isSearchActive = overlays.isSearchActive,
            selectedTransactionIds = overlays.selectedIds,
            inspectingTransactionId = overlays.inspectingId,
            expandedTransactionId = overlays.expandedId,
            showEditorSheet = overlays.showEditorSheet,
            showSplitSheet = overlays.showSplitSheet,
            showCsvImportSheet = overlays.showCsvImportSheet,
            showFilterSheet = overlays.showFilterSheet,
            showBatchDeleteConfirm = overlays.showBatchDeleteConfirm,
            singleDeleteConfirmId = overlays.singleDeleteConfirmId,
            showCategoryBatchPicker = overlays.showCategoryBatchPicker,
            logoFinderTransactionId = overlays.logoFinderTransactionId,
            ruleDraftTransactionId = overlays.ruleDraftTransactionId,
            categoryPickerTransactionId = overlays.categoryPickerTransactionId,
            showCustomizeActionsSheet = overlays.showCustomizeActionsSheet,
            isSyncing = isSyncing,
            syncToastMessage = syncToast
        )
    }.flowOn(computeDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionsUiState(isLoading = true)
        )

    fun setSearchQuery(query: String) {
        searchQueryInput = query
        _searchQuery.value = query
    }
    fun setSearchActive(active: Boolean) {
        _overlays.update { it.copy(isSearchActive = active) }
        if (!active) setSearchQuery("")
    }
    fun setQuickFilter(filter: QuickFilter) { _quickFilter.value = filter }
    fun setSortOption(sort: TransactionSortOption) { _sortOption.value = sort }
    fun toggleCompactView() { _isCompactView.value = !_isCompactView.value }
    fun applyFilterDraft(draft: TransactionFilterDraft) { _filterDraft.value = draft }
    fun setShowBatchDeleteConfirm(show: Boolean) { _overlays.update { it.copy(showBatchDeleteConfirm = show) } }
    fun setSingleDeleteConfirmId(id: Long?) { _overlays.update { it.copy(singleDeleteConfirmId = id) } }
    fun setShowCategoryBatchPicker(show: Boolean) { _overlays.update { it.copy(showCategoryBatchPicker = show) } }

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
        setSearchQuery("")
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
        openSearch: Boolean = false,
        includeTransfers: Boolean = true,
        includeIgnored: Boolean = true
    ) {
        val hasDeepLink = transactionId != null || accountId != null || categoryId != null ||
            uncategorizedOnly || startDate != null || endDate != null ||
            sortOption != null || openAddSheet || openSearch || !includeTransfers || !includeIgnored
        if (!hasDeepLink) return

        setSearchQuery("")
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

        _overlays.update {
            it.copy(
                isSearchActive = openSearch,
                inspectingId = transactionId,
                expandedId = transactionId,
                showEditorSheet = openAddSheet,
                showSplitSheet = false
            )
        }
        _currentPage.value = 1
    }

    fun toggleTransactionSelection(id: Long) {
        _overlays.update { state ->
            val current = state.selectedIds
            state.copy(selectedIds = if (current.contains(id)) current - id else current + id)
        }
    }

    fun selectAll() {
        val visibleIds = uiState.value.filteredTransactions.map { it.id }.toSet()
        _overlays.update { it.copy(selectedIds = visibleIds) }
    }

    fun clearSelection() { _overlays.update { it.copy(selectedIds = emptySet()) } }

    fun toggleExpandTransaction(id: Long) {
        _overlays.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    fun selectTransactionForInspect(id: Long?) {
        _overlays.update { state ->
            if (id != null && state.inspectingId == id && !state.showEditorSheet && !state.showSplitSheet) {
                state.copy(inspectingId = null)
            } else {
                state.copy(inspectingId = id, showEditorSheet = false, showSplitSheet = false)
            }
        }
    }

    fun dismissInspectorPane() {
        _overlays.update { it.copy(inspectingId = null, showEditorSheet = false, showSplitSheet = false) }
    }

    fun openTransactionEditor(id: Long? = null) {
        _overlays.update { it.copy(inspectingId = id, showEditorSheet = true) }
    }

    fun closeTransactionEditor() { _overlays.update { it.copy(showEditorSheet = false) } }
    fun openSplitSheet(id: Long) {
        _overlays.update { it.copy(inspectingId = id, showSplitSheet = true) }
    }
    fun closeSplitSheet() { _overlays.update { it.copy(showSplitSheet = false) } }
    fun openCsvImportSheet() { _overlays.update { it.copy(showCsvImportSheet = true) } }
    fun closeCsvImportSheet() { _overlays.update { it.copy(showCsvImportSheet = false) } }
    fun openFilterSheet() { _overlays.update { it.copy(showFilterSheet = true) } }
    fun closeFilterSheet() { _overlays.update { it.copy(showFilterSheet = false) } }
    fun openLogoFinder(transactionId: Long) { _overlays.update { it.copy(logoFinderTransactionId = transactionId) } }
    fun closeLogoFinder() { _overlays.update { it.copy(logoFinderTransactionId = null) } }
    fun openCategoryPicker(transactionId: Long) { _overlays.update { it.copy(categoryPickerTransactionId = transactionId) } }
    fun closeCategoryPicker() { _overlays.update { it.copy(categoryPickerTransactionId = null) } }
    fun openCustomizeActionsSheet() { _overlays.update { it.copy(showCustomizeActionsSheet = true) } }
    fun closeCustomizeActionsSheet() { _overlays.update { it.copy(showCustomizeActionsSheet = false) } }

    fun reportMerchantLogoStatus(merchantKey: String, status: String) {
        viewModelScope.launch(computeDispatcher) { repository.reportMerchantLogoStatus(merchantKey, status) }
    }

    fun useCategoryIconForTransaction(transactionId: Long) {
        viewModelScope.launch(computeDispatcher) { repository.overrideMerchantLogo(transactionId, useCategoryIcon = true) }
    }

    suspend fun searchMerchantLogos(transactionId: Long, query: String): Result<MerchantLogoSearchResponseDto> =
        repository.searchMerchantLogos(transactionId, query)

    suspend fun saveMerchantLogoOverride(transactionId: Long, logoUrl: String): Result<Unit> =
        repository.overrideMerchantLogo(transactionId, logoUrl = logoUrl, useCategoryIcon = false).map {}

    suspend fun useCategoryIconSuspend(transactionId: Long): Result<Unit> =
        repository.overrideMerchantLogo(transactionId, useCategoryIcon = true).map {}

    fun syncSimpleFin() {
        if (_isSyncing.value) return
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
            val txn = uiState.value.transactions.find { it.id == id } ?: return@launch
            repository.setTransactionIgnored(id, !txn.effectiveIsIgnored)
        }
    }

    fun toggleMha(id: Long) {
        viewModelScope.launch(computeDispatcher) {
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

    fun quickSetCategory(transactionId: Long, categoryId: Long?) {
        closeCategoryPicker()
        viewModelScope.launch(computeDispatcher) {
            val txn = uiState.value.transactions.find { it.id == transactionId } ?: return@launch
            val updated = txn.copy(
                editedCategoryId = categoryId,
                editedCategoryIdSource = "user",
                editSource = EditSource.USER
            )
            repository.saveTransaction(updated)
        }
    }

    fun saveTransactionQuickActions(pinnedIds: List<String>, orderIds: List<String>) {
        closeCustomizeActionsSheet()
        viewModelScope.launch(computeDispatcher) {
            val updated = repository.preferences.value.copy(
                transactionPinnedActions = pinnedIds,
                transactionActionOrder = orderIds
            )
            repository.updatePreferences(updated)
        }
    }

    fun resetMerchant(id: Long) = resetField(id, "merchant")
    fun resetCategory(id: Long) = resetField(id, "category")
    fun resetField(id: Long, field: String) { viewModelScope.launch { repository.resetTransactionFields(id, listOf(field)) } }
    fun resetTransaction(id: Long) { viewModelScope.launch { repository.resetTransaction(id); closeTransactionEditor() } }
    fun requestOpenRuleEditor(ruleId: Long?) = repository.requestOpenRuleEditor(ruleId)
    fun requestOpenBudgetMonth(month: String?) = repository.requestOpenBudgetMonth(month)

    fun deleteTransaction(id: Long) {
        viewModelScope.launch(computeDispatcher) {
            repository.deleteTransaction(id)
            _overlays.update { state ->
                state.copy(
                    inspectingId = if (state.inspectingId == id) null else state.inspectingId,
                    expandedId = if (state.expandedId == id) null else state.expandedId,
                    showEditorSheet = false,
                    showSplitSheet = false
                )
            }
        }
    }

    fun splitTransaction(parentId: Long, splits: List<Pair<Cents, Long?>>) {
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
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
        viewModelScope.launch(computeDispatcher) {
            repository.undoImportBatch(batchId)
        }
    }

    fun batchCategorize(categoryId: Long) {
        val selected = _overlays.value.selectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch(computeDispatcher) {
            repository.batchEditCategory(selected, categoryId)
            clearSelection()
        }
    }

    fun batchMarkTransfer(isTransfer: Boolean) {
        val selected = _overlays.value.selectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch(computeDispatcher) {
            repository.batchMarkTransfer(selected, isTransfer)
            clearSelection()
        }
    }

    fun batchToggleMha() {
        val selected = _overlays.value.selectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch(computeDispatcher) {
            val txns = uiState.value.transactions.filter { it.id in selected }
            val allEligible = txns.all { it.effectiveMhaEligible == true }
            repository.batchSetMhaEligible(selected, !allEligible)
            clearSelection()
        }
    }

    fun batchMarkIgnored(isIgnored: Boolean) {
        val selected = _overlays.value.selectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch(computeDispatcher) {
            repository.batchSetIgnored(selected, isIgnored)
            clearSelection()
        }
    }

    fun batchDelete() {
        val selected = _overlays.value.selectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch(computeDispatcher) {
            repository.batchDeleteTransactions(selected)
            clearSelection()
        }
    }

    fun openCreateRuleModal(transactionId: Long) {
        _overlays.update { it.copy(showEditorSheet = false, ruleDraftTransactionId = transactionId) }
    }

    fun closeCreateRuleModal() { _overlays.update { it.copy(ruleDraftTransactionId = null) } }

    fun saveRuleFromTransaction(rule: TransactionRule) {
        _overlays.update { it.copy(ruleDraftTransactionId = null) }
        viewModelScope.launch(computeDispatcher) { repository.saveRule(rule) }
    }

    fun previewRuleMatchCount(conditions: List<RuleCondition>): Int {
        if (conditions.isEmpty()) return 0
        return RuleMatcherEngine.countMatches(repository.transactions.value, conditions)
    }

    fun previewRuleMatches(conditions: List<RuleCondition>): List<Transaction> =
        RuleMatcherEngine.matchingTransactions(repository.transactions.value, conditions)

    fun markTransactionRecurring(transaction: Transaction) {
        viewModelScope.launch(computeDispatcher) {
            repository.createUpcomingFromTransaction(transaction.id)
        }
    }
}
