package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringKindSelector(
    selectedKind: UpcomingKind,
    onKindSelected: (UpcomingKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val kinds = listOf(
        UpcomingKind.BILL to "Bill",
        UpcomingKind.SUBSCRIPTION to "Sub",
        UpcomingKind.GIVING to "Giving",
        UpcomingKind.INCOME to "Income"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "KIND",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { index, (kind, label) ->
                val isSelected = selectedKind == kind
                SegmentedButton(
                    selected = isSelected,
                    onClick = {
                        haptics.tick()
                        onKindSelected(kind)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                    icon = {},
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringAmountStrategySection(
    amountStrategy: AmountStrategy,
    lookbackMonths: Int,
    onStrategyChanged: (AmountStrategy) -> Unit,
    onLookbackChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val strategies = listOf(
        AmountStrategy.FIXED to "Fixed Amount",
        AmountStrategy.HISTORY_AVERAGE to "History Average"
    )
    val lookbackWindows = listOf(3, 6, 12)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "AMOUNT STRATEGY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            strategies.forEachIndexed { index, (strategy, label) ->
                val isSelected = amountStrategy == strategy
                SegmentedButton(
                    selected = isSelected,
                    onClick = {
                        haptics.tick()
                        onStrategyChanged(strategy)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size),
                    icon = {},
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        if (amountStrategy == AmountStrategy.HISTORY_AVERAGE) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lookback Window:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow {
                    lookbackWindows.forEachIndexed { index, months ->
                        val isSel = lookbackMonths == months
                        SegmentedButton(
                            selected = isSel,
                            onClick = {
                                haptics.tick()
                                onLookbackChanged(months)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = lookbackWindows.size),
                            icon = {},
                            label = {
                                Text(
                                    text = "${months}M",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringCustomScheduleFields(
    frequency: RecurrenceFrequency,
    customInterval: Int,
    customUnit: String,
    onIntervalChanged: (Int) -> Unit,
    onUnitChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (frequency != RecurrenceFrequency.CUSTOM) return

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = if (customInterval > 0) customInterval.toString() else "",
                onValueChange = { str ->
                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                    onIntervalChanged(num)
                },
                label = { Text("Every (N)") },
                singleLine = true,
                modifier = Modifier.weight(0.45f)
            )

            OrbitDropdownField(
                label = "Unit",
                selectedText = customUnit.replaceFirstChar { it.uppercase() },
                options = listOf("days", "weeks", "months"),
                optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                onOptionSelected = onUnitChanged,
                modifier = Modifier.weight(0.55f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringStatusAndNotesSection(
    status: UpcomingStatus,
    notes: String,
    onStatusChanged: (UpcomingStatus) -> Unit,
    onNotesChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val statuses = listOf(
        UpcomingStatus.ACTIVE to "Active",
        UpcomingStatus.PAUSED to "Paused"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STATUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow {
                statuses.forEachIndexed { index, (st, label) ->
                    val isSelected = status == st
                    SegmentedButton(
                        selected = isSelected,
                        onClick = {
                            haptics.tick()
                            onStatusChanged(st)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = statuses.size),
                        icon = {},
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OrbitTextField(
            value = notes,
            onValueChange = onNotesChanged,
            label = { Text("Notes (Optional)") },
            placeholder = { Text("e.g. Account number, renewal terms") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
