package app.overbay.orbit.ui.calculators.housing.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.housing.HousingUiState
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents

@Composable
fun MonthlyPaymentBreakdownCard(
    uiState: HousingUiState,
    modifier: Modifier = Modifier
) {
    val total = uiState.totalMonthlyPaymentCents
    val pi = uiState.monthlyPrincipalInterestCents
    val tax = uiState.monthlyPropertyTaxCents
    val ins = uiState.monthlyHomeInsuranceCents
    val hoa = uiState.monthlyHoaPaymentCents

    val totalLong = total ?: 0L
    val piRatio = if (totalLong > 0 && pi != null) (pi.toFloat() / totalLong.toFloat()).coerceIn(0f, 1f) else 0f
    val taxRatio = if (totalLong > 0 && tax != null) (tax.toFloat() / totalLong.toFloat()).coerceIn(0f, 1f) else 0f
    val insRatio = if (totalLong > 0 && ins != null) (ins.toFloat() / totalLong.toFloat()).coerceIn(0f, 1f) else 0f
    val hoaRatio = if (totalLong > 0 && hoa != null) (hoa.toFloat() / totalLong.toFloat()).coerceIn(0f, 1f) else 0f

    val piColor = MaterialTheme.colorScheme.primary
    val taxColor = MaterialTheme.colorScheme.secondary
    val insColor = MaterialTheme.colorScheme.tertiary
    val hoaColor = MaterialTheme.colorScheme.outline

    OrbitSectionCard(
        title = "Monthly Payment",
        subtitle = "Principal, interest, taxes, insurance, and HOA dues",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (total != null && total > 0L) {
                // Hero Total
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PROJECTED MONTHLY PAYMENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RollingTickerCents(
                            cents = total,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Visual Proportion Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    if (piRatio > 0f) Box(modifier = Modifier.weight(piRatio).height(8.dp).background(piColor))
                    if (taxRatio > 0f) Box(modifier = Modifier.weight(taxRatio).height(8.dp).background(taxColor))
                    if (insRatio > 0f) Box(modifier = Modifier.weight(insRatio).height(8.dp).background(insColor))
                    if (hoaRatio > 0f) Box(modifier = Modifier.weight(hoaRatio).height(8.dp).background(hoaColor))
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // The 5 Monthly Payment Rows
            HousingResultRow(
                label = "Principal & Interest",
                value = pi?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "Property Taxes",
                value = tax?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "Home Insurance",
                value = ins?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HousingResultRow(
                label = "HOA Fees",
                value = hoa?.let { MoneyUtils.formatCents(it, showCents = false) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            HousingResultRow(
                label = "Projected Monthly Payment",
                value = total?.let { MoneyUtils.formatCents(it, showCents = false) },
                isBold = true
            )
        }
    }
}
