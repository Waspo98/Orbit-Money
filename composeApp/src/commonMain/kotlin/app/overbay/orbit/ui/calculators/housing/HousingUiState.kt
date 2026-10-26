package app.overbay.orbit.ui.calculators.housing

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AmortizationMonth
import app.overbay.orbit.data.model.BuyingScenarioResult
import app.overbay.orbit.data.model.SellingProceedsResult
import app.overbay.orbit.domain.Cents

data class HousingUiState(
    val selectedMortgageAccountId: Long? = null,
    val availableMortgageAccounts: List<Account> = emptyList(),
    // Selling inputs
    val salePriceCents: Cents? = null,
    val remainingMortgageCents: Cents? = null,
    val agentCommissionPercent: Double = 5.0,
    val sellerClosingPercent: Double = 2.0,
    val proratedPropertyTaxCents: Cents = 2000_00L,
    val miscRepairsCents: Cents = 1000_00L,
    // Selling result
    val sellingProceeds: SellingProceedsResult? = null,
    // Buying inputs
    val purchasePriceCents: Cents = 375_000_00L,
    val downPaymentCents: Cents = 75_000_00L,
    val interestRatePercent: Double = 6.0,
    val loanTermYears: Int = 30,
    val annualPropertyTaxesCents: Cents = 8_000_00L,
    val annualHomeInsuranceCents: Cents = 1_500_00L,
    val monthlyHoaCents: Cents = 23_00L,
    val buyerClosingPercent: Double = 3.0,
    // Buying result
    val buyingResult: BuyingScenarioResult = BuyingScenarioResult(
        purchasePriceCents = 375_000_00L,
        downPaymentCents = 75_000_00L,
        loanAmountCents = 300_000_00L,
        buyerClosingCostsCents = 11_250_00L,
        cashToCloseCents = 86_250_00L,
        monthlyPrincipalInterestCents = 1798_65L,
        monthlyPropertyTaxCents = 666_66L,
        monthlyHomeInsuranceCents = 125_00L,
        monthlyHoaCents = 23_00L,
        totalMonthlyPaymentCents = 2613_31L
    ),
    // Amortization & extra principal
    val extraMonthlyPrincipalCents: Cents = 0L,
    val amortizationSchedule: List<AmortizationMonth> = emptyList(),
    val totalInterestPaidCents: Cents = 0L,
    val monthsSavedWithExtraPrincipal: Int = 0,
    val interestSavedWithExtraPrincipalCents: Cents = 0L
)
