package app.overbay.orbit.ui.mha

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
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
}
