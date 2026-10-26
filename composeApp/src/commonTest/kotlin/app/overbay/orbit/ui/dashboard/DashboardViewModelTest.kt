package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingDirection
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.DemoSeedGenerator
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: DashboardViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = DashboardViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testDefaultCardConfiguration_emitsAllSixCardsInOrder() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(15, state.visibleCards.size, "Must have 15 default visible cards")
        assertEquals(DashboardCardType.ACCOUNTS_OVERVIEW, state.visibleCards[0])
        assertEquals(DashboardCardType.BUDGET_PULSE, state.visibleCards[1])

        collectJob.cancel()
    }

    @Test
    fun testEmptySnapshot_rendersGracefulZeroMetricsWithoutCrash() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0L, state.cashflow.netCents)
        assertEquals(0.0, state.cashflow.savingsRatePercent)
        assertEquals(0L, state.budgetPulse.totalBudgetedCents)
        assertEquals(BudgetPaceStatus.NO_BUDGET, state.budgetPulse.paceStatus)
        assertTrue(state.upcoming.items.isEmpty())
        assertTrue(state.recentTransactions.transactions.isEmpty())
        assertEquals(0L, state.accounts.totalNetWorthCents)

        collectJob.cancel()
    }

    @Test
    fun testPeriodNavigation_previousAndNextMonth() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val initialMonth = viewModel.uiState.value.selectedMonth
        assertTrue(viewModel.uiState.value.cashflow.isCurrentMonth)
        assertFalse(viewModel.uiState.value.cashflow.canGoNextMonth)

        // Navigate to previous month
        viewModel.onPreviousMonth()
        testScheduler.advanceUntilIdle()

        val prevMonth = viewModel.uiState.value.selectedMonth
        val expectedPrev = LocalDateUtils.addMonthsToYearMonth(initialMonth, -1)
        assertEquals(expectedPrev, prevMonth)
        assertFalse(viewModel.uiState.value.cashflow.isCurrentMonth)
        assertTrue(viewModel.uiState.value.cashflow.canGoNextMonth)

        // Navigate back to current month
        viewModel.onNextMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(initialMonth, viewModel.uiState.value.selectedMonth)
        assertTrue(viewModel.uiState.value.cashflow.isCurrentMonth)

        // Attempting to navigate past current month is rejected
        viewModel.onNextMonth()
        testScheduler.advanceUntilIdle()
        assertEquals(initialMonth, viewModel.uiState.value.selectedMonth)

        collectJob.cancel()
    }

    @Test
    fun testCardVisibilityToggle_updatesPreferencesAndVisibleCards() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Hide CASHFLOW_HERO
        viewModel.toggleCardVisibility(DashboardCardType.CASHFLOW_HERO, isVisible = false)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.visibleCards.contains(DashboardCardType.CASHFLOW_HERO))
        assertEquals(14, state.visibleCards.size)

        // Show CASHFLOW_HERO again
        viewModel.toggleCardVisibility(DashboardCardType.CASHFLOW_HERO, isVisible = true)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.visibleCards.contains(DashboardCardType.CASHFLOW_HERO))
        assertEquals(15, viewModel.uiState.value.visibleCards.size)

        collectJob.cancel()
    }

    @Test
    fun testCardReorder_movesPosition() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Move item 0 (ACCOUNTS_OVERVIEW) to index 1
        viewModel.moveCard(0, 1)
        testScheduler.advanceUntilIdle()

        val updatedCards = viewModel.uiState.value.visibleCards
        assertEquals(DashboardCardType.BUDGET_PULSE, updatedCards[0])
        assertEquals(DashboardCardType.ACCOUNTS_OVERVIEW, updatedCards[1])

        // Reset to default
        viewModel.resetCardsToDefault()
        testScheduler.advanceUntilIdle()

        assertEquals(DashboardCardType.ACCOUNTS_OVERVIEW, viewModel.uiState.value.visibleCards[0])

        collectJob.cancel()
    }

    @Test
    fun testMarkUpcomingPaid_createsTransactionAndAdvancesDate() = runTest(testDispatcher) {
        // Seed an upcoming bill
        val upcomingItem = UpcomingItem(
            id = 42L,
            name = "Fiber Internet",
            merchant = "Fiber ISP",
            amountCents = 7500L,
            kind = UpcomingKind.BILL,
            direction = "expense",
            frequencyType = RecurrenceFrequency.MONTHLY,
            nextDate = "2026-05-01",
            status = UpcomingStatus.ACTIVE
        )
        repository.saveUpcoming(upcomingItem)
        repository.saveAccount(Account(id = 1L, name = "Primary Checking", type = AccountType.CHECKING))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Mark bill paid for 2026-05-01
        viewModel.markUpcomingPaid(42L, "2026-05-01")
        testScheduler.advanceUntilIdle()

        // A new transaction should have been generated
        val txns = repository.transactions.value
        val paidTxn = txns.find { it.originalMerchant == "Fiber ISP" }
        assertTrue(paidTxn != null, "Reconciled transaction must be created in repository")
        assertEquals(-7500L, paidTxn.originalAmountCents)
        assertEquals("2026-05-01", paidTxn.originalDate)
        assertEquals("upcoming_reconcile", paidTxn.source)

        // The upcoming item nextDate should have been advanced by 1 month
        val updatedUpcoming = repository.upcomingItems.value.find { it.id == 42L }
        assertTrue(updatedUpcoming != null)
        assertEquals("2026-06-01", updatedUpcoming.nextDate)

        collectJob.cancel()
    }

    @Test
    fun testConfigurationChange_stateSurvivesRotationWithinTimeout() = runTest(testDispatcher) {
        val firstCollectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        val capturedState = viewModel.uiState.value

        // Cancel collector simulating rotation onDestroy
        firstCollectJob.cancel()
        testScheduler.advanceTimeBy(2000)

        // State remains available synchronously
        assertEquals(capturedState, viewModel.uiState.value)

        // New collector attaches simulating onCreate
        val secondCollectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(capturedState, viewModel.uiState.value)

        secondCollectJob.cancel()
    }

    @Test
    fun testCardCustomization_atomicMutationsAndBoundaryHandling() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Boundary: moving top card up is a no-op
        val firstCard = viewModel.uiState.value.visibleCards.first()
        viewModel.moveCardUp(firstCard)
        testScheduler.advanceUntilIdle()
        assertEquals(firstCard, viewModel.uiState.value.visibleCards.first())

        // Boundary: moving bottom card down is a no-op
        val lastCard = viewModel.uiState.value.visibleCards.last()
        viewModel.moveCardDown(lastCard)
        testScheduler.advanceUntilIdle()
        assertEquals(lastCard, viewModel.uiState.value.visibleCards.last())

        // Move first card down: swaps positions with second card
        viewModel.moveCardDown(firstCard)
        testScheduler.advanceUntilIdle()
        assertEquals(firstCard, viewModel.uiState.value.visibleCards[1])

        val prefs = repository.preferences.value.dashboardWidgets.sortedBy { it.sortOrder }
        assertEquals(firstCard.widgetId, prefs[1].widgetId)

        // Hide two cards
        viewModel.toggleCardVisibility(DashboardCardType.BUDGET_PULSE, isVisible = false)
        viewModel.toggleCardVisibility(DashboardCardType.GOALS_PROGRESS, isVisible = false)
        testScheduler.advanceUntilIdle()

        val visible = viewModel.uiState.value.visibleCards
        assertEquals(13, visible.size)
        assertFalse(visible.contains(DashboardCardType.BUDGET_PULSE))
        assertFalse(visible.contains(DashboardCardType.GOALS_PROGRESS))

        // Card configs in customization state still contain all 16 items
        val configs = viewModel.uiState.value.customization.cardConfigs
        assertEquals(16, configs.size)
        assertFalse(configs.first { it.type == DashboardCardType.BUDGET_PULSE }.isVisible)
        assertFalse(configs.first { it.type == DashboardCardType.GOALS_PROGRESS }.isVisible)

        // Reset to default
        viewModel.resetCardsToDefault()
        testScheduler.advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.visibleCards.size)
        assertTrue(viewModel.uiState.value.visibleCards.contains(DashboardCardType.BUDGET_PULSE))
        assertTrue(viewModel.uiState.value.visibleCards.contains(DashboardCardType.GOALS_PROGRESS))

        collectJob.cancel()
    }

    @Test
    fun testFinancialCalculations_cashflowAndBudgetAndAccountsAndGoals() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val priorMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -1)

        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 5000_00L))
        repository.saveAccount(Account(id = 2L, name = "Credit Card", type = AccountType.CREDIT, currentBalanceCents = -1500_00L))
        repository.saveAccount(Account(id = 3L, name = "Brokerage", type = AccountType.INVESTMENT, currentBalanceCents = 20000_00L))
        repository.saveAccount(Account(id = 4L, name = "Archived Old", type = AccountType.CHECKING, currentBalanceCents = 100_00L, isArchived = true))

        repository.saveCategory(Category(id = 10L, name = "Income Cat", isIncome = true, isTransfer = false))
        repository.saveCategory(Category(id = 20L, name = "Groceries", isIncome = false, isTransfer = false))
        repository.saveCategory(Category(id = 30L, name = "Transfer Cat", isIncome = false, isTransfer = true))
        repository.saveBudget(categoryId = 20L, amountCents = 800_00L)

        // Current month transactions
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$currentMonth-05", originalMerchant = "Employer", originalAmountCents = 4000_00L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "$currentMonth-10", originalMerchant = "Supermarket", originalAmountCents = -720_00L, originalCategoryId = 20L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "$currentMonth-12", originalMerchant = "Transfer", originalAmountCents = 1000_00L, originalCategoryId = 30L, editedIsTransfer = true))
        repository.saveTransaction(Transaction(id = 4L, accountId = 1L, originalDate = "$currentMonth-15", originalMerchant = "Reimbursed", originalAmountCents = -200_00L, originalCategoryId = 20L, editedIsIgnored = true))

        // Prior month transactions
        repository.saveTransaction(Transaction(id = 5L, accountId = 1L, originalDate = "$priorMonth-05", originalMerchant = "Employer", originalAmountCents = 4000_00L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 6L, accountId = 1L, originalDate = "$priorMonth-10", originalMerchant = "Rent", originalAmountCents = -1000_00L, originalCategoryId = 20L))

        repository.saveGoal(Goal(id = 1L, name = "Emergency Fund", targetAmountCents = 10000_00L, currentAmountCents = 5000_00L))
        repository.saveGoal(Goal(id = 2L, name = "Vacation", targetAmountCents = 2000_00L, currentAmountCents = 2000_00L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value

        // Cashflow assertions
        assertEquals(4000_00L, state.cashflow.incomeCents)
        assertEquals(720_00L, state.cashflow.expenseCents)
        assertEquals(3280_00L, state.cashflow.netCents)
        assertEquals(82.0, state.cashflow.savingsRatePercent)
        assertEquals(3000_00L, state.cashflow.priorMonthNetCents)
        val delta = state.cashflow.netDeltaPercent
        assertTrue(delta != null && delta > 9.3 && delta < 9.4)

        // Budget Pulse assertions
        assertEquals(800_00L, state.budgetPulse.totalBudgetedCents)
        assertEquals(720_00L, state.budgetPulse.totalSpentCents)
        assertEquals(80_00L, state.budgetPulse.remainingCents)
        assertEquals(0.90, state.budgetPulse.utilizationRatio)
        assertEquals(BudgetPaceStatus.WATCH, state.budgetPulse.paceStatus)

        // Accounts Overview assertions
        assertEquals(3, state.accounts.accountCount)
        assertEquals(23500_00L, state.accounts.totalNetWorthCents, "Archived accounts must be excluded from net worth")
        assertEquals(1500_00L, state.accounts.totalLiabilitiesCents)
        assertEquals(20000_00L, state.accounts.investmentsCents)

        // Goals Progress assertions
        assertEquals(1, state.goals.activeGoalsCount)
        assertEquals(1, state.goals.completedGoalsCount)
        assertEquals(7000_00L, state.goals.totalSavedCents)
        assertEquals(12000_00L, state.goals.totalTargetCents)

        collectJob.cancel()
    }

    @Test
    fun testUpcomingBills_14DayLookaheadFilteringAndStatus() = runTest(testDispatcher) {
        val todayIso = LocalDateUtils.todayIsoDate()
        val in3Days = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(LocalDateUtils.parseIsoDate(todayIso)!!, 3))
        val in10Days = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(LocalDateUtils.parseIsoDate(todayIso)!!, 10))
        val in20Days = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(LocalDateUtils.parseIsoDate(todayIso)!!, 20))

        repository.saveUpcoming(UpcomingItem(id = 1L, name = "Water Bill", amountCents = 4500L, kind = UpcomingKind.BILL, direction = "expense", frequencyType = RecurrenceFrequency.MONTHLY, nextDate = in3Days, status = UpcomingStatus.ACTIVE))
        repository.saveUpcoming(UpcomingItem(id = 2L, name = "Freelance Client", amountCents = 150000L, kind = UpcomingKind.INCOME, direction = "income", frequencyType = RecurrenceFrequency.MONTHLY, nextDate = in10Days, status = UpcomingStatus.ACTIVE))
        repository.saveUpcoming(UpcomingItem(id = 3L, name = "Insurance", amountCents = 12000L, kind = UpcomingKind.BILL, direction = "expense", frequencyType = RecurrenceFrequency.MONTHLY, nextDate = in20Days, status = UpcomingStatus.ACTIVE))
        repository.saveUpcoming(UpcomingItem(id = 4L, name = "Gym Membership", amountCents = 3000L, kind = UpcomingKind.BILL, direction = "expense", frequencyType = RecurrenceFrequency.MONTHLY, nextDate = in3Days, status = UpcomingStatus.PAUSED))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val upcoming = viewModel.uiState.value.upcoming
        assertEquals(2, upcoming.items.size)
        assertEquals("Water Bill", upcoming.items[0].name)
        assertTrue(upcoming.items[0].isDueSoon)
        assertEquals(4500L, upcoming.totalUpcomingOutflowCents)
        assertEquals(150000L, upcoming.totalUpcomingInflowCents)

        collectJob.cancel()
    }

    @Test
    fun testProvenancePreservation_markUpcomingPaid_expenseAndIncome() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Primary Checking", type = AccountType.CHECKING))

        val billItem = UpcomingItem(
            id = 50L,
            name = "Power Co",
            merchant = "City Electric",
            amountCents = 8550L,
            kind = UpcomingKind.BILL,
            direction = "expense",
            frequencyType = RecurrenceFrequency.MONTHLY,
            nextDate = "2026-05-10",
            categoryId = 7L,
            status = UpcomingStatus.ACTIVE
        )
        val paycheckItem = UpcomingItem(
            id = 51L,
            name = "Company Payroll",
            merchant = "Acme Corp",
            amountCents = 250000L,
            kind = UpcomingKind.INCOME,
            direction = "income",
            frequencyType = RecurrenceFrequency.BIWEEKLY,
            nextDate = "2026-05-15",
            categoryId = 1L,
            status = UpcomingStatus.ACTIVE
        )
        repository.saveUpcoming(billItem)
        repository.saveUpcoming(paycheckItem)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Reconcile expense
        viewModel.markUpcomingPaid(50L, "2026-05-10")
        testScheduler.advanceUntilIdle()

        val billTxn = repository.transactions.value.find { it.originalMerchant == "City Electric" }
        assertTrue(billTxn != null)
        assertEquals(-8550L, billTxn.originalAmountCents)
        assertEquals("2026-05-10", billTxn.originalDate)
        assertEquals(7L, billTxn.originalCategoryId)
        assertEquals("upcoming_reconcile", billTxn.source)
        assertEquals(EditSource.SYSTEM, billTxn.editSource)
        assertEquals(null, billTxn.editedAmountCents)
        assertEquals(null, billTxn.editedMerchant)
        assertEquals(null, billTxn.editedDate)
        assertEquals(null, billTxn.editedCategoryId)

        val updatedBill = repository.upcomingItems.value.find { it.id == 50L }!!
        assertEquals("2026-06-10", updatedBill.nextDate)

        // 2. Reconcile income
        viewModel.markUpcomingPaid(51L, "2026-05-15")
        testScheduler.advanceUntilIdle()

        val incomeTxn = repository.transactions.value.find { it.originalMerchant == "Acme Corp" }
        assertTrue(incomeTxn != null)
        assertEquals(250000L, incomeTxn.originalAmountCents)
        assertEquals(EditSource.SYSTEM, incomeTxn.editSource)
        assertEquals(null, incomeTxn.editedAmountCents)

        val updatedPaycheck = repository.upcomingItems.value.find { it.id == 51L }!!
        assertEquals("2026-05-29", updatedPaycheck.nextDate)

        collectJob.cancel()
    }

    @Test
    fun testPeriodNavigation_pastMonthMarksMonthCompleteAndZeroDaysRemaining() = runTest(testDispatcher) {
        val currentMonth = LocalDateUtils.currentYearMonth()
        val pastMonth = LocalDateUtils.addMonthsToYearMonth(currentMonth, -2)

        repository.saveCategory(Category(id = 1L, name = "Groceries", isIncome = false, isTransfer = false))
        repository.saveBudget(categoryId = 1L, amountCents = 50000L)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "$pastMonth-10", originalMerchant = "Market", originalAmountCents = -30000L, originalCategoryId = 1L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.onPreviousMonth()
        viewModel.onPreviousMonth()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(pastMonth, state.selectedMonth)
        assertFalse(state.cashflow.isCurrentMonth)
        assertTrue(state.cashflow.canGoNextMonth)

        assertEquals("Month complete", state.budgetPulse.dailySafeSpendDetail)
        assertEquals(0L, state.budgetPulse.dailySafeSpendCents)

        viewModel.onResetToCurrentMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(currentMonth, viewModel.uiState.value.selectedMonth)
        assertTrue(viewModel.uiState.value.cashflow.isCurrentMonth)
        assertFalse(viewModel.uiState.value.cashflow.canGoNextMonth)

        collectJob.cancel()
    }

    @Test
    fun testRecentTransactions_sortingAndExclusions() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))

        for (i in 1..8) {
            repository.saveTransaction(
                Transaction(
                    id = i.toLong(),
                    accountId = 1L,
                    originalDate = "2026-04-${i.toString().padStart(2, '0')}",
                    originalMerchant = "Store $i",
                    originalAmountCents = -(i * 1000L)
                )
            )
        }
        repository.saveTransaction(
            Transaction(
                id = 9L,
                accountId = 1L,
                originalDate = "2026-04-20",
                originalMerchant = "Store Ignored",
                originalAmountCents = -9000L,
                editedIsIgnored = true
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val recents = viewModel.uiState.value.recentTransactions.transactions
        assertEquals(5, recents.size)
        assertFalse(recents.any { it.id == 9L })
        assertEquals("Store 8", recents[0].merchant)
        assertEquals("Store 7", recents[1].merchant)
        assertEquals("Store 6", recents[2].merchant)
        assertEquals("Store 5", recents[3].merchant)
        assertEquals("Store 4", recents[4].merchant)

        collectJob.cancel()
    }
}
