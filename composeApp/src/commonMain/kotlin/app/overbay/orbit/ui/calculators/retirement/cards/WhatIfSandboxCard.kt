package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementActions
import app.overbay.orbit.ui.calculators.retirement.RetirementAmountField
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.calculators.retirement.components.AgeStepperChip
import app.overbay.orbit.ui.calculators.retirement.components.OrbitSmoothSlider
import app.overbay.orbit.ui.calculators.retirement.components.SpendingBenchmarkStrip
import app.overbay.orbit.ui.calculators.retirement.components.TappableAmountPill
import app.overbay.orbit.ui.calculators.retirement.components.rememberStableSliderUpperBound
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun WhatIfSandboxCard(
    uiState: RetirementUiState,
    actions: RetirementActions,
    onDragStateChanged: (Boolean) -> Unit,
    onOpenHousehold: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "What-If Levers",
        subtitle = "Adjust age, savings pace, and retirement lifestyle in real time",
        modifier = modifier
    ) {
        // 1. Current Age Anchor Row (properly weighted so "From Household" never clips)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Current Age",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.hasAgeSource) "Synced from oldest adult household member" else "Default baseline age",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.hasAgeSource) {
                        OrbitBadge(
                            text = "Household",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (onOpenHousehold != null) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                onOpenHousehold()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Edit Household", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = "${uiState.currentAge.roundToInt()}",
                        style = TabularNumStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Target Retirement Age Lever with ±1yr Precision Stepper Pills
        val minRetireAge = (uiState.currentAge + 1.0).toFloat()
        val maxRetireAge = max(minRetireAge, min(max(80f, minRetireAge + 5f), (uiState.runwayEndAge - 1.0).toFloat()))
        val retireAgeInt = uiState.retirementAge.roundToInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Retirement Age",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AgeStepperChip(isIncrement = false, description = "Decrease Age") {
                    actions.onSetRetirementAge((retireAgeInt - 1).toDouble())
                }
                Text(
                    text = "Age $retireAgeInt",
                    style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                AgeStepperChip(isIncrement = true, description = "Increase Age") {
                    actions.onSetRetirementAge((retireAgeInt + 1).toDouble())
                }
            }
        }
        OrbitSmoothSlider(
            value = uiState.retirementAge.toFloat(),
            valueRange = minRetireAge..maxRetireAge,
            onDragStateChanged = onDragStateChanged,
            onValueChange = { raw ->
                val stepped = raw.roundToInt()
                if (stepped != retireAgeInt) {
                    haptics.tick()
                    actions.onSetRetirementAge(stepped.toDouble())
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OrbitTheme.colors.cardBorder)

        // 3. Monthly Savings Pace Lever + Tappable Currency Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Monthly Savings",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.monthlySavingsOverridden) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            actions.onResetMonthlySavings()
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "Reset (${MoneyUtils.formatCompactCents(uiState.plannedMonthlySavingsCents)}/mo)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            TappableAmountPill(
                valueText = "${MoneyUtils.formatCents(uiState.monthlySavingsCents, showCents = false)} / mo",
                onClick = { actions.onOpenAmountEditor(RetirementAmountField.MONTHLY_SAVINGS) }
            )
        }
        var isDraggingSavings by remember { mutableStateOf(false) }
        val maxMonthlySavings = rememberStableSliderUpperBound(
            targetMax = max(10_000_00f, uiState.monthlySavingsCents.toFloat() * 1.25f),
            isDragging = isDraggingSavings
        )
        OrbitSmoothSlider(
            value = uiState.monthlySavingsCents.toFloat(),
            valueRange = 0f..maxMonthlySavings,
            onDragStateChanged = { dragging ->
                isDraggingSavings = dragging
                onDragStateChanged(dragging)
            },
            onValueChange = { raw ->
                val step = 50_00L
                val quantized = ((raw.roundToLong() + step / 2) / step) * step
                if (quantized != uiState.monthlySavingsCents) {
                    haptics.tick()
                    actions.onSetMonthlySavings(quantized)
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OrbitTheme.colors.cardBorder)

        // 4. Annual Retirement Spending + Tappable Pill + Interactive 55% / 80% Income Snap Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Annual Spending",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "In today's dollars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TappableAmountPill(
                valueText = "${MoneyUtils.formatCents(uiState.annualSpendingCents, showCents = false)} / yr",
                onClick = { actions.onOpenAmountEditor(RetirementAmountField.ANNUAL_SPENDING) }
            )
        }
        val minSpendingCents = 20_000_00L
        val targetMaxSpendingCents: Cents = if (uiState.grossIncomeAnnualCents > 0L) {
            max((uiState.grossIncomeAnnualCents * 1.5).roundToLong(), uiState.annualSpendingCents + 20_000_00L)
        } else {
            max(250_000_00L, uiState.annualSpendingCents + 20_000_00L)
        }
        var isDraggingSpending by remember { mutableStateOf(false) }
        val maxSpendingFloat = rememberStableSliderUpperBound(
            targetMax = targetMaxSpendingCents.toFloat(),
            isDragging = isDraggingSpending
        )
        val maxSpendingCents = maxSpendingFloat.roundToLong().coerceAtLeast(minSpendingCents + 1_000_00L)
        OrbitSmoothSlider(
            value = uiState.annualSpendingCents.toFloat(),
            valueRange = minSpendingCents.toFloat()..maxSpendingFloat,
            onDragStateChanged = { dragging ->
                isDraggingSpending = dragging
                onDragStateChanged(dragging)
            },
            onValueChange = { raw ->
                val step = 1_000_00L
                val quantized = ((raw.roundToLong() + step / 2) / step) * step
                if (quantized != uiState.annualSpendingCents) {
                    haptics.tick()
                    actions.onSetAnnualSpending(quantized)
                }
            }
        )

        if (uiState.grossIncomeAnnualCents > 0L) {
            SpendingBenchmarkStrip(
                grossIncomeAnnualCents = uiState.grossIncomeAnnualCents,
                annualSpendingCents = uiState.annualSpendingCents,
                minSpendingCents = minSpendingCents,
                maxSpendingCents = maxSpendingCents,
                onApplyFraction = actions.onApplySpendingFraction
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Compact Assumptions Strip Opening Unified AssumptionsSheet
        val modeShort = when (uiState.targetMode) {
            app.overbay.orbit.data.model.RetirementTargetMode.WITHDRAWAL_4_PERCENT -> "4% Rule"
            app.overbay.orbit.data.model.RetirementTargetMode.NEST_EGG -> "Perpetual Real"
            app.overbay.orbit.data.model.RetirementTargetMode.LEAVE_LEGACY -> "Legacy"
            app.overbay.orbit.data.model.RetirementTargetMode.CUSTOM -> "Custom Goal"
        }
        val shape = RoundedCornerShape(12.dp)
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable {
                    haptics.tick()
                    actions.onOpenAssumptionsSheet()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.preset.label} (${RetirementCalculatorEngine.formatRatePercent(uiState.preset.nominalAnnualReturn)}) · $modeShort · Start ${MoneyUtils.formatCompactCents(uiState.currentBalanceCents)}",
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tune",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
