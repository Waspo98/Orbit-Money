package app.overbay.orbit.ui.accounts.cards

import app.overbay.orbit.data.model.RewardCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class CardFormatTest {

    @Test
    fun testFormatCardDate() {
        assertEquals("Not Set", CardFormat.formatCardDate(null))
        assertEquals("Not Set", CardFormat.formatCardDate(""))
        assertEquals("Not Set", CardFormat.formatCardDate("   "))
        assertEquals("Not Set", CardFormat.formatCardDate("invalid-date"))
        val currentYear = app.overbay.orbit.domain.LocalDateUtils.todayLocalDate().year
        assertEquals("Nov 10", CardFormat.formatCardDate("$currentYear-11-10"))
        assertEquals("Jan 1, 2025", CardFormat.formatCardDate("2025-01-01"))
        assertEquals("Jun 30, 2027", CardFormat.formatCardDate("2027-06-30"))
    }

    @Test
    fun testFormatDaysUntil() {
        assertEquals("Today", CardFormat.formatDaysUntil(0))
        assertEquals("Tomorrow", CardFormat.formatDaysUntil(1))
        assertEquals("in 2 days", CardFormat.formatDaysUntil(2))
        assertEquals("in 15 days", CardFormat.formatDaysUntil(15))
        assertEquals("Yesterday", CardFormat.formatDaysUntil(-1))
        assertEquals("2 days ago", CardFormat.formatDaysUntil(-2))
        assertEquals("10 days ago", CardFormat.formatDaysUntil(-10))
    }

    @Test
    fun testFormatEarnRate() {
        val wholePoints = RewardCategory(category = "Dining", multiplier = 4.0, rateType = "points")
        assertEquals("4\u00D7 points", CardFormat.formatEarnRate(wholePoints))

        val fracPoints = RewardCategory(category = "Travel", multiplier = 1.5, rateType = "points")
        assertEquals("1.5\u00D7 points", CardFormat.formatEarnRate(fracPoints))

        val wholeCash = RewardCategory(category = "Groceries", multiplier = 3.0, rateType = "percent")
        assertEquals("3% cash", CardFormat.formatEarnRate(wholeCash))

        val fracCash = RewardCategory(category = "Everything", multiplier = 1.5, rateType = "percent")
        assertEquals("1.5% cash", CardFormat.formatEarnRate(fracCash))
    }

    @Test
    fun testFormatBps() {
        assertEquals("—", CardFormat.formatBps(null))
        assertEquals("N/A", CardFormat.formatBps(null, fallback = "N/A"))
        assertEquals("0%", CardFormat.formatBps(0))
        assertEquals("24%", CardFormat.formatBps(2400))
        assertEquals("24.9%", CardFormat.formatBps(2490))
        assertEquals("24.99%", CardFormat.formatBps(2499))
        assertEquals("0.5%", CardFormat.formatBps(50))
        assertEquals("0.05%", CardFormat.formatBps(5))
        assertEquals("100%", CardFormat.formatBps(10000))
        assertEquals("-0.5%", CardFormat.formatBps(-50))
        assertEquals("-0.01%", CardFormat.formatBps(-1))
        assertEquals("-0.99%", CardFormat.formatBps(-99))
        assertEquals("-1%", CardFormat.formatBps(-100))
        assertEquals("-1.5%", CardFormat.formatBps(-150))
        assertEquals("-24.99%", CardFormat.formatBps(-2499))
    }
}
