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

    private val dialogFlow = combine(
        _inspectingCategoryId,
        _editingCategory,
        _showEditorDialog,
        _deleteConfirmId
    ) { inspectingId, editing, showDialog, deleteId ->
        CategoriesUiState(
            inspectingCategoryId = inspectingId,
            editingCategory = editing,
            showEditorDialog = showDialog,
            deleteConfirmCategoryId = deleteId
        )
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.categories,
        repository.transactions,
        dialogFlow
    ) { categories, transactions, dialogState ->
        // Count and sum per category
        val txnCountMap = mutableMapOf<Long, Int>()
        val txnSumMap = mutableMapOf<Long, Cents>()

        for (txn in transactions) {
            val catId = txn.effectiveCategoryId ?: continue
            txnCountMap[catId] = (txnCountMap[catId] ?: 0) + 1
            txnSumMap[catId] = (txnSumMap[catId] ?: 0L) + abs(txn.effectiveAmountCents)
        }

        val summaries = categories.map { cat ->
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
        ).filter { it.items.isNotEmpty() }

        dialogState.copy(
            categories = categories,
            groups = groups,
            totalCategoriesCount = categories.size
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
            val category = Category(
                id = id,
                name = name,
                color = color,
                icon = icon,
                isTransfer = isTransfer,
                isIncome = isIncome,
                mhaDefaultEligible = mhaEligible
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
