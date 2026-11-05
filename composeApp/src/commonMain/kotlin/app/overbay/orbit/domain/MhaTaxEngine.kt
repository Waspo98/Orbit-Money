package app.overbay.orbit.domain

import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class FederalBracket(val thresholdCents: Cents, val rate: Double)

data class MhaTaxCalculationResult(
    val eligibleTotalCents: Cents,
    val excludableAllowanceCents: Cents, // 3-limit cap
    val totalSavingsCents: Cents,
    val effectiveSavingsRate: Double,
    val federalSavingsCents: Cents,
    val federalMarginalRate: Double,
    val stateSavingsCents: Cents,
    val stateRate: Double,
    val taxableIncomeBeforeMhaCents: Cents,
    val taxableIncomeAfterMhaCents: Cents,
    val secaSubjectCents: Cents, // MHA is exempt from income tax, but subject to SECA
    val secaTaxEstimateCents: Cents, // 15.3% SECA estimate
    val assumptions: List<String>
) {
    val secaEstimateCents: Cents get() = secaTaxEstimateCents
}

object MhaTaxEngine {

    const val TAX_YEAR = 2026
    const val SECA_TAX_RATE = 0.153 // 15.3% SECA

    val STANDARD_DEDUCTIONS_2026 = mapOf(
        FilingStatus.SINGLE to 16_100_00L,
        FilingStatus.MARRIED_JOINT to 32_200_00L,
        FilingStatus.MARRIED_SEPARATE to 16_100_00L,
        FilingStatus.HEAD_OF_HOUSEHOLD to 24_150_00L
    )

    val FEDERAL_BRACKETS_2026 = mapOf(
        FilingStatus.SINGLE to listOf(
            FederalBracket(0L, 0.10),
            FederalBracket(12_400_00L, 0.12),
            FederalBracket(50_400_00L, 0.22),
            FederalBracket(105_700_00L, 0.24),
            FederalBracket(201_775_00L, 0.32),
            FederalBracket(256_225_00L, 0.35),
            FederalBracket(640_600_00L, 0.37)
        ),
        FilingStatus.MARRIED_JOINT to listOf(
            FederalBracket(0L, 0.10),
            FederalBracket(24_800_00L, 0.12),
            FederalBracket(100_800_00L, 0.22),
            FederalBracket(211_400_00L, 0.24),
            FederalBracket(403_550_00L, 0.32),
            FederalBracket(512_450_00L, 0.35),
            FederalBracket(768_700_00L, 0.37)
        ),
        FilingStatus.MARRIED_SEPARATE to listOf(
            FederalBracket(0L, 0.10),
            FederalBracket(12_400_00L, 0.12),
            FederalBracket(50_400_00L, 0.22),
            FederalBracket(105_700_00L, 0.24),
            FederalBracket(201_775_00L, 0.32),
            FederalBracket(256_225_00L, 0.35),
            FederalBracket(384_350_00L, 0.37)
        ),
        FilingStatus.HEAD_OF_HOUSEHOLD to listOf(
            FederalBracket(0L, 0.10),
            FederalBracket(17_700_00L, 0.12),
            FederalBracket(67_450_00L, 0.22),
            FederalBracket(105_700_00L, 0.24),
            FederalBracket(201_750_00L, 0.32),
            FederalBracket(256_200_00L, 0.35),
            FederalBracket(640_600_00L, 0.37)
        )
    )

    // Flat single rate states for 2026
    val STATE_RATES_2026 = mapOf(
        "AK" to 0.0, "FL" to 0.0, "NV" to 0.0, "NH" to 0.0, "SD" to 0.0, "TN" to 0.0, "TX" to 0.0, "WY" to 0.0, "WA" to 0.0,
        "AZ" to 0.025,
        "CO" to 0.044,
        "GA" to 0.0519,
        "IL" to 0.0495,
        "IN" to 0.0295,
        "IA" to 0.038,
        "KY" to 0.035,
        "LA" to 0.030,
        "MI" to 0.0425,
        "MS" to 0.040,
        "NC" to 0.0399,
        "PA" to 0.0307,
        "UT" to 0.045
    )

