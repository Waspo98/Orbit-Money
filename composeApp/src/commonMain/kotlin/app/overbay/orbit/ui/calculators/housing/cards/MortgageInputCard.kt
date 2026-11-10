package app.overbay.orbit.ui.calculators.housing.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.housing.HousingUiState
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun MortgagePrefillBar(
    selectedAccountId: Long?,
    availableAccounts: List<Account>,
    onSelectMortgage: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedAccount = availableAccounts.find { it.id == selectedAccountId }

    OrbitSectionCard(
        title = "Mortgage",
        subtitle = "Prefill selling parameters from an active property account",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitDropdownField(
                label = "Select mortgage",
                selectedText = selectedAccount?.let { "${it.name}${it.institution?.let { inst -> " - $inst" } ?: ""}" }
                    ?: "Manual Estimate",
                options = availableAccounts,
                optionLabel = { "${it.name}${it.institution?.let { inst -> " - $inst" } ?: ""}" },
                onOptionSelected = { onSelectMortgage(it.id) },
                nullOptionLabel = "Manual Estimate",
                onNullSelected = { onSelectMortgage(null) }
            )

            val helperText = if (availableAccounts.isEmpty()) {
                "Mark an account as Mortgage and add Estimated Value to prefill this."
            } else {
                "Selection fills sale price from Estimated Value and payoff from balance."
            }

            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BuyingCard(
    uiState: HousingUiState,
    onPurchasePriceChange: (Cents?) -> Unit,
    onDownPaymentChange: (Cents?) -> Unit,
    onInterestRateChange: (Double?) -> Unit,
    onLoanTermChange: (Int?) -> Unit,
    onPropertyTaxChange: (Cents?) -> Unit,
    onInsuranceChange: (Cents?) -> Unit,
    onHoaChange: (Cents?) -> Unit,
    onBuyerClosingChange: (Double?) -> Unit,
    onUseNetProceeds: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canUseProceeds = uiState.netProceedsCents != null && uiState.netProceedsCents > 0L

    OrbitSectionCard(
        title = "Buying",
        subtitle = "Purchase financing, downpayment, and required cash to close",
        action = {
            Button(
                onClick = onUseNetProceeds,
                enabled = canUseProceeds,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = "Use Net Proceeds",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HousingMoneyField(
                    label = "Purchase Price",
                    valueCents = uiState.purchasePriceCents,
                    placeholder = "$375,000",
                    onValueChange = onPurchasePriceChange,
                    modifier = Modifier.weight(1f)
                )
                HousingMoneyField(
                    label = "Downpayment",
                    valueCents = uiState.downPaymentCents,
                    placeholder = "$0",
                    onValueChange = onDownPaymentChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HousingPercentField(
                    label = "Interest Rate (%)",
                    valuePercent = uiState.interestRatePercent,
                    placeholder = "6%",
                    onValueChange = onInterestRateChange,
                    modifier = Modifier.weight(1f)
                )
                HousingLoanTermControl(
                    value = uiState.loanTermYears,
                    onChange = onLoanTermChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HousingMoneyField(
                    label = "Property Taxes (Annual)",
                    valueCents = uiState.annualPropertyTaxesCents,
                    placeholder = "$8,000",
                    onValueChange = onPropertyTaxChange,
                    modifier = Modifier.weight(1f)
                )
                HousingMoneyField(
                    label = "Home Insurance (Annual)",
                    valueCents = uiState.annualHomeInsuranceCents,
                    placeholder = "$1,500",
                    onValueChange = onInsuranceChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HousingMoneyField(
                    label = "HOA Fees (Monthly)",
                    valueCents = uiState.monthlyHoaCents,
                    placeholder = "$23",
                    onValueChange = onHoaChange,
                    modifier = Modifier.weight(1f)
                )
                HousingPercentField(
                    label = "Buyer Closing Costs (%)",
                    valuePercent = uiState.buyerClosingPercent,
                    placeholder = "3%",
                    onValueChange = onBuyerClosingChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(2.dp))

            // 3 Buying Result Rows
            HousingResultRow(
                label = "Buyer Closing Costs",
                value = uiState.buyerClosingCostsCents?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "Loan Amount",
                subtitle = "(Purchase Price - Downpayment)",
                value = uiState.loanAmountCents?.let { MoneyUtils.formatCents(it, showCents = false) },
                isBold = true
            )
            HousingResultRow(
                label = "Cash to Close",
                subtitle = "(Downpayment + Buyer Closing Costs)",
                value = uiState.cashToCloseCents?.let { MoneyUtils.formatCents(it, showCents = false) },
                isBold = true
            )
        }
    }
}
