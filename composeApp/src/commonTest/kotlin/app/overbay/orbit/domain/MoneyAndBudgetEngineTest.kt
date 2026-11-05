package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyAndBudgetEngineTest {

    // -------------------------------------------------------------------------
    // MoneyUtils Unit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testDollarsToCents_exactConversions() {
        assertEquals(0L, MoneyUtils.dollarsToCents(0.0))
        assertEquals(100L, MoneyUtils.dollarsToCents(1.00))
        assertEquals(1234L, MoneyUtils.dollarsToCents(12.34))
        assertEquals(-1234L, MoneyUtils.dollarsToCents(-12.34))
        assertEquals(123456789L, MoneyUtils.dollarsToCents(1234567.89))
    }

    @Test
    fun testDollarsToCents_roundingAndEpsilon() {
        assertEquals(1999L, MoneyUtils.dollarsToCents(19.99))
        assertEquals(1L, MoneyUtils.dollarsToCents(0.005))
        assertEquals(0L, MoneyUtils.dollarsToCents(0.004))
    }

    @Test
    fun testDollarsToCents_stringParsing() {
        assertEquals(123456L, MoneyUtils.dollarsToCents("$1,234.56"))
        assertEquals(-5000L, MoneyUtils.dollarsToCents("-$50.00"))
        assertEquals(0L, MoneyUtils.dollarsToCents("", fallback = 0L))
        assertEquals(100L, MoneyUtils.dollarsToCents("invalid", fallback = 100L))
    }

    @Test
    fun testCentsToDollars() {
        assertEquals(12.34, MoneyUtils.centsToDollars(1234L))
        assertEquals(-12.34, MoneyUtils.centsToDollars(-1234L))
        assertEquals(0.0, MoneyUtils.centsToDollars(0L))
    }

    @Test
    fun testFormatCents_standardFormats() {
        assertEquals("$1,234.56", MoneyUtils.formatCents(123456L))
        assertEquals("-$1,234.56", MoneyUtils.formatCents(-123456L))
        assertEquals("$0.00", MoneyUtils.formatCents(0L))
        assertEquals("$1,235", MoneyUtils.formatCents(123456L, showCents = false))
        assertEquals("+$1,234.56", MoneyUtils.formatCents(123456L, signed = true))
        assertEquals("-$50.00", MoneyUtils.formatCents(-5000L, signed = true))
    }

    @Test
    fun testFormatCompactCents() {
        assertEquals("$4.2M", MoneyUtils.formatCompactCents(4_200_000_00L))
        assertEquals("$500K", MoneyUtils.formatCompactCents(500_000_00L))
        assertEquals("$250", MoneyUtils.formatCompactCents(250_00L))
        assertEquals("-$1.5M", MoneyUtils.formatCompactCents(-1_500_000_00L))
    }

    // -------------------------------------------------------------------------
    // BudgetEngine Unit Tests
    // -------------------------------------------------------------------------

    private val groceries = Category(id = 1L, name = "Groceries", color = "#4CAF50")
    private val utilities = Category(id = 2L, name = "Utilities", color = "#2196F3")

    @Test
    fun testBudgetEngine_computesSpendingAndRemaining() {
        val categories = listOf(groceries, utilities)
        val budgets = listOf(
            CategoryBudget(id = 1L, categoryId = 1L, amountCents = 500_00L), // $500 budget
            CategoryBudget(id = 2L, categoryId = 2L, amountCents = 200_00L)  // $200 budget
        )

        val txns = listOf(
            Transaction(accountId = 10L, originalDate = "2026-04-05", originalMerchant = "Kroger", originalAmountCents = -150_00L, originalCategoryId = 1L),
            Transaction(accountId = 10L, originalDate = "2026-04-12", originalMerchant = "Costco", originalAmountCents = -200_00L, originalCategoryId = 1L),
            Transaction(accountId = 10L, originalDate = "2026-04-10", originalMerchant = "Electric Co", originalAmountCents = -180_00L, originalCategoryId = 2L)
        )

        val overview = BudgetEngine.calculateMonthlyOverview(
            month = "2026-04",
            categories = categories,
            budgets = budgets,
            transactions = txns,
            todayDate = "2026-04-15"
        )

        assertEquals(700_00L, overview.totalBudgetedCents)
        assertEquals(530_00L, overview.totalSpentCents)
        assertEquals(170_00L, overview.totalRemainingCents)

        val groceryRow = overview.categoryRows.find { it.category.id == 1L }!!
        assertEquals(500_00L, groceryRow.budgetedCents)
        assertEquals(350_00L, groceryRow.spentCents)
        assertEquals(150_00L, groceryRow.remainingCents)
        assertEquals(BudgetPaceStatus.ON_TRACK, groceryRow.paceStatus)

        val utilRow = overview.categoryRows.find { it.category.id == 2L }!!
        assertEquals(200_00L, utilRow.budgetedCents)
        assertEquals(180_00L, utilRow.spentCents)
        assertEquals(20_00L, utilRow.remainingCents)
        assertEquals(BudgetPaceStatus.WATCH, utilRow.paceStatus)
    }

    @Test
    fun testBudgetEngine_excludesTransfersAndIgnored() {
        val categories = listOf(groceries)
        val budgets = listOf(CategoryBudget(id = 1L, categoryId = 1L, amountCents = 300_00L))

        val txns = listOf(
            Transaction(accountId = 10L, originalDate = "2026-04-05", originalMerchant = "Trader Joe's", originalAmountCents = -100_00L, originalCategoryId = 1L),
            Transaction(accountId = 10L, originalDate = "2026-04-06", originalMerchant = "Ignored Grocery", originalAmountCents = -50_00L, originalCategoryId = 1L, isIgnored = true),
            Transaction(accountId = 10L, originalDate = "2026-04-07", originalMerchant = "Checking Transfer", originalAmountCents = -200_00L, originalCategoryId = 1L, isTransfer = true)
        )

        val overview = BudgetEngine.calculateMonthlyOverview(
            month = "2026-04",
            categories = categories,
            budgets = budgets,
            transactions = txns
        )

        // Only the $100 non-ignored, non-transfer transaction is counted
        assertEquals(100_00L, overview.totalSpentCents)
    }

    @Test
    fun testBudgetEngine_overBudgetPace() {
        val categories = listOf(groceries)
        val budgets = listOf(CategoryBudget(id = 1L, categoryId = 1L, amountCents = 100_00L))
        val txns = listOf(
            Transaction(accountId = 10L, originalDate = "2026-04-10", originalMerchant = "Whole Foods", originalAmountCents = -150_00L, originalCategoryId = 1L)
        )

        val overview = BudgetEngine.calculateMonthlyOverview(
            month = "2026-04",
            categories = categories,
            budgets = budgets,
            transactions = txns
        )

        val row = overview.categoryRows.first()
        assertEquals(-50_00L, row.remainingCents)
        assertEquals(BudgetPaceStatus.OVER_BUDGET, row.paceStatus)
    }

    @Test
    fun testBudgetEngine_dailySafeSpend() {
        val categories = listOf(groceries)
        val budgets = listOf(CategoryBudget(id = 1L, categoryId = 1L, amountCents = 300_00L))
        val txns = listOf(
            Transaction(accountId = 10L, originalDate = "2026-04-01", originalMerchant = "Store", originalAmountCents = -100_00L, originalCategoryId = 1L)
        )

        // On April 16 in a 30-day month, daysLeft = 30 - 16 + 1 = 15 days
        // Remaining budget = 200_00L
        // Safe spend = 200_00L / 15 = 13_33L ($13.33/day)
        val overview = BudgetEngine.calculateMonthlyOverview(
            month = "2026-04",
            categories = categories,
            budgets = budgets,
            transactions = txns,
            todayDate = "2026-04-16"
        )

        assertEquals(13_33L, overview.dailySafeSpendCents)
    }
}
