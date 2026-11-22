package app.overbay.orbit.ui.mha

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.MhaYearTaxRecord
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.data.model.Transaction
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
class MhaTrackerViewModelTest {

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

    @Test
    fun testInitialState_3LimitRule_actualExpensesLowest() = runTest(testDispatcher) {
        val catHousing = Category(id = 10L, name = "Rent & Mortgage", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)

        // Seed 2 housing transactions for 2026 totaling $20,000
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-01-15", originalMerchant = "Landlord", originalAmountCents = -1200000L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-02-15", originalMerchant = "Landlord", originalAmountCents = -800000L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        // Set designation cap to $30k and FMRV to $36k
        viewModel.updateMhaConfig(
            designationCapCents = 3000000L,
            fairMarketRentalValueCents = 3600000L,
            profile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 4.95)
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2000000L, state.actualEligibleHousingSpentCents)
        assertEquals(3000000L, state.mhaDesignationCapCents)
        assertEquals(3600000L, state.fairMarketRentalValueCents)

        // 3-limit binding: Actual expenses ($20,000) is the lowest
        assertEquals(2000000L, state.excludableAllowanceCents)
        assertEquals(MhaLimitingFactor.ACTUAL_EXPENSES, state.limitingFactor)

        collectJob.cancel()
    }

    @Test
    fun test3LimitRule_designationCapLowest() = runTest(testDispatcher) {
        val catHousing = Category(id = 10L, name = "Rent", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)

        // Spend $32,000 in 2026
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-03-01", originalMerchant = "Housing", originalAmountCents = -3200000L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 2500000L, // Cap is $25k (lowest)
            fairMarketRentalValueCents = 3000000L, // FMRV is $30k
            profile = MhaTaxProfile()
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2500000L, state.excludableAllowanceCents)
        assertEquals(MhaLimitingFactor.DESIGNATION_CAP, state.limitingFactor)

        collectJob.cancel()
    }

    @Test
    fun test3LimitRule_fairMarketRentalValueLowest() = runTest(testDispatcher) {
        val catHousing = Category(id = 10L, name = "Rent", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)

        // Spend $35,000, Designation Cap $35,000, FMRV $28,000 (lowest)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-04-01", originalMerchant = "Housing", originalAmountCents = -3500000L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 3500000L,
            fairMarketRentalValueCents = 2800000L,
            profile = MhaTaxProfile()
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2800000L, state.excludableAllowanceCents)
        assertEquals(MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE, state.limitingFactor)

        collectJob.cancel()
    }

    @Test
    fun testTaxSavingsCalculation_simpleMode() = runTest(testDispatcher) {
        val catHousing = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)

        // $20,000 excludable housing spent
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Housing", originalAmountCents = -2000000L, originalCategoryId = 10L))

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 2500000L,
            fairMarketRentalValueCents = 2500000L,
            profile = MhaTaxProfile(
                mode = "simple",
                simpleFederalRatePercent = 22.0,
                simpleStateRatePercent = 4.95
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val savings = viewModel.uiState.value.taxSavingsResult
        assertNotNull(savings)

        // Federal: $20,000 * 22% = $4,400
        assertEquals(440000L, savings.federalSavingsCents)
        // State: $20,000 * 4.95% = $990
        assertEquals(99000L, savings.stateSavingsCents)
        // Total: $5,390
        assertEquals(539000L, savings.totalSavingsCents)
        // SECA estimate: $20,000 * 15.3% = $3,060
        assertEquals(306000L, savings.secaTaxEstimateCents)

        collectJob.cancel()
    }

    @Test
    fun testTaxSavingsCalculation_householdMode() = runTest(testDispatcher) {
        val catHousing = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(catHousing)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Housing", originalAmountCents = -2000000L, originalCategoryId = 10L))

        // Seed household member with $120,000 income
        val snap = repository.snapshot.value.copy(
            members = listOf(HouseholdMember(id = 1L, name = "Earner", grossIncomeAnnualCents = 12000000L))
        )
        repository.loadDemoData(snap)

        viewModel.setYear(2026)
        viewModel.updateMhaConfig(
            designationCapCents = 3000000L,
            fairMarketRentalValueCents = 3000000L,
            profile = MhaTaxProfile(
                mode = "household",
                filingStatus = FilingStatus.MARRIED_JOINT,
                state = "IL"
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val savings = viewModel.uiState.value.taxSavingsResult
        assertNotNull(savings)
        assertTrue(savings.federalSavingsCents > 0L)
        assertTrue(savings.totalSavingsCents > 0L)

        collectJob.cancel()
    }

    @Test
    fun testToggleTransactionMhaEligibility_preservesProvenance() = runTest(testDispatcher) {
        val catOther = Category(id = 99L, name = "Hardware", isIncome = false, isTransfer = false, mhaDefaultEligible = false)
        repository.saveCategory(catOther)

        // Seed transaction with no MHA flag
        val originalTx = Transaction(
            id = 55L,
            accountId = 1L,
            originalDate = "2026-06-15",
            originalMerchant = "Ace Hardware",
            originalAmountCents = -7500L,
            originalCategoryId = 99L
        )
        repository.saveTransaction(originalTx)

        viewModel.setYear(2026)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Toggle Ace Hardware to MHA Eligible
        viewModel.toggleTransactionEligibility(55L)
        testScheduler.advanceUntilIdle()

        val updatedTx = repository.transactions.value.find { it.id == 55L }
        assertNotNull(updatedTx)

        // Provenance verification: Original fields are untouched
        assertEquals("Ace Hardware", updatedTx.originalMerchant)
        assertEquals(-7500L, updatedTx.originalAmountCents)
        assertEquals("2026-06-15", updatedTx.originalDate)
        assertEquals(99L, updatedTx.originalCategoryId)

        // User edit overlay populated
        assertEquals(true, updatedTx.editedMhaEligible)
        assertEquals("user", updatedTx.editedMhaEligibleSource)
        assertEquals(EditSource.USER, updatedTx.editSource)

        // ViewModel state reflects updated eligibility
        assertTrue(viewModel.uiState.value.eligibleTransactions.any { it.id == 55L })

        collectJob.cancel()
    }

    @Test
    fun testResetTransaction_restoresOriginalEligibility() = runTest(testDispatcher) {
        val cat = Category(id = 1L, name = "Supplies", isIncome = false, isTransfer = false, mhaDefaultEligible = false)
        repository.saveCategory(cat)

        repository.saveTransaction(Transaction(id = 88L, accountId = 1L, originalDate = "2026-07-01", originalMerchant = "Office Depot", originalAmountCents = -5000L, originalCategoryId = 1L))

        viewModel.setYear(2026)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. User sets eligible
        viewModel.toggleTransactionEligibility(88L)
        testScheduler.advanceUntilIdle()
        assertEquals(true, repository.transactions.value.find { it.id == 88L }?.editedMhaEligible)

        // 2. Reset transaction
        repository.resetTransaction(88L)
        testScheduler.advanceUntilIdle()

        val resetTx = repository.transactions.value.find { it.id == 88L }
        assertNotNull(resetTx)
        assertNull(resetTx.editedMhaEligible)
        assertNull(resetTx.editedMhaEligibleSource)
        assertEquals(EditSource.NONE, resetTx.editSource)
        assertFalse(viewModel.uiState.value.eligibleTransactions.any { it.id == 88L })

        collectJob.cancel()
    }

    @Test
    fun testBatchSetMhaEligible_updatesMultipleTransactionsWithProvenance() = runTest(testDispatcher) {
        val cat = Category(id = 1L, name = "General", isIncome = false, isTransfer = false, mhaDefaultEligible = false)
        repository.saveCategory(cat)

        repository.saveTransaction(Transaction(id = 101L, accountId = 1L, originalDate = "2026-08-01", originalMerchant = "Item A", originalAmountCents = -1000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 102L, accountId = 1L, originalDate = "2026-08-02", originalMerchant = "Item B", originalAmountCents = -2000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 103L, accountId = 1L, originalDate = "2026-08-03", originalMerchant = "Item C", originalAmountCents = -3000L, originalCategoryId = 1L))

        viewModel.setYear(2026)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.batchSetMhaEligible(setOf(101L, 102L), eligible = true)
        testScheduler.advanceUntilIdle()

        val tx1 = repository.transactions.value.find { it.id == 101L }
        val tx2 = repository.transactions.value.find { it.id == 102L }
        val tx3 = repository.transactions.value.find { it.id == 103L }

        assertEquals(true, tx1?.editedMhaEligible)
        assertEquals(EditSource.USER, tx1?.editSource)
        assertEquals(true, tx2?.editedMhaEligible)
        assertEquals(EditSource.USER, tx2?.editSource)
        assertNull(tx3?.editedMhaEligible)

        collectJob.cancel()
    }

    @Test
    fun testYearSwitching_filtersTransactionsByCalendarYear() = runTest(testDispatcher) {
        val cat = Category(id = 1L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(cat)

        // Seed 2025 and 2026 transactions
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2025-11-01", originalMerchant = "Rent 2025", originalAmountCents = -150000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-01-01", originalMerchant = "Rent 2026", originalAmountCents = -160000L, originalCategoryId = 1L))

        viewModel.setYear(2026)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // In 2026 (default)
        assertEquals(160000L, viewModel.uiState.value.actualEligibleHousingSpentCents)
        assertEquals(1, viewModel.uiState.value.eligibleTransactions.size)
        assertEquals("Rent 2026", viewModel.uiState.value.eligibleTransactions.first().effectiveMerchant)

        // Switch to 2025
        viewModel.setYear(2025)
        testScheduler.advanceUntilIdle()

        assertEquals(150000L, viewModel.uiState.value.actualEligibleHousingSpentCents)
        assertEquals(1, viewModel.uiState.value.eligibleTransactions.size)
        assertEquals("Rent 2025", viewModel.uiState.value.eligibleTransactions.first().effectiveMerchant)

        collectJob.cancel()
    }

    @Test
    fun testConfigSheet_openDismissAndUpdate() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showConfigSheet)

        viewModel.openConfigSheet()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showConfigSheet)

        viewModel.dismissConfigSheet()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showConfigSheet)

        collectJob.cancel()
    }

    @Test
    fun testYearConfig_storesPerYearEffectiveRateWithoutMutatingOtherYears() = runTest(testDispatcher) {
        val cat = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(cat)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-04-01", originalMerchant = "Rent 2026", originalAmountCents = -20_000_00L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2025-04-01", originalMerchant = "Rent 2025", originalAmountCents = -25_000_00L, originalCategoryId = 10L))

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Configure 2026 live (26.95%, $30k cap)
        viewModel.setYear(2026)
        viewModel.saveYearConfig(
            year = 2026,
            updatedProfile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 4.95),
            designationCapCents = 30_000_00L,
            fairMarketRentalValueCents = null,
            customEffectiveRatePercent = null,
            lockYearRate = false
        )
        testScheduler.advanceUntilIdle()
        assertEquals(5390_00L, viewModel.uiState.value.taxCalculationResult?.totalSavingsCents)

        // Switch to 2025 and save a custom 18.0% effective rate and $20k cap
        viewModel.setYear(2025)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isHistoricalYearWithoutSavedRate)

        viewModel.saveYearConfig(
            year = 2025,
            updatedProfile = viewModel.uiState.value.taxProfile,
            designationCapCents = 20_000_00L,
            fairMarketRentalValueCents = null,
            customEffectiveRatePercent = 18.0,
            lockYearRate = true
        )
        testScheduler.advanceUntilIdle()

        val state2025 = viewModel.uiState.value
        assertTrue(state2025.isUsingStoredYearRate)
        assertFalse(state2025.isHistoricalYearWithoutSavedRate)
        assertEquals(20_000_00L, state2025.excludableAllowanceCents)
        assertEquals(3600_00L, state2025.taxCalculationResult?.totalSavingsCents)

        // Verify 2026 retained its own cap and live rate
        viewModel.setYear(2026)
        testScheduler.advanceUntilIdle()
        val state2026 = viewModel.uiState.value
        assertEquals(30_000_00L, state2026.designatedCapCents)
        assertEquals(5390_00L, state2026.taxCalculationResult?.totalSavingsCents)

        collectJob.cancel()
    }

    @Test
    fun testRecordCurrentYearRateIfChanged_persistsYearRecordAutomatically() = runTest(testDispatcher) {
        val cat = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(cat)
        val currentYr = app.overbay.orbit.domain.LocalDateUtils.todayLocalDate().year
        repository.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "$currentYr-05-01",
                originalMerchant = "Mortgage",
                originalAmountCents = -10_000_00L,
                originalCategoryId = 10L
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        viewModel.setYear(currentYr)
        testScheduler.advanceUntilIdle()

        viewModel.recordCurrentYearRateIfChanged()
        testScheduler.advanceUntilIdle()

        val savedRecord = repository.snapshot.value.mhaTaxProfile.yearlyRecords[currentYr.toString()]
        assertNotNull(savedRecord)
        assertEquals(26.95, savedRecord.effectiveRatePercent)
        assertFalse(savedRecord.isLocked)

        collectJob.cancel()
    }

    @Test
    fun testCategoryFilterMonthGrouping_andShowExcludedToggle() = runTest(testDispatcher) {
        val catMortgage = Category(id = 10L, name = "Mortgage", icon = "🏠", mhaDefaultEligible = true)
        val catUtil = Category(id = 11L, name = "Utilities", icon = "💡", mhaDefaultEligible = true)
        val catFood = Category(id = 12L, name = "Dining", icon = "🍽️", mhaDefaultEligible = false)
        repository.saveCategory(catMortgage)
        repository.saveCategory(catUtil)
        repository.saveCategory(catFood)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-11-05", originalMerchant = "Lender", originalAmountCents = -2000_00L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-10-12", originalMerchant = "Electric Co", originalAmountCents = -300_00L, originalCategoryId = 11L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "2026-11-10", originalMerchant = "Coffee Shop", originalAmountCents = -15_00L, originalCategoryId = 12L))

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        viewModel.setYear(2026)
        testScheduler.advanceUntilIdle()

        val defaultState = viewModel.uiState.value
        // Only counted transactions are visible by default
        assertEquals(2, defaultState.visibleTransactions.size)
        assertEquals(2, defaultState.groupedVisibleMonths.size)
        assertEquals("2026-11", defaultState.groupedVisibleMonths[0].yearMonth)
        assertEquals("2026-10", defaultState.groupedVisibleMonths[1].yearMonth)
        assertEquals(listOf(10L, 11L), defaultState.categoryBreakdown.map { it.categoryId })

        // Filter by Utilities category
        viewModel.setSelectedCategory(11L)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.visibleTransactions.size)
        assertEquals(300_00L, viewModel.uiState.value.visibleTotalCents)

        // Clear category and toggle Show Excluded
        viewModel.setSelectedCategory(null)
        viewModel.setShowExcludedInList(true)
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.visibleTransactions.size)
        val excluded = viewModel.uiState.value.visibleTransactions.first { it.id == 3L }
        assertFalse(excluded.isEligible)
        assertTrue(excluded.eligibilityReason.isNotBlank())

        collectJob.cancel()
    }

    @Test
    fun testHistoricalYear_unlockingWithCapsRevertsToLiveProfileWithoutSticking() = runTest(testDispatcher) {
        val cat = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(cat)
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2025-04-01", originalMerchant = "Rent 2025", originalAmountCents = -25_000_00L, originalCategoryId = 10L))

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        viewModel.setYear(2025)
        testScheduler.advanceUntilIdle()

        // 1. Lock a custom 18.0% rate + $20k cap for 2025
        viewModel.saveYearConfig(
            year = 2025,
            updatedProfile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 4.95),
            designationCapCents = 20_000_00L,
            fairMarketRentalValueCents = null,
            customEffectiveRatePercent = 18.0,
            lockYearRate = true
        )
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUsingStoredYearRate)
        assertEquals(3600_00L, viewModel.uiState.value.taxCalculationResult?.totalSavingsCents)

        // 2. Uncheck saved rate while keeping 2025's $20k cap -> must revert to live profile (26.95%)
        viewModel.saveYearConfig(
            year = 2025,
            updatedProfile = MhaTaxProfile(mode = "simple", simpleFederalRatePercent = 22.0, simpleStateRatePercent = 4.95),
            designationCapCents = 20_000_00L,
            fairMarketRentalValueCents = null,
            customEffectiveRatePercent = null,
            lockYearRate = false
        )
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isUsingStoredYearRate)
        assertTrue(viewModel.uiState.value.isHistoricalYearWithoutSavedRate)
        assertEquals(20_000_00L, viewModel.uiState.value.designatedCapCents)
        assertEquals(5390_00L, viewModel.uiState.value.taxCalculationResult?.totalSavingsCents)

        // 3. Updating active simple rate propagates to 2025 while 2025 keeps its $20k cap
        viewModel.saveYearConfig(
            year = 2025,
            updatedProfile = viewModel.uiState.value.taxProfile.copy(simpleFederalRatePercent = 24.0, simpleStateRatePercent = 5.0),
            designationCapCents = 20_000_00L,
            fairMarketRentalValueCents = null,
            customEffectiveRatePercent = null,
            lockYearRate = false
        )
        testScheduler.advanceUntilIdle()
        assertEquals(5800_00L, viewModel.uiState.value.taxCalculationResult?.totalSavingsCents)

        // 4. Clearing caps and saved rate when no global caps exist removes the 2025 record cleanly
        viewModel.saveYearConfig(
            year = 2025,
            updatedProfile = viewModel.uiState.value.taxProfile,
            designationCapCents = null,
            fairMarketRentalValueCents = null,
            customEffectiveRatePercent = null,
            lockYearRate = false
        )
        testScheduler.advanceUntilIdle()
        assertNull(repository.snapshot.value.mhaTaxProfile.yearlyRecords["2025"])
        assertEquals(7250_00L, viewModel.uiState.value.taxCalculationResult?.totalSavingsCents)

        collectJob.cancel()
    }

    @Test
    fun testRecordCurrentYearRateIfChanged_preservesExistingGlobalCapsConfiguredFlag() = runTest(testDispatcher) {
        val cat = Category(id = 10L, name = "Housing", isIncome = false, isTransfer = false, mhaDefaultEligible = true)
        repository.saveCategory(cat)
        val currentYr = app.overbay.orbit.domain.LocalDateUtils.todayLocalDate().year
        repository.updateMhaTaxProfile(
            MhaTaxProfile(
                mode = "simple",
                simpleFederalRatePercent = 22.0,
                simpleStateRatePercent = 4.95,
                designatedCapCents = 30_000_00L
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "$currentYr-05-01",
                originalMerchant = "Mortgage",
                originalAmountCents = -10_000_00L,
                originalCategoryId = 10L
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        viewModel.setYear(currentYr)
        testScheduler.advanceUntilIdle()

        viewModel.recordCurrentYearRateIfChanged()
        testScheduler.advanceUntilIdle()

        val savedRecord = repository.snapshot.value.mhaTaxProfile.yearlyRecords[currentYr.toString()]
        assertNotNull(savedRecord)
        assertTrue(savedRecord.capsConfigured)
        assertEquals(30_000_00L, savedRecord.designatedCapCents)
        assertFalse(savedRecord.useLiveProfile)

        collectJob.cancel()
    }

    @Test
    fun switchingToGeneralTaxModeUncapsHistoricalCappedYear() = runTest(testDispatcher) {
        val catUtility = Category(id = 10L, name = "Utilities", mhaDefaultEligible = true)
        repository.saveCategory(catUtility)
        repository.saveHouseholdMember(
            HouseholdMember(
                id = 1L,
                name = "Jordan",
                grossIncomeAnnualCents = 100_000_00L
            )
        )
        val initialProfile = MhaTaxProfile(
            trackingMode = TaxTrackingMode.MHA,
            filingStatus = FilingStatus.SINGLE,
            designatedCapCents = 12_000_00L,
            fairMarketRentalValueCents = 12_000_00L,
            yearlyRecords = mapOf(
                "2025" to MhaYearTaxRecord(
                    year = 2025,
                    effectiveRatePercent = 25.0,
                    designatedCapCents = 10_000_00L,
                    capsConfigured = true,
                    isLocked = true,
                    useLiveProfile = false
                )
            )
        )
        repository.updateMhaTaxProfile(initialProfile)
        repository.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "2025-05-01",
                originalMerchant = "Electric Co",
                originalAmountCents = -15_000_00L,
                originalCategoryId = 10L
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 2L,
                accountId = 1L,
                originalDate = "2025-05-02",
                originalMerchant = "Coffee Shop",
                originalAmountCents = -5_00L
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        viewModel.setYear(2025)
        viewModel.setShowExcludedInList(true)
        testScheduler.advanceUntilIdle()

        assertEquals(10_000_00L, viewModel.uiState.value.excludableAllowanceCents)
        assertEquals(MhaLimitingFactor.DESIGNATION_CAP, viewModel.uiState.value.limitingFactor)

        repository.updateMhaTaxProfile(initialProfile.copy(trackingMode = TaxTrackingMode.GENERAL))
        testScheduler.advanceUntilIdle()

        val generalState = viewModel.uiState.value
        assertEquals(15_000_00L, generalState.excludableAllowanceCents)
        assertEquals(3_750_00L, generalState.taxCalculationResult?.totalSavingsCents)
        assertEquals(MhaLimitingFactor.UNCAPPED_ACTUAL, generalState.limitingFactor)
        val excludedReason = generalState.visibleTransactions.first { it.id == 2L }.eligibilityReason
        assertTrue(excludedReason.contains("Tax Ded."))
        assertFalse(excludedReason.contains("MHA"))

        collectJob.cancel()
    }
}
