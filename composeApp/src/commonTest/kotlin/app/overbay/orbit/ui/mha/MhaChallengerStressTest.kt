package app.overbay.orbit.ui.mha

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MhaTaxEngine
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
class MhaChallengerStressTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: MhaTrackerViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = MhaTrackerViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    // -------------------------------------------------------------------------
    // 1. Zero Designation Cap Boundary ($0 cap binds, $0 exclusion)
    // -------------------------------------------------------------------------

    @Test
    fun challenge_zeroDesignationCap_engine_mustBindToZeroExclusion() {
        // Minister spent $25,000 on housing, but church designated $0 cap
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 25_000_00L,
            designatedCapCents = 0L,
            fairMarketRentalValueCents = 30_000_00L
        )
        // IRC § 107: Designation cap of $0 means zero excludable allowance
        assertEquals(0L, cap, "A designated cap of $0 must limit excludable allowance to $0")

        val savings = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = 25_000_00L,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 5.0),
            designatedCapCents = 0L,
            fairMarketRentalValueCents = 30_000_00L
        )
        assertEquals(0L, savings.excludableAllowanceCents)
        assertEquals(0L, savings.totalSavingsCents)
    }

    @Test
    fun challenge_zeroDesignationCap_viewModel_stateMustReflectZeroExclusionAndCapBinding() = runTest(testDispatcher) {
        val catHousing = Category(id = 10L, name = "Rent", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-03-01", originalMerchant = "Housing", originalAmountCents = -20_000_00L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 0L, // $0 cap
            fairMarketRentalValueCents = 30_000_00L,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 4.95)
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20_000_00L, state.actualEligibleHousingSpentCents)
        assertEquals(0L, state.designatedCapCents)
        assertEquals(MhaLimitingFactor.DESIGNATION_CAP, state.limitingFactor)
        assertEquals(0L, state.excludableAllowanceCents, "ViewModel excludableAllowanceCents must be 0 when designated cap is 0")
        assertEquals(0L, state.taxCalculationResult?.totalSavingsCents, "Total savings must be 0 when cap is 0")

        collectJob.cancel()
    }

    // -------------------------------------------------------------------------
    // 2. Zero Housing Expenses Boundary ($0 spent binds)
    // -------------------------------------------------------------------------

    @Test
    fun challenge_zeroHousingExpenses_engineAndViewModel_bindToActualExpenses() = runTest(testDispatcher) {
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 0L,
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 36_000_00L
        )
        assertEquals(0L, cap)

        val savings = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = 0L,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 5.0),
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 36_000_00L
        )
        assertEquals(0L, savings.excludableAllowanceCents)
        assertEquals(0L, savings.totalSavingsCents)

        // ViewModel test with no transactions
        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 30_000_00L,
            fairMarketRentalValueCents = 36_000_00L,
            profile = MhaTaxProfile()
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.actualEligibleHousingSpentCents)
        assertEquals(0L, state.excludableAllowanceCents)
        assertEquals(MhaLimitingFactor.ACTUAL_EXPENSES, state.limitingFactor)
        assertEquals(0L, state.taxCalculationResult?.totalSavingsCents)

        collectJob.cancel()
    }

    // -------------------------------------------------------------------------
    // 3. Fair Market Rental Value Lower Than Expenses and Cap (FMRV Binds)
    // -------------------------------------------------------------------------

    @Test
    fun challenge_fmrvLowerThanExpensesAndCap_bindsToFmrv() = runTest(testDispatcher) {
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 40_000_00L,
            designatedCapCents = 45_000_00L,
            fairMarketRentalValueCents = 28_000_00L // FMRV lowest
        )
        assertEquals(28_000_00L, cap)

        val catHousing = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-04-01", originalMerchant = "Housing", originalAmountCents = -40_000_00L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 45_000_00L,
            fairMarketRentalValueCents = 28_000_00L,
            profile = MhaTaxProfile()
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(28_000_00L, state.excludableAllowanceCents)
        assertEquals(MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE, state.limitingFactor)

        collectJob.cancel()
    }

    @Test
    fun challenge_zeroFmrv_engineAndViewModel_mustBindToZeroExclusionAndZeroTaxSavings() = runTest(testDispatcher) {
        // Engine test
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 25_000_00L,
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 0L // $0 FMRV
        )
        assertEquals(0L, cap, "FMRV of $0 must limit excludable allowance to $0")

        val savings = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = 25_000_00L,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 5.0),
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 0L
        )
        assertEquals(0L, savings.excludableAllowanceCents, "Excludable allowance must be $0 under $0 FMRV")
        assertEquals(0L, savings.totalSavingsCents, "Total tax savings must be $0 under $0 FMRV")
        assertEquals(0L, savings.federalSavingsCents, "Federal tax savings must be $0 under $0 FMRV")
        assertEquals(0L, savings.stateSavingsCents, "State tax savings must be $0 under $0 FMRV")

        // ViewModel test
        val catHousing = Category(id = 10L, name = "Rent", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-03-01", originalMerchant = "Housing", originalAmountCents = -20_000_00L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 30_000_00L,
            fairMarketRentalValueCents = 0L, // $0 FMRV cap
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 4.95)
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20_000_00L, state.actualEligibleHousingSpentCents)
        assertEquals(0L, state.fairMarketRentalValueCents)
        assertEquals(MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE, state.limitingFactor, "Limiting factor must be FAIR_MARKET_RENTAL_VALUE when FMRV is 0")
        assertEquals(0L, state.excludableAllowanceCents, "ViewModel excludableAllowanceCents must be 0 when FMRV is 0")
        assertEquals(0L, state.taxCalculationResult?.totalSavingsCents, "Total savings must be 0 when FMRV is 0")

        collectJob.cancel()
    }

    @Test
    fun challenge_uncappedTrackingMode_tracksActualExpensesWithoutArtificialCap() = runTest(testDispatcher) {
        // Engine test: null caps
        val expenses = 35_000_00L
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = expenses,
            designatedCapCents = null,
            fairMarketRentalValueCents = null
        )
        assertEquals(expenses, cap, "Uncapped tracking must equal actual expenses")

        val savings = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = expenses,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 5.0),
            designatedCapCents = null,
            fairMarketRentalValueCents = null
        )
        assertEquals(expenses, savings.excludableAllowanceCents, "Uncapped excludable allowance must equal actual expenses")
        // 35,000 * 27% = 9,450
        assertEquals(9450_00L, savings.totalSavingsCents, "Uncapped tax savings must calculate on full actual expenses")

        // ViewModel test
        val catHousing = Category(id = 20L, name = "Mortgage", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Lender", originalAmountCents = -35_000_00L, originalCategoryId = 20L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = null, // uncapped
            fairMarketRentalValueCents = null, // uncapped
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 5.0)
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(35_000_00L, state.actualEligibleHousingSpentCents)
        assertNull(state.designatedCapCents)
        assertNull(state.fairMarketRentalValueCents)
        assertEquals(MhaLimitingFactor.UNCAPPED_ACTUAL, state.limitingFactor, "Limiting factor must be UNCAPPED_ACTUAL")
        assertEquals(35_000_00L, state.excludableAllowanceCents, "Excludable allowance must match actual spending")
        assertEquals(9450_00L, state.taxCalculationResult?.totalSavingsCents, "Total savings must be calculated on full actual spending")

        collectJob.cancel()
    }

    @Test
    fun challenge_dualZeroCaps_andMixedNullZeroCaps_strictZeroBinding() = runTest(testDispatcher) {
        // 1. Both caps = 0L
        val bothZeroCap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 20_000_00L,
            designatedCapCents = 0L,
            fairMarketRentalValueCents = 0L
        )
        assertEquals(0L, bothZeroCap, "Both caps 0L must result in 0L cap")

        // 2. Designation cap = 0L, FMRV = null
        val desZeroNullFmrv = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 20_000_00L,
            designatedCapCents = 0L,
            fairMarketRentalValueCents = null
        )
        assertEquals(0L, desZeroNullFmrv, "0L designation cap with null FMRV must result in 0L cap")

        // 3. Designation cap = null, FMRV = 0L
        val nullDesZeroFmrv = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 20_000_00L,
            designatedCapCents = null,
            fairMarketRentalValueCents = 0L
        )
        assertEquals(0L, nullDesZeroFmrv, "Null designation cap with 0L FMRV must result in 0L cap")

        // ViewModel test with null designation cap and 0L FMRV
        val cat = Category(id = 30L, name = "Rent", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(cat)
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "2026-07-01", originalMerchant = "Landlord", originalAmountCents = -15_000_00L, originalCategoryId = 30L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = null,
            fairMarketRentalValueCents = 0L,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 5.0)
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(15_000_00L, state.actualEligibleHousingSpentCents)
        assertNull(state.designatedCapCents)
        assertEquals(0L, state.fairMarketRentalValueCents)
        assertEquals(MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE, state.limitingFactor)
        assertEquals(0L, state.excludableAllowanceCents)
        assertEquals(0L, state.taxCalculationResult?.totalSavingsCents)

        collectJob.cancel()
    }

    // -------------------------------------------------------------------------
    // 4. Tax Savings in States Without Income Tax (FL, TX, WA, NV -> 0% state tax)
    // -------------------------------------------------------------------------

    @Test
    fun challenge_noIncomeTaxStates_yieldZeroStateTaxSavings() {
        val noTaxStates = listOf("FL", "TX", "WA", "NV", "AK", "TN", "WY", "SD", "NH")
        for (state in noTaxStates) {
            val profile = MhaTaxProfile(
                mode = "household",
                filingStatus = FilingStatus.SINGLE,
                state = state
            )
            val result = MhaTaxEngine.calculateMhaSavings(
                actualEligibleCents = 20_000_00L,
                profile = profile,
                householdMembers = listOf(HouseholdMember(id = 1L, name = "Pastor", grossIncomeAnnualCents = 80_000_00L)),
                designatedCapCents = 25_000_00L,
                fairMarketRentalValueCents = 25_000_00L
            )
            assertEquals(0.0, result.stateRate, "State rate for $state must be 0.0")
            assertEquals(0L, result.stateSavingsCents, "State tax savings for $state must be 0")
            assertEquals(result.federalSavingsCents, result.totalSavingsCents, "Total savings must equal federal savings in $state")
        }
    }

    // -------------------------------------------------------------------------
    // 5. Progressive Income Brackets (Household Mode: MFJ vs Single)
    // -------------------------------------------------------------------------

    @Test
    fun challenge_progressiveTaxBrackets_singleVsMarriedFilingJointly() {
        val grossIncome = 120_000_00L
        val housingExclusion = 20_000_00L
        val member = HouseholdMember(id = 1L, name = "Pastor", grossIncomeAnnualCents = grossIncome)

        // 1. Single
        val singleProfile = MhaTaxProfile(
            mode = "household",
            filingStatus = FilingStatus.SINGLE,
            state = "TX" // 0% state tax to isolate federal
        )
        val singleResult = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = housingExclusion,
            profile = singleProfile,
            householdMembers = listOf(member),
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 30_000_00L
        )

        // Single Standard Deduction = $16,100
        // Taxable Before = $120,000 - $16,100 = $103,900 (in 22% bracket: 50,400 to 105,700)
        // Taxable After = $103,900 - $20,000 = $83,900 (still in 22% bracket)
        // Federal Savings = $20,000 * 22% = $4,400
        assertEquals(103_900_00L, singleResult.taxableIncomeBeforeMhaCents)
        assertEquals(83_900_00L, singleResult.taxableIncomeAfterMhaCents)
        assertEquals(4400_00L, singleResult.federalSavingsCents)

        // 2. Married Filing Jointly
        val mfjProfile = MhaTaxProfile(
            mode = "household",
            filingStatus = FilingStatus.MARRIED_JOINT,
            state = "TX"
        )
        val mfjResult = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = housingExclusion,
            profile = mfjProfile,
            householdMembers = listOf(member),
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 30_000_00L
        )

        // MFJ Standard Deduction = $32,200
        // Taxable Before = $120,000 - $32,200 = $87,800 (in 12% bracket: 24,800 to 100,800)
        // Taxable After = $87,800 - $20,000 = $67,800 (still in 12% bracket)
        // Federal Savings = $20,000 * 12% = $2,400
        assertEquals(87_800_00L, mfjResult.taxableIncomeBeforeMhaCents)
        assertEquals(67_800_00L, mfjResult.taxableIncomeAfterMhaCents)
        assertEquals(2400_00L, mfjResult.federalSavingsCents)

        // Single saved $4,400 vs MFJ $2,400 on identical $120k income due to progressive bracket shifts
        assertTrue(singleResult.federalSavingsCents > mfjResult.federalSavingsCents)
    }

    // -------------------------------------------------------------------------
    // 6. Transaction Eligibility Toggle Roundtrip: Provenance & Reset
    // -------------------------------------------------------------------------

    @Test
    fun challenge_transactionEligibilityToggleRoundtrip_preservesProvenanceAndClearsOnReset() = runTest(testDispatcher) {
        val cat = Category(id = 5L, name = "Repairs", isIncome = false, isTransfer = false, mhaDefaultEligible = false)
        repository.saveCategory(cat)

        val tx = Transaction(
            id = 777L,
            accountId = 1L,
            originalDate = "2026-06-20",
            originalMerchant = "Plumbing Supply",
            originalAmountCents = -45000L,
            originalCategoryId = 5L,
            notes = "Water heater repair"
        )
        repository.saveTransaction(tx)

        viewModel.setYear(2026)
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        // 1. Initial State: not eligible
        assertFalse(viewModel.uiState.value.eligibleTransactions.any { it.id == 777L })
        assertTrue(viewModel.uiState.value.reviewQueueTransactions.any { it.id == 777L })

        // 2. Toggle to Eligible (User action)
        viewModel.toggleTransactionEligibility(777L)
        testScheduler.advanceUntilIdle()

        val toggledTx = repository.transactions.value.first { it.id == 777L }
        // Verify provenance: original_* fields strictly untouched
        assertEquals("2026-06-20", toggledTx.originalDate)
        assertEquals("Plumbing Supply", toggledTx.originalMerchant)
        assertEquals(-45000L, toggledTx.originalAmountCents)
        assertEquals(5L, toggledTx.originalCategoryId)
        // Verify user overlay
        assertEquals(true, toggledTx.editedMhaEligible)
        assertEquals("user", toggledTx.editedMhaEligibleSource)
        assertEquals(EditSource.USER, toggledTx.editSource)
        // Verify in UI state
        assertTrue(viewModel.uiState.value.eligibleTransactions.any { it.id == 777L })

        // 3. Reset Transaction (Clears user edits)
        viewModel.resetTransactionEligibility(777L)
        testScheduler.advanceUntilIdle()

        val resetTx = repository.transactions.value.first { it.id == 777L }
        // Verify original fields untouched
        assertEquals("2026-06-20", resetTx.originalDate)
        assertEquals("Plumbing Supply", resetTx.originalMerchant)
        assertEquals(-45000L, resetTx.originalAmountCents)
        assertEquals(5L, resetTx.originalCategoryId)
        // Verify user edits cleared
        assertNull(resetTx.editedMhaEligible)
        assertNull(resetTx.editedMhaEligibleSource)
        assertEquals(EditSource.NONE, resetTx.editSource)
        // Verify UI state reflects reset
        assertFalse(viewModel.uiState.value.eligibleTransactions.any { it.id == 777L })
        assertTrue(viewModel.uiState.value.reviewQueueTransactions.any { it.id == 777L })

        collectJob.cancel()
    }
}
