package app.overbay.orbit.ui.calculators.retirement

import androidx.compose.runtime.Immutable
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.domain.RetirementScenarioModel

enum class RetirementWorkspaceTab(val label: String) {
    WHAT_IF("What-If"),
    RUNWAY("Runway"),
    ACCOUNTS("Accounts")
}

enum class RetirementAmountField(
    val title: String,
    val subtitle: String,
    val unitSuffix: String
) {
    MONTHLY_SAVINGS(
        title = "Monthly Savings Pace",
        subtitle = "Combined monthly retirement contributions across accounts",
        unitSuffix = "/ mo"
    ),
    ANNUAL_SPENDING(
        title = "Annual Retirement Spending",
        subtitle = "Target annual retirement budget in today's purchasing power",
        unitSuffix = "/ yr"
    ),
    STARTING_BALANCE(
        title = "Starting Portfolio Override",
        subtitle = "Enter an override or reset to track connected accounts",
        unitSuffix = ""
    )
}

data class LinkedAccountChip(
    val id: Long,
    val displayKind: String,
    val name: String
)

data class MemberRetirementSummary(
    val memberId: Long,
    val name: String,
    val employer: String?,
    val payFrequencyLabel: String,
    val perPaycheckCents: Cents,
    val annualEmployeeSavingsCents: Cents,
    val annualEmployerMatchCents: Cents,
    val matchCapReached: Boolean,
    val annualHsaCents: Cents,
    val annualTotalCents: Cents,
    val linkedAccounts: List<LinkedAccountChip>
)

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
    val accountById: Map<Long, Account> = emptyMap(),
    val linkedAccounts: List<LinkedRetirementAccountDetail> = emptyList(),
    val memberSummaries: List<MemberRetirementSummary> = emptyList(),
    val historyPoints: List<RetirementHistoryPoint> = emptyList(),
    val filteredHistoryPoints: List<RetirementHistoryPoint> = emptyList(),
    val actualHistoryPoints: List<ProjectionPoint> = emptyList(),
    val selectedHorizon: String = "All",
    val selectedTab: RetirementWorkspaceTab = RetirementWorkspaceTab.WHAT_IF,
    val showAssumptionsSheet: Boolean = false,
    val activeAmountField: RetirementAmountField? = null,
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
    )
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

@Immutable
data class RetirementActions(
    val onSelectTab: (RetirementWorkspaceTab) -> Unit,
    val onSelectPreset: (RetirementScenarioPreset) -> Unit,
    val onSetRetirementAge: (Double) -> Unit,
    val onSetRunwayEndAge: (Double) -> Unit,
    val onSetTargetMode: (RetirementTargetMode) -> Unit,
    val onSetMonthlySavings: (Cents) -> Unit,
    val onResetMonthlySavings: () -> Unit,
    val onSetAnnualSpending: (Cents) -> Unit,
    val onApplySpendingFraction: (Double) -> Unit,
    val onResetAnnualSpending: () -> Unit,
    val onSetCurrentBalance: (Cents) -> Unit,
    val onResetCurrentBalance: () -> Unit,
    val onSetCustomTarget: (Cents?) -> Unit,
    val onSetLegacyAmount: (Cents) -> Unit,
    val onSelectHorizon: (String) -> Unit,
    val onOpenAssumptionsSheet: () -> Unit,
    val onDismissAssumptionsSheet: () -> Unit,
    val onOpenAmountEditor: (RetirementAmountField) -> Unit,
    val onDismissAmountEditor: () -> Unit,
    val onCommitAmountEditor: (RetirementAmountField, Cents) -> Unit
)
