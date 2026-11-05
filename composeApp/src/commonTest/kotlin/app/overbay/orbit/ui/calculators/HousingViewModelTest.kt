package app.overbay.orbit.ui.calculators

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.ui.calculators.housing.HousingViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HousingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: HousingViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = HousingViewModel(repository, testScope)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_computesDefaultBuyingScenario() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(375_000_00L, state.purchasePriceCents)
        assertEquals(75_000_00L, state.downPaymentCents)
        assertEquals(300_000_00L, state.buyingResult.loanAmountCents)
        assertEquals(6.0, state.interestRatePercent)
        assertEquals(30, state.loanTermYears)

        // PITI: ~$1,798.65 PI + $666.66 Tax + $125.00 Ins + $23 HOA = ~$2,613.31
        assertTrue(state.buyingResult.monthlyPrincipalInterestCents in 1798_00L..1800_00L)
        assertTrue(state.buyingResult.totalMonthlyPaymentCents in 2612_00L..2615_00L)
        assertTrue(state.amortizationSchedule.isNotEmpty())
    }

    @Test
    fun testMortgageSelection_populatesRemainingBalanceAndProceeds() = runTest(testDispatcher) {
        val mortgageAcc = Account(
            id = 42L,
            name = "Home Mortgage",
            type = AccountType.MORTGAGE,
            institution = "Chase",
            currentBalanceCents = -200_000_00L,
            estimatedValueCents = 350_000_00L
        )
        repository.saveAccount(mortgageAcc)
        testScheduler.advanceUntilIdle()

        viewModel.selectMortgage(42L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(42L, state.selectedMortgageAccountId)
        assertEquals(200_000_00L, state.remainingMortgageCents)
        assertEquals(350_000_00L, state.salePriceCents)

        assertNotNull(state.sellingProceeds)
        // Sale 350k - remaining 200k - fees (~5% + 2% + 2k + 1k = 17.5k + 7k + 3k = 27.5k) = 122.5k
        val proceeds = state.sellingProceeds!!
        assertEquals(122_500_00L, proceeds.netProceedsCents)
        // Auto-applied to down payment if proceeds > 0
        assertEquals(122_500_00L, state.downPaymentCents)
    }

    @Test
    fun testSellingInputs_recomputesNetProceeds() = runTest(testDispatcher) {
        viewModel.setSalePrice(400_000_00L)
        viewModel.setRemainingMortgage(250_000_00L)
        viewModel.setAgentCommissionPercent(5.0)
        viewModel.setSellerClosingPercent(2.0)
        viewModel.setProratedPropertyTax(2000_00L)
        viewModel.setMiscRepairs(1000_00L)
        testScheduler.advanceUntilIdle()

        val proceeds = viewModel.uiState.value.sellingProceeds
        assertNotNull(proceeds)
        assertEquals(20_000_00L, proceeds.agentCommissionCents)
        assertEquals(8_000_00L, proceeds.sellerClosingCents)
        assertEquals(119_000_00L, proceeds.netProceedsCents)
    }

    @Test
    fun testUseNetProceedsAsDownPayment_appliesProceedsToBuyingScenario() = runTest(testDispatcher) {
        viewModel.setSalePrice(500_000_00L)
        viewModel.setRemainingMortgage(300_000_00L)
        testScheduler.advanceUntilIdle()

        viewModel.useNetProceedsAsDownPayment()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val expectedProceeds = state.sellingProceeds?.netProceedsCents
        assertNotNull(expectedProceeds)
        assertTrue(expectedProceeds > 0L)
        assertEquals(expectedProceeds, state.downPaymentCents)
    }

    @Test
    fun testBuyingInputs_recomputesMonthlyPaymentBreakdown() = runTest(testDispatcher) {
        viewModel.setPurchasePrice(500_000_00L)
        viewModel.setDownPayment(100_000_00L)
        viewModel.setInterestRate(6.5)
        viewModel.setLoanTermYears(15)
        viewModel.setAnnualPropertyTaxes(6000_00L)
        viewModel.setAnnualHomeInsurance(1200_00L)
        viewModel.setMonthlyHoa(50_00L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(400_000_00L, state.buyingResult.loanAmountCents)
        assertEquals(15, state.loanTermYears)
        assertEquals(500_00L, state.buyingResult.monthlyPropertyTaxCents) // 6000 / 12
        assertEquals(100_00L, state.buyingResult.monthlyHomeInsuranceCents) // 1200 / 12
        assertEquals(50_00L, state.buyingResult.monthlyHoaCents)
        assertTrue(state.buyingResult.monthlyPrincipalInterestCents > 0L)
    }

    @Test
    fun testExtraPrincipal_acceleratesPayoffAndSavesInterest() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setExtraMonthlyPrincipal(500_00L) // Add $500/mo extra principal
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.monthsSavedWithExtraPrincipal > 60) // Should save > 5 years (60+ months) on 30y mortgage
        assertTrue(state.interestSavedWithExtraPrincipalCents > 50_000_00L) // Should save > $50,000 in interest
    }

    @Test
    fun testReset_restoresDefaults() = runTest(testDispatcher) {
        viewModel.setPurchasePrice(999_000_00L)
        viewModel.setLoanTermYears(15)
        testScheduler.advanceUntilIdle()

        viewModel.reset()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(375_000_00L, state.purchasePriceCents)
        assertEquals(30, state.loanTermYears)
    }
}
