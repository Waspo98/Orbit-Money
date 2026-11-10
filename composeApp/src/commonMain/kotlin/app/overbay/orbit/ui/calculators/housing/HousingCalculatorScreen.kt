package app.overbay.orbit.ui.calculators.housing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.housing.cards.BuyingCard
import app.overbay.orbit.ui.calculators.housing.cards.MonthlyPaymentBreakdownCard
import app.overbay.orbit.ui.calculators.housing.cards.MortgagePrefillBar
import app.overbay.orbit.ui.calculators.housing.cards.SellingProceedsBreakdownCard
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun HousingCalculatorScreen(
    viewModel: HousingViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
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
                scrollState = heroScrollState,
                canopyBar = { _, isCompact, barHeight ->
                    val monthlyStr = uiState.totalMonthlyPaymentCents?.let {
                        MoneyUtils.formatCents(it, showCents = false)
                    } ?: "—"
                    val closeStr = uiState.cashToCloseCents?.let {
                        MoneyUtils.formatCents(it, showCents = false)
                    } ?: "—"

                    CanopySurfacePill(
                        expandedText = "Est. $monthlyStr/mo · $closeStr Cash to Close",
                        compactText = "$monthlyStr/mo Est.",
                        isCompact = isCompact,
                        leadingIcon = OrbitIcons.HousingCalculator,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CanopyIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Reset Calculator",
                        onClick = { viewModel.requestReset() },
                        buttonSize = barHeight
                    )

                    CanopySettingsButton(
                        buttonSize = barHeight,
                        onClick = { onNavigate(OrbitNavKey.Settings) }
                    )
                }
            )

            if (isTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Prefill Bar + Selling Card
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            MortgagePrefillBar(
                                selectedAccountId = uiState.selectedMortgageAccountId,
                                availableAccounts = uiState.availableMortgageAccounts,
                                onSelectMortgage = viewModel::selectMortgage
                            )
                        }

                        item {
                            SellingProceedsBreakdownCard(
                                uiState = uiState,
                                onSalePriceChange = viewModel::setSalePrice,
                                onRemainingMortgageChange = viewModel::setRemainingMortgage,
                                onAgentCommissionChange = viewModel::setAgentCommissionPercent,
                                onSellerClosingChange = viewModel::setSellerClosingPercent,
                                onProratedPropertyTaxChange = viewModel::setProratedPropertyTax,
                                onMiscRepairsChange = viewModel::setMiscRepairs
                            )
                        }
                    }

                    // Right Column: Buying Card + Monthly Payment Card + Reset Button
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            BuyingCard(
                                uiState = uiState,
                                onPurchasePriceChange = viewModel::setPurchasePrice,
                                onDownPaymentChange = viewModel::setDownPayment,
                                onInterestRateChange = viewModel::setInterestRate,
                                onLoanTermChange = viewModel::setLoanTermYears,
                                onPropertyTaxChange = viewModel::setAnnualPropertyTaxes,
                                onInsuranceChange = viewModel::setAnnualHomeInsurance,
                                onHoaChange = viewModel::setMonthlyHoa,
                                onBuyerClosingChange = viewModel::setBuyerClosingPercent,
                                onUseNetProceeds = viewModel::useNetProceedsAsDownPayment
                            )
                        }

                        item {
                            MonthlyPaymentBreakdownCard(uiState = uiState)
                        }

                        item {
                            Button(
                                onClick = viewModel::requestReset,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Reset", fontWeight = FontWeight.Bold)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
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
                        MortgagePrefillBar(
                            selectedAccountId = uiState.selectedMortgageAccountId,
                            availableAccounts = uiState.availableMortgageAccounts,
                            onSelectMortgage = viewModel::selectMortgage
                        )
                    }

                    item {
                        SellingProceedsBreakdownCard(
                            uiState = uiState,
                            onSalePriceChange = viewModel::setSalePrice,
                            onRemainingMortgageChange = viewModel::setRemainingMortgage,
                            onAgentCommissionChange = viewModel::setAgentCommissionPercent,
                            onSellerClosingChange = viewModel::setSellerClosingPercent,
                            onProratedPropertyTaxChange = viewModel::setProratedPropertyTax,
                            onMiscRepairsChange = viewModel::setMiscRepairs
                        )
                    }

                    item {
                        BuyingCard(
                            uiState = uiState,
                            onPurchasePriceChange = viewModel::setPurchasePrice,
                            onDownPaymentChange = viewModel::setDownPayment,
                            onInterestRateChange = viewModel::setInterestRate,
                            onLoanTermChange = viewModel::setLoanTermYears,
                            onPropertyTaxChange = viewModel::setAnnualPropertyTaxes,
                            onInsuranceChange = viewModel::setAnnualHomeInsurance,
                            onHoaChange = viewModel::setMonthlyHoa,
                            onBuyerClosingChange = viewModel::setBuyerClosingPercent,
                            onUseNetProceeds = viewModel::useNetProceedsAsDownPayment
                        )
                    }

                    item {
                        MonthlyPaymentBreakdownCard(uiState = uiState)
                    }

                    item {
                        Button(
                            onClick = viewModel::requestReset,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Reset", fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        if (uiState.showResetConfirmation) {
            OrbitConfirmationDialog(
                title = "Reset housing calculator",
                message = "Reset all fields to default blank assumptions?",
                confirmText = "Reset",
                dismissText = "Cancel",
                isDestructive = true,
                onConfirm = viewModel::confirmReset,
                onDismiss = viewModel::cancelReset
            )
        }
    }
}
