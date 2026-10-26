package app.overbay.orbit.ui.calculators.housing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.housing.cards.AmortizationSummaryCard
import app.overbay.orbit.ui.calculators.housing.cards.MonthlyPaymentBreakdownCard
import app.overbay.orbit.ui.calculators.housing.cards.MortgageInputCard
import app.overbay.orbit.ui.components.HeroStatItem
import app.overbay.orbit.ui.components.HeroStatTone
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.rememberHeroScrollState

@Composable
fun HousingCalculatorScreen(
    viewModel: HousingViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState(maxHeight = 220.dp, minHeight = 64.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(heroScrollState.nestedScrollConnection)
    ) {
        // Edge-to-Edge Curved Green Canopy Hero
        MorphingPageHero(
            kicker = "HOME PLANNING",
            title = "Housing Calculator",
            subtitle = "Estimate selling proceeds, cash to close, and projected monthly payment.",
            canNavigateBack = true,
            onBack = { onNavigate(OrbitNavKey.Dashboard) },
            scrollState = heroScrollState,
            stats = listOf(
                HeroStatItem("MONTHLY PITI", MoneyUtils.formatCents(uiState.buyingResult.totalMonthlyPaymentCents), HeroStatTone.GOOD),
                HeroStatItem("LOAN AMOUNT", MoneyUtils.formatCents(uiState.buyingResult.loanAmountCents), HeroStatTone.DEFAULT),
                HeroStatItem("DOWN PAYMENT", MoneyUtils.formatCents(uiState.downPaymentCents), HeroStatTone.GOOD),
                HeroStatItem("INTEREST RATE", "${uiState.interestRatePercent}%", HeroStatTone.DEFAULT)
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
            }

            item {
                MonthlyPaymentBreakdownCard(
                    result = uiState.buyingResult
                )
            }

            item {
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
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
