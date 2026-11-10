package app.overbay.orbit.ui.calculators.retirement

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.domain.RetirementScenarioModel

data class LinkedRetirementAccountDetail(
    val id: Long,
    val accountId: Long?,
    val name: String,
    val ownerName: String?,
    val planKind: String,
    val institution: String?,
    val balanceCents: Cents,
    val colorIndex: Int = 0
)

data class RetirementUiState(
    val currentAge: Double = 35.0,
    val hasAgeSource: Boolean = false,
    val retirementAge: Double = 67.0,
    val runwayEndAge: Double = 95.0,
    val preset: RetirementScenarioPreset = RetirementScenarioPreset.BALANCED,
    val targetMode: RetirementTargetMode = RetirementTargetMode.WITHDRAWAL_4_PERCENT,
    val currentBalanceCents: Cents = 100_000_00L,
    val connectedBalanceCents: Cents = 100_000_00L,
    val currentBalanceOverridden: Boolean = false,
    val plannedMonthlySavingsCents: Cents = 1000_00L,
    val monthlySavingsCents: Cents = 1000_00L,
    val monthlySavingsOverridden: Boolean = false,
    val annualSpendingCents: Cents = 60_000_00L,
    val baselineSpendingCents: Cents = 60_000_00L,
    val annualSpendingOverridden: Boolean = false,
    val grossIncomeAnnualCents: Cents = 0L,
    val customTargetCents: Cents? = null,
    val legacyAmountCents: Cents = 1_000_000_00L,
    val investmentAccounts: List<Account> = emptyList(),
    val householdMembers: List<HouseholdMember> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
    val linkedAccounts: List<LinkedRetirementAccountDetail> = emptyList(),
    val historyPoints: List<RetirementHistoryPoint> = emptyList(),
    val actualHistoryPoints: List<ProjectionPoint> = emptyList(),
    val selectedHorizon: String = "All",
    val showTargetSettingsSheet: Boolean = false,
    val nonHsaCurrentBalanceCents: Cents = 100_000_00L,
    val nonHsaMonthlySavingsCents: Cents = 1000_00L,
    val hsaCurrentBalanceCents: Cents = 0L,
    val hsaMonthlySavingsCents: Cents = 0L,
    val scenarios: Map<RetirementScenarioPreset, RetirementScenarioModel> = emptyMap(),
    val scenario: RetirementScenarioModel = RetirementCalculatorEngine.calculateScenario(
        currentAge = 35.0,
        retirementAge = 67.0,
        runwayEndAge = 95.0,
        currentBalanceCents = 100_000_00L,
        monthlySavingsCents = 1000_00L,
        annualSpendingCents = 60_000_00L,
        preset = RetirementScenarioPreset.BALANCED,
        targetMode = RetirementTargetMode.WITHDRAWAL_4_PERCENT
    ),
    val scrubbedAge: Double? = null,
    val scrubbedBalanceCents: Cents? = null
) {
    val savingsRatePercent: Double
        get() = if (grossIncomeAnnualCents > 0L) {
            ((monthlySavingsCents.toDouble() * 12.0) / grossIncomeAnnualCents.toDouble()) * 100.0
        } else 0.0

    val targetFundingGapCents: Cents
        get() = kotlin.math.max(0L, scenario.targetNestEggCents - scenario.projectedBalanceCents)

    val surplusOrGapCents: Cents
        get() = scenario.projectedBalanceCents - scenario.targetNestEggCents
}
