package app.overbay.orbit.ui.trends

import app.overbay.orbit.domain.CategorySpendingTrend
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.SpendingTrendsOverview
import app.overbay.orbit.ui.components.charts.SpendingBarItem

enum class TrendsPeriod(val label: String, val monthsCount: Int) {
    ONE_MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("ALL", 24)
}

enum class TrendsChartMode(val label: String) {
    CASH_FLOW("Cash Flow"),
    CATEGORY_TREND("Category History")
}

data class SpendingTrendsUiState(
    val overview: SpendingTrendsOverview = SpendingTrendsOverview(
        months = emptyList(),
        flowSeries = emptyList(),
        categoryTrends = emptyList(),
        topMerchants = emptyList(),
        totalPeriodSpendingCents = 0L,
        totalPeriodIncomeCents = 0L
    ),
    val selectedPeriod: TrendsPeriod = TrendsPeriod.SIX_MONTHS,
    val chartMode: TrendsChartMode = TrendsChartMode.CASH_FLOW,
    val selectedCategoryId: Long? = null,
    val scrubbedIndex: Int? = null,
    val scrubbedBarItem: SpendingBarItem? = null,
    val isLoading: Boolean = false
) {
    val selectedCategoryTrend: CategorySpendingTrend?
        get() = overview.categoryTrends.firstOrNull { it.category.id == selectedCategoryId }
            ?: overview.categoryTrends.firstOrNull()

    val totalPeriodNetCents: Cents
        get() = overview.totalPeriodIncomeCents - overview.totalPeriodSpendingCents

    val overallSavingsRatePercent: Double
        get() = if (overview.totalPeriodIncomeCents > 0L) {
            (totalPeriodNetCents.toDouble() / overview.totalPeriodIncomeCents.toDouble()) * 100.0
        } else 0.0

    val averageMonthlyExpensesCents: Cents
        get() = if (overview.months.isNotEmpty()) {
            overview.totalPeriodSpendingCents / overview.months.size
        } else 0L

    val averageMonthlyIncomeCents: Cents
        get() = if (overview.months.isNotEmpty()) {
            overview.totalPeriodIncomeCents / overview.months.size
        } else 0L

    val hasData: Boolean
        get() = overview.totalPeriodSpendingCents > 0L || overview.totalPeriodIncomeCents > 0L
}
