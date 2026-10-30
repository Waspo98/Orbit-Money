package app.overbay.orbit.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.domain.Cents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class CategoriesViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _inspectingCategoryId = MutableStateFlow<Long?>(null)
    private val _editingCategory = MutableStateFlow<Category?>(null)
    private val _showEditorDialog = MutableStateFlow(false)
    private val _deleteConfirmId = MutableStateFlow<Long?>(null)
    private val _ruleBlockedCategory = MutableStateFlow<CategorySummary?>(null)
    private val _isReorderMode = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _groupOrder = MutableStateFlow(
        listOf("Income", "Living Needs (Fixed)", "Discretionary (Variable)", "Transfers & Savings")
    )
    private val _categorySortOverrides = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val dialogFlow = combine(
        _inspectingCategoryId,
        _editingCategory,
        _showEditorDialog,
        _deleteConfirmId,
        _ruleBlockedCategory
    ) { inspectingId, editing, showDialog, deleteId, ruleBlocked ->
        CategoriesUiState(
            inspectingCategoryId = inspectingId,
            editingCategory = editing,
            showEditorDialog = showDialog,
            deleteConfirmCategoryId = deleteId,
            ruleBlockedDeleteCategory = ruleBlocked
        )
    }.combine(_isReorderMode) { state, isReorder ->
        state.copy(isReorderMode = isReorder)
    }.combine(_searchQuery) { state, query ->
        state.copy(searchQuery = query)
    }

    private val repoFlow = combine(
        repository.categories,
        repository.transactions,
        repository.budgets,
        repository.rules
    ) { categories, transactions, budgets, rules ->
        CategoriesRepoSnapshot(categories, transactions, budgets, rules)
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        repoFlow,
        dialogFlow,
        _groupOrder,
        _categorySortOverrides
    ) { repoSnap, dialogState, groupOrder, sortOverrides ->
        val categories = repoSnap.categories
        val transactions = repoSnap.transactions
        val budgets = repoSnap.budgets
        val rules = repoSnap.rules
        // 1. Local transaction counts and sums
        val txnCountMap = mutableMapOf<Long, Int>()
        val txnSumMap = mutableMapOf<Long, Cents>()
        for (txn in transactions) {
            val catId = txn.effectiveCategoryId ?: continue
            txnCountMap[catId] = (txnCountMap[catId] ?: 0) + 1
            txnSumMap[catId] = (txnSumMap[catId] ?: 0L) + abs(txn.effectiveAmountCents)
        }

        // 2. Local budget counts
        val budgetCountMap = mutableMapOf<Long, Int>()
        for (b in budgets) {
            budgetCountMap[b.categoryId] = (budgetCountMap[b.categoryId] ?: 0) + 1
        }

        // 3. Local rule references (conditions or actions)
        val ruleCountMap = mutableMapOf<Long, Int>()
        for (r in rules) {
            val refCategoryIds = mutableSetOf<Long>()
            for (c in r.conditions) {
                if (c.field == RuleConditionField.CATEGORY_ID || c.field == RuleConditionField.CATEGORY) {
                    c.targetId?.let { refCategoryIds.add(it) }
                        ?: c.value.toLongOrNull()?.let { refCategoryIds.add(it) }
                }
            }
            for (a in r.actions) {
                if (a.type == RuleActionType.CATEGORIZE || a.type == RuleActionType.SET_CATEGORY) {
                    a.targetCategoryId?.let { refCategoryIds.add(it) }
                        ?: a.value.toLongOrNull()?.let { refCategoryIds.add(it) }
                }
            }
            for (id in refCategoryIds) {
                ruleCountMap[id] = (ruleCountMap[id] ?: 0) + 1
            }
        }

        val sortedCategories = categories.sortedWith(
            compareBy<Category> { sortOverrides[it.id] ?: it.sortOrder }.thenBy { it.name }
        )

        val query = dialogState.searchQuery.trim().lowercase()
        val allSummaries = sortedCategories.map { cat ->
            val effTx = maxOf(cat.transactionCount, txnCountMap[cat.id] ?: 0)
            val effBudget = maxOf(cat.budgetCount, budgetCountMap[cat.id] ?: 0)
            val effRule = maxOf(cat.ruleCount, ruleCountMap[cat.id] ?: 0)
            CategorySummary(
                category = cat,
                transactionCount = effTx,
                budgetCount = effBudget,
                ruleCount = effRule,
                totalAmountCents = txnSumMap[cat.id] ?: 0L
            )
        }

        val filteredSummaries = if (query.isNotBlank()) {
            allSummaries.filter { item ->
                item.category.name.lowercase().contains(query) ||
                        (item.category.icon?.contains(query) == true)
            }
        } else allSummaries

        // Group into 4 M3E groups
        val incomeList = mutableListOf<CategorySummary>()
        val transferList = mutableListOf<CategorySummary>()
        val fixedNeedsList = mutableListOf<CategorySummary>()
        val variableDiscretionaryList = mutableListOf<CategorySummary>()

        val fixedKeywords = setOf("rent", "mortgage", "housing", "utilities", "electric", "water", "groceries", "insurance", "medical", "health", "auto", "gas", "loan")

        for (item in filteredSummaries) {
            val nameLower = item.category.name.lowercase()
            when {
                item.category.isIncome -> incomeList.add(item)
                item.category.isTransfer || nameLower.contains("transfer") || nameLower.contains("saving") -> transferList.add(item)
                fixedKeywords.any { nameLower.contains(it) } -> fixedNeedsList.add(item)
                else -> variableDiscretionaryList.add(item)
            }
        }

        val groups = listOf(
            CategoryGroupDisplay("Income", incomeList, incomeList.sumOf { it.totalAmountCents }),
            CategoryGroupDisplay("Living Needs (Fixed)", fixedNeedsList, fixedNeedsList.sumOf { it.totalAmountCents }),
            CategoryGroupDisplay("Discretionary (Variable)", variableDiscretionaryList, variableDiscretionaryList.sumOf { it.totalAmountCents }),
            CategoryGroupDisplay("Transfers & Savings", transferList, transferList.sumOf { it.totalAmountCents })
        ).filter { it.items.isNotEmpty() }.sortedBy { grp ->
            val idx = groupOrder.indexOf(grp.title)
            if (idx >= 0) idx else Int.MAX_VALUE
        }

        dialogState.copy(
            categories = sortedCategories,
            groups = groups,
            totalCategoriesCount = sortedCategories.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoriesUiState(isLoading = true)
    )

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun selectCategoryForInspect(id: Long?) {
        val nextId = if (_inspectingCategoryId.value == id) null else id
        _inspectingCategoryId.value = nextId
        if (nextId != null && _showEditorDialog.value && _editingCategory.value?.id != nextId) {
            _showEditorDialog.value = false
            _editingCategory.value = null
        }
    }

    fun dismissInspectorPane() {
        _inspectingCategoryId.value = null
        _showEditorDialog.value = false
        _editingCategory.value = null
    }

    fun openAddCategory() {
        _editingCategory.value = null
        _showEditorDialog.value = true
    }

    fun openEditCategory(category: Category) {
        _inspectingCategoryId.value = category.id
        _editingCategory.value = category
        _showEditorDialog.value = true
    }

    fun closeEditorDialog() {
        _showEditorDialog.value = false
        _editingCategory.value = null
    }

    fun toggleReorderMode() {
        _isReorderMode.value = !_isReorderMode.value
    }

    fun moveCategoryGroup(fromIndex: Int, toIndex: Int) {
        val visibleTitles = uiState.value.groups.map { it.title }.toMutableList()
        if (fromIndex !in visibleTitles.indices || toIndex !in visibleTitles.indices || fromIndex == toIndex) return
        val moved = visibleTitles.removeAt(fromIndex)
        visibleTitles.add(toIndex, moved)
        val remaining = _groupOrder.value.filter { it !in visibleTitles }
        _groupOrder.value = visibleTitles + remaining
    }

    fun moveCategoryInGroup(groupTitle: String, fromIndex: Int, toIndex: Int) {
        val group = uiState.value.groups.find { it.title == groupTitle } ?: return
        val list = group.items.map { it.category }.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        val newOverrides = _categorySortOverrides.value.toMutableMap()
        list.forEachIndexed { idx, cat -> newOverrides[cat.id] = idx }
        _categorySortOverrides.value = newOverrides
        viewModelScope.launch {
            list.forEachIndexed { idx, cat ->
                if (cat.sortOrder != idx) {
                    repository.saveCategory(cat.copy(sortOrder = idx))
                }
            }
        }
    }

    fun saveCategory(
        id: Long,
        name: String,
        color: String,
        icon: String?,
        isTransfer: Boolean,
        isIncome: Boolean,
        mhaEligible: Boolean
    ) {
        viewModelScope.launch {
            val existing = uiState.value.categories.find { it.id == id } ?: _editingCategory.value
            val category = Category(
                id = id,
                name = name,
                color = color,
                icon = icon,
                isTransfer = isTransfer,
                isIncome = isIncome,
                mhaDefaultEligible = mhaEligible,
                mhaDefaultIgnored = existing?.mhaDefaultIgnored ?: false,
                sortOrder = existing?.sortOrder ?: 0
            )
            repository.saveCategory(category)
            closeEditorDialog()
        }
    }

    fun requestDeleteCategory(id: Long) {
        val summary = uiState.value.groups.asSequence()
            .flatMap { it.items.asSequence() }
            .find { it.category.id == id }

        if (summary != null && summary.ruleCount > 0) {
            _ruleBlockedCategory.value = summary
            _deleteConfirmId.value = null
        } else {
            _deleteConfirmId.value = id
            _ruleBlockedCategory.value = null
        }
    }

    fun dismissRuleBlockedDialog() {
        _ruleBlockedCategory.value = null
    }

    fun dismissDeleteCategory() {
        _deleteConfirmId.value = null
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            if (_inspectingCategoryId.value == id) {
                _inspectingCategoryId.value = null
            }
            _deleteConfirmId.value = null
            _ruleBlockedCategory.value = null
        }
    }

    private data class CategoriesRepoSnapshot(
        val categories: List<Category>,
        val transactions: List<app.overbay.orbit.data.model.Transaction>,
        val budgets: List<app.overbay.orbit.data.model.CategoryBudget>,
        val rules: List<app.overbay.orbit.data.model.TransactionRule>
    )
}
