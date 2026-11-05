package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MhaTaxCalculationResult
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents

@Composable
fun MhaTaxSavingsCard(
    taxResult: MhaTaxCalculationResult?,
    projectedAnnualSavingsCents: Cents,
    taxProfile: MhaTaxProfile,
    modifier: Modifier = Modifier
) {
    val totalSavings = taxResult?.totalSavingsCents ?: 0L
    val fedSavings = taxResult?.federalSavingsCents ?: 0L
    val stateSavings = taxResult?.stateSavingsCents ?: 0L
    val effectiveRatePercent = ((taxResult?.effectiveSavingsRate ?: 0.0) * 100).let {
        val rounded = (it * 100).toLong() / 100.0
        "$rounded"
    }
    val secaEstimate = taxResult?.secaTaxEstimateCents ?: 0L

    OrbitSectionCard(
        title = "2026 Tax Savings Estimation",
        subtitle = "Income tax savings based on ${taxProfile.mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} mode",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // Main Tax Savings Figure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "TOTAL ESTIMATED SAVINGS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    RollingTickerCents(
                        cents = totalSavings,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$effectiveRatePercent% Rate",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-metrics Grid (Federal + State + Annualized)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val fedRate = ((taxResult?.federalMarginalRate ?: 0.0) * 100).toInt()
                val stRate = (((taxResult?.stateRate ?: 0.0) * 10000).toLong() / 100.0)

                TaxBreakdownColumn(
                    label = "Federal Savings",
                    amountCents = fedSavings,
                    subtitle = "Marginal rate: $fedRate%",
                    modifier = Modifier.weight(1f)
                )
                TaxBreakdownColumn(
                    label = "State Savings (${taxProfile.state})",
                    amountCents = stateSavings,
                    subtitle = "State rate: $stRate%",
                    modifier = Modifier.weight(1f)
                )
                TaxBreakdownColumn(
                    label = "Projected Full Year",
                    amountCents = projectedAnnualSavingsCents,
                    subtitle = "Extrapolated to Dec 31",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // SECA Clarification Note (IRC § 1402(a)(8))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SECA (Self-Employment Tax) Notice",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Housing allowance is exempt from income tax, but subject to 15.3% SECA (est. ${MoneyUtils.formatCents(secaEstimate)}) unless you hold an approved IRS Form 4361 clergy exemption.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxBreakdownColumn(
    label: String,
    amountCents: Cents,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(2.dp))
            Text(MoneyUtils.formatCents(amountCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
