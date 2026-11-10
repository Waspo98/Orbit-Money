package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.BenefitUsage
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.CreditCardStrategy
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CreditCardBenefitEngineTest {

    private val refToday = LocalDate(2026, 11, 9)

    @Test
    fun cardmemberYearCalculatesAnniversaryWindowClampingDay() {
        val opened = LocalDate(2025, 4, 18)
        val (start, end) = CreditCardBenefitEngine.cardmemberYear(opened, refToday)
        assertEquals(LocalDate(2026, 4, 18), start)
        assertEquals(LocalDate(2027, 4, 17), end)

        val leapOpened = LocalDate(2024, 2, 29)
        val leapEval = LocalDate(2025, 3, 1)
        val (lStart, lEnd) = CreditCardBenefitEngine.cardmemberYear(leapOpened, leapEval)
        assertEquals(LocalDate(2025, 2, 28), lStart)
        assertEquals(LocalDate(2026, 2, 27), lEnd)

        val sameDayOpened = LocalDate(2026, 11, 9)
        val (sStart, sEnd) = CreditCardBenefitEngine.cardmemberYear(sameDayOpened, refToday)
        assertEquals(LocalDate(2026, 11, 9), sStart)
        assertEquals(LocalDate(2027, 11, 8), sEnd)
    }

    @Test
    fun currentPeriodResolvesStandardCadencesForCalendarAnchor() {
        val monthly = CardBenefit(id = "b1", cadence = BenefitCadence.MONTHLY)
        val mPeriod = CreditCardBenefitEngine.currentPeriod(monthly, null, refToday)
        assertEquals("2026-11", mPeriod.key)
        assertEquals("Nov 2026", mPeriod.label)
        assertEquals(LocalDate(2026, 11, 1), mPeriod.start)
        assertEquals(LocalDate(2026, 11, 30), mPeriod.endInclusive)

        val quarterly = CardBenefit(id = "b2", cadence = BenefitCadence.QUARTERLY)
        val qPeriod = CreditCardBenefitEngine.currentPeriod(quarterly, null, refToday)
        assertEquals("2026-Q4", qPeriod.key)
        assertEquals("Q4 2026", qPeriod.label)
        assertEquals(LocalDate(2026, 10, 1), qPeriod.start)
        assertEquals(LocalDate(2026, 12, 31), qPeriod.endInclusive)

        val semiAnnual = CardBenefit(id = "b3", cadence = BenefitCadence.SEMI_ANNUAL)
        val sPeriod = CreditCardBenefitEngine.currentPeriod(semiAnnual, null, refToday)
        assertEquals("2026-H2", sPeriod.key)
        assertEquals("Jul–Dec 2026", sPeriod.label)
        assertEquals(LocalDate(2026, 7, 1), sPeriod.start)
        assertEquals(LocalDate(2026, 12, 31), sPeriod.endInclusive)

        val annual = CardBenefit(id = "b4", cadence = BenefitCadence.ANNUAL)
        val aPeriod = CreditCardBenefitEngine.currentPeriod(annual, null, refToday)
        assertEquals("2026", aPeriod.key)
        assertEquals("2026 Annual", aPeriod.label)
        assertEquals(LocalDate(2026, 1, 1), aPeriod.start)
        assertEquals(LocalDate(2026, 12, 31), aPeriod.endInclusive)
    }

    @Test
    fun currentPeriodResolvesCardmemberYearWhenOpenedDatePresent() {
        val travelCredit = CardBenefit(
            id = "csr-travel",
            title = "$300 Travel Credit",
            cadence = BenefitCadence.ANNUAL,
            anchor = BenefitAnchor.CARDMEMBER_YEAR
        )
        val opened = LocalDate(2025, 4, 18)
        val period = CreditCardBenefitEngine.currentPeriod(travelCredit, opened, refToday)

        assertEquals("CM-2026-04-18", period.key)
        assertTrue(period.label.contains("Apr 17, 2027"))
        assertEquals(LocalDate(2026, 4, 18), period.start)
        assertEquals(LocalDate(2027, 4, 17), period.endInclusive)

        val fallbackPeriod = CreditCardBenefitEngine.currentPeriod(travelCredit, null, refToday)
        assertEquals("2026", fallbackPeriod.key)
        assertEquals(LocalDate(2026, 1, 1), fallbackPeriod.start)
        assertEquals(LocalDate(2026, 12, 31), fallbackPeriod.endInclusive)
    }

    @Test
    fun multiYearCreditTracksFourYearCycleAndAnnualizedValue() {
        val globalEntry = CardBenefit(
            id = "ge",
            title = "Global Entry",
            periodValueCents = 120_00L,
            cadence = BenefitCadence.EVERY_4_YEARS,
            usage = listOf(BenefitUsage(periodKey = "2025", amountCents = 120_00L, usedOn = "2025-06-01"))
        )

        assertEquals(30_00L, CreditCardBenefitEngine.annualizedValueCents(globalEntry))

        val period = CreditCardBenefitEngine.currentPeriod(globalEntry, null, refToday)
        assertEquals("2025", period.key)
        assertEquals(LocalDate(2025, 1, 1), period.start)
        assertEquals(LocalDate(2028, 12, 31), period.endInclusive)

        val used = CreditCardBenefitEngine.usedCents(globalEntry, period)
        val remaining = CreditCardBenefitEngine.remainingCents(globalEntry, period)
        assertEquals(120_00L, used)
        assertEquals(0L, remaining)

        val profile = CreditCardProfile(accountId = 1L, benefits = listOf(globalEntry))
        val ledger = CreditCardBenefitEngine.ledger(profile, refToday)
        assertEquals(0L, ledger.creditsCapturedCents)
        assertEquals(30_00L, ledger.creditsAvailableCents)
    }

    @Test
    fun partialUsageTracksUsedAndRemainingCents() {
        val monthlyDining = CardBenefit(
            id = "dining",
            title = "$10 Dining Credit",
            periodValueCents = 10_00L,
            cadence = BenefitCadence.MONTHLY,
            usage = listOf(BenefitUsage(periodKey = "2026-11", amountCents = 6_00L, usedOn = "2026-11-05"))
        )
        val period = CreditCardBenefitEngine.currentPeriod(monthlyDining, null, refToday)
        val used = CreditCardBenefitEngine.usedCents(monthlyDining, period)
        val remaining = CreditCardBenefitEngine.remainingCents(monthlyDining, period)
        val daysUntilReset = CreditCardBenefitEngine.daysUntilReset(period, refToday)

        assertEquals(6_00L, used)
        assertEquals(4_00L, remaining)
        assertEquals(21, daysUntilReset)
    }

    @Test
    fun upgradeLegacyIsDeterministicAndUpgradesUsedPeriods() {
        val legacy = CardBenefit(
            title = "$10/mo Dining Credit",
            annualCreditValueCents = 120_00L,
            cadence = BenefitCadence.MONTHLY,
            usedPeriods = listOf("2026-01", "2026-02")
        )

        val up1 = CreditCardBenefitEngine.upgradeLegacy(legacy, 0, refToday)
        val up2 = CreditCardBenefitEngine.upgradeLegacy(legacy, 0, refToday)

        assertEquals(up1.id, up2.id)
        assertTrue(up1.id.startsWith("legacy-0-"))
        assertEquals(10_00L, up1.periodValueCents)
        assertEquals(
            listOf(
                BenefitUsage(periodKey = "2026-01", amountCents = 10_00L),
                BenefitUsage(periodKey = "2026-02", amountCents = 10_00L)
            ),
            up1.usage
        )
        assertEquals(null, up1.annualCreditValueCents)
        assertEquals(emptyList(), up1.usedPeriods)
        assertFalse(up1.usedThisYear)
    }

    @Test
    fun upgradeLegacyConvertsUsedThisYearForAnnualCadence() {
        val legacy = CardBenefit(
            title = "$50 Hotel Credit",
            annualCreditValueCents = 50_00L,
            cadence = BenefitCadence.ANNUAL,
            usedThisYear = true
        )

        val up = CreditCardBenefitEngine.upgradeLegacy(legacy, 1, refToday)
        assertEquals(50_00L, up.periodValueCents)
        assertEquals(listOf(BenefitUsage(periodKey = "2026", amountCents = 50_00L)), up.usage)
        assertEquals(null, up.annualCreditValueCents)
        assertFalse(up.usedThisYear)
        assertEquals(emptyList(), up.usedPeriods)
    }

    @Test
    fun upgradeLegacyPreservesAlreadyUpgradedBenefit() {
        val modern = CardBenefit(
            id = "csr-travel-credit",
            title = "$300 Annual Travel Credit",
            periodValueCents = 300_00L,
            cadence = BenefitCadence.ANNUAL
        )
        val upgraded = CreditCardBenefitEngine.upgradeLegacy(modern, 0, refToday)
        assertSame(modern, upgraded)
    }

    @Test
    fun setUsageUpdatesReplacesRemovesAndCapsAt120() {
        var perk = CardBenefit(
            id = "p1",
            title = "Test Perk",
            periodValueCents = 10_00L,
            cadence = BenefitCadence.MONTHLY
        )

        perk = CreditCardBenefitEngine.setUsage(perk, "2026-11", 5_00L, "2026-11-05")
        assertEquals(1, perk.usage.size)
        assertEquals(5_00L, perk.usage.first().amountCents)

        perk = CreditCardBenefitEngine.setUsage(perk, "2026-11", 8_00L, "2026-11-08")
        assertEquals(1, perk.usage.size)
        assertEquals(8_00L, perk.usage.first().amountCents)

        val clamped = CreditCardBenefitEngine.setUsage(perk, "2026-11", 15_00L, "2026-11-09")
        assertEquals(10_00L, clamped.usage.first().amountCents)

        val removedNull = CreditCardBenefitEngine.setUsage(perk, "2026-11", null, null)
        assertTrue(removedNull.usage.isEmpty())

        val removedZero = CreditCardBenefitEngine.setUsage(perk, "2026-11", 0L, null)
        assertTrue(removedZero.usage.isEmpty())

        var capPerk = CardBenefit(id = "cap", title = "Cap", periodValueCents = 1_00L)
        for (i in 1..125) {
            capPerk = CreditCardBenefitEngine.setUsage(capPerk, "p_$i", 1_00L, null)
        }
        assertEquals(120, capPerk.usage.size)
        assertEquals("p_6", capPerk.usage.first().periodKey)
        assertEquals("p_125", capPerk.usage.last().periodKey)
    }

    @Test
    fun ledgerCalculatesCalendarAndCardmemberYearCreditsAndNetFee() {
        val monthlyPerk = CardBenefit(
            id = "m",
            title = "Monthly",
            periodValueCents = 10_00L,
            cadence = BenefitCadence.MONTHLY,
            usage = listOf(
                BenefitUsage("2026-01", 10_00L),
                BenefitUsage("2026-02", 10_00L),
                BenefitUsage("2026-11", 10_00L)
            )
        )
        val semiPerk = CardBenefit(
            id = "s",
            title = "Semi",
            periodValueCents = 50_00L,
            cadence = BenefitCadence.SEMI_ANNUAL,
            usage = listOf(BenefitUsage("2026-H1", 50_00L))
        )
        val profileCalendar = CreditCardProfile(
            accountId = 10L,
            annualFeeCents = 325_00L,
            benefits = listOf(monthlyPerk, semiPerk)
        )

        val calLedger = CreditCardBenefitEngine.ledger(profileCalendar, refToday)
        assertFalse(calLedger.isCardmemberYear)
        assertEquals(220_00L, calLedger.creditsAvailableCents)
        assertEquals(80_00L, calLedger.creditsCapturedCents)
        assertEquals(245_00L, calLedger.feeAfterCreditsCents)

        val travelPerk = CardBenefit(
            id = "t",
            title = "Travel",
            periodValueCents = 300_00L,
            cadence = BenefitCadence.ANNUAL,
            anchor = BenefitAnchor.CARDMEMBER_YEAR,
            usage = listOf(BenefitUsage("CM-2026-04-18", 300_00L))
        )
        val profileCardYear = CreditCardProfile(
            accountId = 11L,
            annualFeeCents = 550_00L,
            strategy = CreditCardStrategy(openedDate = "2025-04-18"),
            benefits = listOf(monthlyPerk, semiPerk, travelPerk)
        )
        val cmLedger = CreditCardBenefitEngine.ledger(profileCardYear, refToday)
        assertTrue(cmLedger.isCardmemberYear)
        assertEquals(LocalDate(2026, 4, 18), cmLedger.windowStart)
        assertEquals(LocalDate(2027, 4, 17), cmLedger.windowEnd)
        assertEquals(310_00L, cmLedger.creditsCapturedCents)
        assertEquals(550_00L - 310_00L, cmLedger.feeAfterCreditsCents)
    }

    @Test
    fun newBenefitIdGeneratesUniquePrefixedIdentifiers() {
        val id1 = CreditCardBenefitEngine.newBenefitId()
        val id2 = CreditCardBenefitEngine.newBenefitId()
        assertTrue(id1.startsWith("b_"))
        assertTrue(id2.startsWith("b_"))
        assertEquals(12, id1.length)
        assertNotEquals(id1, id2)
    }
}
