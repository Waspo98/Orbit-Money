package app.overbay.orbit.ui.calculators.retirement

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.domain.RetirementScenarioModel

data class RetirementUiState(
    val currentAge: Double = 35.0,
    val retirementAge: Double = 67.0,
    val runwayEndAge: Double = 95.0,
    val preset: RetirementScenarioPreset = RetirementScenarioPreset.BALANCED,
    val targetMode: RetirementTargetMode = RetirementTargetMode.WITHDRAWAL_4_PERCENT,
    val currentBalanceCents: Cents = 100_000_00L,
    val monthlySavingsCents: Cents = 1000_00L,
    val annualSpendingCents: Cents = 60_000_00L,
    val customTargetCents: Cents? = null,
    val legacyAmountCents: Cents = 1_000_000_00L,
    val useConnectedAccounts: Boolean = true,
    val investmentAccounts: List<Account> = emptyList(),
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
)
