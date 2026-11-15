package app.overbay.orbit.ui.calculators

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRetirementAccount
import app.overbay.orbit.data.model.RetirementAccountMonthBalance
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.ui.calculators.retirement.RetirementAccountResolver
import app.overbay.orbit.ui.calculators.retirement.RetirementAmountField
import app.overbay.orbit.ui.calculators.retirement.RetirementViewModel
import app.overbay.orbit.ui.calculators.retirement.RetirementWorkspaceTab
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
    fun testWorkspaceState_tabsSheetsAndAmountEditorWork() = runTest(testDispatcher) {
        viewModel.selectTab(RetirementWorkspaceTab.RUNWAY)
        assertEquals(RetirementWorkspaceTab.RUNWAY, viewModel.uiState.value.selectedTab)

        viewModel.openAssumptionsSheet()
        assertTrue(viewModel.uiState.value.showAssumptionsSheet)
        viewModel.dismissAssumptionsSheet()
        assertFalse(viewModel.uiState.value.showAssumptionsSheet)

        viewModel.openAmountEditor(RetirementAmountField.MONTHLY_SAVINGS)
        assertEquals(RetirementAmountField.MONTHLY_SAVINGS, viewModel.uiState.value.activeAmountField)

        viewModel.commitAmountEditor(RetirementAmountField.MONTHLY_SAVINGS, 2_250_00L)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeAmountField)
        assertEquals(2_250_00L, viewModel.uiState.value.monthlySavingsCents)
        assertTrue(viewModel.uiState.value.monthlySavingsOverridden)
    }

    @Test
    fun testMemberSummariesAndSpendingFraction_computeCorrectly() = runTest(testDispatcher) {
        val inv = Account(id = 10L, name = "Campus 403b", type = AccountType.INVESTMENT, currentBalanceCents = 42_000_00L)
        repository.saveAccount(inv)
        repository.saveHouseholdMember(
            HouseholdMember(
                id = 1L,
                name = "Jordan",
                role = "adult",
                grossIncomeAnnualCents = 120_000_00L,
                employeeContributionPercent = 10.0,
                employerMatchPercent = 50.0,
                employerMatchLimitPercent = 6.0,
                hsaContributionAnnualCents = 3_600_00L,
                payFrequency = "biweekly",
                payPeriodsPerYear = 26.0,
                retirementAccounts = listOf(
                    HouseholdRetirementAccount(memberId = 1L, accountId = 10L, accountKind = "403B")
                )
            )
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.memberSummaries.size)
        val member = state.memberSummaries.first()
        assertEquals("403(b)", member.linkedAccounts.first().displayKind)
        // 10% of $120k = $12,000 + 50% match on 6% ($3,600) + $3,600 HSA = $19,200/yr
        assertEquals(19_200_00L, member.annualTotalCents)

        viewModel.applySpendingIncomeFraction(0.55)
        testScheduler.advanceUntilIdle()
        assertEquals(66_000_00L, viewModel.uiState.value.annualSpendingCents)
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

    @Test
    fun testCustomIncludedAccounts_updatesBalancesHsaSplitAndResetsToAuto() = runTest(testDispatcher) {
        repository.saveAccount(
            Account(id = 10L, name = "Campus 403b", type = AccountType.INVESTMENT, currentBalanceCents = 40_000_00L)
        )
        repository.saveAccount(
            Account(id = 11L, name = "Taxable Brokerage", type = AccountType.INVESTMENT, currentBalanceCents = 25_000_00L)
        )
        repository.saveAccount(
            Account(id = 12L, name = "HealthEquity HSA", type = AccountType.SAVINGS, currentBalanceCents = 10_000_00L)
        )
        repository.saveHouseholdMember(
            HouseholdMember(
                id = 1L,
                name = "Jordan",
                role = "adult",
                retirementAccounts = listOf(
                    HouseholdRetirementAccount(memberId = 1L, accountId = 10L, accountKind = "403b")
                )
            )
        )
        testScheduler.advanceUntilIdle()

        // Auto default uses only Household link (403b = $40k)
        val initial = viewModel.uiState.value
        assertFalse(initial.hasCustomAccountSelection)
        assertEquals(setOf(10L), initial.includedAccountIds)
        assertEquals(40_000_00L, initial.currentBalanceCents)
        assertEquals(0L, initial.hsaCurrentBalanceCents)

        // Include 403b + unlinked HSA savings account
        viewModel.openIncludedAccountsSheet()
        assertTrue(viewModel.uiState.value.showIncludedAccountsSheet)

        viewModel.setIncludedAccounts(listOf(10L, 12L))
        testScheduler.advanceUntilIdle()

        val custom = viewModel.uiState.value
        assertFalse(custom.showIncludedAccountsSheet)
        assertTrue(custom.hasCustomAccountSelection)
        assertEquals(setOf(10L, 12L), custom.includedAccountIds)
        assertEquals(50_000_00L, custom.connectedBalanceCents)
        assertEquals(50_000_00L, custom.currentBalanceCents)
        assertEquals(10_000_00L, custom.hsaCurrentBalanceCents)
        assertEquals(40_000_00L, custom.nonHsaCurrentBalanceCents)
        assertEquals(listOf(10L, 12L), repository.preferences.value.retirementIncludedAccountIds)

        // Explicit empty selection drops connected & starting balance to $0
        viewModel.setIncludedAccounts(emptyList())
        testScheduler.advanceUntilIdle()
        val emptyState = viewModel.uiState.value
        assertTrue(emptyState.hasCustomAccountSelection)
        assertTrue(emptyState.linkedAccounts.isEmpty())
        assertEquals(0L, emptyState.currentBalanceCents)

        // Reset back to Auto Default restores $40k Household link
        viewModel.setIncludedAccounts(null)
        testScheduler.advanceUntilIdle()
        val reset = viewModel.uiState.value
        assertFalse(reset.hasCustomAccountSelection)
        assertEquals(setOf(10L), reset.includedAccountIds)
        assertEquals(40_000_00L, reset.currentBalanceCents)
        assertNull(repository.preferences.value.retirementIncludedAccountIds)
    }

    @Test
    fun testFilterHistoryByAccounts_resumsMatchingAccountsOnly() {
        val raw = listOf(
            RetirementHistoryPoint(
                month = "2026-10",
                balanceCents = 34_000_00L,
                accounts = listOf(
                    RetirementAccountMonthBalance(accountId = 10L, balanceCents = 24_000_00L),
                    RetirementAccountMonthBalance(accountId = 11L, balanceCents = 10_000_00L)
                )
            )
        )
        val filtered = RetirementAccountResolver.filterHistoryPoints(
            history = raw,
            horizon = "All",
            currentYear = "2026",
            customIncludedIds = setOf(11L)
        )
        assertEquals(1, filtered.size)
        assertEquals(10_000_00L, filtered.first().balanceCents)
        assertEquals(listOf(11L), filtered.first().accounts.map { it.accountId })
    }
}
