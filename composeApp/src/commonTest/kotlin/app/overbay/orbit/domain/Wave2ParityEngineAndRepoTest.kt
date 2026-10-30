package app.overbay.orbit.domain

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.addBalanceRecord
import app.overbay.orbit.data.importTransactionsWithBatch
import app.overbay.orbit.data.muteSimpleFinAccountWarnings
import app.overbay.orbit.data.unmuteSimpleFinAccountWarning
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.previewBackupJson
import app.overbay.orbit.data.resetTransactionFields
import app.overbay.orbit.data.saveGoalWithAllocations
import app.overbay.orbit.data.undoImportBatch
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class Wave2ParityEngineAndRepoTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testGoalAllocationEngine_percentAndFixedWithReserveAndConflicts() {
        val hysa = Account(
            id = 1L,
            name = "HYSA",
            type = AccountType.SAVINGS,
            currentBalanceCents = 1_200_000L // $12,000
        )
        val checking = Account(
            id = 2L,
            name = "Checking",
            type = AccountType.CHECKING,
            currentBalanceCents = 500_000L // $5,000
        )

        // HYSA has $2,000 reserve -> $10,000 allocatable. 60% = $6,000.
        val emergencyGoal = Goal(
            id = 10L,
            name = "Emergency Fund",
            targetAmountCents = 1_000_000L,
            currentAmountCents = 600_000L,
            allocations = listOf(
                GoalAllocation(
                    goalId = 10L,
                    accountId = 1L,
                    allocationType = GoalAllocationType.PERCENT,
                    allocationPercent = 60.0,
                    reserveAmountCents = 200_000L
                )
            )
        )
        // Checking fixed $1,500 + HYSA 30% ($3,000) -> no conflict yet (90% total on HYSA)
        val vacationGoal = Goal(
            id = 11L,
            name = "Vacation",
            targetAmountCents = 500_000L,
            currentAmountCents = 450_000L,
            allocations = listOf(
                GoalAllocation(
                    goalId = 11L,
                    accountId = 1L,
                    allocationType = GoalAllocationType.PERCENT,
                    allocationPercent = 30.0,
                    reserveAmountCents = 200_000L
                ),
                GoalAllocation(
                    goalId = 11L,
                    accountId = 2L,
                    allocationType = GoalAllocationType.FIXED,
                    allocationAmountCents = 150_000L
                )
            )
        )

        val summaries = GoalAllocationEngine.computeAccountSummaries(
            accounts = listOf(hysa, checking),
            goals = listOf(emergencyGoal, vacationGoal)
        )
        val hysaSummary = summaries.find { it.accountId == 1L }
        assertNotNull(hysaSummary)
        assertEquals(1_000_000L, hysaSummary.allocatableAmountCents)
        assertEquals(900_000L, hysaSummary.allocatedAmountCents)
        assertEquals(100_000L, hysaSummary.remainingAmountCents)

        val noConflict = GoalAllocationEngine.detectConflicts(
            draftGoal = vacationGoal,
            existingGoals = listOf(emergencyGoal, vacationGoal),
            accounts = listOf(hysa, checking)
        )
        assertTrue(noConflict.isEmpty())

        // Drafting 50% on HYSA pushes total to 110% -> triggers conflict against emergencyGoal
        val overDraft = vacationGoal.copy(
            allocations = listOf(
                GoalAllocation(
                    goalId = 11L,
                    accountId = 1L,
                    allocationType = GoalAllocationType.PERCENT,
                    allocationPercent = 50.0,
                    reserveAmountCents = 200_000L
                )
            )
        )
        val conflicts = GoalAllocationEngine.detectConflicts(
            draftGoal = overDraft,
            existingGoals = listOf(emergencyGoal, vacationGoal),
            accounts = listOf(hysa, checking)
        )
        assertEquals(1, conflicts.size)
        assertEquals(1L, conflicts.first().accountId)
        assertEquals(110.0, conflicts.first().totalPercent)
    }

    @Test
    fun testGoalPaceEtaAndWhatIfSimulation() {
        val currentYm = LocalDateUtils.currentYearMonth()
        val m2 = LocalDateUtils.addMonthsToYearMonth(currentYm, -2)
        val m1 = LocalDateUtils.addMonthsToYearMonth(currentYm, -1)

        val goal = Goal(
            id = 20L,
            name = "House Down Payment",
            targetAmountCents = 1_000_000L, // $10,000
            currentAmountCents = 400_000L   // $4,000 ($6,000 remaining)
        )
        val history = listOf(
            app.overbay.orbit.data.model.GoalMonthlyPoint(m2, 200_000L),
            app.overbay.orbit.data.model.GoalMonthlyPoint(m1, 300_000L),
            app.overbay.orbit.data.model.GoalMonthlyPoint(currentYm, 400_000L)
        )

        val (pace, eta) = GoalAllocationEngine.computeMonthlyPaceAndEta(goal, history)
        assertEquals(100_000L, pace) // +$1,000/mo pace
        assertEquals(6, eta.months)  // $6,000 / $1,000 = 6 months
        assertEquals("projected", eta.status)

        // Adding +$500/mo boost ($1,500/mo total) cuts remaining time to 4 months
        val boosted = GoalAllocationEngine.simulateWhatIf(
            currentAmountCents = 400_000L,
            targetAmountCents = 1_000_000L,
            baselinePaceCents = pace,
            additionalMonthlyCents = 50_000L
        )
        assertEquals(4, boosted.months)
    }

    @Test
    fun testUpcomingScheduleHelper_givingKindHistoryAverageAndMonthDays() {
        assertEquals(UpcomingKind.GIVING, UpcomingScheduleHelper.detectKind("Grace Community Church Tithe"))
        assertEquals(UpcomingKind.SUBSCRIPTION, UpcomingScheduleHelper.detectKind("Netflix.com"))

        val electricBill = UpcomingItem(
            id = 30L,
            name = "City Electric",
            merchant = "City Electric",
            amountCents = 10_000L,
            amountStrategy = AmountStrategy.HISTORY_AVERAGE,
            nextDate = "2026-05-01",
            recurrenceRule = RecurrenceRule.MonthDays(listOf(1, 15))
        )
        val pastTxns = listOf(
            Transaction(id = 1L, accountId = 1L, originalDate = "2026-02-01", originalMerchant = "City Electric Co", originalAmountCents = -12_000L),
            Transaction(id = 2L, accountId = 1L, originalDate = "2026-03-01", originalMerchant = "City Electric Co", originalAmountCents = -15_000L),
            Transaction(id = 3L, accountId = 1L, originalDate = "2026-04-01", originalMerchant = "City Electric Co", originalAmountCents = -18_000L)
        )

        val projected = UpcomingScheduleHelper.computeProjectedAmount(electricBill, pastTxns, "2026-04-20")
        assertTrue(projected.isProjectedAverage)
        assertEquals(15_000L, projected.amountCents) // Average of $120, $150, $180 = $150

        val nextOccurrence = UpcomingScheduleHelper.advanceRecurrence(electricBill, "2026-05-01")
        assertEquals("2026-05-15", nextOccurrence)
    }

    @Test
    fun testNetWorthEngine_incorporatesBalanceRecordCheckpoints() {
        val brokerage = Account(
            id = 1L,
            name = "Brokerage",
            type = AccountType.INVESTMENT,
            currentBalanceCents = 5_000_000L // $50,000 in 2026-04
        )
        val records = listOf(
            BalanceRecord(id = 1L, accountId = 1L, recordDate = "2026-02-28", balanceCents = 4_200_000L)
        )

        val points = NetWorthEngine.reconstructHistory(
            accounts = listOf(brokerage),
            transactions = emptyList(),
            balanceRecords = records,
            monthsCount = 4,
            latestMonth = "2026-04"
        )
        assertEquals(4, points.size)
        val febPoint = points.find { it.month == "2026-02" }
        val aprPoint = points.find { it.month == "2026-04" }
        assertNotNull(febPoint)
        assertNotNull(aprPoint)
        assertEquals(4_200_000L, febPoint.snapshot.netWorthCents)
        assertEquals(5_000_000L, aprPoint.snapshot.netWorthCents)
    }

    @Test
    fun testRepositoryExtensions_perFieldResetReappliesRuleAndPreservesUserMerchant() = runTest(testDispatcher) {
        val catCoffee = Category(id = 5L, name = "Coffee Shops")
        val catManual = Category(id = 6L, name = "Dining Out")
        repository.saveCategory(catCoffee)
        repository.saveCategory(catManual)

        val rule = TransactionRule(
            id = 1L,
            name = "Starbucks Rule",
            priority = 10,
            enabled = true,
            conditions = listOf(RuleCondition(RuleField.MERCHANT, RuleOperator.CONTAINS, "STARBUCKS")),
            actions = listOf(RuleAction(RuleActionType.SET_CATEGORY, value = "5", targetCategoryId = 5L))
        )
        repository.saveRule(rule)

        val tx = Transaction(
            id = 100L,
            accountId = 1L,
            originalDate = "2026-04-10",
            originalMerchant = "STARBUCKS #1234",
            originalAmountCents = -650L,
            editedMerchant = "Morning Coffee",
            editedMerchantSource = "user",
            editedCategoryId = 6L,
            editedCategoryIdSource = "user",
            editSource = EditSource.USER
        )
        repository.saveTransaction(tx)

        // Reset ONLY category_id -> user merchant edit ("Morning Coffee") stays intact, rule sets category 5L!
        repository.resetTransactionFields(100L, listOf("category_id"))

        val updated = repository.transactions.value.find { it.id == 100L }
        assertNotNull(updated)
        assertEquals("Morning Coffee", updated.editedMerchant)
        assertEquals("user", updated.editedMerchantSource)
        assertEquals(5L, updated.effectiveCategoryId)
    }

    @Test
    fun testRepositoryExtensions_batchImportUndoAndBackupPreview() = runTest(testDispatcher) {
        val acct = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 250_000L)
        repository.saveAccount(acct)
        repository.addBalanceRecord(1L, "2026-04-15", 250_000L)
        repository.saveGoalWithAllocations(
            Goal(id = 0L, name = "Safety Net", targetAmountCents = 500_000L, currentAmountCents = 250_000L),
            stealFromOthers = false
        )

        val importedBatch = repository.importTransactionsWithBatch(
            fileName = "april_export.csv",
            rawCsvContent = null,
            newTransactions = listOf(
                Transaction(accountId = 1L, originalDate = "2026-04-01", originalMerchant = "Trader Joes", originalAmountCents = -8_400L),
                Transaction(accountId = 1L, originalDate = "2026-04-02", originalMerchant = "Target", originalAmountCents = -4_200L)
            )
        ).getOrThrow()

        assertEquals(2, repository.transactions.value.size)
        assertEquals(1, repository.snapshot.value.importBatches.size)

        // Preview backup JSON before undo
        val exportedJson = repository.exportBackupJson()
        val preview = repository.previewBackupJson(exportedJson).getOrThrow()
        assertEquals(1, preview.accountCount)
        assertEquals(2, preview.transactionCount)
        assertEquals(1, preview.goalCount)
        assertEquals(1, preview.balanceRecordCount)

        // Undo batch -> removes both imported transactions cleanly
        val undone = repository.undoImportBatch(importedBatch.id).getOrThrow()
        assertTrue(undone)
        assertTrue(repository.transactions.value.isEmpty())
        assertNull(repository.snapshot.value.importBatches.find { it.id == importedBatch.id })
    }

    @Test
    fun testRetirementEngine_hsaEarlyRetirementBridgeAnalysis() {
        // Retiring at 55 (10 bridge years until Medicare/HSA penalty-free age 65), $60k/yr spending -> $600k bridge need
        val shortfallBridge = RetirementCalculatorEngine.computeHsaBridgeAnalysis(
            currentAge = 50.0,
            retirementAge = 55.0,
            nonHsaCurrentBalanceCents = 200_000_00L, // $200k non-HSA
            nonHsaMonthlySavingsCents = 1_000_00L,
            hsaCurrentBalanceCents = 150_000_00L,    // $150k locked in HSA until 65
            hsaMonthlySavingsCents = 600_00L,
            annualSpendingCents = 60_000_00L,
            realReturn = 0.04
        )
        assertNotNull(shortfallBridge)
        assertEquals(10, shortfallBridge.bridgeYears)
        assertEquals(600_000_00L, shortfallBridge.bridgeNeedCents)
        assertFalse(shortfallBridge.isBridgeCovered)
        assertTrue(shortfallBridge.shortfallCents > 0L)

        // Retiring at 65+ needs no pre-65 HSA bridge
        val post65 = RetirementCalculatorEngine.computeHsaBridgeAnalysis(
            currentAge = 50.0,
            retirementAge = 65.0,
            nonHsaCurrentBalanceCents = 500_000_00L,
            nonHsaMonthlySavingsCents = 2_000_00L,
            hsaCurrentBalanceCents = 100_000_00L,
            hsaMonthlySavingsCents = 500_00L,
            annualSpendingCents = 60_000_00L,
            realReturn = 0.04
        )
        assertNull(post65)
    }

    @Test
    fun testSimpleFinWarningParser_extractsAccountAndSuppressesMutedAccountWarnings() = runTest(testDispatcher) {
        val singlePartialError = "Partial sync — SimpleFIN reported: Connection to Bright Start 529 - College Savings Plan may need attention"
        val initialIssues = SimpleFinWarningParser.parseUnmutedIssues(singlePartialError)
        assertEquals(1, initialIssues.size)
        assertEquals("Bright Start 529 - College Savings Plan", initialIssues[0].accountLabel)

        // User toggles "Do not warn me about this account again"
        repository.muteSimpleFinAccountWarnings(listOf(initialIssues[0].accountLabel))
        assertEquals(listOf("Bright Start 529 - College Savings Plan"), repository.preferences.value.mutedSimpleFinAccounts)

        // Subsequent partial sync with only Bright Start 529 produces zero unmuted issues (popup suppressed)
        val afterMute = SimpleFinWarningParser.parseUnmutedIssues(
            rawError = singlePartialError,
            mutedAccounts = repository.preferences.value.mutedSimpleFinAccounts
        )
        assertTrue(afterMute.isEmpty())

        // Multi-account partial sync suppresses Bright Start 529 while still surfacing Chase Checking
        val multiError = "Partial sync - SimpleFIN reported: Connection to Bright Start 529 - College Savings Plan may need attention; Connection to Chase Checking failed"
        val partialUnmuted = SimpleFinWarningParser.parseUnmutedIssues(
            rawError = multiError,
            mutedAccounts = repository.preferences.value.mutedSimpleFinAccounts
        )
        assertEquals(1, partialUnmuted.size)
        assertEquals("Chase Checking", partialUnmuted[0].accountLabel)

        // Unmuting restores warnings for that account
        repository.unmuteSimpleFinAccountWarning("Bright Start 529 - College Savings Plan")
        assertTrue(repository.preferences.value.mutedSimpleFinAccounts.isEmpty())
    }
}
