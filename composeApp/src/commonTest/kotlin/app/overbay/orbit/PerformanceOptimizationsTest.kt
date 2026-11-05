package app.overbay.orbit

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.LocalSnapshotEngine
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.ui.dashboard.DashboardTransactionAggregator
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceOptimizationsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testDashboardTransactionAggregator_matchesFullPassAndStableTieBreaking() {
        val accounts = listOf(
            Account(id = 1L, name = "Checking", type = AccountType.CHECKING, mhaDefaultEligible = false),
            Account(id = 2L, name = "Housing Account", type = AccountType.CHECKING, mhaDefaultEligible = true)
        )
        val accountById = accounts.associateBy { it.id }

        val transactions = listOf(
            Transaction(id = 10L, accountId = 1L, originalDate = "2026-10-15", originalMerchant = "Salary", originalAmountCents = 450_000L, originalCategoryId = 1L),
            Transaction(id = 11L, accountId = 1L, originalDate = "2026-10-18", originalMerchant = "Groceries A", originalAmountCents = -12_500L, originalCategoryId = 2L),
            Transaction(id = 12L, accountId = 1L, originalDate = "2026-10-18", originalMerchant = "Uncat 1", originalAmountCents = -3_200L, originalCategoryId = null),
            Transaction(id = 13L, accountId = 1L, originalDate = "2026-10-18", originalMerchant = "Uncat 2", originalAmountCents = -8_900L, originalCategoryId = null),
            Transaction(id = 14L, accountId = 2L, originalDate = "2026-10-20", originalMerchant = "Electric Utility", originalAmountCents = -21_000L, originalCategoryId = 3L),
            Transaction(id = 15L, accountId = 1L, originalDate = "2026-10-21", originalMerchant = "Hidden Huge Expense", originalAmountCents = -99_000L, originalCategoryId = 2L),
            Transaction(id = 16L, accountId = 1L, originalDate = "2026-10-22", originalMerchant = "Internal Transfer", originalAmountCents = -50_000L, originalCategoryId = 4L, isTransfer = true),
            Transaction(id = 17L, accountId = 1L, originalDate = "2026-10-25", originalMerchant = "Ignored Refund", originalAmountCents = 5_000L, originalCategoryId = null, isIgnored = true),
            Transaction(id = 18L, accountId = 1L, originalDate = "2026-09-12", originalMerchant = "Prior Salary", originalAmountCents = 420_000L, originalCategoryId = 1L),
            Transaction(id = 19L, accountId = 2L, originalDate = "2026-09-14", originalMerchant = "Prior Housing", originalAmountCents = -180_000L, originalCategoryId = 3L)
        )
        val hiddenIds = setOf(15L)

        val scan = DashboardTransactionAggregator.scan(
            transactions = transactions,
            accountById = accountById,
            selectedMonth = "2026-10",
            priorMonth = "2026-09",
            currentYearStr = "2026",
            hiddenBiggestIds = hiddenIds
        )

        assertEquals(450_000L, scan.incomeCents)
        assertEquals(12_500L + 3_200L + 8_900L + 21_000L + 99_000L, scan.expenseCents)
        assertEquals(420_000L, scan.priorIncomeCents)
        assertEquals(180_000L, scan.priorExpenseCents)
        assertEquals(2, scan.uncategorizedCount)
        // Stable tie-breaker on same date ("2026-10-18"): id=12 encountered before id=13
        assertEquals(listOf(12L, 13L), scan.topUncategorized.map { it.id })
        // Recent top-5 sorts by effectiveDate DESC then id DESC (includes transfers, excludes ignored)
        val expectedRecentIds = transactions
            .filter { !it.effectiveIsIgnored }
            .sortedWith(compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id })
            .take(5)
            .map { it.id }
        assertEquals(expectedRecentIds, scan.topRecent.map { it.id })
        // Biggest expenses excludes hiddenIds (15L) and transfers (16L)
        val expectedBiggestIds = listOf(14L, 11L, 13L, 12L)
        assertEquals(expectedBiggestIds, scan.topBiggestExpenses.map { it.id })
        // MHA YTD includes account 2 (mhaDefaultEligible = true) across 2026-09 and 2026-10
        assertEquals(2, scan.mhaTransactionCount)
        assertEquals(21_000L + 180_000L, scan.mhaSpendingCents)
    }

    @Test
    fun testStartupPreferencesSidecar_fastSelfHostedBootSkipsLocalWorkspaceParse() = runTest(testDispatcher) {
        val localEngine = LocalSnapshotEngine(storage)
        val prefs = OrbitPreferences(
            storageMode = StorageMode.SELF_HOSTED,
            selfHostedServerUrl = "https://orbit.example.com",
            onboardingCompleted = true
        )
        localEngine.saveStartupPreferences(prefs)

        // Seed remote offline cache snapshot while leaving SNAPSHOT_KEY unparseable sentinel
        // to prove fast SELF_HOSTED startup does not decode orbit_workspace_snapshot.json.
        storage.saveString(LocalSnapshotEngine.SNAPSHOT_KEY, "{not-parsed-on-fast-self-hosted-boot")
        val cachedRemote = OrbitWorkspaceSnapshot.empty().copy(
            accounts = listOf(Account(id = 7L, name = "Cached Checking", type = AccountType.CHECKING, currentBalanceCents = 250_000L)),
            preferences = prefs
        )
        val json = Json { encodeDefaults = true }
        storage.saveString(
            "orbit_remote_cache_snapshot.json",
            json.encodeToString(OrbitWorkspaceSnapshot.serializer(), cachedRemote)
        )

        val repo = OrbitRepository(storage)
        repo.initialize()

        assertEquals(StorageMode.SELF_HOSTED, repo.storageMode.value)
        assertEquals(1, repo.accounts.value.size)
        assertEquals("Cached Checking", repo.accounts.value.first().name)
        assertNull(storage.loadString("${LocalSnapshotEngine.SNAPSHOT_KEY}.corrupt_backup"))
    }

    @Test
    fun testReportMerchantLogoStatus_loadedSkipsSnapshotChurn_failedClearsCandidateUrl() = runTest(testDispatcher) {
        val repo = OrbitRepository(storage)
        repo.initialize()
        repo.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "2026-10-20",
                originalMerchant = "Starbucks",
                originalAmountCents = -650L,
                merchantLogo = MerchantLogo(
                    merchantKey = "starbucks",
                    url = "https://img.example.com/starbucks.png",
                    status = "candidate"
                )
            )
        )
        testScheduler.advanceUntilIdle()

        val listBeforeLoaded = repo.transactions.value
        repo.reportMerchantLogoStatus("starbucks", "loaded")
        testScheduler.advanceUntilIdle()

        // Must not clone or re-emit transaction list when status is "loaded"
        assertSame(listBeforeLoaded, repo.transactions.value)
        assertNotNull(repo.transactions.value.first().merchantLogo?.url)

        // When status is "failed", candidate URL must be cleared so fallback icon renders
        repo.reportMerchantLogoStatus("starbucks", "failed")
        testScheduler.advanceUntilIdle()
        assertEquals("failed", repo.transactions.value.first().merchantLogo?.status)
        assertNull(repo.transactions.value.first().merchantLogo?.url)
    }
}
