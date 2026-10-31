package app.overbay.orbit.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.getActiveBudgetMonths
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.domain.BudgetEngine
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
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

class BudgetsViewModel(
    private val repository: OrbitRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val currentMonth = LocalDateUtils.currentYearMonth()
    private val _selectedMonth = MutableStateFlow(currentMonth)
    private val _sortOption = MutableStateFlow(repository.preferences.value.budgetSortOption)
    private val _expandedCategoryId = MutableStateFlow<Long?>(null)
    private val _editingItem = MutableStateFlow<CategoryBudgetOverviewItem?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _showAddCategorySheet = MutableStateFlow(false)
    private val _initialAddCategoryId = MutableStateFlow<Long?>(null)
    private val _activeMonths = MutableStateFlow<List<String>>(listOf(currentMonth))

    init {
        viewModelScope.launch {
            val months = repository.getActiveBudgetMonths()
            val combined = (months + currentMonth).distinct().sortedDescending()
            _activeMonths.value = combined
        }

        viewModelScope.launch {
            repository.pendingBudgetMonth.collect { pending ->
                if (!pending.isNullOrBlank()) {
                    _selectedMonth.value = pending
                    repository.requestOpenBudgetMonth(null)
                }
            }
        }
    }

    private val dataFlow = combine(
        repository.categories,
        repository.budgets,
        repository.transactions
    ) { categories, budgets, transactions ->
        Triple(categories, budgets, transactions)
    }

    private val sheetFlow = combine(
        _editingItem,
        _showEditorSheet,
        _showAddCategorySheet,
        _initialAddCategoryId
    ) { editing, showEditor, showAdd, initialCatId ->
        BudgetSheetsState(editing, showEditor, showAdd, initialCatId)
    }

    private val controlFlow = combine(
        _selectedMonth,
        _sortOption,
        _expandedCategoryId,
        _activeMonths,
        sheetFlow
    ) { month, sort, expandedCatId, activeMonths, sheets ->
        BudgetsControlState(month, sort, expandedCatId, activeMonths, sheets)
    }

    val uiState: StateFlow<BudgetsUiState> = combine(
        dataFlow,
        controlFlow
    ) { (categories, budgets, transactions), control ->
        val today = LocalDateUtils.todayLocalDate()
        val todayIso = LocalDateUtils.formatIsoDate(today)
        val overview = BudgetEngine.calculateMonthlyOverview(
            month = control.month,
            categories = categories,
            budgets = budgets,
            transactions = transactions,
            todayDate = todayIso,
            sortOption = control.sortOption
        )
        val notables = BudgetEngine.extractNotables(overview)

        val isCurrent = control.month == currentMonth
        val maxFuture = LocalDateUtils.addMonthsToYearMonth(currentMonth, 24)
        val canGoNext = control.month < maxFuture
        val minPast = control.activeMonths.minOrNull() ?: LocalDateUtils.addMonthsToYearMonth(currentMonth, -36)
        val canGoPrev = control.month > minPast

        val (y, m) = LocalDateUtils.parseYearMonth(control.month) ?: Pair(today.year, today.monthNumber)
        val totalDaysInMonth = LocalDateUtils.daysInMonth(y, m)
        val dayOfMonth = if (isCurrent) today.dayOfMonth else totalDaysInMonth
        val daysRemaining = (totalDaysInMonth - dayOfMonth).coerceAtLeast(0)

        // Group transactions for this month across all categories
        val monthTxns = transactions.filter {
            it.effectiveDate.take(7) == control.month && !it.effectiveIsIgnored && !it.effectiveIsTransfer
        }
        val categoryTransactions = monthTxns.groupBy { it.effectiveCategoryId ?: 0L }

        BudgetsUiState(
            selectedMonth = control.month,
            monthLabel = LocalDateUtils.formatMonthLabel(control.month),
            isCurrentMonth = isCurrent,
            canGoNextMonth = canGoNext,
            canGoPreviousMonth = canGoPrev,
            daysRemaining = daysRemaining,
            overview = overview,
            notables = notables,
            activeMonths = control.activeMonths,
            sortOption = control.sortOption,
            expandedCategoryId = control.expandedCategoryId,
            categoryTransactions = categoryTransactions,
            editingItem = control.sheets.editingItem,
            showEditorSheet = control.sheets.showEditorSheet,
            showAddCategorySheet = control.sheets.showAddCategorySheet,
            initialAddCategoryId = control.sheets.initialAddCategoryId,
            isLoading = false
        )
    }.flowOn(computeDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState(isLoading = true)
    )

    fun onPreviousMonth() {
        _selectedMonth.value = LocalDateUtils.addMonthsToYearMonth(_selectedMonth.value, -1)
    }

    fun onNextMonth() {
        val maxFuture = LocalDateUtils.addMonthsToYearMonth(currentMonth, 24)
        if (_selectedMonth.value < maxFuture) {
            _selectedMonth.value = LocalDateUtils.addMonthsToYearMonth(_selectedMonth.value, 1)
        }
    }

    fun jumpToMonth(month: String) {
        _selectedMonth.value = month
    }

    fun onResetToCurrentMonth() {
        _selectedMonth.value = currentMonth
    }

    fun setSortOption(option: CategorySortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            repository.updatePreferences(repository.preferences.value.copy(budgetSortOption = option))
        }
    }

    fun toggleExpandCategory(categoryId: Long) {
        if (_expandedCategoryId.value == categoryId && !_showEditorSheet.value) {
            _expandedCategoryId.value = null
        } else {
            _expandedCategoryId.value = categoryId
            _showEditorSheet.value = false
            _editingItem.value = null
        }
    }

    fun dismissInspectorPane() {
        _expandedCategoryId.value = null
        _showEditorSheet.value = false
        _editingItem.value = null
        _showAddCategorySheet.value = false
    }

    fun openAddBudget() {
        _initialAddCategoryId.value = null
        _showAddCategorySheet.value = true
    }

    fun openAddBudgetPicker(initialCategoryId: Long? = null) {
        _initialAddCategoryId.value = initialCategoryId
        _showAddCategorySheet.value = true
    }

    fun closeAddBudgetPicker() {
        _showAddCategorySheet.value = false
        _initialAddCategoryId.value = null
    }

    fun openBudgetEditor(item: CategoryBudgetOverviewItem) {
        _expandedCategoryId.value = item.category.id
        _editingItem.value = item
        _showEditorSheet.value = true
    }

    fun closeBudgetEditor() {
        _showEditorSheet.value = false
        _editingItem.value = null
    }

    fun saveBudget(categoryId: Long, amountCents: Cents, rollover: Boolean) {
        viewModelScope.launch {
            repository.saveBudget(
                categoryId = categoryId,
                amountCents = amountCents,
                rollover = rollover
            )
            closeBudgetEditor()
            closeAddBudgetPicker()
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            repository.deleteBudget(id)
            closeBudgetEditor()
        }
    }

    fun calculate3MonthAverageSpend(categoryId: Long): Cents {
        val allTxns = repository.transactions.value.filter {
            !it.effectiveIsIgnored && !it.effectiveIsTransfer && it.effectiveCategoryId == categoryId
        }
        val months = (1..3).map { LocalDateUtils.addMonthsToYearMonth(currentMonth, -it) }
        val spend = months.map { m ->
            allTxns.filter { it.effectiveDate.take(7) == m }
                .filter { it.effectiveAmountCents < 0 }
                .sumOf { abs(it.effectiveAmountCents) }
        }
        val nonZero = spend.filter { it > 0L }
        return if (nonZero.isNotEmpty()) nonZero.sum() / nonZero.size else 0L
    }

    private data class BudgetSheetsState(
        val editingItem: CategoryBudgetOverviewItem?,
        val showEditorSheet: Boolean,
        val showAddCategorySheet: Boolean,
        val initialAddCategoryId: Long?
    )

    private data class BudgetsControlState(
        val month: String,
        val sortOption: CategorySortOption,
        val expandedCategoryId: Long?,
        val activeMonths: List<String>,
        val sheets: BudgetSheetsState
    )
}
