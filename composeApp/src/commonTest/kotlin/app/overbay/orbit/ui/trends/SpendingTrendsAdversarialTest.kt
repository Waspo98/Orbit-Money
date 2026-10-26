package app.overbay.orbit.ui.trends

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.SpendingTrendsEngine
import app.overbay.orbit.ui.components.charts.SpendingBarItem
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpendingTrendsAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: SpendingTrendsViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = SpendingTrendsViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testEmptyTransactions_hasDataFalse_andNoDivisionByZero() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasData, "Empty state must have hasData = false")
        assertEquals(0L, state.overview.totalPeriodSpendingCents)
        assertEquals(0L, state.overview.totalPeriodIncomeCents)
        assertEquals(0L, state.totalPeriodNetCents)
        assertEquals(0.0, state.overallSavingsRatePercent)
        assertEquals(0L, state.averageMonthlyExpensesCents)
        assertEquals(0L, state.averageMonthlyIncomeCents)
        assertTrue(state.overview.categoryTrends.isEmpty())
        assertTrue(state.overview.topMerchants.isEmpty())
        assertNull(state.selectedCategoryTrend)
        assertFalse(state.isLoading)

        collectJob.cancel()
    }

    @Test
    fun testAllIncomeZeroExpenses_savingsRate100_andPositiveNet() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val prevMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -1)

        val catSalary = Category(id = 100L, name = "Salary", isIncome = true, isTransfer = false)
        repository.saveCategory(catSalary)

        // Seed only income transactions ($3,000 + $2,000 = $5,000)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "Company Inc", originalAmountCents = 300000L, originalCategoryId = 100L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$prevMonth-15", originalMerchant = "Bonus", originalAmountCents = 200000L, originalCategoryId = 100L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasData, "Has data should be true with income")
        assertEquals(500000L, state.overview.totalPeriodIncomeCents)
        assertEquals(0L, state.overview.totalPeriodSpendingCents)
        assertEquals(500000L, state.totalPeriodNetCents)
        assertEquals(100.0, state.overallSavingsRatePercent, "Savings rate with zero expenses must be exactly 100%")

        // Flow series check: each month with income and 0 expense must have 100% savings rate
        val currentFlow = state.overview.flowSeries.firstOrNull { it.month == currentMonth }
        assertNotNull(currentFlow)
        assertEquals(300000L, currentFlow.incomeCents)
        assertEquals(0L, currentFlow.expensesCents)
        assertEquals(300000L, currentFlow.netCents)
        assertEquals(100.0, currentFlow.savingsRatePercent)

        // Category trends and top merchants should be empty because there were $0 expenses
        assertTrue(state.overview.categoryTrends.isEmpty(), "Zero expenses must produce empty category trends")
        assertTrue(state.overview.topMerchants.isEmpty(), "Zero expenses must produce empty top merchants")

        collectJob.cancel()
    }

    @Test
    fun testAllExpensesZeroIncome_savingsRateZero_andNegativeNet() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val prevMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -1)

        val catGroceries = Category(id = 200L, name = "Groceries", isIncome = false, isTransfer = false)
        val catUtilities = Category(id = 201L, name = "Utilities", isIncome = false, isTransfer = false)
        repository.saveCategory(catGroceries)
        repository.saveCategory(catUtilities)

        // Seed only expense transactions ($150 + $75 = $225)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-05", originalMerchant = "Grocery Mart", originalAmountCents = -15000L, originalCategoryId = 200L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$prevMonth-10", originalMerchant = "Electric Co", originalAmountCents = -7500L, originalCategoryId = 201L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasData, "Has data should be true with expenses")
        assertEquals(0L, state.overview.totalPeriodIncomeCents)
        assertEquals(22500L, state.overview.totalPeriodSpendingCents)
        assertEquals(-22500L, state.totalPeriodNetCents)
        assertEquals(0.0, state.overallSavingsRatePercent, "Savings rate with zero income must be 0%")

        // Flow series check: 0 income must have 0% savings rate
        val currentFlow = state.overview.flowSeries.firstOrNull { it.month == currentMonth }
        assertNotNull(currentFlow)
        assertEquals(0L, currentFlow.incomeCents)
        assertEquals(15000L, currentFlow.expensesCents)
        assertEquals(-15000L, currentFlow.netCents)
        assertEquals(0.0, currentFlow.savingsRatePercent)

        // Category trends should have Groceries and Utilities
        assertEquals(2, state.overview.categoryTrends.size)
        assertEquals("Grocery Mart", state.overview.topMerchants.first().merchant)

        collectJob.cancel()
    }

    @Test
    fun testHorizonTransitions_andDynamicDateBoundaries() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Test 1M
        viewModel.setPeriod(TrendsPeriod.ONE_MONTH)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.overview.months.size)
        assertEquals(currentMonth, viewModel.uiState.value.overview.months.last())

        // Test 3M
        viewModel.setPeriod(TrendsPeriod.THREE_MONTHS)
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.overview.months.size)
        assertEquals(currentMonth, viewModel.uiState.value.overview.months.last())
        val expected3MStart = LocalDateUtils.addMonthsToYearMonth(currentMonth, -2)
        assertEquals(expected3MStart, viewModel.uiState.value.overview.months.first())

        // Test 6M
        viewModel.setPeriod(TrendsPeriod.SIX_MONTHS)
        testScheduler.advanceUntilIdle()
        assertEquals(6, viewModel.uiState.value.overview.months.size)
        val expected6MStart = LocalDateUtils.addMonthsToYearMonth(currentMonth, -5)
        assertEquals(expected6MStart, viewModel.uiState.value.overview.months.first())

        // Test 1Y
        viewModel.setPeriod(TrendsPeriod.ONE_YEAR)
        testScheduler.advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.overview.months.size)
        val expected1YStart = LocalDateUtils.addMonthsToYearMonth(currentMonth, -11)
        assertEquals(expected1YStart, viewModel.uiState.value.overview.months.first())

        // Test ALL (24M)
        viewModel.setPeriod(TrendsPeriod.ALL)
        testScheduler.advanceUntilIdle()
        assertEquals(24, viewModel.uiState.value.overview.months.size)
        val expectedAllStart = LocalDateUtils.addMonthsToYearMonth(currentMonth, -23)
        assertEquals(expectedAllStart, viewModel.uiState.value.overview.months.first())

        // Verify chronological monotonicity across all months
        val months = viewModel.uiState.value.overview.months
        for (i in 0 until months.size - 1) {
            assertTrue(months[i] < months[i + 1], "Months must be strictly monotonically increasing: ${months[i]} < ${months[i+1]}")
        }

        collectJob.cancel()
    }

    @Test
    fun testSelectedCategoryDeletionOrDeselect_fallsBackCleanly() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val catA = Category(id = 301L, name = "Dining Out", isIncome = false, isTransfer = false)
        val catB = Category(id = 302L, name = "Entertainment", isIncome = false, isTransfer = false)
        repository.saveCategory(catA)
        repository.saveCategory(catB)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Bistro", originalAmountCents = -4500L, originalCategoryId = 301L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-04", originalMerchant = "Cinema", originalAmountCents = -3000L, originalCategoryId = 302L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Select Category A
        viewModel.selectCategory(301L)
        testScheduler.advanceUntilIdle()
        assertEquals(TrendsChartMode.CATEGORY_TREND, viewModel.uiState.value.chartMode)
        assertEquals(301L, viewModel.uiState.value.selectedCategoryId)
        assertEquals("Dining Out", viewModel.uiState.value.selectedCategoryTrend?.category?.name)

        // 2. Explicit deselect via clearCategorySelection
        viewModel.clearCategorySelection()
        testScheduler.advanceUntilIdle()
        assertEquals(TrendsChartMode.CASH_FLOW, viewModel.uiState.value.chartMode)
        assertNull(viewModel.uiState.value.selectedCategoryId)

        // 3. Re-select Category A, then select null
        viewModel.selectCategory(301L)
        testScheduler.advanceUntilIdle()
        assertEquals(301L, viewModel.uiState.value.selectedCategoryId)

        viewModel.selectCategory(null)
        testScheduler.advanceUntilIdle()
        assertEquals(TrendsChartMode.CASH_FLOW, viewModel.uiState.value.chartMode)
        assertNull(viewModel.uiState.value.selectedCategoryId)

        // 4. Select Category A, then delete Category A
        viewModel.selectCategory(301L)
        testScheduler.advanceUntilIdle()
        assertEquals(301L, viewModel.uiState.value.selectedCategoryId)

        // Delete Category A from repository
        repository.deleteCategory(301L)
        testScheduler.advanceUntilIdle()

        // State must fall back cleanly without crashing
        val stateAfterDelete = viewModel.uiState.value
        // Either falls back to Cat B or null, but must not hold invalid 301L
        assertFalse(stateAfterDelete.overview.categoryTrends.any { it.category.id == 301L })

        collectJob.cancel()
    }

    @Test
    fun testOutOfBoundsBarScrubbing_handlesGracefully() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Negative index with null item
        viewModel.onScrubBar(-1, null)
        testScheduler.advanceUntilIdle()
        assertEquals(-1, viewModel.uiState.value.scrubbedIndex)
        assertNull(viewModel.uiState.value.scrubbedBarItem)

        // Huge out of bounds index
        viewModel.onScrubBar(99999, null)
        testScheduler.advanceUntilIdle()
        assertEquals(99999, viewModel.uiState.value.scrubbedIndex)
        assertNull(viewModel.uiState.value.scrubbedBarItem)

        // Valid item with out of bounds index
        val item = SpendingBarItem(label = "Future", expenseCents = 12345L)
        viewModel.onScrubBar(-5, item)
        testScheduler.advanceUntilIdle()
        assertEquals(-5, viewModel.uiState.value.scrubbedIndex)
        assertEquals(item, viewModel.uiState.value.scrubbedBarItem)

        // Clear scrub
        viewModel.clearScrub()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.scrubbedIndex)
        assertNull(viewModel.uiState.value.scrubbedBarItem)

        collectJob.cancel()
    }

    @Test
    fun testEngineYearBoundaryCrossing_preservesCorrectMonthDeltas() {
        // Direct adversarial engine test across leap year and multi-year spans
        val overview = SpendingTrendsEngine.computeTrends(
            transactions = emptyList(),
            categories = emptyList(),
            budgets = emptyList(),
            monthsCount = 24,
            endMonth = "2026-01"
        )

        assertEquals(24, overview.months.size)
        assertEquals("2024-02", overview.months.first())
        assertEquals("2026-01", overview.months.last())

        // Verify each month transition is exactly +1 month
        for (i in 0 until overview.months.size - 1) {
            val curr = overview.months[i]
            val next = overview.months[i + 1]
            val expectedNext = LocalDateUtils.addMonthsToYearMonth(curr, 1)
            assertEquals(expectedNext, next, "Month transition from $curr must yield $expectedNext, got $next")
        }
    }

    @Test
    fun testTransactionsOutsideHorizon_areExcludedFromTotalsAndRankings() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val inRangeMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -2) // 3M horizon includes this
        val outOfRangeMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -5) // 3M horizon excludes this

        val cat = Category(id = 401L, name = "Software", isIncome = false, isTransfer = false)
        repository.saveCategory(cat)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$inRangeMonth-15", originalMerchant = "JetBrains", originalAmountCents = -20000L, originalCategoryId = 401L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$outOfRangeMonth-15", originalMerchant = "Old Service", originalAmountCents = -80000L, originalCategoryId = 401L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.setPeriod(TrendsPeriod.THREE_MONTHS)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20000L, state.overview.totalPeriodSpendingCents, "Out-of-range transaction must be excluded")
        assertEquals(1, state.overview.topMerchants.size)
        assertEquals("JetBrains", state.overview.topMerchants.first().merchant)

        collectJob.cancel()
    }

    @Test
    fun testExtremeValues_billionDollarTransactions_noOverflow() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val cat = Category(id = 501L, name = "Acquisitions", isIncome = false, isTransfer = false)
        val catCapital = Category(id = 502L, name = "Capital", isIncome = true, isTransfer = false)
        repository.saveCategory(cat)
        repository.saveCategory(catCapital)

        // $10 Billion = 1,000,000,000,000 cents
        val tenBillionCents = 1_000_000_000_000L
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "VC Fund", originalAmountCents = tenBillionCents, originalCategoryId = 502L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Startup Buyout", originalAmountCents = -tenBillionCents / 2, originalCategoryId = 501L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(tenBillionCents, state.overview.totalPeriodIncomeCents)
        assertEquals(tenBillionCents / 2, state.overview.totalPeriodSpendingCents)
        assertEquals(tenBillionCents / 2, state.totalPeriodNetCents)
        assertEquals(50.0, state.overallSavingsRatePercent)

        collectJob.cancel()
    }

    @Test
    fun testAllTransactionsIgnoredOrTransfers_resultsInCleanEmptyState() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val cat = Category(id = 601L, name = "Internal", isIncome = false, isTransfer = true)
        repository.saveCategory(cat)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "Account Transfer", originalAmountCents = -50000L, isTransfer = true))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Ignored Expense", originalAmountCents = -30000L, editedIsIgnored = true))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasData, "When all txns are ignored or transfers, hasData must be false")
        assertEquals(0L, state.overview.totalPeriodSpendingCents)
        assertEquals(0L, state.overview.totalPeriodIncomeCents)
        assertTrue(state.overview.categoryTrends.isEmpty())
        assertTrue(state.overview.topMerchants.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun testMedianSpentCalculation_oddAndEvenMonthCounts() {
        val cat = Category(id = 701L, name = "Dining", isIncome = false, isTransfer = false)

        // 3 months (odd): spend values = 100, 300, 200 -> sorted = 100, 200, 300 -> median = 200
        val txns3 = listOf(
            Transaction(id = 1L, accountId = 1L, originalDate = "2026-01-05", originalMerchant = "A", originalAmountCents = -10000L, originalCategoryId = 701L),
            Transaction(id = 2L, accountId = 1L, originalDate = "2026-02-05", originalMerchant = "B", originalAmountCents = -30000L, originalCategoryId = 701L),
            Transaction(id = 3L, accountId = 1L, originalDate = "2026-03-05", originalMerchant = "C", originalAmountCents = -20000L, originalCategoryId = 701L)
        )
        val overview3 = SpendingTrendsEngine.computeTrends(
            transactions = txns3,
            categories = listOf(cat),
            monthsCount = 3,
            endMonth = "2026-03"
        )
        val trend3 = overview3.categoryTrends.first()
        assertEquals(20000L, trend3.medianSpentCents, "Odd median should be middle element")

        // 4 months (even): spend values = 100, 200, 300, 400 -> sorted = 100, 200, 300, 400 -> median = (200 + 300) / 2 = 250
        val txns4 = txns3 + Transaction(id = 4L, accountId = 1L, originalDate = "2026-04-05", originalMerchant = "D", originalAmountCents = -40000L, originalCategoryId = 701L)
        val overview4 = SpendingTrendsEngine.computeTrends(
            transactions = txns4,
            categories = listOf(cat),
            monthsCount = 4,
            endMonth = "2026-04"
        )
        val trend4 = overview4.categoryTrends.first()
        assertEquals(25000L, trend4.medianSpentCents, "Even median should be average of two middle elements")
    }
}

