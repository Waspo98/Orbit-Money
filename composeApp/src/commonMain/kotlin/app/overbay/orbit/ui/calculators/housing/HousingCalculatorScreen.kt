package app.overbay.orbit.ui.calculators.housing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import app.overbay.orbit.ui.calculators.housing.cards.AmortizationSummaryCard
import app.overbay.orbit.ui.calculators.housing.cards.MonthlyPaymentBreakdownCard
import app.overbay.orbit.ui.calculators.housing.cards.MortgageInputCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun HousingCalculatorScreen(
    viewModel: HousingViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            MorphingPageHero(
                kicker = "HOME PLANNING",
                title = "Housing Calculator",
                subtitle = "Estimate selling proceeds, cash to close, and projected monthly payment.",
                canNavigateBack = true,
                onBack = onBack,
                scrollState = heroScrollState
            )

            if (isTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Mortgage Inputs
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
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
                    }

                    // Right Column: Monthly Payment Breakdown + Amortization Summary
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                    }
                }
            } else {
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
    }
}
