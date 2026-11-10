package app.overbay.orbit.ui.calculators.housing

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.Cents

data class HousingUiState(
    val selectedMortgageAccountId: Long? = null,
    val availableMortgageAccounts: List<Account> = emptyList(),
    // Selling inputs (blank initial state, showing assumptions as placeholders)
    val salePriceCents: Cents? = null,
    val remainingMortgageCents: Cents? = null,
    val agentCommissionPercent: Double? = null,
    val sellerClosingPercent: Double? = null,
    val proratedPropertyTaxCents: Cents? = null,
    val miscRepairsCents: Cents? = null,
    // Selling results (calculated when inputs are present)
    val agentCommissionCents: Cents? = null,
    val sellerClosingCents: Cents? = null,
    val totalSellingFeesCents: Cents? = null,
    val netProceedsCents: Cents? = null,
    // Buying inputs (blank initial state, showing assumptions as placeholders)
    val purchasePriceCents: Cents? = null,
    val downPaymentCents: Cents? = null,
    val interestRatePercent: Double? = 6.0,
    val loanTermYears: Int? = 30,
    val annualPropertyTaxesCents: Cents? = null,
    val annualHomeInsuranceCents: Cents? = null,
    val monthlyHoaCents: Cents? = null,
    val buyerClosingPercent: Double? = null,
    // Buying results (calculated when inputs are present)
    val buyerClosingCostsCents: Cents? = null,
    val loanAmountCents: Cents? = null,
    val cashToCloseCents: Cents? = null,
    // Monthly payment results (calculated when inputs are present)
    val monthlyPrincipalInterestCents: Cents? = null,
    val monthlyPropertyTaxCents: Cents? = null,
    val monthlyHomeInsuranceCents: Cents? = null,
    val monthlyHoaPaymentCents: Cents? = null,
    val totalMonthlyPaymentCents: Cents? = null,
    // Dialog state
    val showResetConfirmation: Boolean = false
)
