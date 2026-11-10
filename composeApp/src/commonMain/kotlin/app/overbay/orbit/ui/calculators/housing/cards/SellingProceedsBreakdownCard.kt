package app.overbay.orbit.ui.calculators.housing.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.housing.HousingUiState
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun SellingProceedsBreakdownCard(
    uiState: HousingUiState,
    onSalePriceChange: (Cents?) -> Unit,
    onRemainingMortgageChange: (Cents?) -> Unit,
    onAgentCommissionChange: (Double?) -> Unit,
    onSellerClosingChange: (Double?) -> Unit,
    onProratedPropertyTaxChange: (Cents?) -> Unit,
    onMiscRepairsChange: (Cents?) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Selling",
        subtitle = "Estimate equity, sales commission, and net proceeds",
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
                    label = "Estimated Sale Price",
                    valueCents = uiState.salePriceCents,
                    placeholder = "$0",
                    onValueChange = onSalePriceChange,
                    modifier = Modifier.weight(1f)
                )
                HousingMoneyField(
                    label = "Remaining Mortgage Balance",
                    valueCents = uiState.remainingMortgageCents,
                    placeholder = "$0",
                    onValueChange = onRemainingMortgageChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HousingPercentField(
                    label = "Agent Commission (%)",
                    valuePercent = uiState.agentCommissionPercent,
                    placeholder = "5%",
                    onValueChange = onAgentCommissionChange,
                    modifier = Modifier.weight(1f)
                )
                HousingPercentField(
                    label = "Seller Closing Costs (%)",
                    valuePercent = uiState.sellerClosingPercent,
                    placeholder = "2%",
                    onValueChange = onSellerClosingChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HousingMoneyField(
                    label = "Prorated Property Tax",
                    valueCents = uiState.proratedPropertyTaxCents,
                    placeholder = "$2,000",
                    onValueChange = onProratedPropertyTaxChange,
                    modifier = Modifier.weight(1f)
                )
                HousingMoneyField(
                    label = "Miscellaneous Repairs",
                    valueCents = uiState.miscRepairsCents,
                    placeholder = "$1,000",
                    onValueChange = onMiscRepairsChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(2.dp))

            // 4 Selling Result Rows
            HousingResultRow(
                label = "Agent commission",
                value = uiState.agentCommissionCents?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "Seller closing costs",
                value = uiState.sellerClosingCents?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "Total fees",
                value = uiState.totalSellingFeesCents?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "Net proceeds",
                value = uiState.netProceedsCents?.let { MoneyUtils.formatCents(it, showCents = false) },
                isBold = true
            )
        }
    }
}
