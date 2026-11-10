package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreditCardStrategyEngineTest {

    private val refToday = LocalDate(2026, 11, 9)

    private fun creditAccount(
        id: Long,
        name: String,
        openedDate: String? = null,
        isBusinessCard: Boolean = false,
        cardholder: String? = "Player 1",
        isArchived: Boolean = false,
        strategy: CreditCardStrategy = CreditCardStrategy(
            cardholder = cardholder,
            openedDate = openedDate,
            isBusinessCard = isBusinessCard
        ),
        benefits: List<CardBenefit> = emptyList(),
        annualFeeCents: Cents? = null
    ): Account = Account(
        id = id,
        name = name,
        type = AccountType.CREDIT,
        isArchived = isArchived,
        creditCardProfile = CreditCardProfile(
            accountId = id,
            cardName = name,
            annualFeeCents = annualFeeCents,
            benefits = benefits,
            strategy = strategy
        )
    )

    @Test
    fun chase524CountsPersonalCardsWithin24MonthsAndExcludesBusinessCards() {
        val accounts = listOf(
            creditAccount(1L, "Old Sapphire", openedDate = "2024-05-01"), // > 24 mos ago -> excluded
            creditAccount(2L, "Freedom Unlimited", openedDate = "2025-01-15"), // slot 1
            creditAccount(3L, "Ink Business Cash", openedDate = "2025-03-10", isBusinessCard = true), // biz -> excluded
            creditAccount(4L, "Amex Gold", openedDate = "2025-06-20"), // slot 2
            creditAccount(5L, "Closed Citi Custom Cash", openedDate = "2025-09-01", isArchived = true), // slot 3 (closed still counts)
            creditAccount(6L, "Venture X", openedDate = "2026-02-10"), // slot 4
            creditAccount(7L, "Bilt Mastercard", openedDate = "2026-08-05") // slot 5
        )

        val status = CreditCardStrategyEngine.computeChase524Status(accounts, refToday)
        assertEquals(5, status.count)
        assertFalse(status.isUnder524)
        assertEquals(1, status.excludedBusinessCount)
        assertEquals("2027-01-15", status.nextSlotFreeDate)
        assertEquals("2027-01-15", status.under524DropDate)
    }

    @Test
    fun subProgressSumsQualifyingPurchasesAndExcludesAnnualFeesAndTransfers() {
        val strategy = CreditCardStrategy(
            cardholder = "Player 1",
            openedDate = "2026-10-01",
            subRewardText = "75,000 UR Points",
            subTargetSpendCents = 4000_00L,
            subDeadlineDate = "2027-01-01",
            subManualSpendOffsetCents = 500_00L
        )
        val account = creditAccount(10L, "Sapphire Preferred", strategy = strategy)

        val txns = listOf(
            // Qualifying purchase: $1,200
            Transaction(id = 1L, accountId = 10L, originalDate = "2026-10-05", originalMerchant = "Delta Air Lines", originalAmountCents = -1200_00L),
            // Qualifying purchase: $800
            Transaction(id = 2L, accountId = 10L, originalDate = "2026-10-12", originalMerchant = "Costco Wholesale", originalAmountCents = -800_00L),
            // Refund/return: +$100 (reduces net spend)
            Transaction(id = 3L, accountId = 10L, originalDate = "2026-10-15", originalMerchant = "Costco Return", originalAmountCents = 100_00L),
            // Annual Fee: should be excluded automatically
            Transaction(id = 4L, accountId = 10L, originalDate = "2026-10-02", originalMerchant = "ANNUAL MEMBERSHIP FEE", originalAmountCents = -95_00L),
            // Credit card payment / transfer: excluded
            Transaction(id = 5L, accountId = 10L, originalDate = "2026-10-25", originalMerchant = "Payment Thank You", originalAmountCents = 1500_00L, isTransfer = true)
        )

        val progress = CreditCardStrategyEngine.computeSubProgress(account, txns, refToday)
        assertNotNull(progress)
        // $500 offset + $1,200 + $800 - $100 refund = $2,400
        assertEquals(2400_00L, progress.currentSpendCents)
        assertEquals(1600_00L, progress.remainingSpendCents)
        assertEquals(0.60f, progress.progressRatio, 0.001f)
        assertEquals(SubTrackerStatus.ON_TRACK, progress.status)
    }

    @Test
    fun subEligibilityCalculates48MonthSapphireAndLifetimeRules() {
        val recentSapphire = CreditCardProfile(
            accountId = 1L,
            strategy = CreditCardStrategy(
                openedDate = "2024-06-01",
                subReceivedDate = "2024-08-15",
                subCooldownMonths = 48
            )
        )
        val elig1 = CreditCardStrategyEngine.computeSubEligibility(recentSapphire, refToday)
        assertNotNull(elig1)
        assertFalse(elig1.isEligibleNow)
        assertEquals("2028-08-15", elig1.eligibleDate)

        val oldSapphire = CreditCardProfile(
            accountId = 2L,
            strategy = CreditCardStrategy(
                openedDate = "2021-03-01",
                subReceivedDate = "2021-05-10",
                subCooldownMonths = 48
            )
        )
        val elig2 = CreditCardStrategyEngine.computeSubEligibility(oldSapphire, refToday)
        assertNotNull(elig2)
        assertTrue(elig2.isEligibleNow)
    }

    @Test
    fun cadenceAwareBenefitsTrackPeriodsAndCalculateNetEffectiveAnnualFee() {
        val uberMonthly = CardBenefit(
            title = "$10/mo Uber Cash",
            annualCreditValueCents = 120_00L,
            cadence = BenefitCadence.MONTHLY,
            usedPeriods = listOf("2026-01", "2026-02", "2026-10") // 3 months used so far ($30)
        )
        assertFalse(CreditCardStrategyEngine.isBenefitUsedInCurrentPeriod(uberMonthly, refToday))

        val toggledNov = CreditCardStrategyEngine.toggleBenefitCurrentPeriod(uberMonthly, refToday)
        assertTrue(CreditCardStrategyEngine.isBenefitUsedInCurrentPeriod(toggledNov, refToday))
        assertTrue("2026-11" in toggledNov.usedPeriods)
        // 4 months used * $10 = $40 captured YTD
        assertEquals(40_00L, CreditCardStrategyEngine.capturedBenefitValueYtdCents(toggledNov, refToday))

        val amexGoldTemplate = CreditCardTemplates.ALL.first { it.id == "amex_gold" }
        val applied = amexGoldTemplate.applyToProfile(CreditCardProfile(accountId = 99L))
        assertEquals(325_00L, applied.annualFeeCents)
        assertTrue(applied.benefits.isNotEmpty())
        assertTrue(applied.rewardCategories.any { it.multiplier == 4.0 })
    }
}
