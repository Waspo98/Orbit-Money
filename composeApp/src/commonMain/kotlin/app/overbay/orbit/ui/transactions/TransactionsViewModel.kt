package app.overbay.orbit.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.TransactionFilterDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _quickFilter = MutableStateFlow(QuickFilter.ALL)
    private val _filterDraft = MutableStateFlow(TransactionFilterDraft())
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _inspectingId = MutableStateFlow<Long?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _showSplitSheet = MutableStateFlow(false)
    private val _showCsvImportSheet = MutableStateFlow(false)
    private val _showFilterSheet = MutableStateFlow(false)

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
        _filterDraft
    ) { query, quick, draft ->
        Triple(query, quick, draft)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        dataFlow,
        filterFlow
    ) { (txns, categories, accounts), (query, quick, draft) ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val filtered = filterTransactions(
            transactions = txns,
            query = query,
            quick = quick,
            draft = draft,
            categoryMap = categoryMap
        )

        var totalExpense = 0L
        var totalIncome = 0L
        for (txn in filtered) {
            if (!txn.effectiveIsTransfer && !txn.effectiveIsIgnored) {
                if (txn.effectiveAmountCents < 0) {
                    totalExpense += txn.effectiveAmountCents
                } else {
                    totalIncome += txn.effectiveAmountCents
                }
            }
        }

        TransactionsUiState(
            transactions = txns,
            filteredTransactions = filtered,
            categories = categories,
            accounts = accounts,
            categoryMap = categoryMap,
            accountMap = accountMap,
            searchQuery = query,
            quickFilter = quick,
            filterDraft = draft,
            totalExpenseCents = totalExpense,
            totalIncomeCents = totalIncome,
            netCashflowCents = totalIncome + totalExpense
        )
    }.combine(_selectedIds) { state, selected ->
        state.copy(selectedTransactionIds = selected)
    }.combine(_inspectingId) { state, inspecting ->
        state.copy(inspectingTransactionId = inspecting)
    }.combine(_showEditorSheet) { state, showEditor ->
        state.copy(showEditorSheet = showEditor)
    }.combine(_showSplitSheet) { state, showSplit ->
        state.copy(showSplitSheet = showSplit)
    }.combine(_showCsvImportSheet) { state, showCsv ->
        state.copy(showCsvImportSheet = showCsv)
    }.combine(_showFilterSheet) { state, showFilter ->
        state.copy(showFilterSheet = showFilter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setQuickFilter(filter: QuickFilter) {
        _quickFilter.value = filter
    }

    fun applyFilterDraft(draft: TransactionFilterDraft) {
        _filterDraft.value = draft
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _quickFilter.value = QuickFilter.ALL
        _filterDraft.value = TransactionFilterDraft()
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

    fun selectTransactionForInspect(id: Long?) {
        _inspectingId.value = id
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

    fun openCsvImportSheet() {
        _showCsvImportSheet.value = true
    }

    fun closeCsvImportSheet() {
        _showCsvImportSheet.value = false
    }

    fun openFilterSheet() {
        _showFilterSheet.value = true
    }

    fun closeFilterSheet() {
        _showFilterSheet.value = false
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
        mhaEligible: Boolean?
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
                val newTxn = Transaction(
                    id = 0L,
                    accountId = uiState.value.accounts.firstOrNull()?.id ?: 1L,
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

    // -------------------------------------------------------------------------
    // Filtering Logic
    // -------------------------------------------------------------------------
    private fun filterTransactions(
        transactions: List<Transaction>,
        query: String,
        quick: QuickFilter,
        draft: TransactionFilterDraft,
        categoryMap: Map<Long, app.overbay.orbit.data.model.Category>
    ): List<Transaction> {
        val queryLower = query.trim().lowercase()

        val filtered = transactions.filter { txn ->
            // 1. Text Search
            if (queryLower.isNotBlank()) {
                val matchesMerchant = txn.effectiveMerchant.lowercase().contains(queryLower)
                val matchesOriginalDesc = txn.originalDescription?.lowercase()?.contains(queryLower) == true
                val matchesNotes = txn.notes?.lowercase()?.contains(queryLower) == true
                val catName = txn.effectiveCategoryId?.let { categoryMap[it]?.name }
                val matchesCategory = catName?.lowercase()?.contains(queryLower) == true
                if (!matchesMerchant && !matchesOriginalDesc && !matchesNotes && !matchesCategory) {
                    return@filter false
                }
            }

            // 2. Quick Filter
            when (quick) {
                QuickFilter.ALL -> {}
                QuickFilter.EXPENSES -> if (!txn.isExpense || txn.effectiveIsTransfer) return@filter false
                QuickFilter.INCOME -> if (!txn.isIncome || txn.effectiveIsTransfer) return@filter false
                QuickFilter.TRANSFERS -> if (!txn.effectiveIsTransfer) return@filter false
                QuickFilter.UNCATEGORIZED -> if (txn.effectiveCategoryId != null || txn.effectiveIsTransfer) return@filter false
                QuickFilter.PENDING -> if (!txn.isPending) return@filter false
                QuickFilter.MHA -> if (txn.effectiveMhaEligible != true) return@filter false
            }

            // 3. Draft Filter: Type
            when (draft.type) {
                "expense" -> if (!txn.isExpense || txn.effectiveIsTransfer) return@filter false
                "income" -> if (!txn.isIncome || txn.effectiveIsTransfer) return@filter false
                "transfer" -> if (!txn.effectiveIsTransfer) return@filter false
            }

            // 4. Draft Filter: Account
            if (draft.accountId != null && txn.accountId != draft.accountId) {
                return@filter false
            }

            // 5. Draft Filter: Category
            if (draft.categoryId != null && txn.effectiveCategoryId != draft.categoryId) {
                return@filter false
            }

            // 6. Draft Filter: Date Presets
            if (draft.datePreset != "all") {
                val date = txn.effectiveDate
                val today = LocalDateUtils.todayLocalDate()
                val currentYear = today.year.toString()
                val currentMonth = LocalDateUtils.formatYearMonth(today)
                when (draft.datePreset) {
                    "this_month" -> if (!date.startsWith(currentMonth)) return@filter false
                    "this_year" -> if (!date.startsWith(currentYear)) return@filter false
                    "last_month" -> {
                        val lastMonthYear = if (today.monthNumber == 1) today.year - 1 else today.year
                        val lastMonthNum = if (today.monthNumber == 1) 12 else today.monthNumber - 1
                        val lastMonthPrefix = "$lastMonthYear-${lastMonthNum.toString().padStart(2, '0')}"
                        if (!date.startsWith(lastMonthPrefix)) return@filter false
                    }
                }
            }

            true
        }

        // Sort descending by effectiveDate, then by id DESC
        return filtered.sortedWith(
            compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id }
        )
    }
}
