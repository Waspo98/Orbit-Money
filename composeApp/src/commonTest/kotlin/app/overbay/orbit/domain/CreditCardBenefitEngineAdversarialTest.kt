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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreditCardBenefitEngineAdversarialTest {

    @Test
    fun cardmemberYearStressTestEveryDayContinuityAcross12YearsLeapOpened() {
        val leapOpened = LocalDate(2024, 2, 29)
        var cursor = leapOpened
        val horizon = LocalDate(2036, 2, 28) // 12 full years

        var lastEndInclusive: LocalDate? = null

        while (cursor <= horizon) {
            val (wStart, wEnd) = CreditCardBenefitEngine.cardmemberYear(leapOpened, cursor)

            // 1. Current day must be inside the resolved window
            assertTrue(
                cursor in wStart..wEnd,
                "Day $cursor must be within window [$wStart, $wEnd]"
            )

            // 2. Length of window must be either 365 days (leap) or 364 days (normal) between start and endInclusive
            val windowDays = LocalDateUtils.daysBetween(wStart, wEnd)
            assertTrue(
                windowDays in 364..365,
                "Window from $wStart to $wEnd has unexpected day span: $windowDays"
            )

            // 3. Continuity check: when moving across window boundary, the next window must start immediately
            if (lastEndInclusive != null && cursor == wStart) {
                assertEquals(
                    cursor,
                    LocalDateUtils.addDays(lastEndInclusive, 1),
                    "Gap or overlap detected between $lastEndInclusive and $cursor"
                )
            }

            if (cursor == wEnd) {
                lastEndInclusive = wEnd
            }

            cursor = LocalDateUtils.addDays(cursor, 1)
        }
    }

    @Test
    fun cardmemberYearStressTestMonthEndOpeningsAcross5Years() {
        val monthEnds = listOf(
            LocalDate(2025, 1, 31),
            LocalDate(2025, 3, 31),
            LocalDate(2025, 5, 31),
            LocalDate(2025, 8, 31),
            LocalDate(2025, 10, 31)
        )

        for (opened in monthEnds) {
            var cursor = opened
            val horizon = LocalDate(opened.year + 5, opened.monthNumber, opened.dayOfMonth)

            while (cursor <= horizon) {
                val (wStart, wEnd) = CreditCardBenefitEngine.cardmemberYear(opened, cursor)
                assertTrue(cursor in wStart..wEnd, "For opened $opened, cursor $cursor was not in [$wStart, $wEnd]")
                cursor = LocalDateUtils.addDays(cursor, 1)
            }
        }
    }

    @Test
    fun cardmemberYearHandlesFutureOpenedDateGracefully() {
        val openedInFuture = LocalDate(2027, 6, 1)
        val today = LocalDate(2026, 11, 10)
        val (start, end) = CreditCardBenefitEngine.cardmemberYear(openedInFuture, today)

        assertEquals(LocalDate(2027, 6, 1), start)
        assertEquals(LocalDate(2028, 5, 31), end)
    }

    @Test
    fun cadenceResolutionBoundaryStressTest() {
        // Month boundary leap year 2024
        val monthly = CardBenefit(id = "m", cadence = BenefitCadence.MONTHLY)
        val leapFeb29 = LocalDate(2024, 2, 29)
        val periodFeb = CreditCardBenefitEngine.currentPeriod(monthly, null, leapFeb29)
        assertEquals("2024-02", periodFeb.key)
        assertEquals(LocalDate(2024, 2, 1), periodFeb.start)
        assertEquals(LocalDate(2024, 2, 29), periodFeb.endInclusive)
        assertEquals(0, CreditCardBenefitEngine.daysUntilReset(periodFeb, leapFeb29))

        val mar1 = LocalDate(2024, 3, 1)
        val periodMar = CreditCardBenefitEngine.currentPeriod(monthly, null, mar1)
        assertEquals("2024-03", periodMar.key)
        assertEquals(LocalDate(2024, 3, 1), periodMar.start)
        assertEquals(LocalDate(2024, 3, 31), periodMar.endInclusive)
        assertEquals(30, CreditCardBenefitEngine.daysUntilReset(periodMar, mar1))

        // Quarter boundaries
        val quarterly = CardBenefit(id = "q", cadence = BenefitCadence.QUARTERLY)
        val q1End = LocalDate(2026, 3, 31)
        val q1 = CreditCardBenefitEngine.currentPeriod(quarterly, null, q1End)
        assertEquals("2026-Q1", q1.key)
        assertEquals(0, CreditCardBenefitEngine.daysUntilReset(q1, q1End))

        val q2Start = LocalDate(2026, 4, 1)
        val q2 = CreditCardBenefitEngine.currentPeriod(quarterly, null, q2Start)
        assertEquals("2026-Q2", q2.key)
        assertEquals(LocalDate(2026, 4, 1), q2.start)
        assertEquals(LocalDate(2026, 6, 30), q2.endInclusive)

        // Semi-annual boundaries
        val semi = CardBenefit(id = "s", cadence = BenefitCadence.SEMI_ANNUAL)
        val h1End = LocalDate(2026, 6, 30)
        val h1 = CreditCardBenefitEngine.currentPeriod(semi, null, h1End)
        assertEquals("2026-H1", h1.key)
        assertEquals(0, CreditCardBenefitEngine.daysUntilReset(h1, h1End))

        val h2Start = LocalDate(2026, 7, 1)
        val h2 = CreditCardBenefitEngine.currentPeriod(semi, null, h2Start)
        assertEquals("2026-H2", h2.key)
        assertEquals(LocalDate(2026, 7, 1), h2.start)
        assertEquals(LocalDate(2026, 12, 31), h2.endInclusive)
    }

    @Test
    fun everyFourYearsTracksMultiYearLifecycleAndReset() {
        val fourYearPerk = CardBenefit(
            id = "ge",
            cadence = BenefitCadence.EVERY_4_YEARS,
            periodValueCents = 100_00L,
            usage = listOf(BenefitUsage("2024", 100_00L, "2024-05-10"))
        )

        // Year 1 (2024): active
        val p2024 = CreditCardBenefitEngine.currentPeriod(fourYearPerk, null, LocalDate(2024, 6, 1))
        assertEquals("2024", p2024.key)
        assertEquals(100_00L, CreditCardBenefitEngine.usedCents(fourYearPerk, p2024))
        assertEquals(0L, CreditCardBenefitEngine.remainingCents(fourYearPerk, p2024))

        // Year 3 (2026): still active
        val p2026 = CreditCardBenefitEngine.currentPeriod(fourYearPerk, null, LocalDate(2026, 11, 10))
        assertEquals("2024", p2026.key)
        assertEquals(100_00L, CreditCardBenefitEngine.usedCents(fourYearPerk, p2026))
        assertEquals(0L, CreditCardBenefitEngine.remainingCents(fourYearPerk, p2026))

        // Year 4 last day (2027-12-31): last day of cycle
        val p2027End = CreditCardBenefitEngine.currentPeriod(fourYearPerk, null, LocalDate(2027, 12, 31))
        assertEquals("2024", p2027End.key)
        assertEquals(0, CreditCardBenefitEngine.daysUntilReset(p2027End, LocalDate(2027, 12, 31)))

        // Year 5 (2028-01-01): reset! (2024 + 4 > 2028 is false)
        val p2028 = CreditCardBenefitEngine.currentPeriod(fourYearPerk, null, LocalDate(2028, 1, 1))
        assertEquals("2028", p2028.key)
        assertEquals(LocalDate(2028, 1, 1), p2028.start)
        assertEquals(LocalDate(2031, 12, 31), p2028.endInclusive)
        assertEquals(0L, CreditCardBenefitEngine.usedCents(fourYearPerk, p2028))
        assertEquals(100_00L, CreditCardBenefitEngine.remainingCents(fourYearPerk, p2028))
    }

    @Test
    fun partialUsageCoercionNegativeAndZeroHandling() {
        val perk = CardBenefit(
            id = "dining",
            periodValueCents = 25_00L,
            cadence = BenefitCadence.MONTHLY
        )

        // Negative usage does not store, acts as removal
        val neg1 = CreditCardBenefitEngine.setUsage(perk, "2026-11", -50_00L, null)
        assertTrue(neg1.usage.isEmpty())

        val neg2 = CreditCardBenefitEngine.setUsage(perk, "2026-11", -1L, null)
        assertTrue(neg2.usage.isEmpty())

        // Zero usage removes
        val zero = CreditCardBenefitEngine.setUsage(perk, "2026-11", 0L, null)
        assertTrue(zero.usage.isEmpty())

        // Over-limit usage coerced to period value
        val capped = CreditCardBenefitEngine.setUsage(perk, "2026-11", 100_00L, "2026-11-01")
        assertEquals(1, capped.usage.size)
        assertEquals(25_00L, capped.usage.first().amountCents)
        assertEquals(0L, CreditCardBenefitEngine.remainingCents(capped, BenefitPeriod("2026-11", "", LocalDate(2026,11,1), LocalDate(2026,11,30))))

        // Unvalued perk (e.g. lounge access periodValue = null/0) allows arbitrary positive usage
        val unvalued = CardBenefit(id = "lounge", cadence = BenefitCadence.ANNUAL)
        val usedUnvalued = CreditCardBenefitEngine.setUsage(unvalued, "2026", 1L, "2026-05-01")
        assertEquals(1L, usedUnvalued.usage.first().amountCents)
    }

    @Test
    fun usageFifoTrimmingMaintainsMaximum120Entries() {
        var perk = CardBenefit(id = "cap_stress", periodValueCents = 10_00L)
        for (i in 1..250) {
            perk = CreditCardBenefitEngine.setUsage(perk, "period_$i", 5_00L, null)
        }
        assertEquals(120, perk.usage.size)
        // First retained must be period_131, last must be period_250
        assertEquals("period_131", perk.usage.first().periodKey)
        assertEquals("period_250", perk.usage.last().periodKey)
    }

    @Test
    fun deterministicUpgradeLegacyAcrossSpecialCharactersAndWhitespace() {
        val exotic = CardBenefit(
            title = "   🌟 VIP Lounge Access & $100 Credit!  \n ",
            annualCreditValueCents = 100_00L,
            cadence = BenefitCadence.ANNUAL,
            usedThisYear = true
        )

        val u1 = CreditCardBenefitEngine.upgradeLegacy(exotic, 3, LocalDate(2026, 11, 10))
        val u2 = CreditCardBenefitEngine.upgradeLegacy(exotic, 3, LocalDate(2026, 11, 10))

        assertEquals(u1.id, u2.id)
        assertTrue(u1.id.startsWith("legacy-3-"))
        assertEquals(100_00L, u1.periodValueCents)
        assertEquals(listOf(BenefitUsage("2026", 100_00L)), u1.usage)
        assertNull(u1.annualCreditValueCents)
        assertFalse(u1.usedThisYear)
        assertTrue(u1.usedPeriods.isEmpty())
    }

    @Test
    fun upgradeLegacyCadencePeriodDivisionAccuracy() {
        val monthly = CardBenefit(title = "Monthly", annualCreditValueCents = 120_00L, cadence = BenefitCadence.MONTHLY)
        val upM = CreditCardBenefitEngine.upgradeLegacy(monthly, 0, LocalDate(2026, 1, 1))
        assertEquals(10_00L, upM.periodValueCents)

        val quarterly = CardBenefit(title = "Quarterly", annualCreditValueCents = 200_00L, cadence = BenefitCadence.QUARTERLY)
        val upQ = CreditCardBenefitEngine.upgradeLegacy(quarterly, 0, LocalDate(2026, 1, 1))
        assertEquals(50_00L, upQ.periodValueCents)

        val semi = CardBenefit(title = "Semi", annualCreditValueCents = 100_00L, cadence = BenefitCadence.SEMI_ANNUAL)
        val upS = CreditCardBenefitEngine.upgradeLegacy(semi, 0, LocalDate(2026, 1, 1))
        assertEquals(50_00L, upS.periodValueCents)

        val fourYear = CardBenefit(title = "4-Year", annualCreditValueCents = 120_00L, cadence = BenefitCadence.EVERY_4_YEARS)
        val up4 = CreditCardBenefitEngine.upgradeLegacy(fourYear, 0, LocalDate(2026, 1, 1))
        assertEquals(120_00L, up4.periodValueCents)
    }

    @Test
    fun parsePeriodStartRobustnessAgainstMalformedKeys() {
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026-01"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026-12"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026-Q1"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026-Q4"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026-H1"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026-H2"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("2026"))
        assertNotNull(CreditCardBenefitEngine.parsePeriodStart("CM-2026-04-18"))

        // Malformed inputs must return null without throwing exceptions
        assertNull(CreditCardBenefitEngine.parsePeriodStart("2026-00"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("2026-13"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("2026-Q0"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("2026-Q5"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("2026-H0"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("2026-H3"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("CM-invalid-date"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart("random_string"))
        assertNull(CreditCardBenefitEngine.parsePeriodStart(""))
    }

    @Test
    fun ledgerMathSupportsSurplusCreditsAndZeroFees() {
        val perk = CardBenefit(
            id = "free_hotel",
            periodValueCents = 300_00L,
            cadence = BenefitCadence.ANNUAL,
            usage = listOf(BenefitUsage("2026", 300_00L))
        )

        // Case 1: Credits exceed fee -> net negative fee (cardholder in the black)
        val surplusProfile = CreditCardProfile(
            accountId = 1L,
            annualFeeCents = 95_00L,
            benefits = listOf(perk)
        )
        val surplusLedger = CreditCardBenefitEngine.ledger(surplusProfile, LocalDate(2026, 11, 10))
        assertEquals(95_00L, surplusLedger.annualFeeCents)
        assertEquals(300_00L, surplusLedger.creditsCapturedCents)
        assertEquals(95_00L - 300_00L, surplusLedger.feeAfterCreditsCents) // -205_00L

        // Case 2: Zero fee card with credits -> feeAfterCreditsCents = -creditsCapturedCents
        val noFeeProfile = CreditCardProfile(
            accountId = 2L,
            annualFeeCents = 0L,
            benefits = listOf(perk)
        )
        val noFeeLedger = CreditCardBenefitEngine.ledger(noFeeProfile, LocalDate(2026, 11, 10))
        assertEquals(0L, noFeeLedger.annualFeeCents)
        assertEquals(300_00L, noFeeLedger.creditsCapturedCents)
        assertEquals(-300_00L, noFeeLedger.feeAfterCreditsCents)

        // Case 3: Fee with zero credits captured -> feeAfterCreditsCents = fee
        val uncapturedProfile = CreditCardProfile(
            accountId = 3L,
            annualFeeCents = 550_00L,
            benefits = listOf(perk.copy(usage = emptyList()))
        )
        val uncapturedLedger = CreditCardBenefitEngine.ledger(uncapturedProfile, LocalDate(2026, 11, 10))
        assertEquals(550_00L, uncapturedLedger.annualFeeCents)
        assertEquals(0L, uncapturedLedger.creditsCapturedCents)
        assertEquals(550_00L, uncapturedLedger.feeAfterCreditsCents)
    }
}
