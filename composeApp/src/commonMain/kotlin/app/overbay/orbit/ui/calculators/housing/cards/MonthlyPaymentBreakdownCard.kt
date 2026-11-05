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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.BuyingScenarioResult
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents

@Composable
fun MonthlyPaymentBreakdownCard(
    result: BuyingScenarioResult,
    modifier: Modifier = Modifier
) {
    val total = result.totalMonthlyPaymentCents
    val piRatio = if (total > 0) (result.monthlyPrincipalInterestCents.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val taxRatio = if (total > 0) (result.monthlyPropertyTaxCents.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val insRatio = if (total > 0) (result.monthlyHomeInsuranceCents.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val hoaRatio = if (total > 0) (result.monthlyHoaCents.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val piColor = MaterialTheme.colorScheme.primary
    val taxColor = MaterialTheme.colorScheme.secondary
    val insColor = MaterialTheme.colorScheme.tertiary
    val hoaColor = MaterialTheme.colorScheme.outline

    OrbitSectionCard(
        title = "Monthly Payment Breakdown",
        subtitle = "PITI and recurring monthly homeownership costs",
        modifier = modifier
    ) {
        // Hero Total
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ESTIMATED MONTHLY PAYMENT",
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Loan Amount: ${MoneyUtils.formatCents(result.loanAmountCents)} • Cash to Close: ${MoneyUtils.formatCents(result.cashToCloseCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visual Proportion Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            if (piRatio > 0f) Box(modifier = Modifier.weight(piRatio).height(10.dp).background(piColor))
            if (taxRatio > 0f) Box(modifier = Modifier.weight(taxRatio).height(10.dp).background(taxColor))
            if (insRatio > 0f) Box(modifier = Modifier.weight(insRatio).height(10.dp).background(insColor))
            if (hoaRatio > 0f) Box(modifier = Modifier.weight(hoaRatio).height(10.dp).background(hoaColor))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detailed Rows
        BreakdownRow(
            color = piColor,
            label = "Principal & Interest",
            cents = result.monthlyPrincipalInterestCents,
            percent = (piRatio * 100).toInt()
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        BreakdownRow(
            color = taxColor,
            label = "Property Taxes",
            cents = result.monthlyPropertyTaxCents,
            percent = (taxRatio * 100).toInt()
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        BreakdownRow(
            color = insColor,
            label = "Homeowners Insurance",
            cents = result.monthlyHomeInsuranceCents,
            percent = (insRatio * 100).toInt()
        )

        if (result.monthlyHoaCents > 0L) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            BreakdownRow(
                color = hoaColor,
                label = "HOA Dues",
                cents = result.monthlyHoaCents,
                percent = (hoaRatio * 100).toInt()
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    color: Color,
    label: String,
    cents: Long,
    percent: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($percent%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = MoneyUtils.formatCents(cents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