    /**
     * Parsonage 3-Limit Rule under IRC Section 107:
     * Excludable allowance is the least of:
     * 1. Actual eligible housing expenses paid
     * 2. Official church MHA designation cap
     * 3. Fair market rental value (including utilities and furnishings)
     */
    fun calculateParsonageCap(
        actualExpensesCents: Cents,
        designatedCapCents: Cents? = null,
        fairMarketRentalValueCents: Cents? = null
    ): Cents {
        var cap = max(0L, actualExpensesCents)
        if (designatedCapCents != null && designatedCapCents >= 0L) {
            cap = min(cap, designatedCapCents)
        }
        if (fairMarketRentalValueCents != null && fairMarketRentalValueCents >= 0L) {
            cap = min(cap, fairMarketRentalValueCents)
        }
        return max(0L, cap)
    }

    fun calculateMhaSavings(
        actualEligibleCents: Cents,
        profile: MhaTaxProfile,
        householdMembers: List<HouseholdMember> = emptyList(),
        designatedCapCents: Cents? = null,
        fairMarketRentalValueCents: Cents? = null
    ): MhaTaxCalculationResult {
        val excludableCents = calculateParsonageCap(actualEligibleCents, designatedCapCents, fairMarketRentalValueCents)

        if (profile.mode.equals("simple", ignoreCase = true)) {
            val fedRate = profile.simpleFederalRatePercent / 100.0
            val stRate = profile.simpleStateRatePercent / 100.0
            val fedSavings = (excludableCents.toDouble() * fedRate).roundToLong()
            val stSavings = (excludableCents.toDouble() * stRate).roundToLong()
            val totalSavings = fedSavings + stSavings
            val effRate = fedRate + stRate
            val secaEstimate = (excludableCents.toDouble() * SECA_TAX_RATE).roundToLong()

            return MhaTaxCalculationResult(
                eligibleTotalCents = actualEligibleCents,
                excludableAllowanceCents = excludableCents,
                totalSavingsCents = totalSavings,
                effectiveSavingsRate = effRate,
                federalSavingsCents = fedSavings,
                federalMarginalRate = fedRate,
                stateSavingsCents = stSavings,
                stateRate = stRate,
                taxableIncomeBeforeMhaCents = 0L,
                taxableIncomeAfterMhaCents = 0L,
                secaSubjectCents = excludableCents,
                secaTaxEstimateCents = secaEstimate,
                assumptions = listOf(
                    "Simple mode uses flat rates: ${profile.simpleFederalRatePercent}% federal, ${profile.simpleStateRatePercent}% state.",
                    "MHA is exempt from federal and state income taxes but subject to SECA (15.3%) unless exempt via Form 4361."
                )
            )
        }

        // Household mode: compute marginal brackets
        val grossIncomeCents = householdMembers.sumOf { it.grossIncomeAnnualCents }
        val pretaxDeductionsCents = householdMembers.sumOf { memberPretaxContributions(it) }

        val deductionCents = if (profile.deductionMode.equals("itemized", ignoreCase = true)) {
            profile.itemizedDeductionCents
        } else {
            STANDARD_DEDUCTIONS_2026[profile.filingStatus] ?: 32_200_00L
        }

        val estimatedTaxableCents = max(0L, grossIncomeCents - pretaxDeductionsCents - deductionCents)
        val taxableBefore = profile.taxableIncomeOverrideCents ?: estimatedTaxableCents
        val taxableAfter = max(0L, taxableBefore - excludableCents)

        val fedBefore = computeFederalTax(taxableBefore, profile.filingStatus)
        val fedAfter = computeFederalTax(taxableAfter, profile.filingStatus)
        val fedSavings = max(0L, fedBefore - fedAfter)
        val fedMarginal = computeMarginalRate(taxableBefore, profile.filingStatus)

        val stRate = profile.stateRateOverridePercent?.let { it / 100.0 }
            ?: (STATE_RATES_2026[profile.state] ?: 0.0)
        val stSavings = (excludableCents.toDouble() * stRate).roundToLong()
        val totalSavings = fedSavings + stSavings
        val effRate = if (excludableCents > 0L) totalSavings.toDouble() / excludableCents.toDouble() else (fedMarginal + stRate)
        val secaEstimate = (excludableCents.toDouble() * SECA_TAX_RATE).roundToLong()

        return MhaTaxCalculationResult(
            eligibleTotalCents = actualEligibleCents,
            excludableAllowanceCents = excludableCents,
            totalSavingsCents = totalSavings,
            effectiveSavingsRate = effRate,
            federalSavingsCents = fedSavings,
            federalMarginalRate = fedMarginal,
            stateSavingsCents = stSavings,
            stateRate = stRate,
            taxableIncomeBeforeMhaCents = taxableBefore,
            taxableIncomeAfterMhaCents = taxableAfter,
            secaSubjectCents = excludableCents,
            secaTaxEstimateCents = secaEstimate,
            assumptions = listOf(
                "Household mode calculates exact 2026 federal brackets and state marginal savings.",
                "MHA is exempt from federal and state income taxes but subject to SECA (15.3%) unless exempt via Form 4361."
            )
        )
    }

