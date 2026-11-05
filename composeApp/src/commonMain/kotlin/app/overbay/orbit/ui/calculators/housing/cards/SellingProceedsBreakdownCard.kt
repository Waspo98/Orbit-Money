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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.SellingProceedsResult
import app.overbay.orbit.domain.MoneyUtils

@Composable
fun SellingProceedsBreakdownCard(
    proceeds: SellingProceedsResult,
    onUseNetProceeds: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Estimated Net Proceeds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = MoneyUtils.formatCents(proceeds.netProceedsCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            ProceedsLineItem("Sale Price", MoneyUtils.formatCents(proceeds.salePriceCents))
            ProceedsLineItem("Agent Commission", "-${MoneyUtils.formatCents(proceeds.agentCommissionCents)}")
            ProceedsLineItem("Seller Closing Costs", "-${MoneyUtils.formatCents(proceeds.sellerClosingCents)}")
            ProceedsLineItem("Prorated Tax & Repairs", "-${MoneyUtils.formatCents(proceeds.proratedPropertyTaxCents + proceeds.miscRepairsCents)}")
            ProceedsLineItem("Total Selling Fees", "-${MoneyUtils.formatCents(proceeds.totalSellingFeesCents)}", isBold = true)
            ProceedsLineItem("Remaining Mortgage Payoff", "-${MoneyUtils.formatCents(proceeds.remainingMortgageCents)}")

            if (proceeds.netProceedsCents > 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onUseNetProceeds,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Net Proceeds as Down Payment", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ProceedsLineItem(
    label: String,
    value: String,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
