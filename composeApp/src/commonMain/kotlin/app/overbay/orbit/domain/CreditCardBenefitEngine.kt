package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.BenefitUsage
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import kotlinx.datetime.LocalDate
import kotlin.random.Random

data class BenefitPeriod(
    val key: String,
    val label: String,
    val start: LocalDate,
    val endInclusive: LocalDate
)

data class CardValueLedger(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val isCardmemberYear: Boolean,
    val annualFeeCents: Cents,
    val creditsCapturedCents: Cents,
    val creditsAvailableCents: Cents
) {
    val feeAfterCreditsCents: Cents get() = annualFeeCents - creditsCapturedCents
}

object CreditCardBenefitEngine {

    /**
     * Upgrades legacy CardBenefit representations to Benefits v2 with deterministic IDs,
     * normalized per-period values, and structured usage ledger.
     */
    fun upgradeLegacy(b: CardBenefit, index: Int, today: LocalDate): CardBenefit {
        if (b.id.isNotBlank() && b.usedPeriods.isEmpty() && b.annualCreditValueCents == null) {
            return b
        }

        val resolvedId = b.id.ifBlank {
            "legacy-$index-${b.title.trim().lowercase().hashCode().toUInt().toString(36)}"
        }

        val resolvedPeriodValue = b.periodValueCents ?: b.annualCreditValueCents?.let { legacyValue ->
            if (b.cadence.periodMonths >= 12) {
                legacyValue
            } else {
                val periodsInYear = (12 / b.cadence.periodMonths).coerceAtLeast(1)
                legacyValue / periodsInYear
            }
        }

        val convertedPeriods = b.usedPeriods.map { periodKey ->
            BenefitUsage(periodKey = periodKey, amountCents = resolvedPeriodValue ?: 0L)
        }

        val convertedAnnual = if (b.usedPeriods.isEmpty() && b.usedThisYear && b.cadence == BenefitCadence.ANNUAL) {
            listOf(BenefitUsage(periodKey = today.year.toString(), amountCents = resolvedPeriodValue ?: 0L))
        } else {
            emptyList()
        }

        return b.copy(
            id = resolvedId,
            periodValueCents = resolvedPeriodValue,
            usage = b.usage + convertedPeriods + convertedAnnual,
            annualCreditValueCents = null,
            usedThisYear = false,
            usedPeriods = emptyList()
        )
    }

    /**
     * Upgrades all card benefits within a profile to Benefits v2.
     */
    fun upgradeProfile(p: CreditCardProfile, today: LocalDate): CreditCardProfile {
        return p.copy(benefits = p.benefits.mapIndexed { idx, b -> upgradeLegacy(b, idx, today) })
    }

    fun periodValueCents(b: CardBenefit): Cents = b.periodValueCents ?: 0L

    fun annualizedValueCents(b: CardBenefit): Cents {
        val months = b.cadence.periodMonths.coerceAtLeast(1)
        return periodValueCents(b) * 12 / months
    }

    /**
     * Computes the cardmember year window (start through next anniversary minus 1 day)
     * for the given opened date evaluated on today.
     */
    fun cardmemberYear(opened: LocalDate, today: LocalDate): Pair<LocalDate, LocalDate> {
        var years = today.year - opened.year
        val candidate = LocalDateUtils.addMonthsClamped(opened, years * 12)
        if (candidate > today) {
            years -= 1
        }
        if (years < 0) {
            years = 0
        }
        val start = LocalDateUtils.addMonthsClamped(opened, years * 12)
        val nextAnniversary = LocalDateUtils.addMonthsClamped(opened, (years + 1) * 12)
        val endInclusive = LocalDateUtils.addDays(nextAnniversary, -1)
        return Pair(start, endInclusive)
    }