    fun calculateMhaSavings(
        actualEligibleCents: Cents,
        profile: MhaTaxProfile,
        designatedCapCents: Cents? = null,
        fairMarketRentalValueCents: Cents? = null
    ): MhaTaxCalculationResult = calculateMhaSavings(
        actualEligibleCents = actualEligibleCents,
        profile = profile,
        householdMembers = emptyList(),
        designatedCapCents = designatedCapCents,
        fairMarketRentalValueCents = fairMarketRentalValueCents
    )

    fun computeFederalTax(taxableCents: Cents, status: FilingStatus): Cents {
        val brackets = FEDERAL_BRACKETS_2026[status] ?: FEDERAL_BRACKETS_2026[FilingStatus.MARRIED_JOINT]!!
        if (taxableCents <= 0L) return 0L
        var tax = 0.0
        for (i in brackets.indices) {
            val cur = brackets[i]
            val next = brackets.getOrNull(i + 1)
            if (taxableCents <= cur.thresholdCents) break
            val upper = if (next != null) min(taxableCents, next.thresholdCents) else taxableCents
            tax += (upper - cur.thresholdCents).toDouble() * cur.rate
        }
        return tax.roundToLong()
    }

    fun computeMarginalRate(taxableCents: Cents, status: FilingStatus): Double {
        val brackets = FEDERAL_BRACKETS_2026[status] ?: FEDERAL_BRACKETS_2026[FilingStatus.MARRIED_JOINT]!!
        if (taxableCents <= 0L) return brackets.first().rate
        for (i in brackets.indices.reversed()) {
            if (taxableCents >= brackets[i].thresholdCents) {
                return brackets[i].rate
            }
        }
        return brackets.first().rate
    }

    private fun memberPretaxContributions(member: HouseholdMember): Cents {
        val gross = member.grossIncomeAnnualCents
        val explicitRetirement = member.employeeContributionAnnualCents
        val retirement = if (explicitRetirement > 0L) explicitRetirement else {
            (gross.toDouble() * ((member.employeeContributionPercent ?: 0.0) / 100.0)).roundToLong()
        }
        return retirement +
            member.hsaContributionAnnualCents +
            member.dependentCareFsaAnnualCents +
            member.otherBenefitsAnnualCents +
            (member.healthPremiumPerMonthCents * 12)
    }
}
