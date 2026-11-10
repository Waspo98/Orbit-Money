package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MhaTaxCalculationResult
import app.overbay.orbit.domain.MhaTaxEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStatTile

@Composable
fun MhaTaxSavingsCard(
    taxResult: MhaTaxCalculationResult?,
    eligibleExpensesCents: Cents,
    eligibleTransactionCount: Int,
    selectedYear: Int,
    taxProfile: MhaTaxProfile,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHousehold = taxProfile.mode.equals("household", ignoreCase = true)
    val totalSavings = taxResult?.totalSavingsCents ?: 0L
    val fedSavings = taxResult?.federalSavingsCents ?: 0L
    val stateSavings = taxResult?.stateSavingsCents ?: 0L

    val effectiveRatePercent = taxResult?.let {
        ((it.effectiveSavingsRate * 10000).toLong() / 100.0)
    } ?: (taxProfile.simpleFederalRatePercent + taxProfile.simpleStateRatePercent)

    val fedRatePercent = taxResult?.let {
        ((it.federalMarginalRate * 10000).toLong() / 100.0)
    } ?: taxProfile.simpleFederalRatePercent

    val stateRatePercent = taxResult?.let {
        ((it.stateRate * 10000).toLong() / 100.0)
    } ?: taxProfile.simpleStateRatePercent

    val taxableIncomeBeforeMha = taxResult?.taxableIncomeBeforeMhaCents ?: 0L
    val grossIncome = taxResult?.grossIncomeCents ?: 0L
    val reduction = taxResult?.reductionCents ?: 0L

    val showGraduatedWarning = isHousehold &&
        taxProfile.stateRateOverridePercent == null &&
        MhaTaxEngine.isGraduatedState(taxProfile.state)

    OrbitSectionCard(
        title = "MHA Dashboard",
        subtitle = "Calculate Projected MHA Tax Savings",
        action = {
            FilledTonalButton(
                onClick = onEditSettings,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (isHousehold) "Edit Mode (Advanced)" else "Edit Mode (Simple)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Tile 1 (Eligible Total) & Tile 2 (Estimated Tax Savings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrbitStatTile(
                    label = "MHA Eligible Total",
                    value = MoneyUtils.formatCents(eligibleExpensesCents),
                    subtitle = "$eligibleTransactionCount eligible transactions in $selectedYear",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Estimated Tax Savings",
                    value = MoneyUtils.formatCents(totalSavings),
                    subtitle = "$effectiveRatePercent% effective rate",
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Tile 3 (Taxable Income) & Tile 4 (Rate Breakdown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrbitStatTile(
                    label = "Taxable Income",
                    value = if (isHousehold) MoneyUtils.formatCents(taxableIncomeBeforeMha) else "Simple Mode",
                    subtitle = if (isHousehold) {
                        "${MoneyUtils.formatCents(grossIncome)} - ${MoneyUtils.formatCents(reduction)} (Pretax + Deduction)"
                    } else {
                        "Using manual federal + state rates"
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Rate Breakdown",
                    value = "Fed $fedRatePercent% · State $stateRatePercent%",
                    subtitle = "Federal $fedRatePercent% (${MoneyUtils.formatCents(fedSavings)}) · State $stateRatePercent% (${MoneyUtils.formatCents(stateSavings)})",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
            }

            // Graduated State Warning Notice
            if (showGraduatedWarning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "State savings are not auto-estimated for graduated-rate states yet. Enter a State Rate Override in Tax Settings if applicable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MhaTaxSavingsCard(
    taxResult: MhaTaxCalculationResult?,
    projectedAnnualSavingsCents: Cents,
    taxProfile: MhaTaxProfile,
    modifier: Modifier = Modifier
) = MhaTaxSavingsCard(
    taxResult = taxResult,
    eligibleExpensesCents = taxResult?.eligibleTotalCents ?: 0L,
    eligibleTransactionCount = 0,
    selectedYear = 2026,
    taxProfile = taxProfile,
    onEditSettings = {},
    modifier = modifier
)
