package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.roundToInt

@Composable
fun HouseholdIncomeAssumptionsCard(
    uiState: RetirementUiState,
    onCurrentAgeChange: (Double) -> Unit,
    onRetirementAgeChange: (Double) -> Unit,
    onPresetChange: (RetirementScenarioPreset) -> Unit,
    onTargetModeChange: (RetirementTargetMode) -> Unit,
    onAnnualSpendingChange: (Cents) -> Unit,
    onCustomTargetChange: (Cents?) -> Unit,
    onLegacyAmountChange: (Cents) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Planning Assumptions & Ages",
        subtitle = "Age milestones, investment growth preset & target retirement lifestyle",
        modifier = modifier
    ) {
        // 1. Current Age Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current Age",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${uiState.currentAge.roundToInt()} years old",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = uiState.currentAge.toFloat(),
            onValueChange = { onCurrentAgeChange(it.toDouble()) },
            valueRange = 18f..75f,
            steps = 56,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Retirement Age Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Retirement Age",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${uiState.retirementAge.roundToInt()} years old",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = uiState.retirementAge.toFloat(),
            onValueChange = { onRetirementAgeChange(it.toDouble()) },
            valueRange = (uiState.currentAge.toFloat() + 1f)..85f,
            steps = (85 - uiState.currentAge.toInt()),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Investment Preset Chips
        Text(
            text = "Market Return Preset",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RetirementScenarioPreset.entries.forEach { preset ->
                val isSelected = uiState.preset == preset
                val returnStr = "${(preset.nominalAnnualReturn * 100).toInt()}%"
                val inflStr = "${(preset.inflationRate * 100).toInt()}%"
                FilterChip(
                    selected = isSelected,
                    onClick = { onPresetChange(preset) },
                    label = { Text("${preset.label} ($returnStr / $inflStr)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Target Nest Egg Mode
        Text(
            text = "Retirement Target Strategy",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.targetMode == RetirementTargetMode.WITHDRAWAL_4_PERCENT,
                onClick = { onTargetModeChange(RetirementTargetMode.WITHDRAWAL_4_PERCENT) },
                label = { Text("4% Rule") }
            )
            FilterChip(
                selected = uiState.targetMode == RetirementTargetMode.NEST_EGG,
                onClick = { onTargetModeChange(RetirementTargetMode.NEST_EGG) },
                label = { Text("Real Return") }
            )
            FilterChip(
                selected = uiState.targetMode == RetirementTargetMode.LEAVE_LEGACY,
                onClick = { onTargetModeChange(RetirementTargetMode.LEAVE_LEGACY) },
                label = { Text("Legacy") }
            )
            FilterChip(
                selected = uiState.targetMode == RetirementTargetMode.CUSTOM,
                onClick = { onTargetModeChange(RetirementTargetMode.CUSTOM) },
                label = { Text("Custom") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Annual Spending in Retirement
        CurrencyTextField(
            cents = uiState.annualSpendingCents,
            onCentsChange = onAnnualSpendingChange,
            label = "Annual Retirement Spending (Today's Dollars)",
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.targetMode == RetirementTargetMode.LEAVE_LEGACY) {
            Spacer(modifier = Modifier.height(10.dp))
            CurrencyTextField(
                cents = uiState.legacyAmountCents,
                onCentsChange = onLegacyAmountChange,
                label = "Target Legacy to Leave Behind",
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (uiState.targetMode == RetirementTargetMode.CUSTOM) {
            Spacer(modifier = Modifier.height(10.dp))
            CurrencyTextField(
                cents = uiState.customTargetCents ?: 0L,
                onCentsChange = { onCustomTargetChange(if (it == 0L) null else it) },
                label = "Custom Nest Egg Target Amount",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
