package app.overbay.orbit.ui.budgets

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
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
class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: BudgetsViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = BudgetsViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_calculatesOverviewAndPacing() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()

        val catGroceries = Category(id = 1L, name = "Groceries", isIncome = false, isTransfer = false)
        val catDining = Category(id = 2L, name = "Dining", isIncome = false, isTransfer = false)
        repository.saveCategory(catGroceries)
        repository.saveCategory(catDining)

        repository.saveBudget(categoryId = 1L, amountCents = 60000L, rollover = true) // $600
        repository.saveBudget(categoryId = 2L, amountCents = 20000L, rollover = false) // $200

        // Spend in current month
        repository.saveTransaction(Transaction(id = 10L, accountId = 1L, originalDate = "$currentMonth-05", originalMerchant = "Supermarket", originalAmountCents = -45000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 11L, accountId = 1L, originalDate = "$currentMonth-10", originalMerchant = "Restaurant", originalAmountCents = -25000L, originalCategoryId = 2L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(currentMonth, state.selectedMonth)
        assertTrue(state.isCurrentMonth)
        assertTrue(state.canGoNextMonth)

        assertEquals(80000L, state.totalBudgetedCents)
        assertEquals(70000L, state.totalSpentCents)
        assertEquals(10000L, state.totalRemainingCents)

        // Dining is over budget (25000 > 20000)
        val diningItem = state.budgetedItems.find { it.category.id == 2L }
        assertNotNull(diningItem)
        assertEquals(BudgetPaceStatus.OVER_BUDGET, diningItem.paceStatus)

        // Groceries is on track or watch (45000 / 60000 = 75%)
        val groceryItem = state.budgetedItems.find { it.category.id == 1L }
        assertNotNull(groceryItem)
        assertEquals(BudgetPaceStatus.ON_TRACK, groceryItem.paceStatus)
        assertTrue(groceryItem.rollover)

        collectJob.cancel()
    }

    @Test
    fun testPeriodNavigation_previousAndNextMonth() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val prevMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -1)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.onPreviousMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(prevMonth, viewModel.uiState.value.selectedMonth)
        assertFalse(viewModel.uiState.value.isCurrentMonth)
        assertTrue(viewModel.uiState.value.canGoNextMonth)

        // Navigate forward
        viewModel.onNextMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(currentMonth, viewModel.uiState.value.selectedMonth)
        assertTrue(viewModel.uiState.value.isCurrentMonth)
        assertTrue(viewModel.uiState.value.canGoNextMonth)

        // Can navigate into future month (main branch parity)
        val nextMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, 1)
        viewModel.onNextMonth()
        testScheduler.advanceUntilIdle()
        assertEquals(nextMonth, viewModel.uiState.value.selectedMonth)
        assertFalse(viewModel.uiState.value.isCurrentMonth)

        // Reset to current month
        viewModel.onPreviousMonth()
        viewModel.onResetToCurrentMonth()
        testScheduler.advanceUntilIdle()
        assertEquals(currentMonth, viewModel.uiState.value.selectedMonth)

        collectJob.cancel()
    }

    @Test
    fun testSortOptions_ordersItemsCorrectly() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()

        val catA = Category(id = 1L, name = "Apple", isIncome = false, isTransfer = false)
        val catZ = Category(id = 2L, name = "Zebra", isIncome = false, isTransfer = false)
        repository.saveCategory(catA)
        repository.saveCategory(catZ)

        repository.saveBudget(categoryId = 1L, amountCents = 10000L)
        repository.saveBudget(categoryId = 2L, amountCents = 10000L)

        // Cat A spent 5000, Cat Z spent 15000 (over budget)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-01", originalMerchant = "A", originalAmountCents = -5000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-02", originalMerchant = "Z", originalAmountCents = -15000L, originalCategoryId = 2L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Over budget first: Zebra (over) then Apple
        viewModel.setSortOption(CategorySortOption.OVER_BUDGET_FIRST)
        testScheduler.advanceUntilIdle()
        assertEquals("Zebra", viewModel.uiState.value.budgetedItems[0].category.name)

        // Name A to Z: Apple then Zebra
        viewModel.setSortOption(CategorySortOption.NAME_A_TO_Z)
        testScheduler.advanceUntilIdle()
        assertEquals("Apple", viewModel.uiState.value.budgetedItems[0].category.name)

        collectJob.cancel()
    }

    @Test
    fun testBudgetEditor_saveAndRemoveBudget() = runTest(testDispatcher) {
        val cat = Category(id = 99L, name = "Fitness", isIncome = false, isTransfer = false)
        repository.saveCategory(cat)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Save new budget
        viewModel.saveBudget(categoryId = 99L, amountCents = 8000L, rollover = true)
        testScheduler.advanceUntilIdle()

        val budget = repository.budgets.value.find { it.categoryId == 99L }
        assertNotNull(budget)
        assertEquals(8000L, budget.amountCents)
        assertTrue(budget.rollover)

        // 2. Delete budget
        viewModel.deleteBudget(budget.id)
        testScheduler.advanceUntilIdle()

        assertNull(repository.budgets.value.find { it.categoryId == 99L })

        collectJob.cancel()
    }

    @Test
    fun testThreeMonthAverageCalculation() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val m1 = LocalDateUtils.addMonthsToYearMonth(currentMonth, -1)
        val m2 = LocalDateUtils.addMonthsToYearMonth(currentMonth, -2)
        val m3 = LocalDateUtils.addMonthsToYearMonth(currentMonth, -3)

        val cat = Category(id = 50L, name = "Electric Bill", isIncome = false, isTransfer = false)
        repository.saveCategory(cat)

        // Month 1: 100, Month 2: 120, Month 3: 140 -> Avg = 120
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$m1-15", originalMerchant = "Power", originalAmountCents = -10000L, originalCategoryId = 50L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$m2-15", originalMerchant = "Power", originalAmountCents = -12000L, originalCategoryId = 50L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "$m3-15", originalMerchant = "Power", originalAmountCents = -14000L, originalCategoryId = 50L))

        val avg = viewModel.calculate3MonthAverageSpend(50L)
        assertEquals(12000L, avg)
    }
}
