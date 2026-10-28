package app.overbay.orbit.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Category
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
    private val _isReorderMode = MutableStateFlow(false)
    private val _groupOrder = MutableStateFlow(
        listOf("Income", "Living Needs (Fixed)", "Discretionary (Variable)", "Transfers & Savings")
    )
    private val _categorySortOverrides = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val dialogFlow = combine(
        _inspectingCategoryId,
        _editingCategory,
        _showEditorDialog,
        _deleteConfirmId,
        _isReorderMode
    ) { inspectingId, editing, showDialog, deleteId, isReorder ->
        CategoriesUiState(
            inspectingCategoryId = inspectingId,
            editingCategory = editing,
            showEditorDialog = showDialog,
            deleteConfirmCategoryId = deleteId,
            isReorderMode = isReorder
        )
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.categories,
        repository.transactions,
        dialogFlow,
        _groupOrder,
        _categorySortOverrides
    ) { categories, transactions, dialogState, groupOrder, sortOverrides ->
        // Count and sum per category
        val txnCountMap = mutableMapOf<Long, Int>()
        val txnSumMap = mutableMapOf<Long, Cents>()

        for (txn in transactions) {
            val catId = txn.effectiveCategoryId ?: continue
            txnCountMap[catId] = (txnCountMap[catId] ?: 0) + 1
            txnSumMap[catId] = (txnSumMap[catId] ?: 0L) + abs(txn.effectiveAmountCents)
        }

        val sortedCategories = categories.sortedWith(
            compareBy<Category> { sortOverrides[it.id] ?: it.sortOrder }.thenBy { it.name }
        )

        val summaries = sortedCategories.map { cat ->
            CategorySummary(
                category = cat,
                transactionCount = txnCountMap[cat.id] ?: 0,
                totalAmountCents = txnSumMap[cat.id] ?: 0L
            )
        }

        // Group into 4 M3E groups
        val incomeList = mutableListOf<CategorySummary>()
        val transferList = mutableListOf<CategorySummary>()
        val fixedNeedsList = mutableListOf<CategorySummary>()
        val variableDiscretionaryList = mutableListOf<CategorySummary>()

        val fixedKeywords = setOf("rent", "mortgage", "housing", "utilities", "electric", "water", "groceries", "insurance", "medical", "health", "auto", "gas", "loan")

        for (item in summaries) {
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
            val existingSortOrder = _editingCategory.value?.sortOrder ?: 0
            val category = Category(
                id = id,
                name = name,
                color = color,
                icon = icon,
                isTransfer = isTransfer,
                isIncome = isIncome,
                mhaDefaultEligible = mhaEligible,
                sortOrder = existingSortOrder
            )
            repository.saveCategory(category)
            closeEditorDialog()
        }
    }

    fun requestDeleteCategory(id: Long) {
        _deleteConfirmId.value = id
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
        }
    }
}
