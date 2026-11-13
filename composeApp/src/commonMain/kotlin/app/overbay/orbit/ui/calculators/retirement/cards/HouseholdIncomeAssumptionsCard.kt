package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdIncomeAssumptionsCard(
    uiState: RetirementUiState,
    onRetirementAgeChange: (Double) -> Unit,
    onPresetChange: (RetirementScenarioPreset) -> Unit,
    onTargetModeChange: (RetirementTargetMode) -> Unit,
    onAnnualSpendingChange: (Cents) -> Unit,
    onResetAnnualSpending: () -> Unit,
    onCustomTargetChange: (Cents?) -> Unit,
    onLegacyAmountChange: (Cents) -> Unit,
    onNavigateToHousehold: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val grossIncome = uiState.grossIncomeAnnualCents
    val guide55 = if (grossIncome > 0L) MoneyUtils.multiplyPercent(grossIncome, 55.0) else 45_000_00L
    val guide80 = if (grossIncome > 0L) MoneyUtils.multiplyPercent(grossIncome, 80.0) else 65_000_00L
    val snapTolerance = if (grossIncome > 0L) MoneyUtils.multiplyPercent(grossIncome, 4.0) else 3000_00L
    val maxSpending = max(200_000_00L, if (grossIncome > 0L) MoneyUtils.multiplyPercent(grossIncome, 150.0) else 150_000_00L)

    OrbitSectionCard(
        title = "Planning Assumptions & Ages",
        subtitle = "Age milestones, investment growth preset & target retirement lifestyle",
        modifier = modifier
    ) {
        // 1. Current Age (Derived from Household, NOT a slider)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Age",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${uiState.currentAge.roundToInt()} years old",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uiState.hasAgeSource) {
                            "Derived from oldest adult household member"
                        } else {
                            "Default (35) · Add birth date in Household"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (onNavigateToHousehold != null && !uiState.hasAgeSource) {
                    TextButton(onClick = onNavigateToHousehold) {
                        Text("Edit Household", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OrbitBadge(
                        text = if (uiState.hasAgeSource) "From Household" else "Default",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Retirement Age Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Retirement Age",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Age ${uiState.retirementAge.roundToInt()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val minRetireAge = (uiState.currentAge.toFloat() + 1f).coerceAtLeast(40f)
        val maxRetireAge = max(85f, minRetireAge + 5f)
        Slider(
            value = uiState.retirementAge.toFloat().coerceIn(minRetireAge, maxRetireAge),
            onValueChange = { onRetirementAgeChange(it.toDouble()) },
            valueRange = minRetireAge..maxRetireAge,
            steps = max(0, (maxRetireAge.toInt() - minRetireAge.toInt()) - 1),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Investment Preset
        Text(
            text = "Market Return Preset",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val presets = RetirementScenarioPreset.entries
            presets.forEachIndexed { index, preset ->
                val returnStr = "${(preset.nominalAnnualReturn * 100).toInt()}%"
                val inflStr = "${(preset.inflationRate * 100).toInt()}%"
                SegmentedButton(
                    selected = uiState.preset == preset,
                    onClick = { onPresetChange(preset) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size),
                    icon = {},
                    label = { Text("${preset.label} ($returnStr/$inflStr)", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Target Strategy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Retirement Target Strategy",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                RetirementTargetMode.WITHDRAWAL_4_PERCENT to "4% Rule",
                RetirementTargetMode.NEST_EGG to "Real Return",
                RetirementTargetMode.LEAVE_LEGACY to "Legacy",
                RetirementTargetMode.CUSTOM to "Custom"
            )
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = uiState.targetMode == mode,
                    onClick = { onTargetModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {},
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        OrbitAccordionVisibility(visible = uiState.targetMode == RetirementTargetMode.LEAVE_LEGACY) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                CurrencyTextField(
                    cents = uiState.legacyAmountCents,
                    onCentsChange = onLegacyAmountChange,
                    label = "Target Legacy to Leave Behind",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        OrbitAccordionVisibility(visible = uiState.targetMode == RetirementTargetMode.CUSTOM) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                CurrencyTextField(
                    cents = uiState.customTargetCents ?: 0L,
                    onCentsChange = { onCustomTargetChange(if (it == 0L) null else it) },
                    label = "Custom Nest Egg Target Amount",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Annual Spending in Retirement with Fidelity 55%/80% Spending Guides
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Annual Spending in Retirement",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OrbitScaleBadgeVisibility(visible = uiState.annualSpendingOverridden) {
                TextButton(
                    onClick = onResetAnnualSpending,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("80% Income", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        CurrencyTextField(
            cents = uiState.annualSpendingCents,
            onCentsChange = onAnnualSpendingChange,
            label = "Retirement Spending (Today's Dollars)",
            modifier = Modifier.fillMaxWidth()
        )

        // Spending Slider with Magnetic Snapping to 55% and 80%
        Spacer(modifier = Modifier.height(6.dp))
        val currentSpendingVal = uiState.annualSpendingCents.toFloat().coerceIn(0f, maxSpending.toFloat())
        Slider(
            value = currentSpendingVal,
            onValueChange = { rawFloat ->
                val rawLong = rawFloat.roundToLong()
                val snapped = when {
                    abs(rawLong - guide55) <= snapTolerance -> guide55
                    abs(rawLong - guide80) <= snapTolerance -> guide80
                    else -> rawLong
                }
                onAnnualSpendingChange(snapped)
            },
            valueRange = 0f..maxSpending.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        // Guide Labels Row (55% and 80%)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "55%: ${MoneyUtils.formatCompactCents(guide55)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (abs(uiState.annualSpendingCents - guide55) < snapTolerance) FontWeight.Bold else FontWeight.Normal,
                color = if (abs(uiState.annualSpendingCents - guide55) < snapTolerance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Text(
                text = "80%: ${MoneyUtils.formatCompactCents(guide80)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (abs(uiState.annualSpendingCents - guide80) < snapTolerance) FontWeight.Bold else FontWeight.Normal,
                color = if (abs(uiState.annualSpendingCents - guide80) < snapTolerance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Text(
                text = MoneyUtils.formatCompactCents(maxSpending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Fidelity suggests estimating 55–80% of current gross income.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
