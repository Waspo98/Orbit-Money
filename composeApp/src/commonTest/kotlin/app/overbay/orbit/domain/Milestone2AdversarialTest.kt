package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.network.RuleConditionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Milestone2AdversarialTest {

    // =========================================================================
    // 1. RuleMatcherEngine & Provenance Stress Tests
    // =========================================================================

    @Test
    fun testRuleMatcher_provenanceLock_userEditSourceVulnerability() {
        // Empirically challenge: Does applyRules protect transactions with editSource == EditSource.USER?
        val rule = TransactionRule(
            id = 1L,
            name = "Rename and Recat",
            conditions = listOf(
                RuleCondition(field = RuleConditionField.MERCHANT, operator = RuleOperator.CONTAINS, value = "uber")
            ),
            actions = listOf(
                RuleAction(type = RuleActionType.RENAME_MERCHANT, value = "Uber Ride"),
                RuleAction(type = RuleActionType.SET_CATEGORY, value = "10")
            ),
            enabled = true
        )

        // Case A: User explicitly edited merchant; merchant is protected by field-level source
        val txnUserMerchant = Transaction(
            id = 101L,
            accountId = 1L,
            originalDate = "2026-04-10",
            originalMerchant = "UBER TRIP 123",
            originalAmountCents = -2500L,
            editedMerchant = "My Custom Uber Trip",
            editedMerchantSource = "user",
            editSource = EditSource.USER
        )
        val evalA = RuleMatcherEngine.applyRules(listOf(txnUserMerchant), listOf(rule)).first()
        assertEquals("My Custom Uber Trip", evalA.effectiveMerchant)
        assertEquals("user", evalA.editedMerchantSource)

        // Case B: Transaction with EditSource.USER (user adjusted amount, merchant unedited)
        val txnUserAmount = Transaction(
            id = 102L,
            accountId = 1L,
            originalDate = "2026-04-10",
            originalMerchant = "UBER TRIP 456",
            originalAmountCents = -3000L,
            editedAmountCents = -2800L,
            editSource = EditSource.USER
        )
        val evalB = RuleMatcherEngine.applyRules(listOf(txnUserAmount), listOf(rule)).first()

        // Verified: RuleMatcherEngine must NEVER mutate transactions with editSource == EditSource.USER
        assertNull(evalB.editedMerchant, "Automated rule must not set editedMerchant on EditSource.USER txn")
        assertEquals("UBER TRIP 456", evalB.effectiveMerchant, "Effective merchant remains original UBER TRIP 456")
        assertNull(evalB.editedMerchantSource, "editedMerchantSource must remain null")
        assertNull(evalB.editedCategoryId, "Automated rule must not categorize EditSource.USER txn")
        assertNull(evalB.editedCategoryIdSource, "editedCategoryIdSource must remain null")
        assertEquals(-2800L, evalB.effectiveAmountCents, "User edited amount must be preserved")
        assertEquals(EditSource.USER, evalB.editSource, "editSource must remain EditSource.USER")
        assertEquals(txnUserAmount, evalB, "Transaction with editSource == EditSource.USER must be returned completely unchanged")
    }

    @Test
    fun testRuleMatcher_originalFieldsStrictlyImmutable() {
        val rule = TransactionRule(
            id = 2L,
            name = "Mutate Everything",
            conditions = listOf(
                RuleCondition(field = RuleConditionField.MERCHANT, operator = RuleOperator.CONTAINS, value = "target")
            ),
            actions = listOf(
                RuleAction(type = RuleActionType.RENAME_MERCHANT, value = "Target Stores"),
                RuleAction(type = RuleActionType.SET_CATEGORY, value = "42"),
                RuleAction(type = RuleActionType.MARK_TRANSFER, value = "true"),
                RuleAction(type = RuleActionType.MARK_IGNORED, value = "true")
            ),
            enabled = true
        )

        val originalTxn = Transaction(
            id = 103L,
            accountId = 1L,
            originalDate = "2026-04-15",
            originalMerchant = "Target T-1234",
            originalAmountCents = -5420L,
            originalCategoryId = null,
            isTransfer = false,
            isIgnored = false
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(originalTxn), listOf(rule)).first()

        // Original fields MUST remain 100% unchanged
        assertEquals("Target T-1234", evaluated.originalMerchant)
        assertEquals(-5420L, evaluated.originalAmountCents)
        assertEquals("2026-04-15", evaluated.originalDate)
        assertNull(evaluated.originalCategoryId)
        assertFalse(evaluated.originalIsTransfer)
        assertFalse(evaluated.originalIsIgnored)

        // Effective fields reflect the applied rule
        assertEquals("Target Stores", evaluated.effectiveMerchant)
        assertEquals(42L, evaluated.effectiveCategoryId)
        assertTrue(evaluated.effectiveIsTransfer)
        assertTrue(evaluated.effectiveIsIgnored)
        assertEquals(EditSource.RULE, evaluated.editSource)
    }

    @Test
    fun testRuleMatcher_adversarialConditions_malformedRegexDoesNotCrash() {
        val brokenRegexRule = TransactionRule(
            id = 3L,
            name = "Broken Regex Rule",
            conditions = listOf(
                RuleCondition(field = RuleConditionField.MERCHANT, operator = RuleOperator.REGEX, value = "[unclosed-bracket(")
            ),
            actions = listOf(RuleAction(type = RuleActionType.SET_CATEGORY, value = "99")),
            enabled = true
        )

        val txn = Transaction(
            id = 104L,
            accountId = 1L,
            originalDate = "2026-04-16",
            originalMerchant = "Some Random Merchant",
            originalAmountCents = -1000L
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(txn), listOf(brokenRegexRule)).first()
        assertNull(evaluated.editedCategoryId)
        assertEquals(EditSource.NONE, evaluated.editSource)
    }

    @Test
    fun testRuleMatcher_amountConditions_absoluteCentsMatching() {
        val amountBetweenRule = TransactionRule(
            id = 4L,
            name = "Mid-range expense",
            conditions = listOf(
                RuleCondition(
                    field = RuleConditionField.AMOUNT,
                    operator = RuleOperator.BETWEEN,
                    value = "20.00-50.00",
                    minAmountCentsValue = 20_00L,
                    maxAmountCentsValue = 50_00L
                )
            ),
            actions = listOf(RuleAction(type = RuleActionType.SET_CATEGORY, value = "7")),
            enabled = true
        )

        val txn = Transaction(
            id = 105L,
            accountId = 1L,
            originalDate = "2026-04-17",
            originalMerchant = "Dinner Out",
            originalAmountCents = -35_00L
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(txn), listOf(amountBetweenRule)).first()
        assertEquals(7L, evaluated.effectiveCategoryId)
    }

    @Test
    fun testRuleMatcher_amountConditions_betweenStringParsingAndDtoMapping() {
        val dto = RuleConditionDto(
            field = "amount",
            operator = "between",
            value = "25.00 - 75.00"
        )
        val domainCond = dto.toDomain()
        assertEquals(RuleOperator.BETWEEN, domainCond.operator)
        assertEquals(RuleConditionField.AMOUNT, domainCond.field)

        val dtoIs = RuleConditionDto(field = "account", operator = "is", value = "1")
        assertEquals(RuleOperator.IS, dtoIs.toDomain().operator)

        val dtoIsNot = RuleConditionDto(field = "account", operator = "is_not", value = "2")
        assertEquals(RuleOperator.IS_NOT, dtoIsNot.toDomain().operator)

        // Evaluate rule constructed from DTO (minAmountCentsValue and maxAmountCentsValue are null)
        val stringBetweenRule = TransactionRule(
            id = 5L,
            name = "Between from DTO",
            conditions = listOf(domainCond),
            actions = listOf(RuleAction(type = RuleActionType.SET_CATEGORY, value = "9")),
            enabled = true
        )

        val matchingTxn = Transaction(
            id = 106L,
            accountId = 1L,
            originalDate = "2026-04-18",
            originalMerchant = "Grocery",
            originalAmountCents = -50_00L
        )
        val nonMatchingTxn = Transaction(
            id = 107L,
            accountId = 1L,
            originalDate = "2026-04-18",
            originalMerchant = "Coffee",
            originalAmountCents = -10_00L
        )

        val evals = RuleMatcherEngine.applyRules(listOf(matchingTxn, nonMatchingTxn), listOf(stringBetweenRule))
        assertEquals(9L, evals[0].effectiveCategoryId, "Txn with $50.00 must match between '25.00 - 75.00'")
        assertNull(evals[1].editedCategoryId, "Txn with $10.00 must NOT match between '25.00 - 75.00'")
    }

    // =========================================================================
    // 2. TransferMatcherEngine Boundary & Edge Tests
    // =========================================================================

    @Test
    fun testTransferMatcher_exact4DaysBoundaryVs5Days() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        // Pair 1: Exactly 4 days apart (April 10 to April 14) -> Pairs
        val txOut4d = Transaction(id = 201L, accountId = 1L, originalDate = "2026-04-10", originalMerchant = "Out 4d", originalAmountCents = -200_00L, originalCategoryId = 8L)
        val txIn4d = Transaction(id = 202L, accountId = 2L, originalDate = "2026-04-14", originalMerchant = "In 4d", originalAmountCents = 200_00L, originalCategoryId = 8L)
        val result4d = TransferMatcherEngine.matchTransfers(listOf(txOut4d, txIn4d), categories)
        assertEquals(1, result4d.matchCount)

        // Pair 2: Exactly 5 days apart (April 10 to April 15) -> Rejects
        val txOut5d = Transaction(id = 203L, accountId = 1L, originalDate = "2026-04-10", originalMerchant = "Out 5d", originalAmountCents = -300_00L, originalCategoryId = 8L)
        val txIn5d = Transaction(id = 204L, accountId = 2L, originalDate = "2026-04-15", originalMerchant = "In 5d", originalAmountCents = 300_00L, originalCategoryId = 8L)
        val result5d = TransferMatcherEngine.matchTransfers(listOf(txOut5d, txIn5d), categories)
        assertEquals(0, result5d.matchCount)
    }

    @Test
    fun testTransferMatcher_crossYearBoundary() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        // Dec 30, 2025 to Jan 2, 2026 (3 days apart across year boundary)
        val txOutYear = Transaction(id = 205L, accountId = 1L, originalDate = "2025-12-30", originalMerchant = "Year-end Out", originalAmountCents = -500_00L, originalCategoryId = 8L)
        val txInYear = Transaction(id = 206L, accountId = 2L, originalDate = "2026-01-02", originalMerchant = "New-year In", originalAmountCents = 500_00L, originalCategoryId = 8L)

        val result = TransferMatcherEngine.matchTransfers(listOf(txOutYear, txInYear), categories)
        assertEquals(1, result.matchCount, "Cross-year 3-day transfer should match")
        val pA = result.updatedTransactions.find { it.id == 205L }!!
        val pB = result.updatedTransactions.find { it.id == 206L }!!
        assertEquals(206L, pA.transferPairId)
        assertEquals(205L, pB.transferPairId)
    }

    @Test
    fun testTransferMatcher_crossLeapYearFebruary() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        // 2028 is a leap year (Feb 29 exists): Feb 28 to March 2 is 3 days apart
        val txOutLeap = Transaction(id = 207L, accountId = 1L, originalDate = "2028-02-28", originalMerchant = "Leap Out", originalAmountCents = -750_00L, originalCategoryId = 8L)
        val txInLeap = Transaction(id = 208L, accountId = 2L, originalDate = "2028-03-02", originalMerchant = "Leap In", originalAmountCents = 750_00L, originalCategoryId = 8L)

        val result = TransferMatcherEngine.matchTransfers(listOf(txOutLeap, txInLeap), categories)
        assertEquals(1, result.matchCount, "Leap-year Feb 28 to Mar 2 (3 days) should match")
    }

    @Test
    fun testTransferMatcher_userProvenanceLock_respectsUserTransferSource() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        val txOut = Transaction(
            id = 209L,
            accountId = 1L,
            originalDate = "2026-04-10",
            originalMerchant = "Manual Out",
            originalAmountCents = -100_00L,
            originalCategoryId = 8L,
            editedIsTransferSource = "user"
        )
        val txIn = Transaction(
            id = 210L,
            accountId = 2L,
            originalDate = "2026-04-11",
            originalMerchant = "Manual In",
            originalAmountCents = 100_00L,
            originalCategoryId = 8L
        )

        val result = TransferMatcherEngine.matchTransfers(listOf(txOut, txIn), categories)
        assertEquals(0, result.matchCount, "Transactions with editedIsTransferSource == user must not be paired")
    }

    @Test
    fun testTransferMatcher_sameAccountRejection() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        val txOut = Transaction(id = 211L, accountId = 1L, originalDate = "2026-04-10", originalMerchant = "Internal Out", originalAmountCents = -100_00L, originalCategoryId = 8L)
        val txIn = Transaction(id = 212L, accountId = 1L, originalDate = "2026-04-11", originalMerchant = "Internal In", originalAmountCents = 100_00L, originalCategoryId = 8L)

        val result = TransferMatcherEngine.matchTransfers(listOf(txOut, txIn), categories)
        assertEquals(0, result.matchCount, "Transfers in the same account must NEVER pair")
    }

    @Test
    fun testTransferMatcher_zeroAmountRejection() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        val txA = Transaction(id = 213L, accountId = 1L, originalDate = "2026-04-10", originalMerchant = "Zero A", originalAmountCents = 0L, originalCategoryId = 8L)
        val txB = Transaction(id = 214L, accountId = 2L, originalDate = "2026-04-10", originalMerchant = "Zero B", originalAmountCents = 0L, originalCategoryId = 8L)

        val result = TransferMatcherEngine.matchTransfers(listOf(txA, txB), categories)
        assertEquals(0, result.matchCount, "Zero-dollar transactions must NEVER pair as transfers")
    }

    // =========================================================================
    // 3. UpcomingEngine Month-Day Clamping & Edge Tests
    // =========================================================================

    @Test
    fun testUpcomingEngine_monthDayClamping_31stAcrossAllMonths() {
        val item31st = UpcomingItem(
            id = 1L,
            name = "Month End Subscription",
            amountCents = 15_00L,
            frequencyType = RecurrenceFrequency.MONTHLY,
            frequencyInterval = 1,
            nextDate = "2026-01-31"
        )

        val dates = UpcomingEngine.generateDates(item31st, "2026-01-01", "2026-07-01")
        val expected = listOf(
            "2026-01-31", // Jan: 31
            "2026-02-28", // Feb: clamped to 28
            "2026-03-31", // Mar: restored to 31
            "2026-04-30", // Apr: clamped to 30
            "2026-05-31", // May: restored to 31
            "2026-06-30"  // Jun: clamped to 30
        )
        assertEquals(expected, dates, "31st recurrence must restore anchor day after 28/30-day months")
    }

    @Test
    fun testUpcomingEngine_monthDayClamping_leapYearFebruary() {
        val itemLeap = UpcomingItem(
            id = 2L,
            name = "Leap Year Bill",
            amountCents = 50_00L,
            frequencyType = RecurrenceFrequency.MONTHLY,
            frequencyInterval = 1,
            nextDate = "2028-01-31"
        )

        val dates = UpcomingEngine.generateDates(itemLeap, "2028-01-01", "2028-04-01")
        val expected = listOf("2028-01-31", "2028-02-29", "2028-03-31")
        assertEquals(expected, dates, "Leap year Feb must clamp to 29, then restore to 31 in March")
    }

    @Test
    fun testUpcomingEngine_monthDaysRule_lastDayOfMonth() {
        val itemLastDay = UpcomingItem(
            id = 3L,
            name = "Last Day Payday",
            amountCents = 3000_00L,
            nextDate = "2026-01-01",
            recurrenceRule = RecurrenceRule.MonthDays(listOf(-1))
        )

        val dates = UpcomingEngine.generateDates(itemLastDay, "2026-01-01", "2026-05-01")
        val expected = listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30")
        assertEquals(expected, dates, "MonthDays(-1) must always yield the true last day of every month")
    }

    @Test
    fun testUpcomingEngine_advanceInterval_anchorDayDriftVulnerability() {
        // Verified: advanceInterval with anchor day preserves 31st across February
        val feb = UpcomingEngine.advanceInterval("2026-01-31", RecurrenceFrequency.MONTHLY, anchorDay = 31)
        assertEquals("2026-02-28", feb, "January 31st advances and clamps to February 28th")

        val mar = UpcomingEngine.advanceInterval(feb, RecurrenceFrequency.MONTHLY, anchorDay = 31)
        assertEquals("2026-03-31", mar, "February 28th advances and restores anchor day 31st in March")

        val apr = UpcomingEngine.advanceInterval(mar, RecurrenceFrequency.MONTHLY, anchorDay = 31)
        assertEquals("2026-04-30", apr, "March 31st advances and clamps to April 30th")

        val may = UpcomingEngine.advanceInterval(apr, RecurrenceFrequency.MONTHLY, anchorDay = 31)
        assertEquals("2026-05-31", may, "April 30th advances and restores anchor day 31st in May")
    }

    @Test
    fun testUpcomingEngine_reconciliation_overMatchingBeyondGraceWindowVulnerability() {
        val item = UpcomingItem(
            id = 10L,
            name = "Electric Utility",
            merchant = "City Power",
            amountCents = 120_00L,
            nextDate = "2026-04-10"
        )

        // Transaction 60 days late (June 10)
        val txn60DaysLate = Transaction(
            id = 702L,
            accountId = 1L,
            originalDate = "2026-06-10",
            originalMerchant = "City Power and Light",
            originalAmountCents = -120_00L
        )

        // Verified: ScoreMatch must disqualify or penalize transactions outside the grace window
        val score = UpcomingEngine.scoreMatch(
            expectedMerchant = "City Power",
            expectedDate = "2026-04-10",
            expectedAmountCents = 120_00L,
            expectedCategoryId = null,
            expectedAccountId = null,
            txn = txn60DaysLate
        )
        assertTrue(score < UpcomingEngine.MIN_MATCH_SCORE, "Score must be below MIN_MATCH_SCORE for transaction 60 days late")

        // Reconcile occurrences must NOT match transaction 60 days outside grace window
        val resLate = UpcomingEngine.reconcileOccurrences(listOf(item), listOf(txn60DaysLate), "2026-06-15")
        assertEquals(0, resLate.matchedCount, "ReconcileOccurrences must NOT match transaction 60 days outside grace period")
        val occ = resLate.occurrences.first()
        assertEquals(OccurrenceStatus.MISSED, occ.status, "Occurrence must be marked MISSED when past grace period without match")
        assertNull(occ.matchedTransactionId, "matchedTransactionId must remain null")
    }

    @Test
    fun testUpcomingEngine_reconciliation_exactGraceBoundary() {
        val item = UpcomingItem(
            id = 20L,
            name = "Internet Service",
            merchant = "Comcast Xfinity",
            amountCents = 80_00L,
            nextDate = "2026-04-10"
        )

        // Case A: Exactly 4 days late (April 14) -> exactly on the GRACE_DAYS boundary (daysDiff == 4) -> MATCHES
        val txnOnGraceBoundary = Transaction(
            id = 801L,
            accountId = 1L,
            originalDate = "2026-04-14",
            originalMerchant = "Comcast Xfinity",
            originalAmountCents = -80_00L
        )
        val resWithin = UpcomingEngine.reconcileOccurrences(listOf(item), listOf(txnOnGraceBoundary), "2026-04-20")
        assertEquals(1, resWithin.matchedCount, "Transaction exactly 4 days late (within GRACE_DAYS) must match")
        val occWithin = resWithin.occurrences.first()
        assertEquals(OccurrenceStatus.MATCHED, occWithin.status)
        assertEquals(801L, occWithin.matchedTransactionId)

        // Case B: Exactly 5 days late (April 15) -> strictly outside GRACE_DAYS boundary (daysDiff == 5 > 4) -> REJECTS
        val txnBeyondGraceBoundary = Transaction(
            id = 802L,
            accountId = 1L,
            originalDate = "2026-04-15",
            originalMerchant = "Comcast Xfinity",
            originalAmountCents = -80_00L
        )
        val resOutside = UpcomingEngine.reconcileOccurrences(listOf(item), listOf(txnBeyondGraceBoundary), "2026-04-20")
        assertEquals(0, resOutside.matchedCount, "Transaction 5 days late (strictly outside GRACE_DAYS) must NOT match")
        val occOutside = resOutside.occurrences.first()
        assertEquals(OccurrenceStatus.MISSED, occOutside.status)
        assertNull(occOutside.matchedTransactionId)
    }

    @Test
    fun testMoneyUtils_multiplyPercent_symmetricRoundingOnNegativeAmounts() {
        // Zero cases
        assertEquals(0L, MoneyUtils.multiplyPercent(0L, 10.0), "Zero cents yields 0")
        assertEquals(0L, MoneyUtils.multiplyPercent(1000L, 0.0), "Zero percent yields 0")
        assertEquals(0L, MoneyUtils.multiplyPercent(-1000L, 0.0), "Negative cents with zero percent yields 0")

        // Exact integer calculations
        assertEquals(10L, MoneyUtils.multiplyPercent(100L, 10.0), "10% of 100 is 10")
        assertEquals(-10L, MoneyUtils.multiplyPercent(-100L, 10.0), "10% of -100 is -10")
        assertEquals(25L, MoneyUtils.multiplyPercent(100L, 25.0), "25% of 100 is 25")
        assertEquals(-25L, MoneyUtils.multiplyPercent(-100L, 25.0), "25% of -100 is -25")

        // Rounding away from zero for fractional amounts (> 0.5)
        assertEquals(11L, MoneyUtils.multiplyPercent(106L, 10.0), "106 at 10% rounds to 11")
        assertEquals(-11L, MoneyUtils.multiplyPercent(-106L, 10.0), "-106 at 10% rounds to -11")

        // Rounding towards zero for fractional amounts (< 0.5)
        assertEquals(10L, MoneyUtils.multiplyPercent(104L, 10.0), "104 at 10% rounds to 10")
        assertEquals(-10L, MoneyUtils.multiplyPercent(-104L, 10.0), "-104 at 10% rounds to -10")

        // High percentage and large amounts
        assertEquals(300L, MoneyUtils.multiplyPercent(2000L, 15.0), "15% of 2000 is 300")
        assertEquals(-300L, MoneyUtils.multiplyPercent(-2000L, 15.0), "15% of -2000 is -300")
    }
}
