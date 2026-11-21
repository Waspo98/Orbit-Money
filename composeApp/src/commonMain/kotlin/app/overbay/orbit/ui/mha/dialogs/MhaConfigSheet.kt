package app.overbay.orbit.ui.mha.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.MhaYearTaxRecord
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MhaTaxEngine
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import kotlin.math.max

@Composable
fun MhaConfigSheet(
    taxProfile: MhaTaxProfile,
    selectedYear: Int = LocalDateUtils.todayLocalDate().year,
    currentYear: Int = LocalDateUtils.todayLocalDate().year,
    activeYearRecord: MhaYearTaxRecord? = null,
    computedEffectiveRatePercent: Double = taxProfile.simpleFederalRatePercent + taxProfile.simpleStateRatePercent,
    designatedCapCents: Cents? = null,
    fairMarketRentalValueCents: Cents? = null,
    mhaEnabled: Boolean = true,
    onToggleMhaEnabled: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (MhaTaxProfile, Cents?, Cents?, Double?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        MhaConfigContent(
            taxProfile = taxProfile,
            selectedYear = selectedYear,
            currentYear = currentYear,
            activeYearRecord = activeYearRecord,
            computedEffectiveRatePercent = computedEffectiveRatePercent,
            designatedCapCents = designatedCapCents,
            fairMarketRentalValueCents = fairMarketRentalValueCents,
            mhaEnabled = mhaEnabled,
            onToggleMhaEnabled = onToggleMhaEnabled,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

@Composable
fun MhaConfigContent(
    taxProfile: MhaTaxProfile,
    selectedYear: Int = LocalDateUtils.todayLocalDate().year,
    currentYear: Int = LocalDateUtils.todayLocalDate().year,
    activeYearRecord: MhaYearTaxRecord? = null,
    computedEffectiveRatePercent: Double = taxProfile.simpleFederalRatePercent + taxProfile.simpleStateRatePercent,
    designatedCapCents: Cents? = null,
    fairMarketRentalValueCents: Cents? = null,
    mhaEnabled: Boolean = true,
    onToggleMhaEnabled: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (MhaTaxProfile, Cents?, Cents?, Double?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialCap = if (activeYearRecord?.capsConfigured == true) activeYearRecord.designatedCapCents else (designatedCapCents ?: taxProfile.designatedCapCents)
    val initialFmrv = if (activeYearRecord?.capsConfigured == true) activeYearRecord.fairMarketRentalValueCents else (fairMarketRentalValueCents ?: taxProfile.fairMarketRentalValueCents)
    val isHistorical = selectedYear < currentYear
    val hasStoredHistoricalRate = activeYearRecord != null && !activeYearRecord.useLiveProfile
    val defaultPin = if (isHistorical) hasStoredHistoricalRate else activeYearRecord?.isLocked == true
    val initialCustomRate = if (hasStoredHistoricalRate) activeYearRecord!!.effectiveRatePercent else computedEffectiveRatePercent
    val globalProfileKey = remember(taxProfile) { taxProfile.copy(yearlyRecords = emptyMap()) }

    var currentMhaEnabled by remember(mhaEnabled) { mutableStateOf(mhaEnabled) }
    var trackingMode by remember(globalProfileKey) { mutableStateOf(taxProfile.trackingMode) }
    val isMhaMode = trackingMode == TaxTrackingMode.MHA
    var pinCustomYearRate by remember(selectedYear, activeYearRecord?.year, activeYearRecord?.isLocked, activeYearRecord?.useLiveProfile) {
        mutableStateOf(defaultPin)
    }
    var customEffectiveRateText by remember(selectedYear, activeYearRecord?.year, activeYearRecord?.effectiveRatePercent, activeYearRecord?.useLiveProfile) {
        mutableStateOf(MhaTaxEngine.formatPercent(initialCustomRate))
    }
    LaunchedEffect(computedEffectiveRatePercent, pinCustomYearRate, hasStoredHistoricalRate) {
        if (!pinCustomYearRate && !hasStoredHistoricalRate) {
            customEffectiveRateText = MhaTaxEngine.formatPercent(computedEffectiveRatePercent)
        }
    }
    var enableDesignatedCap by remember(selectedYear, initialCap) { mutableStateOf(initialCap != null) }
    var inputDesignatedCapCents by remember(selectedYear, initialCap) { mutableStateOf(initialCap ?: 0L) }
    var enableFmrv by remember(selectedYear, initialFmrv) { mutableStateOf(initialFmrv != null) }
    var inputFmrvCents by remember(selectedYear, initialFmrv) { mutableStateOf(initialFmrv ?: 0L) }
    var mode by remember(globalProfileKey) { mutableStateOf(taxProfile.mode) }
    var simpleFedRate by remember(globalProfileKey) { mutableStateOf(taxProfile.simpleFederalRatePercent.toString()) }
    var simpleStateRate by remember(globalProfileKey) { mutableStateOf(taxProfile.simpleStateRatePercent.toString()) }
    var selectedFilingStatus by remember(globalProfileKey) { mutableStateOf(taxProfile.filingStatus) }
    var stateCode by remember(globalProfileKey) { mutableStateOf(taxProfile.state) }
    var deductionMode by remember(globalProfileKey) { mutableStateOf(taxProfile.deductionMode) }
    var itemizedDeductionCents by remember(globalProfileKey) { mutableStateOf(taxProfile.itemizedDeductionCents) }

    var enableTaxableIncomeOverride by remember(globalProfileKey) {
        mutableStateOf(taxProfile.taxableIncomeOverrideCents != null)
    }
    var inputTaxableIncomeOverrideCents by remember(globalProfileKey) {
        mutableStateOf(taxProfile.taxableIncomeOverrideCents ?: 0L)
    }

    var enableStateRateOverride by remember(globalProfileKey) {
        mutableStateOf(taxProfile.stateRateOverridePercent != null)
    }
    var inputStateRateOverride by remember(globalProfileKey) {
        mutableStateOf(taxProfile.stateRateOverridePercent?.toString() ?: "")
    }

    OrbitModalScaffold(
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            val resolvedCap = if (isMhaMode && enableDesignatedCap) max(0L, inputDesignatedCapCents) else null
            val resolvedFmrv = if (isMhaMode && enableFmrv) max(0L, inputFmrvCents) else null
            val parsedYearRate = if (pinCustomYearRate) {
                (customEffectiveRateText.toDoubleOrNull() ?: initialCustomRate).coerceIn(0.0, 100.0)
            } else null
            val updated = if (isHistorical && pinCustomYearRate) {
                taxProfile.copy(trackingMode = trackingMode)
            } else {
                taxProfile.copy(
                    trackingMode = trackingMode,
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
            }
            onSave(updated, resolvedCap, resolvedFmrv, parsedYearRate, pinCustomYearRate)
        },
        modifier = modifier,
        title = if (isMhaMode) "$selectedYear MHA Tax Rules" else "$selectedYear Tax Deduction Rules",
        subtitle = if (isHistorical) {
            if (isMhaMode) "Edit $selectedYear effective rate and Section 107 caps." else "Edit $selectedYear effective tax rate."
        } else {
            if (isMhaMode) "Configure Section 107 limits and tax calculation rates." else "Configure tracking mode and tax calculation rates."
        },
        bodySpacing = 16.dp
    ) {

        OrbitSegmentedTabRow(
            items = listOf(TaxTrackingMode.GENERAL, TaxTrackingMode.MHA),
            selected = trackingMode,
            onSelect = { trackingMode = it },
            label = { if (it == TaxTrackingMode.GENERAL) "General Deductions" else "Housing Allowance (MHA)" }
        )

        OrbitSwitchRow(
            title = if (isMhaMode) "MHA Tracker Active" else "Tax Deductions Active",
            subtitle = if (currentMhaEnabled) {
                if (isMhaMode) "Housing allowance tracking and tax calculations enabled" else "Tax-deductible tracking and savings estimates enabled"
            } else "Tax tracking paused",
            checked = currentMhaEnabled,
            onCheckedChange = {
                currentMhaEnabled = it
                onToggleMhaEnabled?.invoke(it)
            }
        )

        MhaYearRateSection(
            selectedYear = selectedYear,
            currentYear = currentYear,
            activeYearRecord = activeYearRecord,
            pinCustomRate = pinCustomYearRate,
            onTogglePinCustomRate = { pinCustomYearRate = it },
            customEffectiveRateText = customEffectiveRateText,
            onCustomEffectiveRateChange = { customEffectiveRateText = it }
        )

        OrbitAccordionVisibility(visible = isMhaMode) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                OrbitSwitchRow(
                    title = "Housing Allowance Designation Cap",
                    subtitle = if (enableDesignatedCap) "Capped by official church/employer designation" else "No annual designation cap configured",
                    checked = enableDesignatedCap,
                    onCheckedChange = { enableDesignatedCap = it }
                )
                OrbitAccordionVisibility(visible = enableDesignatedCap) {
                    CurrencyTextField(
                        cents = inputDesignatedCapCents,
                        onCentsChange = { inputDesignatedCapCents = it },
                        label = "Annual Designated Cap ($)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OrbitSwitchRow(
                    title = "Fair Market Rental Value (FMRV)",
                    subtitle = if (enableFmrv) "Capped by furnished fair market rental value + utilities" else "No annual FMRV cap configured",
                    checked = enableFmrv,
                    onCheckedChange = { enableFmrv = it }
                )
                OrbitAccordionVisibility(visible = enableFmrv) {
                    CurrencyTextField(
                        cents = inputFmrvCents,
                        onCentsChange = { inputFmrvCents = it },
                        label = "Annual Fair Market Rental Value ($)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (!isHistorical || !pinCustomYearRate) {
            MhaTaxModeSection(
                mode = mode,
                onModeChange = { mode = it },
                simpleFedRate = simpleFedRate,
                onSimpleFedRateChange = { simpleFedRate = it },
                simpleStateRate = simpleStateRate,
                onSimpleStateRateChange = { simpleStateRate = it },
                stateCode = stateCode,
                onStateCodeChange = { stateCode = it },
                selectedFilingStatus = selectedFilingStatus,
                onFilingStatusChange = { selectedFilingStatus = it },
                deductionMode = deductionMode,
                onDeductionModeChange = { deductionMode = it },
                itemizedDeductionCents = itemizedDeductionCents,
                onItemizedDeductionChange = { itemizedDeductionCents = it },
                enableTaxableIncomeOverride = enableTaxableIncomeOverride,
                onToggleTaxableIncomeOverride = { enableTaxableIncomeOverride = it },
                inputTaxableIncomeOverrideCents = inputTaxableIncomeOverrideCents,
                onTaxableIncomeOverrideChange = { inputTaxableIncomeOverrideCents = it },
                enableStateRateOverride = enableStateRateOverride,
                onToggleStateRateOverride = { enableStateRateOverride = it },
                inputStateRateOverride = inputStateRateOverride,
                onStateRateOverrideChange = { inputStateRateOverride = it }
            )
        }
    }
}
