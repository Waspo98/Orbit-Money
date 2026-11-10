package app.overbay.orbit.ui.calculators

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.ui.calculators.retirement.RetirementViewModel
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RetirementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: RetirementViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = RetirementViewModel(repository, testScope)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_computesDefaultRetirementScenario() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(35.0, state.currentAge)
        assertEquals(67.0, state.retirementAge)
        assertEquals(RetirementScenarioPreset.BALANCED, state.preset)
        assertEquals(RetirementTargetMode.WITHDRAWAL_4_PERCENT, state.targetMode)

        // 4% rule on $60,000/yr spending = $1,500,000 target nest egg
        assertEquals(1_500_000_00L, state.scenario.targetNestEggCents)
        assertTrue(state.scenario.projectedBalanceCents > 0L)
        assertTrue(state.scenario.accumulationPoints.isNotEmpty())
        assertTrue(state.scenario.drawdownPoints.isNotEmpty())
    }

    @Test
    fun testConnectedAccounts_syncsInvestmentBalance() = runTest(testDispatcher) {
        val inv1 = Account(id = 1L, name = "Vanguard 401k", type = AccountType.INVESTMENT, currentBalanceCents = 150_000_00L)
        val inv2 = Account(id = 2L, name = "Fidelity Roth IRA", type = AccountType.INVESTMENT, currentBalanceCents = 75_000_00L)
        repository.saveAccount(inv1)
        repository.saveAccount(inv2)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.investmentAccounts.size)
        assertFalse(state.currentBalanceOverridden)
        assertEquals(225_000_00L, state.connectedBalanceCents)
        assertEquals(225_000_00L, state.currentBalanceCents)
    }

    @Test
    fun testManualBalanceOverride_setsOverriddenFlag() = runTest(testDispatcher) {
        val inv1 = Account(id = 1L, name = "Vanguard 401k", type = AccountType.INVESTMENT, currentBalanceCents = 150_000_00L)
        repository.saveAccount(inv1)
        testScheduler.advanceUntilIdle()

        viewModel.setCurrentBalance(300_000_00L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.currentBalanceOverridden)
        assertEquals(300_000_00L, state.currentBalanceCents)
    }

    @Test
    fun testRetirementAgeChange_recalculatesRetirementScenario() = runTest(testDispatcher) {
        viewModel.setRetirementAge(65.0)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(35.0, state.currentAge)
        assertEquals(65.0, state.retirementAge)
        // 30 years = 360 months
        assertEquals(360, state.scenario.monthsToRetirement)
    }

    @Test
    fun testPresetChange_updatesRealReturnAndProjection() = runTest(testDispatcher) {
        viewModel.setPreset(RetirementScenarioPreset.AGGRESSIVE)
        testScheduler.advanceUntilIdle()

        val aggState = viewModel.uiState.value
        val aggBalance = aggState.scenario.projectedBalanceCents

        viewModel.setPreset(RetirementScenarioPreset.CONSERVATIVE)
        testScheduler.advanceUntilIdle()

        val conState = viewModel.uiState.value
        val conBalance = conState.scenario.projectedBalanceCents

        assertTrue(aggBalance > conBalance)
    }

    @Test
    fun testTargetModeChange_updatesNestEggCalculation() = runTest(testDispatcher) {
        viewModel.setTargetMode(RetirementTargetMode.CUSTOM)
        viewModel.setCustomTarget(2_500_000_00L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2_500_000_00L, state.scenario.targetNestEggCents)
    }

    @Test
    fun testScrubbing_recordsAgeAndBalance() = runTest(testDispatcher) {
        viewModel.onAgeScrubbed(50.0, 450_000_00L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(50.0, state.scrubbedAge)
        assertEquals(450_000_00L, state.scrubbedBalanceCents)

        viewModel.onAgeScrubbed(null, null)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.scrubbedAge)
        assertNull(viewModel.uiState.value.scrubbedBalanceCents)
    }

    @Test
    fun testSavingsGap_identifiesUnderfundedVsFunded() = runTest(testDispatcher) {
        viewModel.setMonthlySavings(100_00L) // only $100/mo
        testScheduler.advanceUntilIdle()

        val underfunded = viewModel.uiState.value.scenario
        assertTrue(underfunded.fundingGapCents > 0L)
        assertTrue(underfunded.monthlySavingsGapCents > 0L)
        assertTrue(underfunded.readinessPercent < 100.0)

        // Super aggressive savings $15,000/mo
        viewModel.setMonthlySavings(15_000_00L)
        testScheduler.advanceUntilIdle()

        val funded = viewModel.uiState.value.scenario
        assertEquals(0L, funded.fundingGapCents)
        assertEquals(0L, funded.monthlySavingsGapCents)
        assertTrue(funded.readinessPercent >= 100.0)
    }
}
