package app.overbay.orbit.ui.mha.dialogs

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MhaTaxEngine
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MhaTaxModeSection(
    mode: String,
    onModeChange: (String) -> Unit,
    simpleFedRate: String,
    onSimpleFedRateChange: (String) -> Unit,
    simpleStateRate: String,
    onSimpleStateRateChange: (String) -> Unit,
    stateCode: String,
    onStateCodeChange: (String) -> Unit,
    selectedFilingStatus: FilingStatus,
    onFilingStatusChange: (FilingStatus) -> Unit,
    deductionMode: String,
    onDeductionModeChange: (String) -> Unit,
    itemizedDeductionCents: Cents,
    onItemizedDeductionChange: (Cents) -> Unit,
    enableTaxableIncomeOverride: Boolean,
    onToggleTaxableIncomeOverride: (Boolean) -> Unit,
    inputTaxableIncomeOverrideCents: Cents,
    onTaxableIncomeOverrideChange: (Cents) -> Unit,
    enableStateRateOverride: Boolean,
    onToggleStateRateOverride: (Boolean) -> Unit,
    inputStateRateOverride: String,
    onStateRateOverrideChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Tax Calculation Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == "simple",
                onClick = {
                    haptics.tick()
                    onModeChange("simple")
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Simple Flat Rates")
            }
            SegmentedButton(
                selected = mode == "household",
                onClick = {
                    haptics.tick()
                    onModeChange("household")
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Household Brackets")
            }
        }

        AnimatedContent(
            targetState = mode == "simple",
            transitionSpec = { orbitMorphContentTransform(clip = false) },
            modifier = Modifier.fillMaxWidth()
        ) { isSimpleMode ->
            if (isSimpleMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrbitTextField(
                        value = simpleFedRate,
                        onValueChange = onSimpleFedRateChange,
                        label = { Text("Federal Rate (%)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OrbitTextField(
                        value = simpleStateRate,
                        onValueChange = onSimpleStateRateChange,
                        label = { Text("State Rate (%)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val selectedStatePair = remember(stateCode) {
                        MhaTaxEngine.US_STATES.firstOrNull { it.first.equals(stateCode, ignoreCase = true) }
                            ?: ("IL" to "Illinois")
                    }

                    OrbitDropdownField(
                        label = "State",
                        selectedText = "${selectedStatePair.second} (${selectedStatePair.first})",
                        options = MhaTaxEngine.US_STATES,
                        optionLabel = { "${it.second} (${it.first})" },
                        onOptionSelected = { onStateCodeChange(it.first) }
                    )

                    OrbitDropdownField(
                        label = "Filing Status",
                        selectedText = selectedFilingStatus.displayName,
                        options = FilingStatus.entries,
                        optionLabel = { it.displayName },
                        onOptionSelected = onFilingStatusChange
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = deductionMode == "standard",
                            onClick = {
                                haptics.tick()
                                onDeductionModeChange("standard")
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Standard Deduction")
                        }
                        SegmentedButton(
                            selected = deductionMode == "itemized",
                            onClick = {
                                haptics.tick()
                                onDeductionModeChange("itemized")
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Itemized Deduction")
                        }
                    }

                    AnimatedContent(
                        targetState = deductionMode == "standard",
                        transitionSpec = { orbitMorphContentTransform(clip = false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { isStandard ->
                        if (isStandard) {
                            val standardDeductionRef = when (selectedFilingStatus) {
                                FilingStatus.SINGLE -> "$16,100"
                                FilingStatus.MARRIED_JOINT -> "$32,200"
                                FilingStatus.MARRIED_SEPARATE -> "$16,100"
                                FilingStatus.HEAD_OF_HOUSEHOLD -> "$24,150"
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "2026 IRS Standard Deduction Reference",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${selectedFilingStatus.displayName}: $standardDeductionRef",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            CurrencyTextField(
                                cents = itemizedDeductionCents,
                                onCentsChange = onItemizedDeductionChange,
                                label = "Annual Itemized Deductions ($)",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OrbitSwitchRow(
                        title = "Taxable Income Override",
                        subtitle = if (enableTaxableIncomeOverride) "Using custom taxable income" else "Calculated from gross income and deductions",
                        checked = enableTaxableIncomeOverride,
                        onCheckedChange = onToggleTaxableIncomeOverride
                    )

                    OrbitAccordionVisibility(visible = enableTaxableIncomeOverride) {
                        CurrencyTextField(
                            cents = inputTaxableIncomeOverrideCents,
                            onCentsChange = onTaxableIncomeOverrideChange,
                            label = "Taxable Income Override ($)",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OrbitSwitchRow(
                        title = "State Tax Rate Override",
                        subtitle = if (enableStateRateOverride) "Using custom state tax rate" else "Using bundled state rate if single-rate state",
                        checked = enableStateRateOverride,
                        onCheckedChange = onToggleStateRateOverride
                    )

                    OrbitAccordionVisibility(visible = enableStateRateOverride) {
                        OrbitTextField(
                            value = inputStateRateOverride,
                            onValueChange = onStateRateOverrideChange,
                            label = { Text("State Tax Rate Override (%)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
