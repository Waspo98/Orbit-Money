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
    private val _flowScrubIndex = MutableStateFlow<Int?>(null)
    private val _flowScrubItem = MutableStateFlow<SpendingBarItem?>(null)
    private val _categoryScrubIndex = MutableStateFlow<Int?>(null)
    private val _categoryScrubItem = MutableStateFlow<SpendingBarItem?>(null)
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
            _flowScrubIndex,
            _flowScrubItem,
            _categoryScrubIndex,
            _categoryScrubItem,
            _pendingInspectMonth
        ) { flowIdx, flowItem, catIdx, catItem, inspectMonth ->
            ScrubData(flowIdx, flowItem, catIdx, catItem, inspectMonth)
        },
        combine(
            _isInspectMonthDialogOpen,
            _isBudgetModalOpen
        ) { isInspectOpen, isBudgetOpen -> isInspectOpen to isBudgetOpen }
    ) { computed, (mode, categoryId), scrubData, (isInspectOpen, isBudgetOpen) ->
        val overview = computed.overview
        val budgets = computed.budgets

        val validCategoryId = if (categoryId != null && overview.categoryTrends.any { it.category.id == categoryId }) {
            categoryId
        } else {
            null
        }

        val activeCategoryId = validCategoryId ?: overview.categoryTrends.firstOrNull()?.category?.id
        val categoryBudget = activeCategoryId?.let { catId -> budgets.find { it.categoryId == catId } }

        SpendingTrendsUiState(
            overview = overview,
            selectedPeriod = computed.period,
            chartMode = mode,
            selectedCategoryId = validCategoryId,
            selectedCategoryBudget = categoryBudget,
            flowScrubIndex = scrubData.flowIndex,
            flowScrubItem = scrubData.flowItem,
            categoryScrubIndex = scrubData.categoryIndex,
            categoryScrubItem = scrubData.categoryItem,
            pendingInspectMonth = scrubData.inspectMonth,
            isInspectMonthDialogOpen = isInspectOpen,
            isBudgetModalOpen = isBudgetOpen,
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

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        _chartMode.value = if (categoryId != null) TrendsChartMode.CATEGORY_TREND else TrendsChartMode.CASH_FLOW
        clearScrub()
    }

    fun onScrubFlowBar(index: Int?, item: SpendingBarItem?) {
        _flowScrubIndex.value = index
        _flowScrubItem.value = item
    }

    fun onScrubCategoryBar(index: Int?, item: SpendingBarItem?) {
        _categoryScrubIndex.value = index
        _categoryScrubItem.value = item
    }

    fun onScrubBar(index: Int?, item: SpendingBarItem?) = onScrubFlowBar(index, item)

    fun selectScrubItem(index: Int?, item: SpendingBarItem?) = onScrubBar(index, item)

    fun clearScrub() {
        _flowScrubIndex.value = null
        _flowScrubItem.value = null
        _categoryScrubIndex.value = null
        _categoryScrubItem.value = null
    }

    fun clearCategorySelection() {
        selectCategory(null)
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

    private data class ScrubData(
        val flowIndex: Int?,
        val flowItem: SpendingBarItem?,
        val categoryIndex: Int?,
        val categoryItem: SpendingBarItem?,
        val inspectMonth: String?
    )
}