    /**
     * Resolves the current active period for a benefit based on its cadence and anchor.
     */
    fun currentPeriod(b: CardBenefit, opened: LocalDate?, today: LocalDate): BenefitPeriod {
        return when (b.cadence) {
            BenefitCadence.MONTHLY -> {
                val key = LocalDateUtils.formatYearMonth(today)
                val label = "${LocalDateUtils.monthShortName(today.monthNumber)} ${today.year}"
                val start = LocalDate(today.year, today.monthNumber, 1)
                val end = LocalDate(today.year, today.monthNumber, LocalDateUtils.daysInMonth(today.year, today.monthNumber))
                BenefitPeriod(key, label, start, end)
            }
            BenefitCadence.QUARTERLY -> {
                val q = (today.monthNumber - 1) / 3 + 1
                val key = "${today.year}-Q$q"
                val label = "Q$q ${today.year}"
                val startMonth = (q - 1) * 3 + 1
                val endMonth = q * 3
                val start = LocalDate(today.year, startMonth, 1)
                val end = LocalDate(today.year, endMonth, LocalDateUtils.daysInMonth(today.year, endMonth))
                BenefitPeriod(key, label, start, end)
            }
            BenefitCadence.SEMI_ANNUAL -> {
                val h = if (today.monthNumber <= 6) 1 else 2
                val key = "${today.year}-H$h"
                val label = if (h == 1) "Jan–Jun ${today.year}" else "Jul–Dec ${today.year}"
                val startMonth = if (h == 1) 1 else 7
                val endMonth = if (h == 1) 6 else 12
                val start = LocalDate(today.year, startMonth, 1)
                val end = LocalDate(today.year, endMonth, LocalDateUtils.daysInMonth(today.year, endMonth))
                BenefitPeriod(key, label, start, end)
            }
            BenefitCadence.ANNUAL -> {
                if (b.anchor == BenefitAnchor.CARDMEMBER_YEAR && opened != null) {
                    val (wStart, wEnd) = cardmemberYear(opened, today)
                    val key = "CM-${LocalDateUtils.formatIsoDate(wStart)}"
                    val label = "Card-year to ${LocalDateUtils.formatDisplayDate(wEnd)}"
                    BenefitPeriod(key, label, wStart, wEnd)
                } else {
                    val key = "${today.year}"
                    val label = "${today.year} Annual"
                    val start = LocalDate(today.year, 1, 1)
                    val end = LocalDate(today.year, 12, 31)
                    BenefitPeriod(key, label, start, end)
                }
            }
            BenefitCadence.EVERY_4_YEARS -> {
                val pastYears = b.usage.mapNotNull { it.periodKey.toIntOrNull() }
                    .filter { it + 4 > today.year }
                val activeYear = pastYears.maxOrNull() ?: today.year
                val key = "$activeYear"
                val label = "$activeYear–${activeYear + 3} (4-Year)"
                val start = LocalDate(activeYear, 1, 1)
                val end = LocalDate(activeYear + 3, 12, 31)
                BenefitPeriod(key, label, start, end)
            }
        }
    }

    fun usedCents(b: CardBenefit, period: BenefitPeriod): Cents {
        return b.usage.filter { it.periodKey == period.key }.sumOf { it.amountCents }
    }

    fun remainingCents(b: CardBenefit, period: BenefitPeriod): Cents {
        return (periodValueCents(b) - usedCents(b, period)).coerceAtLeast(0L)
    }

    fun daysUntilReset(period: BenefitPeriod, today: LocalDate): Int {
        return LocalDateUtils.daysBetween(today, period.endInclusive).coerceAtLeast(0)
    }

    fun setUsage(b: CardBenefit, periodKey: String, amountCents: Cents?, usedOn: String?): CardBenefit {
        val periodVal = periodValueCents(b)
        val filtered = b.usage.filter { it.periodKey != periodKey }
        val newUsage = if (amountCents != null && amountCents > 0L) {
            val cappedAmount = if (periodVal > 0L) amountCents.coerceAtMost(periodVal) else amountCents
            filtered + BenefitUsage(periodKey = periodKey, amountCents = cappedAmount, usedOn = usedOn)
        } else {
            filtered
        }
        return b.copy(usage = newUsage.takeLast(120))
    }

    fun parsePeriodStart(key: String): LocalDate? {
        if (key.startsWith("CM-")) {
            return LocalDateUtils.parseIsoDate(key.removePrefix("CM-"))
        }
        if (key.length == 7 && key.contains("-")) {
            val parts = key.split("-")
            val year = parts[0].toIntOrNull() ?: return null
            if (parts[1].startsWith("Q")) {
                val q = parts[1].removePrefix("Q").toIntOrNull() ?: return null
                if (q in 1..4) return LocalDate(year, (q - 1) * 3 + 1, 1)
            } else if (parts[1].startsWith("H")) {
                val h = parts[1].removePrefix("H").toIntOrNull() ?: return null
                if (h in 1..2) return LocalDate(year, if (h == 1) 1 else 7, 1)
            } else {
                val m = parts[1].toIntOrNull() ?: return null
                if (m in 1..12) return LocalDate(year, m, 1)
            }
        }
        if (key.length == 4 && key.toIntOrNull() != null) {
            return LocalDate(key.toInt(), 1, 1)
        }
        return null
    }

    fun ledger(p: CreditCardProfile, today: LocalDate): CardValueLedger {
        val opened = p.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
        val (windowStart, windowEnd, isCardmemberYear) = if (opened != null) {
            val (s, e) = cardmemberYear(opened, today)
            Triple(s, e, true)
        } else {
            Triple(LocalDate(today.year, 1, 1), LocalDate(today.year, 12, 31), false)
        }

        var creditsCaptured = 0L
        for (benefit in p.benefits) {
            for (u in benefit.usage) {
                val start = parsePeriodStart(u.periodKey) ?: continue
                if (start in windowStart..windowEnd) {
                    creditsCaptured += u.amountCents
                }
            }
        }

        val creditsAvailable = p.benefits.sumOf { annualizedValueCents(it) }
        val annualFee = p.annualFeeCents ?: 0L

        return CardValueLedger(
            windowStart = windowStart,
            windowEnd = windowEnd,
            isCardmemberYear = isCardmemberYear,
            annualFeeCents = annualFee,
            creditsCapturedCents = creditsCaptured,
            creditsAvailableCents = creditsAvailable
        )
    }

    fun newBenefitId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "b_" + (1..10).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
