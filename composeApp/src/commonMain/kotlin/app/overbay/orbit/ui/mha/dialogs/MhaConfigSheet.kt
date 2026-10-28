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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

@Composable
fun MhaConfigSheet(
    taxProfile: MhaTaxProfile,
    designatedCapCents: Cents? = null,
    fairMarketRentalValueCents: Cents? = null,
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
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MhaConfigContent(
    taxProfile: MhaTaxProfile,
    designatedCapCents: Cents? = null,
    fairMarketRentalValueCents: Cents? = null,
    onDismiss: () -> Unit,
    onSave: (MhaTaxProfile, Cents?, Cents?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var mode by remember(taxProfile) { mutableStateOf(taxProfile.mode) }
    var enableDesignatedCap by remember(designatedCapCents) { mutableStateOf(designatedCapCents != null) }
    var inputDesignatedCapCents by remember(designatedCapCents) { mutableStateOf(designatedCapCents ?: 0L) }
    var enableFmrv by remember(fairMarketRentalValueCents) { mutableStateOf(fairMarketRentalValueCents != null) }
    var inputFmrvCents by remember(fairMarketRentalValueCents) { mutableStateOf(fairMarketRentalValueCents ?: 0L) }
    var simpleFedRate by remember(taxProfile) { mutableStateOf(taxProfile.simpleFederalRatePercent.toString()) }
    var simpleStateRate by remember(taxProfile) { mutableStateOf(taxProfile.simpleStateRatePercent.toString()) }
    var selectedFilingStatus by remember(taxProfile) { mutableStateOf(taxProfile.filingStatus) }
    var stateCode by remember(taxProfile) { mutableStateOf(taxProfile.state) }
    var deductionMode by remember(taxProfile) { mutableStateOf(taxProfile.deductionMode) }
    var itemizedDeductionCents by remember(taxProfile) { mutableStateOf(taxProfile.itemizedDeductionCents) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MHA & Parsonage Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure your official advance Church Designation and Fair Market Rental Value to enforce the 3-Limit IRS test under IRC Section 107.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        // Section 1: Designation & FMRV
        Text(
            text = "1. Section 107 Caps",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        OrbitSwitchRow(
            title = "Church Designation Cap",
            subtitle = if (enableDesignatedCap) "Enforcing official advance designation" else "Uncapped (tracking actual expenses)",
            checked = enableDesignatedCap,
            onCheckedChange = { enableDesignatedCap = it }
        )

        if (enableDesignatedCap) {
            CurrencyTextField(
                cents = inputDesignatedCapCents,
                onCentsChange = { inputDesignatedCapCents = it },
                label = "Annual Church Designation Cap ($)",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        OrbitSwitchRow(
            title = "Fair Market Rental Value (FMRV)",
            subtitle = if (enableFmrv) "Enforcing annual rental value limit" else "Uncapped (no FMRV limit)",
            checked = enableFmrv,
            onCheckedChange = { enableFmrv = it }
        )

        if (enableFmrv) {
            CurrencyTextField(
                cents = inputFmrvCents,
                onCentsChange = { inputFmrvCents = it },
                label = "Fair Market Rental Value (Furnished + Utilities) ($)",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section 2: Mode Selector
        Text(
            text = "2. Tax Calculation Mode",
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
                OutlinedTextField(
                    value = simpleFedRate,
                    onValueChange = { simpleFedRate = it },
                    label = { Text("Federal Rate (%)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = simpleStateRate,
                    onValueChange = { simpleStateRate = it },
                    label = { Text("State Rate (%)") },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            OutlinedTextField(
                value = stateCode,
                onValueChange = { stateCode = it.uppercase() },
                label = { Text("State (e.g. IL, NC, CA)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Filing Status: ${selectedFilingStatus.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
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

            if (deductionMode == "itemized") {
                CurrencyTextField(
                    cents = itemizedDeductionCents,
                    onCentsChange = { itemizedDeductionCents = it },
                    label = "Annual Itemized Deductions ($)",
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
                        itemizedDeductionCents = itemizedDeductionCents
                    )
                    val cap = if (enableDesignatedCap) max(0L, inputDesignatedCapCents) else null
                    val fmrv = if (enableFmrv) max(0L, inputFmrvCents) else null
                    onSave(updated, cap, fmrv)
                }
            ) {
                Text("Save Settings")
            }
        }
    }
}
