package app.overbay.orbit.ui.trends

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
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
class SpendingTrendsViewModelTest {

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
    fun testInitialState_computesOverviewAndFlow() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val prevMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -1)

        val catGroceries = Category(id = 1L, name = "Groceries", isIncome = false, isTransfer = false)
        val catSalary = Category(id = 2L, name = "Salary", isIncome = true, isTransfer = false)
        repository.saveCategory(catGroceries)
        repository.saveCategory(catSalary)

        // Seed income & expenses
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "Employer", originalAmountCents = 500000L, originalCategoryId = 2L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-05", originalMerchant = "Supermarket", originalAmountCents = -120000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "$prevMonth-05", originalMerchant = "Supermarket", originalAmountCents = -100000L, originalCategoryId = 1L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TrendsPeriod.SIX_MONTHS, state.selectedPeriod)
        assertEquals(TrendsChartMode.CASH_FLOW, state.chartMode)
        assertFalse(state.isLoading)

        // Check totals
        assertEquals(220000L, state.overview.totalPeriodSpendingCents)
        assertEquals(500000L, state.overview.totalPeriodIncomeCents)
        assertEquals(280000L, state.totalPeriodNetCents)
        assertTrue(state.overallSavingsRatePercent > 0.0)

        // Top merchants: Supermarket should lead
        assertEquals(1, state.overview.topMerchants.size)
        assertEquals("Supermarket", state.overview.topMerchants.first().merchant)
        assertEquals(220000L, state.overview.topMerchants.first().totalSpentCents)

        collectJob.cancel()
    }

    @Test
    fun testPeriodSwitching_adjustsMonthsCount() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 3 Months
        viewModel.setPeriod(TrendsPeriod.THREE_MONTHS)
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.overview.months.size)
        assertEquals(TrendsPeriod.THREE_MONTHS, viewModel.uiState.value.selectedPeriod)

        // 1 Year
        viewModel.setPeriod(TrendsPeriod.ONE_YEAR)
        testScheduler.advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.overview.months.size)

        // 1 Month
        viewModel.setPeriod(TrendsPeriod.ONE_MONTH)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.overview.months.size)

        // ALL (24 Months standard history)
        viewModel.setPeriod(TrendsPeriod.ALL)
        testScheduler.advanceUntilIdle()
        assertEquals(24, viewModel.uiState.value.overview.months.size)

        collectJob.cancel()
    }

    @Test
    fun testCategoryDrilldown_switchesToCategoryTrendMode() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val catFood = Category(id = 10L, name = "Food", isIncome = false, isTransfer = false)
        val catTech = Category(id = 20L, name = "Tech", isIncome = false, isTransfer = false)
        repository.saveCategory(catFood)
        repository.saveCategory(catTech)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Diner", originalAmountCents = -4000L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-03", originalMerchant = "App Store", originalAmountCents = -1500L, originalCategoryId = 20L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Select Food category
        viewModel.selectCategory(10L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(10L, state.selectedCategoryId)
        assertEquals(TrendsChartMode.CATEGORY_TREND, state.chartMode)
        assertNotNull(state.selectedCategoryTrend)
        assertEquals("Food", state.selectedCategoryTrend?.category?.name)
        assertEquals(4000L, state.selectedCategoryTrend?.totalSpentCents)

        // Reset to Cash Flow
        viewModel.setChartMode(TrendsChartMode.CASH_FLOW)
        testScheduler.advanceUntilIdle()
        assertEquals(TrendsChartMode.CASH_FLOW, viewModel.uiState.value.chartMode)
        assertNull(viewModel.uiState.value.selectedCategoryId)

        collectJob.cancel()
    }

    @Test
    fun testEmptyTransactions_handlesGracefullyWithoutDivisionByZero() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.overview.totalPeriodSpendingCents)
        assertEquals(0L, state.overview.totalPeriodIncomeCents)
        assertEquals(0L, state.totalPeriodNetCents)
        assertEquals(0.0, state.overallSavingsRatePercent)
        assertTrue(state.overview.categoryTrends.isEmpty())
        assertTrue(state.overview.topMerchants.isEmpty())
        assertFalse(state.isLoading)

        collectJob.cancel()
    }

    @Test
    fun testExcludesTransfersAndIgnoredTransactions() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val catDining = Category(id = 1L, name = "Dining", isIncome = false, isTransfer = false)
        repository.saveCategory(catDining)

        // 1 normal expense, 1 transfer, 1 ignored expense
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "Cafe", originalAmountCents = -1500L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Transfer to Savings", originalAmountCents = -50000L, isTransfer = true))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "$currentMonth-03", originalMerchant = "Reimbursed Business", originalAmountCents = -8000L, editedIsIgnored = true))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Only the $15.00 Cafe expense should be counted
        assertEquals(1500L, viewModel.uiState.value.overview.totalPeriodSpendingCents)
        assertEquals(1, viewModel.uiState.value.overview.topMerchants.size)
        assertEquals("Cafe", viewModel.uiState.value.overview.topMerchants.first().merchant)

        collectJob.cancel()
    }

    @Test
    fun testScrubbing_updatesScrubbedSelectionAndClears() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val item = SpendingBarItem(label = "Apr", expenseCents = 15000L, incomeCents = 25000L)
        viewModel.selectScrubItem(index = 2, item = item)
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.scrubbedIndex)
        assertEquals(item, viewModel.uiState.value.scrubbedBarItem)

        // Clear scrub
        viewModel.selectScrubItem(null, null)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.scrubbedIndex)
        assertNull(viewModel.uiState.value.scrubbedBarItem)

        collectJob.cancel()
    }

    @Test
    fun testOverBudgetFlagging_identifiesCategoryExceedingBudget() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val cat = Category(id = 5L, name = "Entertainment", isIncome = false, isTransfer = false)
        repository.saveCategory(cat)
        repository.saveBudget(categoryId = 5L, amountCents = 10000L) // $100 budget

        // Spend $150 in current month -> over budget
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-10", originalMerchant = "Concert", originalAmountCents = -15000L, originalCategoryId = 5L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val trend = viewModel.uiState.value.overview.categoryTrends.find { it.category.id == 5L }
        assertNotNull(trend)
        assertEquals(1, trend.overBudgetMonthCount)
        assertTrue(trend.monthlySeries.any { it.isOverBudget })

        collectJob.cancel()
    }

    @Test
    fun testTopMerchants_sortedDescendingBySpend() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val cat = Category(id = 1L, name = "Shopping", isIncome = false, isTransfer = false)
        repository.saveCategory(cat)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "Small Store", originalAmountCents = -2000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Big Department", originalAmountCents = -50000L, originalCategoryId = 1L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val merchants = viewModel.uiState.value.overview.topMerchants
        assertEquals(2, merchants.size)
        assertEquals("Big Department", merchants[0].merchant)
        assertEquals(50000L, merchants[0].totalSpentCents)
        assertEquals("Small Store", merchants[1].merchant)
        assertEquals(2000L, merchants[1].totalSpentCents)

        collectJob.cancel()
    }
}
