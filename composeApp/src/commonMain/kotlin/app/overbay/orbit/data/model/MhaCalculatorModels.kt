package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable

@Serializable
enum class FilingStatus(val storageKey: String, val displayName: String) {
    SINGLE("single", "Single"),
    MARRIED_JOINT("married_joint", "Married Filing Jointly"),
    MARRIED_SEPARATE("married_separate", "Married Filing Separately"),
    HEAD_OF_HOUSEHOLD("head_of_household", "Head of Household");

    companion object {
        fun fromStorage(key: String?): FilingStatus =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: MARRIED_JOINT
    }
}

@Serializable
enum class MhaTaxMode {
    SIMPLE,
    HOUSEHOLD
}

@Serializable
enum class MhaDeductionMode {
    STANDARD,
    ITEMIZED
}

@Serializable
data class MhaTaxProfile(
    val mode: String = "simple", // simple | household
    val simpleFederalRatePercent: Double = 22.0,
    val simpleStateRatePercent: Double = 4.95,
    val filingStatus: FilingStatus = FilingStatus.MARRIED_JOINT,
    val state: String = "IL",
    val deductionMode: String = "standard", // standard | itemized
    val itemizedDeductionCents: Cents = 0L,
    val taxableIncomeOverrideCents: Cents? = null,
    val stateRateOverridePercent: Double? = null,
    val designatedCapCents: Cents? = null,
    val fairMarketRentalValueCents: Cents? = null
) {
    val simpleFederalRate: Double get() = simpleFederalRatePercent
    val simpleStateRate: Double get() = simpleStateRatePercent
    val stateRateOverride: Double? get() = stateRateOverridePercent
}

@Serializable
data class HouseholdTaxSnapshot(
    val grossIncomeCents: Cents,
    val pretaxContributionsCents: Cents,
    val deductionCents: Cents,
    val standardDeductionCents: Cents,
    val estimatedTaxableIncomeCents: Cents,
    val taxableIncomeCents: Cents,
    val taxableIncomeOverridden: Boolean
)

@Serializable
data class MhaTaxEstimate(
    val mode: MhaTaxMode = MhaTaxMode.SIMPLE,
    val taxYear: Int = 2026,
    val eligibleTotalCents: Cents = 0L,
    val savingsCents: Cents = 0L,
    val effectiveRate: Double = 0.0,
    val federalRate: Double = 0.0,
    val federalSavingsCents: Cents = 0L,
    val stateRate: Double = 0.0,
    val stateSavingsCents: Cents = 0L,
    val stateRateSource: String = "",
    val stateRateConfidence: String = "",
    val stateRateNote: String = "",
    val household: HouseholdTaxSnapshot? = null,
    val assumptions: List<String> = emptyList()
)

@Serializable
data class MhaAccountItem(
    val id: Long,
    val name: String,
    val type: AccountType,
    val institution: String?,
    val accountNumberLast4: String?,
    val currentBalanceCents: Cents,
    val mhaDefaultEligible: Boolean
)

@Serializable
data class MhaSummary(
    val enabled: Boolean = true,
    val year: Int = 2026,
    val availableYears: List<Int> = emptyList(),
    val transactionCount: Int = 0,
    val transactionTotalCents: Cents = 0L,
    val savingsCents: Cents = 0L,
    val savingsRate: Double = 0.27,
    val taxProfile: MhaTaxProfile = MhaTaxProfile(),
    val taxEstimate: MhaTaxEstimate? = null,
    val accounts: List<MhaAccountItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList()
)

@Serializable
data class HousingScenario(
    val selectedMortgageAccountId: Long? = null,
    val salePriceCents: Cents? = null,
    val remainingMortgageCents: Cents? = null,
    val agentCommissionPercent: Double = 5.0,
    val sellerClosingPercent: Double = 2.0,
    val proratedPropertyTaxCents: Cents = 200_000L,
    val miscRepairsCents: Cents = 100_000L,
    val purchasePriceCents: Cents = 37_500_000L,
    val downPaymentCents: Cents = 7_500_000L,
    val interestRatePercent: Double = 6.0,
    val loanTermYears: Int = 30,
    val annualPropertyTaxesCents: Cents = 800_000L,
    val annualHomeInsuranceCents: Cents = 150_000L,
    val monthlyHoaCents: Cents = 2_300L,
    val buyerClosingPercent: Double = 3.0
)

@Serializable
data class SellingProceedsResult(
    val salePriceCents: Cents,
    val agentCommissionCents: Cents,
    val sellerClosingCents: Cents,
    val totalSellingFeesCents: Cents,
    val remainingMortgageCents: Cents,
    val netProceedsCents: Cents
)

@Serializable
data class BuyingScenarioResult(
    val purchasePriceCents: Cents,
    val downPaymentCents: Cents,
    val loanAmountCents: Cents,
    val buyerClosingCostsCents: Cents,
    val cashToCloseCents: Cents,
    val monthlyPrincipalInterestCents: Cents,
    val monthlyPropertyTaxCents: Cents,
    val monthlyHomeInsuranceCents: Cents,
    val monthlyHoaCents: Cents,
    val totalMonthlyPaymentCents: Cents
)

@Serializable
data class AmortizationMonth(
    val monthNumber: Int,
    val paymentCents: Cents,
    val principalPaidCents: Cents,
    val interestPaidCents: Cents,
    val remainingBalanceCents: Cents
)

@Serializable
enum class RetirementTargetMode {
    WITHDRAWAL_4_PERCENT,
    NEST_EGG,
    LEAVE_LEGACY,
    CUSTOM
}

typealias TargetNestEggMode = RetirementTargetMode

@Serializable
enum class RetirementScenarioPreset(
    val label: String,
    val nominalAnnualReturn: Double,
    val inflationRate: Double
) {
    CONSERVATIVE("Conservative", 0.05, 0.03),
    BALANCED("Balanced", 0.07, 0.025),
    AGGRESSIVE("Aggressive", 0.085, 0.0225)
}

typealias RetirementPreset = RetirementScenarioPreset

@Serializable
data class RetirementScenario(
    val retirementAge: Int = 67,
    val runwayEndAge: Int = 95,
    val preset: RetirementScenarioPreset = RetirementScenarioPreset.BALANCED,
    val annualReturnPercent: Double = 7.0,
    val inflationPercent: Double = 2.5,
    val targetMode: RetirementTargetMode = RetirementTargetMode.WITHDRAWAL_4_PERCENT,
    val legacyAmountCents: Cents = 100_000_000L,
    val customTargetAmountCents: Cents? = null,
    val monthlySavingsCents: Cents = 0L
)

@Serializable
data class RetirementAccountInput(
    val accountId: Long,
    val accountName: String,
    val accountKind: String,
    val currentBalanceCents: Cents,
    val monthlyContributionCents: Cents
)

@Serializable
data class RetirementProjectionPoint(
    val age: Int,
    val year: Int,
    val portfolioBalanceCents: Cents,
    val annualContributionCents: Cents,
    val growthCents: Cents,
    val withdrawalCents: Cents
)

@Serializable
data class RetirementProjectionResult(
    val targetNestEggCents: Cents,
    val projectedNestEggAtRetirementCents: Cents,
    val monthlySavingsNeededCents: Cents,
    val isFunded: Boolean,
    val points: List<RetirementProjectionPoint> = emptyList()
)
