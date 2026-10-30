package app.overbay.orbit.ui.calculators.retirement.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

@Composable
fun TargetSettingsSheet(
    targetMode: RetirementTargetMode,
    customTargetAmountCents: Cents?,
    legacyAmountCents: Cents,
    runwayEndAge: Double,
    onDismiss: () -> Unit,
    onSave: (RetirementTargetMode, Cents?, Cents, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        TargetSettingsContent(
            targetMode = targetMode,
            customTargetAmountCents = customTargetAmountCents,
            legacyAmountCents = legacyAmountCents,
            runwayEndAge = runwayEndAge,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSettingsContent(
    targetMode: RetirementTargetMode,
    customTargetAmountCents: Cents?,
    legacyAmountCents: Cents,
    runwayEndAge: Double,
    onDismiss: () -> Unit,
    onSave: (RetirementTargetMode, Cents?, Cents, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var selectedMode by remember(targetMode) { mutableStateOf(targetMode) }
    var customTarget by remember(customTargetAmountCents) { mutableStateOf(customTargetAmountCents ?: 1_500_000_00L) }
    var legacyTarget by remember(legacyAmountCents) { mutableStateOf(legacyAmountCents) }
    var endAge by remember(runwayEndAge) { mutableStateOf(runwayEndAge) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Retirement Target & Runway Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure your target nest egg calculation model and the horizon age for retirement capital drawdown.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        // Target Nest Egg Calculation Mode
        Text(
            text = "Nest Egg Calculation Method",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        val modes = listOf(
            RetirementTargetMode.WITHDRAWAL_4_PERCENT to "4% Rule (25x)",
            RetirementTargetMode.NEST_EGG to "Growth Multiple",
            RetirementTargetMode.LEAVE_LEGACY to "Leave Legacy",
            RetirementTargetMode.CUSTOM to "Custom Target"
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.take(2).forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = {
                        haptics.tick()
                        selectedMode = mode
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.drop(2).forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = {
                        haptics.tick()
                        selectedMode = mode
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Conditional input for Custom Target
        if (selectedMode == RetirementTargetMode.CUSTOM) {
            CurrencyTextField(
                cents = customTarget,
                onCentsChange = { customTarget = it },
                label = "Custom Target Nest Egg ($)",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Conditional input for Legacy Amount
        if (selectedMode == RetirementTargetMode.LEAVE_LEGACY) {
            CurrencyTextField(
                cents = legacyTarget,
                onCentsChange = { legacyTarget = it },
                label = "Target Legacy Capital ($)",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Runway End Age Slider (80 to 105)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Runway End Age",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Age ${endAge.roundToInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = endAge.toFloat(),
            onValueChange = {
                endAge = it.roundToInt().toDouble()
                haptics.tick()
            },
            valueRange = 80f..105f,
            steps = 24,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Projections simulate safe portfolio drawdown through age ${endAge.roundToInt()}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Button(
                onClick = {
                    haptics.tick()
                    onSave(selectedMode, if (selectedMode == RetirementTargetMode.CUSTOM) customTarget else null, legacyTarget, endAge)
                }
            ) {
                Text("Save Target Settings")
            }
        }
    }
}
