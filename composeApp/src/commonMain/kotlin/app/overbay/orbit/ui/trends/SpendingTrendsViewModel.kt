package app.overbay.orbit.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.SpendingTrendsEngine
import app.overbay.orbit.ui.components.charts.SpendingBarItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SpendingTrendsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TrendsPeriod.SIX_MONTHS)
    private val _chartMode = MutableStateFlow(TrendsChartMode.CASH_FLOW)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _scrubbedIndex = MutableStateFlow<Int?>(null)
    private val _scrubbedBarItem = MutableStateFlow<SpendingBarItem?>(null)

    val uiState: StateFlow<SpendingTrendsUiState> = combine(
        combine(
            repository.transactions,
            repository.categories,
            repository.budgets
        ) { txns, cats, budgets ->
            RepoData(txns, cats, budgets)
        },
        combine(
            _selectedPeriod,
            _chartMode,
            _selectedCategoryId
        ) { period, mode, categoryId ->
            SelectionData(period, mode, categoryId)
        },
        combine(
            _scrubbedIndex,
            _scrubbedBarItem
        ) { scrubIdx, scrubItem ->
            ScrubData(scrubIdx, scrubItem)
        }
    ) { repoData, selectionData, scrubData ->
        val txns = repoData.transactions
        val cats = repoData.categories
        val budgets = repoData.budgets

        val period = selectionData.period
        val mode = selectionData.mode
        val categoryId = selectionData.categoryId

        val scrubIdx = scrubData.scrubIndex
        val scrubItem = scrubData.scrubItem

        val latestMonth = LocalDateUtils.currentYearMonth()
        val overview = SpendingTrendsEngine.computeTrends(
            transactions = txns,
            categories = cats,
            budgets = budgets,
            monthsCount = period.monthsCount,
            endMonth = latestMonth
        )

        val validCategoryId = if (categoryId != null && overview.categoryTrends.any { it.category.id == categoryId }) {
            categoryId
        } else if (mode == TrendsChartMode.CATEGORY_TREND) {
            overview.categoryTrends.firstOrNull()?.category?.id
        } else {
            null
        }

        SpendingTrendsUiState(
            overview = overview,
            selectedPeriod = period,
            chartMode = mode,
            selectedCategoryId = validCategoryId,
            scrubbedIndex = scrubIdx,
            scrubbedBarItem = scrubItem,
            isLoading = false
        )
    }.stateIn(
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

    private data class RepoData(
        val transactions: List<app.overbay.orbit.data.model.Transaction>,
        val categories: List<app.overbay.orbit.data.model.Category>,
        val budgets: List<app.overbay.orbit.data.model.CategoryBudget>
    )

    private data class SelectionData(
        val period: TrendsPeriod,
        val mode: TrendsChartMode,
        val categoryId: Long?
    )

    private data class ScrubData(
        val scrubIndex: Int?,
        val scrubItem: SpendingBarItem?
    )
}
