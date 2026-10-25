package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleAndTransferMatcherTest {

    // -------------------------------------------------------------------------
    // RuleMatcherEngine Unit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testRuleMatcher_merchantContainsCondition() {
        val rule = TransactionRule(
            id = 1L,
            name = "Coffee Shop Categorizer",
            conditions = listOf(
                RuleCondition(field = RuleConditionField.MERCHANT, operator = RuleOperator.CONTAINS, value = "starbucks")
            ),
            actions = listOf(
                RuleAction(type = RuleActionType.SET_CATEGORY, value = "5")
            ),
            enabled = true
        )

        val txn = Transaction(
            id = 100L,
            accountId = 1L,
            originalDate = "2026-04-10",
            originalMerchant = "Starbucks Store #1234",
            originalAmountCents = -650L
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(txn), listOf(rule)).first()
        assertEquals(5L, evaluated.effectiveCategoryId)
        assertEquals(5L, evaluated.editedCategoryId)
        assertEquals("rule:1", evaluated.editedCategoryIdSource)
        assertEquals(EditSource.RULE, evaluated.editSource)
        // Original merchant preserved intact
        assertEquals("Starbucks Store #1234", evaluated.originalMerchant)
    }

    @Test
    fun testRuleMatcher_merchantRenameAction() {
        val rule = TransactionRule(
            id = 2L,
            name = "Clean Amazon",
            conditions = listOf(
                RuleCondition(field = RuleConditionField.MERCHANT, operator = RuleOperator.STARTS_WITH, value = "amzn mktp")
            ),
            actions = listOf(
                RuleAction(type = RuleActionType.RENAME_MERCHANT, value = "Amazon")
            ),
            enabled = true
        )

        val txn = Transaction(
            id = 101L,
            accountId = 1L,
            originalDate = "2026-04-11",
            originalMerchant = "AMZN Mktp US*123ABC456",
            originalAmountCents = -2499L
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(txn), listOf(rule)).first()
        assertEquals("Amazon", evaluated.effectiveMerchant)
        assertEquals("AMZN Mktp US*123ABC456", evaluated.originalMerchant)
        assertEquals("Amazon", evaluated.editedMerchant)
    }

    @Test
    fun testRuleMatcher_strictProvenanceLock_userEditNeverOverwritten() {
        // Critical guardrail: user edits must never be mutated by automated rules
        val rule = TransactionRule(
            id = 3L,
            name = "Coffee Categorizer",
            conditions = listOf(
                RuleCondition(field = RuleConditionField.MERCHANT, operator = RuleOperator.CONTAINS, value = "coffee")
            ),
            actions = listOf(
                RuleAction(type = RuleActionType.SET_CATEGORY, value = "5")
            ),
            enabled = true
        )

        val userEditedTxn = Transaction(
            id = 102L,
            accountId = 1L,
            originalDate = "2026-04-12",
            originalMerchant = "Local Coffee Roasters",
            originalAmountCents = -500L,
            originalCategoryId = null,
            editedCategoryId = 99L,
            editedCategoryIdSource = "user",
            editSource = EditSource.USER
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(userEditedTxn), listOf(rule)).first()
        // User edit is preserved: category 99, not rule's category 5
        assertEquals(99L, evaluated.effectiveCategoryId)
        assertEquals("user", evaluated.editedCategoryIdSource)
        assertEquals(EditSource.USER, evaluated.editSource)
    }

    @Test
    fun testRuleMatcher_priorityOrdering() {
        val lowPriorityRule = TransactionRule(
            id = 1L,
            name = "Catch-all Target",
            conditions = listOf(RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, "target")),
            actions = listOf(RuleAction(RuleActionType.SET_CATEGORY, "10")),
            priority = 10,
            enabled = true
        )
        val highPriorityRule = TransactionRule(
            id = 2L,
            name = "Target Pharmacy override",
            conditions = listOf(RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, "target pharmacy")),
            actions = listOf(RuleAction(RuleActionType.SET_CATEGORY, "20")),
            priority = 100,
            enabled = true
        )

        val txn = Transaction(
            id = 103L,
            accountId = 1L,
            originalDate = "2026-04-13",
            originalMerchant = "Target Pharmacy Store 042",
            originalAmountCents = -3500L
        )

        val evaluated = RuleMatcherEngine.applyRules(listOf(txn), listOf(lowPriorityRule, highPriorityRule)).first()
        // High priority rule (priority 100) claims the category first
        assertEquals(20L, evaluated.effectiveCategoryId)
    }

    // -------------------------------------------------------------------------
    // TransferMatcherEngine Unit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testTransferMatcher_pairsOppositeSignsAcrossDifferentAccountsWithin4Days() {
        val transferCategory = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCategory)

        val checkingTx = Transaction(
            id = 201L,
            accountId = 1L, // Checking
            originalDate = "2026-04-10",
            originalMerchant = "Online Transfer Out",
            originalAmountCents = -500_00L,
            originalCategoryId = 8L
        )

        val savingsTx = Transaction(
            id = 202L,
            accountId = 2L, // Savings (different account)
            originalDate = "2026-04-12", // 2 days later (within ±4 days)
            originalMerchant = "Online Transfer In",
            originalAmountCents = 500_00L, // Exact matching magnitude, opposite sign
            originalCategoryId = 8L
        )

        val result = TransferMatcherEngine.matchTransfers(listOf(checkingTx, savingsTx), categories)
        assertEquals(1, result.matchCount)

        val pairedA = result.updatedTransactions.find { it.id == 201L }!!
        val pairedB = result.updatedTransactions.find { it.id == 202L }!!

        assertEquals(202L, pairedA.transferPairId)
        assertEquals(201L, pairedB.transferPairId)
        assertTrue(pairedA.effectiveIsTransfer)
        assertTrue(pairedB.effectiveIsTransfer)
    }

    @Test
    fun testTransferMatcher_rejectsSameAccount() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        val tx1 = Transaction(id = 301L, accountId = 1L, originalDate = "2026-04-10", originalMerchant = "Tx1", originalAmountCents = -100_00L, originalCategoryId = 8L)
        val tx2 = Transaction(id = 302L, accountId = 1L, originalDate = "2026-04-10", originalMerchant = "Tx2", originalAmountCents = 100_00L, originalCategoryId = 8L) // Same account!

        val result = TransferMatcherEngine.matchTransfers(listOf(tx1, tx2), categories)
        assertEquals(0, result.matchCount)
    }

    @Test
    fun testTransferMatcher_rejectsBeyond4Days() {
        val transferCat = Category(id = 8L, name = "Transfer", isTransfer = true)
        val categories = mapOf(8L to transferCat)

        val tx1 = Transaction(id = 401L, accountId = 1L, originalDate = "2026-04-01", originalMerchant = "ACH Out", originalAmountCents = -300_00L, originalCategoryId = 8L)
        val tx2 = Transaction(id = 402L, accountId = 2L, originalDate = "2026-04-08", originalMerchant = "ACH In", originalAmountCents = 300_00L, originalCategoryId = 8L) // 7 days apart!

        val result = TransferMatcherEngine.matchTransfers(listOf(tx1, tx2), categories)
        assertEquals(0, result.matchCount)
    }
}
