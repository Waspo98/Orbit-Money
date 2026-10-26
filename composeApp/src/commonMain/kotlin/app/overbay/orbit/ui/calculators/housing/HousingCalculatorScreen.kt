package app.overbay.orbit.ui.calculators.housing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.calculators.housing.cards.AmortizationSummaryCard
import app.overbay.orbit.ui.calculators.housing.cards.MonthlyPaymentBreakdownCard
import app.overbay.orbit.ui.calculators.housing.cards.MortgageInputCard
import app.overbay.orbit.ui.components.MorphingPageHero

@Composable
fun HousingCalculatorScreen(
    viewModel: HousingViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        MorphingPageHero(
            title = "Housing Calculator",
            subtitle = "Mortgage affordability, PITI payment breakdown & extra principal payoff"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MortgageInputCard(
                uiState = uiState,
                onSelectMortgage = viewModel::selectMortgage,
                onSalePriceChange = viewModel::setSalePrice,
                onRemainingMortgageChange = viewModel::setRemainingMortgage,
                onPurchasePriceChange = viewModel::setPurchasePrice,
                onDownPaymentChange = viewModel::setDownPayment,
                onInterestRateChange = viewModel::setInterestRate,
                onLoanTermChange = viewModel::setLoanTermYears,
                onPropertyTaxChange = viewModel::setAnnualPropertyTaxes,
                onInsuranceChange = viewModel::setAnnualHomeInsurance,
                onHoaChange = viewModel::setMonthlyHoa,
                onUseNetProceeds = viewModel::useNetProceedsAsDownPayment,
                onAgentCommissionChange = viewModel::setAgentCommissionPercent,
                onSellerClosingChange = viewModel::setSellerClosingPercent,
                onBuyerClosingChange = viewModel::setBuyerClosingPercent
            )

            MonthlyPaymentBreakdownCard(
                result = uiState.buyingResult
            )

            AmortizationSummaryCard(
                loanAmountCents = uiState.buyingResult.loanAmountCents,
                totalInterestPaidCents = uiState.totalInterestPaidCents,
                loanTermYears = uiState.loanTermYears,
                extraMonthlyPrincipalCents = uiState.extraMonthlyPrincipalCents,
                onExtraPrincipalChange = viewModel::setExtraMonthlyPrincipal,
                monthsSaved = uiState.monthsSavedWithExtraPrincipal,
                interestSavedCents = uiState.interestSavedWithExtraPrincipalCents,
                amortizationSchedule = uiState.amortizationSchedule
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
