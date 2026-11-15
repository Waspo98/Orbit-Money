package app.overbay.orbit.ui.goals

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountAllocationSummary
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.GoalEtaInfo
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.goals.components.isProjectedPastTargetDate
import app.overbay.orbit.ui.goals.components.monthsUntilTargetDate
import app.overbay.orbit.util.PlatformStorage
import kotlinx.datetime.LocalDate
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
class GoalsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: GoalsViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = GoalsViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_calculatesTotalSavedAndTarget() = runTest(testDispatcher) {
        val account = Account(id = 1L, name = "Savings", type = AccountType.SAVINGS, currentBalanceCents = 500000L)
        repository.saveAccount(account)

        val emergencyFund = Goal(
            id = 1L,
            name = "Emergency Fund",
            targetAmountCents = 1000000L, // $10,000
            currentAmountCents = 600000L, // $6,000
            sortOrder = 0
        )
        val vacation = Goal(
            id = 2L,
            name = "Summer Trip",
            targetAmountCents = 300000L, // $3,000
            currentAmountCents = 300000L, // Completed ($3,000)
            sortOrder = 1
        )
        repository.saveGoal(emergencyFund)
        repository.saveGoal(vacation)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.goals.size)
        assertEquals(900000L, state.totalSavedCents)
        assertEquals(1300000L, state.totalTargetCents)
        assertEquals(account, state.accountMap[1L])

        // Active goals should sort before completed goals
        assertEquals(1L, state.goals[0].id)
        assertFalse(state.goals[0].isCompleted)
        assertEquals(2L, state.goals[1].id)
        assertTrue(state.goals[1].isCompleted)

        collectJob.cancel()
    }

    @Test
    fun testSaveGoalWithAllocations_updatesGoalAndAllocations() = runTest(testDispatcher) {
        val account = Account(id = 1L, name = "Savings", type = AccountType.SAVINGS, currentBalanceCents = 1_000_000L)
        repository.saveAccount(account)

        val goal = Goal(
            id = 10L,
            name = "New Laptop",
            targetAmountCents = 200000L, // $2,000
            currentAmountCents = 210000L // $2,100
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.saveGoalWithAllocations(goal, stealFromOthers = false)
        testScheduler.advanceUntilIdle()

        val updatedGoal = repository.goals.value.find { it.id == 10L }
        assertNotNull(updatedGoal)
        assertEquals(210000L, updatedGoal.currentAmountCents)
        assertTrue(updatedGoal.isCompleted)
        assertEquals(1.0, updatedGoal.progressRatio)
        assertEquals(0L, updatedGoal.remainingAmountCents)

        collectJob.cancel()
    }

    @Test
    fun testMoveGoal_reordersActiveGoalsAndPersistsSortOrder() = runTest(testDispatcher) {
        val g1 = Goal(id = 1L, name = "Goal A", targetAmountCents = 500000L, currentAmountCents = 100000L, sortOrder = 0)
        val g2 = Goal(id = 2L, name = "Goal B", targetAmountCents = 500000L, currentAmountCents = 100000L, sortOrder = 1)
        repository.saveGoal(g1)
        repository.saveGoal(g2)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.moveGoal(completedGroup = false, fromIndex = 0, toIndex = 1)
        testScheduler.advanceUntilIdle()

        val active = viewModel.uiState.value.activeGoals
        assertEquals(2L, active[0].id)
        assertEquals(1L, active[1].id)

        collectJob.cancel()
    }

    @Test
    fun testCalculateMonthlyRequiredCents() = runTest(testDispatcher) {
        val todayIso = LocalDateUtils.todayIsoDate()
        val today = LocalDateUtils.parseIsoDate(todayIso) ?: return@runTest

        // Target in 10 months
        val futureYear = if (today.monthNumber + 10 > 12) today.year + 1 else today.year
        val futureMonth = if (today.monthNumber + 10 > 12) today.monthNumber + 10 - 12 else today.monthNumber + 10
        val targetDate = "$futureYear-${futureMonth.toString().padStart(2, '0')}-01"

        val goal = Goal(
            id = 30L,
            name = "Downpayment",
            targetAmountCents = 1000000L, // $10,000
            currentAmountCents = 0L,
            targetDate = targetDate
        )

        val monthly = viewModel.calculateMonthlyRequiredCents(goal)
        assertNotNull(monthly)
        assertEquals(100000L, monthly) // 1,000,000 / 10 = 100,000 ($1,000/mo)

        // Goal without target date returns null
        val noDateGoal = goal.copy(targetDate = null)
        assertNull(viewModel.calculateMonthlyRequiredCents(noDateGoal))

        // Completed goal returns 0
        val completedGoal = goal.copy(currentAmountCents = 1000000L)
        assertEquals(0L, viewModel.calculateMonthlyRequiredCents(completedGoal))
    }

    @Test
    fun testCrudOperations_openSaveEditDelete() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Open Add
        viewModel.openAddGoal()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertNotNull(viewModel.uiState.value.editingGoal)
        assertEquals(0L, viewModel.uiState.value.editingGoal?.id)

        // Save new goal
        val newGoal = Goal(
            id = 50L,
            name = "Home Renovation",
            targetAmountCents = 1500000L,
            currentAmountCents = 200000L
        )
        viewModel.saveGoal(newGoal)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditorSheet)
        assertEquals(1, repository.goals.value.size)

        // Open Edit
        viewModel.openEditGoal(newGoal)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertEquals(50L, viewModel.uiState.value.editingGoal?.id)

        // Close editor
        viewModel.closeEditorSheet()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showEditorSheet)

        // Delete
        viewModel.deleteGoal(50L)
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.goals.value.size)

        collectJob.cancel()
    }

    @Test
    fun testActiveAggregatesAndFundingSplit() = runTest(testDispatcher) {
        val allocatedAcct = Account(id = 1L, name = "Brokerage", type = AccountType.INVESTMENT, currentBalanceCents = 205281L)
        val idleAcct = Account(id = 2L, name = "Reserve Savings", type = AccountType.SAVINGS, currentBalanceCents = 500000L)
        repository.saveAccount(allocatedAcct)
        repository.saveAccount(idleAcct)

        val activeGoal = Goal(
            id = 1L,
            name = "New Car",
            targetAmountCents = 2500000L,
            currentAmountCents = 205281L,
            allocations = listOf(
                GoalAllocation(
                    goalId = 1L,
                    accountId = 1L,
                    accountName = "Brokerage",
                    allocationType = GoalAllocationType.FIXED,
                    allocationAmountCents = 205281L,
                    currentAmountCents = 205281L
                )
            )
        )
        val completedGoal = Goal(
            id = 2L,
            name = "Emergency Fund",
            targetAmountCents = 1500000L,
            currentAmountCents = 1500000L
        )
        repository.saveGoal(activeGoal)
        repository.saveGoal(completedGoal)

        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(205281L, state.activeSavedCents)
        assertEquals(2500000L, state.activeTargetCents)
        assertEquals(1500000L, state.completedSavedCents)
        assertEquals(1, state.allocatedAccountSummaries.size)
        assertEquals(1, state.unallocatedAccountSummaries.size)
        assertEquals(500000L, state.totalUnallocatedCashCents)

        collectJob.cancel()
    }

    @Test
    fun testHistoryReconstructionSupports24MonthsAndPreferences() = runTest(testDispatcher) {
        val goal = Goal(id = 7L, name = "Sabbatical", targetAmountCents = 1200000L, currentAmountCents = 100000L)
        repository.saveGoal(goal)

        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        assertEquals(24, viewModel.uiState.value.goals.first().history.size)
        assertEquals(GoalsTab.GOALS, viewModel.uiState.value.activeTab)

        viewModel.selectTab(GoalsTab.FUNDING)
        viewModel.setHistoryMonths(24)
        viewModel.setHistoryMonths(99) // ignored invalid window
        viewModel.toggleCompletedGoals()
        viewModel.toggleUnallocatedAccounts()
        viewModel.toggleFundingDetails()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GoalsTab.FUNDING, state.activeTab)
        assertEquals(24, state.historyMonths)
        assertFalse(state.showCompletedGoals)
        assertTrue(state.showUnallocatedAccounts)
        assertTrue(state.showFundingDetails)

        collectJob.cancel()
    }

    @Test
    fun testSmartDefaultWhatIfBoostAndOverAllocatedFlag() = runTest(testDispatcher) {
        val targetIso = LocalDateUtils.formatIsoDate(
            LocalDateUtils.addMonthsClamped(LocalDateUtils.todayLocalDate(), 10)
        )
        val stalledWithTarget = Goal(
            id = 11L,
            name = "College",
            targetAmountCents = 1000000L,
            currentAmountCents = 0L,
            targetDate = targetIso
        )
        val doneGoal = Goal(
            id = 12L,
            name = "Done",
            targetAmountCents = 50000L,
            currentAmountCents = 50000L
        )
        repository.saveGoal(stalledWithTarget)
        repository.saveGoal(doneGoal)

        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.selectGoalForInspect(11L)
        testScheduler.advanceUntilIdle()
        assertEquals(100000L, viewModel.uiState.value.whatIfBoostCents)

        viewModel.setWhatIfBoostCents(25000L)
        testScheduler.advanceUntilIdle()
        assertEquals(25000L, viewModel.uiState.value.whatIfBoostCents)

        viewModel.selectGoalForInspect(12L)
        testScheduler.advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.whatIfBoostCents)

        val fullSummary = AccountAllocationSummary(
            accountId = 1L,
            accountName = "Brokerage",
            accountType = AccountType.INVESTMENT,
            currentBalanceCents = 205281L,
            allocatableAmountCents = 205281L,
            allocatedAmountCents = 205281L,
            remainingAmountCents = 0L,
            allocatedPercent = 100.0
        )
        assertFalse(fullSummary.isOverAllocated)

        val overSummary = fullSummary.copy(allocatedAmountCents = 210000L, allocatedPercent = 102.3)
        assertTrue(overSummary.isOverAllocated)

        collectJob.cancel()
    }

    @Test
    fun testGoalDisplayHelpers_monthsUntilAndPastTargetProjection() {
        val today = LocalDate(2026, 11, 14)
        val goalAhead = Goal(
            id = 1L,
            name = "Vacation",
            targetAmountCents = 500_000L,
            targetDate = "2027-05-01",
            eta = GoalEtaInfo(months = 4, date = "Mar 2027")
        )
        val goalBehind = goalAhead.copy(
            eta = GoalEtaInfo(months = 8, date = "Jul 2027")
        )

        assertEquals(6, goalAhead.monthsUntilTargetDate(today))
        assertFalse(goalAhead.isProjectedPastTargetDate(today))
        assertTrue(goalBehind.isProjectedPastTargetDate(today))
    }

    @Test
    fun testGoalEditorDeleteCancelPreservesEditingState() = runTest(testDispatcher) {
        val goal = Goal(id = 21L, name = "Emergency", targetAmountCents = 1_000_000L, currentAmountCents = 200_000L)
        repository.saveGoal(goal)
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val saved = viewModel.uiState.value.goals.first { it.name == "Emergency" }
        viewModel.openEditGoal(saved)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)

        // Requesting delete then canceling keeps the editor open and intact
        viewModel.requestDeleteGoal(saved.id)
        testScheduler.advanceUntilIdle()
        assertEquals(saved.id, viewModel.uiState.value.deleteConfirmGoalId)
        assertTrue(viewModel.uiState.value.showEditorSheet)

        viewModel.dismissDeleteGoal()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.deleteConfirmGoalId)
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertEquals(saved.id, viewModel.uiState.value.editingGoal?.id)

        // Confirming delete closes the editor and removes the goal
        viewModel.deleteGoal(saved.id)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showEditorSheet)
        assertNull(viewModel.uiState.value.editingGoal)
        assertTrue(viewModel.uiState.value.goals.none { it.id == saved.id })

        collectJob.cancel()
    }
}
