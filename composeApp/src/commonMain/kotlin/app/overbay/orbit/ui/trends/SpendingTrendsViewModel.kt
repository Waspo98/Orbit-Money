package app.overbay.orbit.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.SpendingTrendsEngine
import app.overbay.orbit.domain.SpendingTrendsOverview
import app.overbay.orbit.ui.components.charts.SpendingBarItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpendingTrendsViewModel(
    private val repository: OrbitRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TrendsPeriod.SIX_MONTHS)
    private val _chartMode = MutableStateFlow(TrendsChartMode.CASH_FLOW)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _scrubbedIndex = MutableStateFlow<Int?>(null)
    private val _scrubbedBarItem = MutableStateFlow<SpendingBarItem?>(null)
    private val _pendingInspectMonth = MutableStateFlow<String?>(null)
    private val _isInspectMonthDialogOpen = MutableStateFlow(false)
    private val _isBudgetModalOpen = MutableStateFlow(false)

    private val computedTrendsFlow = combine(
        repository.transactions,
        repository.categories,
        repository.budgets,
        _selectedPeriod
    ) { txns, cats, budgets, period ->
        val latestMonth = LocalDateUtils.currentYearMonth()
        val overview = SpendingTrendsEngine.computeTrends(
            transactions = txns,
            categories = cats,
            budgets = budgets,
            monthsCount = period.monthsCount,
            endMonth = latestMonth
        )
        ComputedTrendsBundle(
            overview = overview,
            budgets = budgets,
            period = period
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<SpendingTrendsUiState> = combine(
        computedTrendsFlow,
        combine(
            _chartMode,
            _selectedCategoryId
        ) { mode, categoryId -> mode to categoryId },
        combine(
            _scrubbedIndex,
            _scrubbedBarItem,
            _pendingInspectMonth,
            _isInspectMonthDialogOpen,
            _isBudgetModalOpen
        ) { scrubIdx, scrubItem, inspectMonth, isInspectOpen, isBudgetOpen ->
            ModalAndScrubData(scrubIdx, scrubItem, inspectMonth, isInspectOpen, isBudgetOpen)
        }
    ) { computed, (mode, categoryId), modalData ->
        val overview = computed.overview
        val budgets = computed.budgets

        val validCategoryId = if (categoryId != null && overview.categoryTrends.any { it.category.id == categoryId }) {
            categoryId
        } else if (mode == TrendsChartMode.CATEGORY_TREND) {
            overview.categoryTrends.firstOrNull()?.category?.id
        } else {
            null
        }

        val categoryBudget = validCategoryId?.let { catId -> budgets.find { it.categoryId == catId } }

        SpendingTrendsUiState(
            overview = overview,
            selectedPeriod = computed.period,
            chartMode = mode,
            selectedCategoryId = validCategoryId,
            selectedCategoryBudget = categoryBudget,
            scrubbedIndex = modalData.scrubIndex,
            scrubbedBarItem = modalData.scrubItem,
            pendingInspectMonth = modalData.inspectMonth,
            isInspectMonthDialogOpen = modalData.isInspectOpen,
            isBudgetModalOpen = modalData.isBudgetOpen,
            isLoading = false
        )
    }.flowOn(computeDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpendingTrendsUiState(isLoading = true)
    )

    fun setPeriod(period: TrendsPeriod) {
        _selectedPeriod.value = period
        clearScrub()
    }

    fun setChartMode(mode: TrendsChartMode) {
        _chartMode.value = mode
        if (mode == TrendsChartMode.CASH_FLOW) {
            _selectedCategoryId.value = null
        }
        clearScrub()
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        if (categoryId != null) {
            _chartMode.value = TrendsChartMode.CATEGORY_TREND
        } else {
            _chartMode.value = TrendsChartMode.CASH_FLOW
        }
        clearScrub()
    }

    fun onScrubBar(index: Int?, item: SpendingBarItem?) {
        _scrubbedIndex.value = index
        _scrubbedBarItem.value = item
    }

    fun selectScrubItem(index: Int?, item: SpendingBarItem?) = onScrubBar(index, item)

    fun clearScrub() {
        _scrubbedIndex.value = null
        _scrubbedBarItem.value = null
    }

    fun clearCategorySelection() {
        _selectedCategoryId.value = null
        _chartMode.value = TrendsChartMode.CASH_FLOW
        clearScrub()
    }

    fun openInspectMonthDialog(month: String) {
        _pendingInspectMonth.value = month
        _isInspectMonthDialogOpen.value = true
    }

    fun closeInspectMonthDialog() {
        _isInspectMonthDialogOpen.value = false
        _pendingInspectMonth.value = null
    }

    fun openBudgetModal() {
        _isBudgetModalOpen.value = true
    }

    fun closeBudgetModal() {
        _isBudgetModalOpen.value = false
    }

    fun saveBudget(categoryId: Long, amountCents: Cents, rollover: Boolean) {
        viewModelScope.launch(computeDispatcher) {
            repository.saveBudget(categoryId, amountCents, rollover)
            _isBudgetModalOpen.value = false
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch(computeDispatcher) {
            repository.deleteBudget(budgetId)
            _isBudgetModalOpen.value = false
        }
    }

    private data class ComputedTrendsBundle(
        val overview: SpendingTrendsOverview,
        val budgets: List<app.overbay.orbit.data.model.CategoryBudget>,
        val period: TrendsPeriod
    )

    private data class ModalAndScrubData(
        val scrubIndex: Int?,
        val scrubItem: SpendingBarItem?,
        val inspectMonth: String?,
        val isInspectOpen: Boolean,
        val isBudgetOpen: Boolean
    )
}
