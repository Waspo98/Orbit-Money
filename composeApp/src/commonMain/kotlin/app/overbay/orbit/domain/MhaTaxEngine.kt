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
    val excludableAllowanceCents: Cents,
    val totalSavingsCents: Cents,
    val effectiveSavingsRate: Double,
    val federalSavingsCents: Cents,
    val federalMarginalRate: Double,
    val stateSavingsCents: Cents,
    val stateRate: Double,
    val taxableIncomeBeforeMhaCents: Cents,
    val taxableIncomeAfterMhaCents: Cents,
    val secaSubjectCents: Cents,
    val secaTaxEstimateCents: Cents,
    val assumptions: List<String>,
    val grossIncomeCents: Cents = 0L,
    val reductionCents: Cents = 0L,
    val isHouseholdMode: Boolean = false
) {
    val secaEstimateCents: Cents get() = secaTaxEstimateCents
    val effectiveSavingsRatePercent: Double get() = (effectiveSavingsRate * 10000).roundToLong() / 100.0
    val marginalFederalRatePercent: Double get() = (federalMarginalRate * 10000).roundToLong() / 100.0
    val effectiveStateRatePercent: Double get() = (stateRate * 10000).roundToLong() / 100.0
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

    val NO_INCOME_TAX_STATES = setOf("AK", "FL", "NV", "NH", "SD", "TN", "TX", "WY", "WA")

    // Flat single rate states for 2026
    val STATE_RATES_2026 = mapOf(
        "AZ" to 0.025,
        "CO" to 0.044,
        "GA" to 0.0519,
        "ID" to 0.053,
        "IL" to 0.0495,
        "IN" to 0.0295,
        "IA" to 0.038,
        "KY" to 0.035,
        "LA" to 0.030,
        "MI" to 0.0425,
        "MS" to 0.040,
        "NC" to 0.0399,
        "OH" to 0.0275,
        "PA" to 0.0307,
        "UT" to 0.045
    )

    val US_STATES: List<Pair<String, String>> = listOf(
        "AL" to "Alabama",
        "AK" to "Alaska",
        "AZ" to "Arizona",
        "AR" to "Arkansas",
        "CA" to "California",
        "CO" to "Colorado",
        "CT" to "Connecticut",
        "DE" to "Delaware",
        "DC" to "District of Columbia",
        "FL" to "Florida",
        "GA" to "Georgia",
        "HI" to "Hawaii",
        "ID" to "Idaho",
        "IL" to "Illinois",
        "IN" to "Indiana",
        "IA" to "Iowa",
        "KS" to "Kansas",
        "KY" to "Kentucky",
        "LA" to "Louisiana",
        "ME" to "Maine",
        "MD" to "Maryland",
        "MA" to "Massachusetts",
        "MI" to "Michigan",
        "MN" to "Minnesota",
        "MS" to "Mississippi",
        "MO" to "Missouri",
        "MT" to "Montana",
        "NE" to "Nebraska",
        "NV" to "Nevada",
        "NH" to "New Hampshire",
        "NJ" to "New Jersey",
        "NM" to "New Mexico",
        "NY" to "New York",
        "NC" to "North Carolina",
        "ND" to "North Dakota",
        "OH" to "Ohio",
        "OK" to "Oklahoma",
        "OR" to "Oregon",
        "PA" to "Pennsylvania",
        "RI" to "Rhode Island",
        "SC" to "South Carolina",
        "SD" to "South Dakota",
        "TN" to "Tennessee",
        "TX" to "Texas",
        "UT" to "Utah",
        "VT" to "Vermont",
        "VA" to "Virginia",
        "WA" to "Washington",
        "WV" to "West Virginia",
        "WI" to "Wisconsin",
        "WY" to "Wyoming"
    )

    fun isGraduatedState(stateCode: String): Boolean {
        val code = stateCode.uppercase().trim()
        return !NO_INCOME_TAX_STATES.contains(code) && !STATE_RATES_2026.containsKey(code)
    }

    fun getStateThresholdCents(stateCode: String, filingStatus: FilingStatus): Cents =
        when (stateCode.uppercase().trim()) {
            "ID" -> when (filingStatus) {
                FilingStatus.SINGLE, FilingStatus.MARRIED_SEPARATE -> 4_811_00L
                FilingStatus.MARRIED_JOINT, FilingStatus.HEAD_OF_HOUSEHOLD -> 9_622_00L
            }
            "MS" -> 10_000_00L
            "OH" -> 26_050_00L
            else -> 0L
        }

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
                    "Simple mode uses the rates you enter."
                ),
                grossIncomeCents = 0L,
                reductionCents = 0L,
                isHouseholdMode = false
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

        val stateCode = profile.state.uppercase().trim()
        val stRate = when {
            profile.stateRateOverridePercent != null -> profile.stateRateOverridePercent / 100.0
            NO_INCOME_TAX_STATES.contains(stateCode) -> 0.0
            STATE_RATES_2026.containsKey(stateCode) -> {
                val threshold = getStateThresholdCents(stateCode, profile.filingStatus)
                if (taxableBefore > threshold) (STATE_RATES_2026[stateCode] ?: 0.0) else 0.0
            }
            else -> 0.0
        }
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
                "Household mode assumes listed paycheck contributions are pre-tax.",
                "Federal savings are calculated as tax before MHA minus tax after MHA."
            ),
            grossIncomeCents = grossIncomeCents,
            reductionCents = pretaxDeductionsCents + deductionCents,
            isHouseholdMode = true
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
