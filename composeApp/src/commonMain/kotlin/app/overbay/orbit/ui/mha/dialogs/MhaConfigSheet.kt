package app.overbay.orbit.ui.mha.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MhaTaxEngine
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

@Composable
fun MhaConfigSheet(
    taxProfile: MhaTaxProfile,
    designatedCapCents: Cents? = null,
    fairMarketRentalValueCents: Cents? = null,
    mhaEnabled: Boolean = true,
    onToggleMhaEnabled: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (MhaTaxProfile, Cents?, Cents?) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        MhaConfigContent(
            taxProfile = taxProfile,
            designatedCapCents = designatedCapCents,
            fairMarketRentalValueCents = fairMarketRentalValueCents,
            mhaEnabled = mhaEnabled,
            onToggleMhaEnabled = onToggleMhaEnabled,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
fun MhaConfigSheet(
    taxProfile: MhaTaxProfile,
    mhaEnabled: Boolean = true,
    onToggleMhaEnabled: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (MhaTaxProfile) -> Unit,
    modifier: Modifier = Modifier
) = MhaConfigSheet(
    taxProfile = taxProfile,
    designatedCapCents = null,
    fairMarketRentalValueCents = null,
    mhaEnabled = mhaEnabled,
    onToggleMhaEnabled = onToggleMhaEnabled,
    onDismiss = onDismiss,
    onSave = { updated, _, _ -> onSave(updated) },
    modifier = modifier
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MhaConfigContent(
    taxProfile: MhaTaxProfile,
    designatedCapCents: Cents? = null,
    fairMarketRentalValueCents: Cents? = null,
    mhaEnabled: Boolean = true,
    onToggleMhaEnabled: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (MhaTaxProfile, Cents?, Cents?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var currentMhaEnabled by remember(mhaEnabled) { mutableStateOf(mhaEnabled) }
    var mode by remember(taxProfile) { mutableStateOf(taxProfile.mode) }
    var simpleFedRate by remember(taxProfile) { mutableStateOf(taxProfile.simpleFederalRatePercent.toString()) }
    var simpleStateRate by remember(taxProfile) { mutableStateOf(taxProfile.simpleStateRatePercent.toString()) }
    var selectedFilingStatus by remember(taxProfile) { mutableStateOf(taxProfile.filingStatus) }
    var stateCode by remember(taxProfile) { mutableStateOf(taxProfile.state) }
    var deductionMode by remember(taxProfile) { mutableStateOf(taxProfile.deductionMode) }
    var itemizedDeductionCents by remember(taxProfile) { mutableStateOf(taxProfile.itemizedDeductionCents) }

    var enableTaxableIncomeOverride by remember(taxProfile) {
        mutableStateOf(taxProfile.taxableIncomeOverrideCents != null)
    }
    var inputTaxableIncomeOverrideCents by remember(taxProfile) {
        mutableStateOf(taxProfile.taxableIncomeOverrideCents ?: 0L)
    }

    var enableStateRateOverride by remember(taxProfile) {
        mutableStateOf(taxProfile.stateRateOverridePercent != null)
    }
    var inputStateRateOverride by remember(taxProfile) {
        mutableStateOf(taxProfile.stateRateOverridePercent?.toString() ?: "")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OrbitSheetHeader(
            title = "MHA Tax Assumptions",
            subtitle = "Configure federal and state tax rates or household estimate mode.",
            onDismiss = onDismiss
        )

        OrbitSwitchRow(
            title = "MHA Tracker Active",
            subtitle = if (currentMhaEnabled) "Housing allowance tracking and tax calculations enabled" else "MHA calculations paused",
            checked = currentMhaEnabled,
            onCheckedChange = {
                currentMhaEnabled = it
                onToggleMhaEnabled?.invoke(it)
            }
        )

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
                    mode = "simple"
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Simple Flat Rates")
            }
            SegmentedButton(
                selected = mode == "household",
                onClick = {
                    haptics.tick()
                    mode = "household"
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Household Brackets")
            }
        }

        if (mode == "simple") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrbitTextField(
                    value = simpleFedRate,
                    onValueChange = { simpleFedRate = it },
                    label = { Text("Federal Rate (%)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OrbitTextField(
                    value = simpleStateRate,
                    onValueChange = { simpleStateRate = it },
                    label = { Text("State Rate (%)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            val selectedStatePair = remember(stateCode) {
                MhaTaxEngine.US_STATES.firstOrNull { it.first.equals(stateCode, ignoreCase = true) }
                    ?: ("IL" to "Illinois")
            }

            OrbitDropdownField(
                label = "State",
                selectedText = "${selectedStatePair.second} (${selectedStatePair.first})",
                options = MhaTaxEngine.US_STATES,
                optionLabel = { "${it.second} (${it.first})" },
                onOptionSelected = { stateCode = it.first }
            )

            OrbitDropdownField(
                label = "Filing Status",
                selectedText = selectedFilingStatus.displayName,
                options = FilingStatus.entries,
                optionLabel = { it.displayName },
                onOptionSelected = { selectedFilingStatus = it }
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = deductionMode == "standard",
                    onClick = {
                        haptics.tick()
                        deductionMode = "standard"
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Standard Deduction")
                }
                SegmentedButton(
                    selected = deductionMode == "itemized",
                    onClick = {
                        haptics.tick()
                        deductionMode = "itemized"
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Itemized Deduction")
                }
            }

            if (deductionMode == "standard") {
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
                    onCentsChange = { itemizedDeductionCents = it },
                    label = "Annual Itemized Deductions ($)",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            OrbitSwitchRow(
                title = "Taxable Income Override",
                subtitle = if (enableTaxableIncomeOverride) "Using custom taxable income" else "Calculated from gross income and deductions",
                checked = enableTaxableIncomeOverride,
                onCheckedChange = { enableTaxableIncomeOverride = it }
            )

            if (enableTaxableIncomeOverride) {
                CurrencyTextField(
                    cents = inputTaxableIncomeOverrideCents,
                    onCentsChange = { inputTaxableIncomeOverrideCents = it },
                    label = "Taxable Income Override ($)",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            OrbitSwitchRow(
                title = "State Tax Rate Override",
                subtitle = if (enableStateRateOverride) "Using custom state tax rate" else "Using bundled state rate if single-rate state",
                checked = enableStateRateOverride,
                onCheckedChange = { enableStateRateOverride = it }
            )

            if (enableStateRateOverride) {
                OrbitTextField(
                    value = inputStateRateOverride,
                    onValueChange = { inputStateRateOverride = it },
                    label = { Text("State Tax Rate Override (%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    haptics.tick()
                    val updated = taxProfile.copy(
                        mode = mode,
                        simpleFederalRatePercent = simpleFedRate.toDoubleOrNull() ?: 22.0,
                        simpleStateRatePercent = simpleStateRate.toDoubleOrNull() ?: 4.95,
                        state = stateCode.take(2).uppercase().ifEmpty { "IL" },
                        filingStatus = selectedFilingStatus,
                        deductionMode = deductionMode,
                        itemizedDeductionCents = itemizedDeductionCents,
                        taxableIncomeOverrideCents = if (enableTaxableIncomeOverride) max(0L, inputTaxableIncomeOverrideCents) else null,
                        stateRateOverridePercent = if (enableStateRateOverride) inputStateRateOverride.toDoubleOrNull()?.coerceIn(0.0, 100.0) else null
                    )
                    onSave(updated, null, null)
                }
            ) {
                Text("Save")
            }
        }
    }
}
