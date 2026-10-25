package app.overbay.orbit.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.domain.BudgetEngine
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class BudgetsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val currentMonth = LocalDateUtils.currentYearMonth()
    private val _selectedMonth = MutableStateFlow(currentMonth)
    private val _sortOption = MutableStateFlow(CategorySortOption.OVER_BUDGET_FIRST)
    private val _editingItem = MutableStateFlow<CategoryBudgetOverviewItem?>(null)
    private val _showEditorSheet = MutableStateFlow(false)

    private val dataFlow = combine(
        repository.categories,
        repository.budgets,
        repository.transactions
    ) { categories, budgets, transactions ->
        Triple(categories, budgets, transactions)
    }

    private val controlFlow = combine(
        _selectedMonth,
        _sortOption,
        _editingItem,
        _showEditorSheet
    ) { month, sort, editing, showSheet ->
        BudgetsControlState(month, sort, editing, showSheet)
    }

    val uiState: StateFlow<BudgetsUiState> = combine(
        dataFlow,
        controlFlow
    ) { (categories, budgets, transactions), control ->
        val todayIso = LocalDateUtils.todayIsoDate()
        val overview = BudgetEngine.calculateMonthlyOverview(
            month = control.month,
            categories = categories,
            budgets = budgets,
            transactions = transactions,
            todayDate = todayIso,
            sortOption = control.sortOption
        )

        val isCurrent = control.month == currentMonth
        val canGoNext = control.month < currentMonth

        BudgetsUiState(
            selectedMonth = control.month,
            monthLabel = LocalDateUtils.formatMonthLabel(control.month),
            isCurrentMonth = isCurrent,
            canGoNextMonth = canGoNext,
            overview = overview,
            sortOption = control.sortOption,
            editingItem = control.editingItem,
            showEditorSheet = control.showEditorSheet,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState(isLoading = true)
    )

    fun onPreviousMonth() {
        _selectedMonth.value = LocalDateUtils.addMonthsToYearMonth(_selectedMonth.value, -1)
    }

    fun onNextMonth() {
        if (_selectedMonth.value < currentMonth) {
            _selectedMonth.value = LocalDateUtils.addMonthsToYearMonth(_selectedMonth.value, 1)
        }
    }

    fun onResetToCurrentMonth() {
        _selectedMonth.value = currentMonth
    }

    fun setSortOption(option: CategorySortOption) {
        _sortOption.value = option
    }

    fun openBudgetEditor(item: CategoryBudgetOverviewItem) {
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
}

private data class BudgetsControlState(
    val month: String,
    val sortOption: CategorySortOption,
    val editingItem: CategoryBudgetOverviewItem?,
    val showEditorSheet: Boolean
)
