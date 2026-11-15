package app.overbay.orbit.ui.calculators.retirement.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.calculators.retirement.components.OrbitSmoothSlider
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitCompactCurrencyField
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Single consolidated modal for secondary modeling assumptions: Market Return Preset,
 * Target Calculation Strategy, Starting Portfolio Balance Override, and Life Expectancy.
 */
@Composable
fun AssumptionsSheet(
    uiState: RetirementUiState,
    onSelectPreset: (RetirementScenarioPreset) -> Unit,
    onSetTargetMode: (RetirementTargetMode) -> Unit,
    onSetCustomTarget: (Cents?) -> Unit,
    onSetLegacyAmount: (Cents) -> Unit,
    onSetCurrentBalance: (Cents) -> Unit,
    onResetCurrentBalance: () -> Unit,
    onSetRunwayEndAge: (Double) -> Unit,
    onOpenIncludedAccounts: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        OrbitModalScaffold(
            title = "Planning Assumptions",
            subtitle = "Return presets, target strategy, starting portfolio, and horizon",
            confirmText = "Done",
            dismissText = null,
            onDismiss = onDismiss,
            onConfirm = onDismiss,
            bodySpacing = 18.dp
        ) {
            // 1. Market Return Preset
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Market Return Preset",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RetirementScenarioPreset.entries.forEach { preset ->
                        val isSelected = uiState.preset == preset
                        val shape = RoundedCornerShape(12.dp)
                        Surface(
                            shape = shape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .clickable {
                                    haptics.tick()
                                    onSelectPreset(preset)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatBasisRate(preset.nominalAnnualReturn)} / ${formatBasisRate(preset.inflationRate)}",
                                    style = TabularNumStyle.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Nominal return vs. assumed annual inflation (all charts adjust to today's dollars).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Retirement Target Strategy (2x2 clean grid, zero line wrapping)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Strategy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val strategyRows = listOf(
                    listOf(
                        Triple(RetirementTargetMode.WITHDRAWAL_4_PERCENT, "4% Rule", "25x spending target"),
                        Triple(RetirementTargetMode.NEST_EGG, "Perpetual Real", "Live on real return")
                    ),
                    listOf(
                        Triple(RetirementTargetMode.LEAVE_LEGACY, "Leave Legacy", "Keep estate reserve"),
                        Triple(RetirementTargetMode.CUSTOM, "Custom Target", "Fixed dollar goal")
                    )
                )

                strategyRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (mode, title, caption) ->
                            val isSelected = uiState.targetMode == mode
                            val shape = RoundedCornerShape(12.dp)
                            Surface(
                                shape = shape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .clickable {
                                        haptics.tick()
                                        onSetTargetMode(mode)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = caption,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                OrbitAccordionVisibility(visible = uiState.targetMode == RetirementTargetMode.LEAVE_LEGACY) {
                    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Target Legacy Reserve",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OrbitCompactCurrencyField(
                            cents = uiState.legacyAmountCents,
                            onCentsChange = onSetLegacyAmount,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }

                OrbitAccordionVisibility(visible = uiState.targetMode == RetirementTargetMode.CUSTOM) {
                    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Custom Target Nest Egg",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OrbitCompactCurrencyField(
                            cents = uiState.customTargetCents ?: uiState.scenario.targetNestEggCents,
                            onCentsChange = { onSetCustomTarget(if (it == 0L) null else it) },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }

            // 3. Starting Portfolio Override
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Starting Portfolio Balance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                onDismiss()
                                onOpenIncludedAccounts()
                            },
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "Accounts (${uiState.linkedAccounts.size})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (uiState.currentBalanceOverridden) {
                            OutlinedButton(
                                onClick = {
                                    haptics.tick()
                                    onResetCurrentBalance()
                                },
                                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = "Use Linked (${MoneyUtils.formatCompactCents(uiState.connectedBalanceCents)})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                OrbitCompactCurrencyField(
                    cents = uiState.currentBalanceCents,
                    onCentsChange = onSetCurrentBalance,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            // 4. Life Expectancy / Runway Horizon
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plan Through Age",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Age ${uiState.runwayEndAge.roundToInt()}",
                        style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val minEnd = (uiState.retirementAge.toFloat() + 1f).coerceAtLeast(75f)
                val maxEnd = max(110f, minEnd + 5f)
                var lastTickedAge = uiState.runwayEndAge.roundToInt()
                OrbitSmoothSlider(
                    value = uiState.runwayEndAge.toFloat().coerceIn(minEnd, maxEnd),
                    valueRange = minEnd..maxEnd,
                    onValueChange = { raw ->
                        val rounded = raw.roundToInt()
                        if (rounded != lastTickedAge) {
                            haptics.tick()
                            lastTickedAge = rounded
                        }
                        onSetRunwayEndAge(rounded.toDouble())
                    }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

internal fun formatBasisRate(rate: Double): String {
    val bps = (rate * 10_000.0).roundToInt()
    val whole = bps / 100
    val frac = kotlin.math.abs(bps % 100)
    return when {
        frac == 0 -> "$whole%"
        frac % 10 == 0 -> "$whole.${frac / 10}%"
        else -> "$whole.${frac.toString().padStart(2, '0')}%"
    }
}
