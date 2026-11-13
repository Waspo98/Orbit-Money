package app.overbay.orbit.domain

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.addBalanceRecord
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.BalanceRecordSource
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceHistoryEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
    }

    @AfterTest
    fun tearDown() {
        storage.clear()
    }

    @Test
    fun testUpsertAutoCheckpoint_deduplicatesUnchangedSameMonthAndCapturesChangesAndMonthlyAnchors() {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 250_000L)
        val zeroManual = Account(id = 2L, name = "Empty Placeholder", type = AccountType.CHECKING, currentBalanceCents = 0L)
        val zeroSf = Account(
            id = 3L,
            name = "Card",
            type = AccountType.CREDIT,
            currentBalanceCents = 0L,
            simplefinAccountId = "sf-card-1",
            isManual = false
        )

        // Zero manual placeholder with no history is not recorded
        val afterZeroManual = BalanceHistoryEngine.upsertAutoCheckpoint(emptyList(), zeroManual, "2026-04-01")
        assertTrue(afterZeroManual.isEmpty())

        // Zero SimpleFIN account DOES record initial baseline
        val afterZeroSf = BalanceHistoryEngine.upsertAutoCheckpoint(emptyList(), zeroSf, "2026-04-01")
        assertEquals(1, afterZeroSf.size)
        assertEquals(BalanceRecordSource.SIMPLEFIN, afterZeroSf.single().source)

        // Initial non-zero baseline
        val r1 = BalanceHistoryEngine.upsertAutoCheckpoint(emptyList(), checking, "2026-04-01", 250_000L)
        assertEquals(1, r1.size)

        // Unchanged later in same month -> returns exact same list instance (0 allocations/writes)
        val r2 = BalanceHistoryEngine.upsertAutoCheckpoint(r1, checking, "2026-04-18", 250_000L)
        assertSame(r1, r2)

        // Balance change in same month -> appends new record
        val r3 = BalanceHistoryEngine.upsertAutoCheckpoint(r2, checking, "2026-04-22", 310_000L)
        assertEquals(2, r3.size)

        // Second balance change on the SAME day -> updates 2026-04-22 in place
        val r4 = BalanceHistoryEngine.upsertAutoCheckpoint(r3, checking, "2026-04-22", 325_000L)
        assertEquals(2, r4.size)
        assertEquals(325_000L, r4.last().balanceCents)

        // Unchanged balance in a NEW calendar month -> appends monthly anchor
        val r5 = BalanceHistoryEngine.upsertAutoCheckpoint(r4, checking, "2026-05-01", 325_000L)
        assertEquals(3, r5.size)
        assertEquals(listOf("2026-04-01", "2026-04-22", "2026-05-01"), r5.map { it.recordDate })
    }

    @Test
    fun testUpsertAutoCheckpoint_preservesSameDayManualOverSimpleFin() {
        val acct = Account(
            id = 5L,
            name = "Brokerage",
            type = AccountType.INVESTMENT,
            currentBalanceCents = 900_000L,
            simplefinAccountId = "sf-brok",
            isManual = false
        )
        val manualList = listOf(
            BalanceRecord(
                id = 1L,
                accountId = 5L,
                recordDate = "2026-06-15",
                balanceCents = 950_000L,
                source = BalanceRecordSource.MANUAL
            )
        )

        val afterSf = BalanceHistoryEngine.upsertAutoCheckpoint(
            records = manualList,
            account = acct,
            recordDate = "2026-06-15",
            balanceCents = 910_000L,
            source = BalanceRecordSource.SIMPLEFIN
        )
        assertSame(manualList, afterSf)
        assertEquals(950_000L, afterSf.single().balanceCents)
    }

    @Test
    fun testEnsureDailySnapshotCheckpoints_skipsArchivedAndProperties() {
        val snap = OrbitWorkspaceSnapshot.empty().copy(
            accounts = listOf(
                Account(id = 1L, name = "Active Savings", type = AccountType.SAVINGS, currentBalanceCents = 500_000L),
                Account(id = 2L, name = "Archived Savings", type = AccountType.SAVINGS, currentBalanceCents = 400_000L, isArchived = true),
                Account(id = 3L, name = "Home Property", type = AccountType.OTHER, estimatedValueCents = 45_000_000L, isProperty = true)
            )
        )

        val hydrated = BalanceHistoryEngine.ensureDailySnapshotCheckpoints(snap, "2026-08-01")
        assertEquals(1, hydrated.balanceRecords.size)
        assertEquals(1L, hydrated.balanceRecords.single().accountId)

        // Second pass on same day preserves referential identity
        val secondPass = BalanceHistoryEngine.ensureDailySnapshotCheckpoints(hydrated, "2026-08-01")
        assertSame(hydrated, secondPass)
    }

    @Test
    fun testRepositoryMutations_autoCaptureOnAccountSaveTransactionDeltaMergeAndDelete() = runTest(testDispatcher) {
        val today = LocalDateUtils.todayIsoDate()
        val checking = repository.saveAccount(
            Account(id = 0L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 100_000L, isManual = true)
        )
        assertEquals(1, repository.snapshot.value.balanceRecords.size)
        assertEquals(100_000L, repository.snapshot.value.balanceRecords.single().balanceCents)

        // Manual transaction updates balance and updates today's checkpoint in place
        repository.saveTransaction(
            Transaction(accountId = checking.id, originalDate = today, originalMerchant = "Grocery", originalAmountCents = -15_000L)
        )
        assertEquals(1, repository.snapshot.value.balanceRecords.size)
        assertEquals(85_000L, repository.snapshot.value.balanceRecords.single().balanceCents)

        // Merge source account with historical record into target checking
        val oldChecking = repository.saveAccount(
            Account(id = 0L, name = "Old Checking", type = AccountType.CHECKING, currentBalanceCents = 25_000L, isManual = true)
        )
        repository.addBalanceRecord(oldChecking.id, "2025-12-31", 40_000L)
        repository.mergeAccounts(sourceAccountId = oldChecking.id, targetAccountId = checking.id, combineBalances = true)

        val targetRecords = repository.snapshot.value.balanceRecords
            .filter { it.accountId == checking.id }
            .sortedBy { it.recordDate }
        assertEquals(2, targetRecords.size)
        assertEquals("2025-12-31", targetRecords[0].recordDate)
        assertEquals(40_000L, targetRecords[0].balanceCents)
        assertEquals(today, targetRecords[1].recordDate)
        assertEquals(110_000L, targetRecords[1].balanceCents)
        assertTrue(repository.snapshot.value.balanceRecords.none { it.accountId == oldChecking.id })

        // Deleting account cleans up its balanceRecords (zero orphans)
        repository.deleteAccount(checking.id)
        assertTrue(repository.snapshot.value.balanceRecords.isEmpty())
    }
}
