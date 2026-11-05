package app.overbay.orbit.ui.networth

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Transaction
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
class NetWorthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: NetWorthViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = NetWorthViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_calculatesAssetsLiabilitiesAndNetWorth() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 1000000L) // $10,000
        val investment = Account(id = 2L, name = "401k", type = AccountType.INVESTMENT, currentBalanceCents = 5000000L) // $50,000
        val creditCard = Account(id = 3L, name = "Credit Card", type = AccountType.CREDIT, currentBalanceCents = -200000L) // -$2,000
        val autoLoan = Account(id = 4L, name = "Auto Loan", type = AccountType.LOAN, currentBalanceCents = -1500000L) // -$15,000

        repository.saveAccount(checking)
        repository.saveAccount(investment)
        repository.saveAccount(creditCard)
        repository.saveAccount(autoLoan)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(6000000L, state.snapshot.totalAssetsCents) // $60,000
        assertEquals(1700000L, state.snapshot.totalLiabilitiesCents) // $17,000
        assertEquals(4300000L, state.snapshot.netWorthCents) // $43,000
        assertEquals(2, state.assetAccounts.size)
        assertEquals(2, state.liabilityAccounts.size)
        assertFalse(state.isLoading)

        collectJob.cancel()
    }

    @Test
    fun testPeriodFiltering_adjustsHistoryLength() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 100000L)
        repository.saveAccount(checking)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Default: TWO_YEARS (24 points, main branch parity)
        assertEquals(NetWorthHorizon.TWO_YEARS, viewModel.uiState.value.selectedHorizon)
        assertEquals(24, viewModel.uiState.value.filteredPoints.size)

        // 1 Year
        viewModel.setHorizon(NetWorthHorizon.ONE_YEAR)
        testScheduler.advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.filteredPoints.size)

        // 3 Years
        viewModel.setHorizon(NetWorthHorizon.THREE_YEARS)
        testScheduler.advanceUntilIdle()
        assertEquals(36, viewModel.uiState.value.filteredPoints.size)

        // 5 Years
        viewModel.setHorizon(NetWorthHorizon.FIVE_YEARS)
        testScheduler.advanceUntilIdle()
        assertEquals(60, viewModel.uiState.value.filteredPoints.size)

        // 10 Years
        viewModel.setHorizon(NetWorthHorizon.TEN_YEARS)
        testScheduler.advanceUntilIdle()
        assertEquals(120, viewModel.uiState.value.filteredPoints.size)

        // All Time (minimum 12 months when no older transactions exist)
        viewModel.setHorizon(NetWorthHorizon.ALL_TIME)
        testScheduler.advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.filteredPoints.size)

        collectJob.cancel()
    }

    @Test
    fun testScrubbing_selectsAndClearsScrubPoint() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 100000L)
        repository.saveAccount(checking)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val points = viewModel.uiState.value.filteredPoints
        assertTrue(points.isNotEmpty())

        val targetPoint = points[2]
        viewModel.selectScrubPoint(targetPoint)
        testScheduler.advanceUntilIdle()

        assertEquals(targetPoint, viewModel.uiState.value.scrubbedPoint)
        assertEquals(targetPoint, viewModel.uiState.value.activePoint)

        // Clear scrub
        viewModel.selectScrubPoint(null)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.scrubbedPoint)
        assertEquals(points.last(), viewModel.uiState.value.activePoint)

        collectJob.cancel()
    }

    @Test
    fun testMortgageWithEstimatedValue_contributesHomeEquity() = runTest(testDispatcher) {
        val mortgage = Account(
            id = 10L,
            name = "Home",
            type = AccountType.MORTGAGE,
            currentBalanceCents = -25000000L, // Loan $250k
            estimatedValueCents = 40000000L  // Property $400k
        )
        repository.saveAccount(mortgage)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // Property value $400k counts as asset, loan $250k counts as liability
        assertEquals(40000000L, state.snapshot.totalAssetsCents)
        assertEquals(25000000L, state.snapshot.totalLiabilitiesCents)
        // Equity = $150k
        assertEquals(15000000L, state.snapshot.netWorthCents)
        assertEquals(15000000L, state.snapshot.realEstateEquityCents)

        collectJob.cancel()
    }
}
