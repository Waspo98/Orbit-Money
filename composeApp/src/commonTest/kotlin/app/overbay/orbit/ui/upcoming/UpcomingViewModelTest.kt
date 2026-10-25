package app.overbay.orbit.ui.upcoming

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
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
class UpcomingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: UpcomingViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = UpcomingViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_calculatesTotalsAndUpcomingBills() = runTest(testDispatcher) {
        val today = LocalDateUtils.todayIsoDate()
        val account = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 100000L)
        val catRent = Category(id = 1L, name = "Rent", isIncome = false, isTransfer = false)
        val catPaycheck = Category(id = 2L, name = "Salary", isIncome = true, isTransfer = false)

        repository.saveAccount(account)
        repository.saveCategory(catRent)
        repository.saveCategory(catPaycheck)

        val rentBill = UpcomingItem(
            id = 1L,
            name = "Rent Payment",
            merchant = "Apartment Mgmt",
            amountCents = 120000L,
            kind = UpcomingKind.BILL,
            direction = "expense",
            nextDate = today,
            categoryId = 1L,
            accountId = 1L
        )
        val paycheck = UpcomingItem(
            id = 2L,
            name = "Biweekly Paycheck",
            merchant = "Employer",
            amountCents = 250000L,
            kind = UpcomingKind.INCOME,
            direction = "income",
            nextDate = today,
            categoryId = 2L,
            accountId = 1L
        )

        repository.saveUpcoming(rentBill)
        repository.saveUpcoming(paycheck)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(120000L, state.totalUpcomingExpenseCents)
        assertEquals(250000L, state.totalUpcomingIncomeCents)
        assertEquals(130000L, state.netCashflowProjectionCents)
        assertFalse(state.isLoading)

        collectJob.cancel()
    }

    @Test
    fun testFilterOption_filtersBillsAndIncome() = runTest(testDispatcher) {
        val today = LocalDateUtils.todayIsoDate()
        val rentBill = UpcomingItem(
            id = 1L,
            name = "Internet",
            amountCents = 7000L,
            kind = UpcomingKind.BILL,
            direction = "expense",
            nextDate = today
        )
        val incomeItem = UpcomingItem(
            id = 2L,
            name = "Side Gig",
            amountCents = 50000L,
            kind = UpcomingKind.INCOME,
            direction = "income",
            nextDate = today
        )
        repository.saveUpcoming(rentBill)
        repository.saveUpcoming(incomeItem)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Filter: Bills only
        viewModel.setFilterOption(UpcomingFilterOption.BILLS)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(1L, viewModel.uiState.value.items.first().id)

        // Filter: Income only
        viewModel.setFilterOption(UpcomingFilterOption.INCOME)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(2L, viewModel.uiState.value.items.first().id)

        // Filter: All
        viewModel.setFilterOption(UpcomingFilterOption.ALL)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.items.size)

        collectJob.cancel()
    }

    @Test
    fun testDateFilter_togglesSelectedDate() = runTest(testDispatcher) {
        val date1 = "2026-11-01"
        val date2 = "2026-11-05"

        val item1 = UpcomingItem(id = 1L, name = "Bill A", amountCents = 5000L, nextDate = date1)
        val item2 = UpcomingItem(id = 2L, name = "Bill B", amountCents = 8000L, nextDate = date2)
        repository.saveUpcoming(item1)
        repository.saveUpcoming(item2)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.items.size)

        // Select date1
        viewModel.setSelectedDateFilter(date1)
        testScheduler.advanceUntilIdle()
        assertEquals(date1, viewModel.uiState.value.selectedDateFilter)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(1L, viewModel.uiState.value.items.first().id)

        // Toggle date1 off
        viewModel.setSelectedDateFilter(date1)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedDateFilter)
        assertEquals(2, viewModel.uiState.value.items.size)

        collectJob.cancel()
    }

    @Test
    fun testReconcileItemPaid_createsPaidTransactionAndAdvancesInterval() = runTest(testDispatcher) {
        val nextDate = "2026-10-20"
        val bill = UpcomingItem(
            id = 5L,
            name = "Electric Utility",
            merchant = "City Power",
            amountCents = 9500L,
            kind = UpcomingKind.BILL,
            direction = "expense",
            frequencyType = RecurrenceFrequency.MONTHLY,
            nextDate = nextDate,
            accountId = 1L,
            categoryId = 3L
        )
        repository.saveUpcoming(bill)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.reconcileItemPaid(upcomingItemId = 5L, occurrenceDate = nextDate)
        testScheduler.advanceUntilIdle()

        // 1. Transaction should be created with negative amount for expense
        val transactions = repository.transactions.value
        assertEquals(1, transactions.size)
        val txn = transactions.first()
        assertEquals("upcoming_reconcile", txn.source)
        assertEquals(-9500L, txn.originalAmountCents)
        assertEquals("City Power", txn.originalMerchant)
        assertEquals(nextDate, txn.originalDate)

        // 2. UpcomingItem nextDate should be advanced to next month (2026-11-20)
        val updatedItem = repository.upcomingItems.value.find { it.id == 5L }
        assertNotNull(updatedItem)
        assertEquals("2026-11-20", updatedItem.nextDate)

        collectJob.cancel()
    }

    @Test
    fun testCandidateDetection_acceptAndDismiss() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Accept a candidate
        val candidate = RecurringCandidate(
            merchant = "Spotify",
            frequency = RecurrenceFrequency.MONTHLY,
            intervalDays = 30,
            amountCents = 1099L,
            confidenceScore = 95,
            lastDate = "2026-10-01",
            nextProjectedDate = "2026-11-01",
            transactionCount = 4,
            suggestedCategoryId = 4L,
            suggestedAccountId = 1L
        )
        viewModel.acceptCandidate(candidate)
        testScheduler.advanceUntilIdle()

        val savedItems = repository.upcomingItems.value
        assertEquals(1, savedItems.size)
        val item = savedItems.first()
        assertEquals("Spotify", item.name)
        assertEquals(1099L, item.amountCents)
        assertEquals("2026-11-01", item.nextDate)
        assertEquals(RecurrenceFrequency.MONTHLY, item.frequencyType)

        // Dismiss candidate
        viewModel.dismissCandidate("Netflix")
        testScheduler.advanceUntilIdle()
        // Merchant is added to dismissed set, preventing further detection

        collectJob.cancel()
    }

    @Test
    fun testCrudOperations_createEditDelete() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Open Add
        viewModel.openAddUpcoming(UpcomingKind.BILL)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertNotNull(viewModel.uiState.value.editingItem)
        assertEquals(UpcomingKind.BILL, viewModel.uiState.value.editingItem?.kind)

        // Save new item
        val newItem = UpcomingItem(
            id = 10L,
            name = "Water Bill",
            amountCents = 4500L,
            nextDate = "2026-11-15"
        )
        viewModel.saveUpcoming(newItem)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditorSheet)
        assertEquals(1, repository.upcomingItems.value.size)

        // Edit item
        viewModel.openEditUpcoming(newItem)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertEquals(10L, viewModel.uiState.value.editingItem?.id)

        // Delete item
        viewModel.deleteUpcoming(10L)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showEditorSheet)
        assertEquals(0, repository.upcomingItems.value.size)

        collectJob.cancel()
    }
}
