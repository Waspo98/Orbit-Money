package app.overbay.orbit.ui.goals

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Goal
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
    fun testDepositToGoal_addsAmountAndCompletesGoal() = runTest(testDispatcher) {
        val goal = Goal(
            id = 10L,
            name = "New Laptop",
            targetAmountCents = 200000L, // $2,000
            currentAmountCents = 150000L // $1,500
        )
        repository.saveGoal(goal)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Open dialog
        viewModel.openAllocationDialog(goal)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showAllocationDialog)
        assertEquals(10L, viewModel.uiState.value.allocatingGoal?.id)

        // Deposit $600 (60000L) -> should reach $2,100 and complete
        viewModel.depositToGoal(goalId = 10L, depositAmountCents = 60000L)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAllocationDialog)
        val updatedGoal = repository.goals.value.find { it.id == 10L }
        assertNotNull(updatedGoal)
        assertEquals(210000L, updatedGoal.currentAmountCents)
        assertTrue(updatedGoal.isCompleted)
        assertEquals(1.0, updatedGoal.progressRatio)
        assertEquals(0L, updatedGoal.remainingAmountCents)

        collectJob.cancel()
    }

    @Test
    fun testDepositToGoal_negativeDepositClampedToZero() = runTest(testDispatcher) {
        val goal = Goal(
            id = 20L,
            name = "Car Down Payment",
            targetAmountCents = 500000L,
            currentAmountCents = 40000L // $400
        )
        repository.saveGoal(goal)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Withdraw $500 (-50000L) -> should clamp to 0L
        viewModel.depositToGoal(goalId = 20L, depositAmountCents = -50000L)
        testScheduler.advanceUntilIdle()

        val updatedGoal = repository.goals.value.find { it.id == 20L }
        assertNotNull(updatedGoal)
        assertEquals(0L, updatedGoal.currentAmountCents)

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
}
