package app.overbay.orbit.domain

import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpcomingEngineTest {

    @Test
    fun testRecurrenceProjection_weeklySchedule() {
        val item = UpcomingItem(
            id = 1L,
            name = "Weekly Piano Lesson",
            amountCents = 40_00L,
            frequencyType = RecurrenceFrequency.WEEKLY,
            frequencyInterval = 1,
            nextDate = "2026-04-01"
        )

        val dates = UpcomingEngine.generateDates(item, "2026-04-01", "2026-04-28")
        assertEquals(listOf("2026-04-01", "2026-04-08", "2026-04-15", "2026-04-22"), dates)
    }

    @Test
    fun testRecurrenceProjection_biweeklySchedule() {
        val item = UpcomingItem(
            id = 2L,
            name = "Biweekly Paycheck",
            amountCents = 2500_00L,
            direction = "income",
            frequencyType = RecurrenceFrequency.BIWEEKLY,
            frequencyInterval = 1,
            nextDate = "2026-04-03"
        )

        val dates = UpcomingEngine.generateDates(item, "2026-04-01", "2026-04-30")
        assertEquals(listOf("2026-04-03", "2026-04-17"), dates)
    }

    @Test
    fun testRecurrenceProjection_monthlyScheduleClamping() {
        // Month end clamping: January 31st advances to February 28th, then March 31st
        val item = UpcomingItem(
            id = 3L,
            name = "End of Month Bill",
            amountCents = 100_00L,
            frequencyType = RecurrenceFrequency.MONTHLY,
            frequencyInterval = 1,
            nextDate = "2026-01-31"
        )

        val dates = UpcomingEngine.generateDates(item, "2026-01-01", "2026-04-01")
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), dates)
    }

    @Test
    fun testRecurrenceProjection_customMonthDaysRule() {
        val item = UpcomingItem(
            id = 4L,
            name = "Split Mortgage",
            amountCents = 900_00L,
            nextDate = "2026-04-01",
            recurrenceRule = RecurrenceRule.MonthDays(listOf(1, 15))
        )

        val dates = UpcomingEngine.generateDates(item, "2026-04-01", "2026-04-30")
        assertEquals(listOf("2026-04-01", "2026-04-15"), dates)
    }

    @Test
    fun testUpcomingReconciliation_matchesTransactionWithinGraceAndTolerance() {
        val item = UpcomingItem(
            id = 5L,
            name = "Internet Service",
            merchant = "Comcast Xfinity",
            amountCents = 80_00L,
            nextDate = "2026-04-15"
        )

        // Posted transaction 2 days early with slight variable tax fee ($82.50 vs $80.00)
        val postedTx = Transaction(
            id = 501L,
            accountId = 1L,
            originalDate = "2026-04-13",
            originalMerchant = "COMCAST CABLE COMM",
            originalAmountCents = -82_50L
        )

        val reconciliation = UpcomingEngine.reconcileOccurrences(
            items = listOf(item),
            transactions = listOf(postedTx),
            todayDate = "2026-04-20"
        )

        val matchedOccurrence = reconciliation.occurrences.find { it.upcomingItemId == 5L }
        assertNotNull(matchedOccurrence)
        assertEquals(OccurrenceStatus.MATCHED, matchedOccurrence.status)
        assertEquals(501L, matchedOccurrence.matchedTransactionId)
    }

    @Test
    fun testUpcomingCandidateAutoDetection_detectsMonthlyRecurringBill() {
        val txns = listOf(
            Transaction(id = 601L, accountId = 1L, originalDate = "2026-01-15", originalMerchant = "Spotify USA", originalAmountCents = -12_99L),
            Transaction(id = 602L, accountId = 1L, originalDate = "2026-02-15", originalMerchant = "Spotify USA", originalAmountCents = -12_99L),
            Transaction(id = 603L, accountId = 1L, originalDate = "2026-03-15", originalMerchant = "Spotify USA", originalAmountCents = -12_99L),
            Transaction(id = 604L, accountId = 1L, originalDate = "2026-04-15", originalMerchant = "Spotify USA", originalAmountCents = -12_99L)
        )

        val candidates = UpcomingEngine.detectCandidates(txns)
        val spotify = candidates.find { it.merchant.contains("spotify", ignoreCase = true) }
        assertNotNull(spotify)
        assertEquals(RecurrenceFrequency.MONTHLY, spotify.frequency)
        assertEquals(12_99L, spotify.amountCents)
        assertTrue(spotify.confidenceScore >= 70)
    }
}
