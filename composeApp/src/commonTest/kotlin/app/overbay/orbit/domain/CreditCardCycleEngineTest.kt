package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreditCardCycleEngineTest {

    @Test
    fun closeDateInClampsDaysCorrectly() {
        assertEquals(LocalDate(2026, 2, 28), CreditCardCycleEngine.closeDateIn(2026, 2, 31))
        assertEquals(LocalDate(2024, 2, 29), CreditCardCycleEngine.closeDateIn(2024, 2, 31)) // leap year
        assertEquals(LocalDate(2026, 4, 30), CreditCardCycleEngine.closeDateIn(2026, 4, 31))
        assertEquals(LocalDate(2026, 1, 15), CreditCardCycleEngine.closeDateIn(2026, 1, 15))
    }

    @Test
    fun lastCloseCalculatesCorrectlyAcrossMonthAndYearBoundaries() {
        val today = LocalDate(2026, 11, 10)
        assertEquals(LocalDate(2026, 11, 5), CreditCardCycleEngine.lastClose(5, today))
        assertEquals(LocalDate(2026, 11, 10), CreditCardCycleEngine.lastClose(10, today))
        assertEquals(LocalDate(2026, 10, 25), CreditCardCycleEngine.lastClose(25, today))

        val janToday = LocalDate(2026, 1, 5)
        assertEquals(LocalDate(2025, 12, 25), CreditCardCycleEngine.lastClose(25, janToday))

        val marchToday = LocalDate(2026, 3, 5)
        assertEquals(LocalDate(2026, 2, 28), CreditCardCycleEngine.lastClose(31, marchToday))
    }

    @Test
    fun nextCloseCalculatesCorrectly() {
        val today = LocalDate(2026, 11, 10)
        assertEquals(LocalDate(2026, 11, 25), CreditCardCycleEngine.nextClose(25, today))
        assertEquals(LocalDate(2026, 12, 5), CreditCardCycleEngine.nextClose(5, today))
        assertEquals(LocalDate(2026, 12, 10), CreditCardCycleEngine.nextClose(10, today))

        val decToday = LocalDate(2026, 12, 20)
        assertEquals(LocalDate(2027, 1, 5), CreditCardCycleEngine.nextClose(5, decToday))
    }

    @Test
    fun dueDateAfterHandlesSameMonthAndNextMonthRollover() {
        assertEquals(LocalDate(2026, 11, 28), CreditCardCycleEngine.dueDateAfter(LocalDate(2026, 11, 3), 28))
        assertEquals(LocalDate(2026, 12, 20), CreditCardCycleEngine.dueDateAfter(LocalDate(2026, 11, 25), 20))
        assertEquals(LocalDate(2026, 12, 20), CreditCardCycleEngine.dueDateAfter(LocalDate(2026, 11, 20), 20))
        assertEquals(LocalDate(2027, 1, 15), CreditCardCycleEngine.dueDateAfter(LocalDate(2026, 12, 25), 15))
        assertEquals(LocalDate(2026, 2, 28), CreditCardCycleEngine.dueDateAfter(LocalDate(2026, 1, 31), 31))
    }

    @Test
    fun nextAnnualFeeDateRollsForwardByWholeYears() {
        val today = LocalDate(2026, 11, 9)
        assertEquals(LocalDate(2027, 4, 18), CreditCardCycleEngine.nextAnnualFeeDate("2025-04-18", today))
        assertEquals(LocalDate(2026, 11, 15), CreditCardCycleEngine.nextAnnualFeeDate("2024-11-15", today))
        assertEquals(LocalDate(2026, 11, 9), CreditCardCycleEngine.nextAnnualFeeDate("2025-11-09", today))
        assertEquals(LocalDate(2028, 6, 1), CreditCardCycleEngine.nextAnnualFeeDate("2028-06-01", today))

        val leapDayEval = LocalDate(2025, 1, 1)
        assertEquals(LocalDate(2025, 2, 28), CreditCardCycleEngine.nextAnnualFeeDate("2024-02-29", leapDayEval))
        assertNull(CreditCardCycleEngine.nextAnnualFeeDate(null, today))
        assertNull(CreditCardCycleEngine.nextAnnualFeeDate("", today))
    }

    @Test
    fun statementEstimateReconstructsBalanceAtClose() {
        val today = LocalDate(2026, 11, 10)
        val card = Account(
            id = 1L,
            name = "Sapphire Reserve",
            type = AccountType.CREDIT,
            currentBalanceCents = -1000_00L,
            creditCardProfile = CreditCardProfile(
                accountId = 1L,
                statementClosingDay = 25,
                paymentDueDay = 20
            )
        )
        val txns = listOf(
            Transaction(
                id = 101L,
                accountId = 1L,
                originalDate = "2026-10-20",
                originalMerchant = "Grocery",
                originalAmountCents = -200_00L
            ),
            Transaction(
                id = 102L,
                accountId = 1L,
                originalDate = "2026-10-28",
                originalMerchant = "Restaurant",
                originalAmountCents = -150_00L
            ),
            Transaction(
                id = 103L,
                accountId = 1L,
                originalDate = "2026-11-02",
                originalMerchant = "Payment",
                originalAmountCents = 50_00L
            ),
            Transaction(
                id = 104L,
                accountId = 1L,
                originalDate = "2026-11-05",
                originalMerchant = "Coffee",
                originalAmountCents = -40_00L,
                isIgnored = true
            )
        )

        val status = CreditCardCycleEngine.status(card, txns, today)
        assertNotNull(status)
        assertEquals(LocalDate(2026, 10, 25), status.lastCloseDate)
        assertEquals(LocalDate(2026, 11, 25), status.nextCloseDate)
        assertEquals(LocalDate(2026, 11, 20), status.dueDate)
        assertEquals(10, status.daysUntilDue)
        assertEquals(900_00L, status.estimatedStatementBalanceCents)
        assertEquals(1000_00L, status.owedNowCents)

        // Positive balance (overpayment) -> estimated statement balance is null
        val overpaidCard = card.copy(currentBalanceCents = 25_00L)
        val overpaidStatus = CreditCardCycleEngine.status(overpaidCard, txns, today)
        assertNotNull(overpaidStatus)
        assertNull(overpaidStatus.estimatedStatementBalanceCents)
    }

    @Test
    fun utilizationBpsCalculatesProperly() {
        val today = LocalDate(2026, 11, 10)
        val card = Account(
            id = 2L,
            name = "Freedom Unlimited",
            type = AccountType.CREDIT,
            currentBalanceCents = -2500_00L,
            creditCardProfile = CreditCardProfile(
                accountId = 2L,
                creditLimitCents = 10000_00L
            )
        )
        val status = CreditCardCycleEngine.status(card, emptyList(), today)
        assertNotNull(status)
        assertEquals(2500, status.utilizationBps) // 25.00%

        val noLimitCard = card.copy(creditCardProfile = card.creditCardProfile?.copy(creditLimitCents = null))
        assertNull(CreditCardCycleEngine.status(noLimitCard, emptyList(), today)?.utilizationBps)
    }

    @Test
    fun statusFiltersNonCreditArchivedOrClosedCards() {
        val today = LocalDate(2026, 11, 10)
        val checking = Account(id = 3L, name = "Checking", type = AccountType.CHECKING)
        assertNull(CreditCardCycleEngine.status(checking, emptyList(), today))

        val archived = Account(
            id = 4L,
            name = "Old Card",
            type = AccountType.CREDIT,
            isArchived = true,
            creditCardProfile = CreditCardProfile(accountId = 4L)
        )
        assertNull(CreditCardCycleEngine.status(archived, emptyList(), today))

        val closed = Account(
            id = 5L,
            name = "Closed Card",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 5L,
                strategy = CreditCardStrategy(closedDate = "2026-01-01")
            )
        )
        assertNull(CreditCardCycleEngine.status(closed, emptyList(), today))
    }

    @Test
    fun summarizeAggregatesHouseholdPortfolioAndIdentifiesNextDue() {
        val today = LocalDate(2026, 11, 10)
        val card1 = Account(
            id = 11L,
            name = "Card 1",
            type = AccountType.CREDIT,
            currentBalanceCents = -1000_00L,
            creditCardProfile = CreditCardProfile(
                accountId = 11L,
                creditLimitCents = 5000_00L,
                statementClosingDay = 10,
                paymentDueDay = 15 // due in 5 days (Nov 15)
            )
        )
        val card2 = Account(
            id = 12L,
            name = "Card 2",
            type = AccountType.CREDIT,
            currentBalanceCents = -500_00L,
            creditCardProfile = CreditCardProfile(
                accountId = 12L,
                creditLimitCents = 2500_00L,
                statementClosingDay = 5,
                paymentDueDay = 30 // due in 20 days (Nov 30)
            )
        )
        val card3 = Account(
            id = 13L,
            name = "Card 3",
            type = AccountType.CREDIT,
            currentBalanceCents = 0L,
            creditCardProfile = CreditCardProfile(
                accountId = 13L,
                strategy = CreditCardStrategy(closedDate = "2026-01-01")
            )
        )

        val (summary, statuses) = CreditCardCycleEngine.summarize(listOf(card1, card2, card3), emptyList(), today)
        assertEquals(2, statuses.size)
        assertEquals(1500_00L, summary.totalOwedCents)
        assertEquals(7500_00L, summary.totalLimitCents)
        assertEquals(2000, summary.utilizationBps) // 20.00%
        assertNotNull(summary.nextDue)
        assertEquals(11L, summary.nextDue?.accountId)
        assertEquals(5, summary.nextDue?.daysUntilDue)
    }
}
