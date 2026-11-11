package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

enum class ScheduleMode(val label: String) {
    STANDARD("Standard Date"),
    MONTH_DAYS("Specific Day(s)"),
    MONTH_WEEKDAYS("Weekday Pattern")
}

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
                    onClick = { haptics.tick(); onKindSelected(kind) },
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
    val lookbackWindows = listOf(3, 6, 9, 12, 18, 24)

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
                    onClick = { haptics.tick(); onStrategyChanged(strategy) },
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
                            onClick = { haptics.tick(); onLookbackChanged(months) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScheduleModeSection(
    frequency: RecurrenceFrequency,
    scheduleMode: ScheduleMode,
    monthDayOne: Int,
    monthDayTwo: Int?,
    ordinalOne: Int,
    ordinalTwo: Int?,
    weekday: Int,
    onScheduleModeChanged: (ScheduleMode) -> Unit,
    onMonthDayOneChanged: (Int) -> Unit,
    onMonthDayTwoChanged: (Int?) -> Unit,
    onOrdinalOneChanged: (Int) -> Unit,
    onOrdinalTwoChanged: (Int?) -> Unit,
    onWeekdayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (frequency != RecurrenceFrequency.MONTHLY && frequency != RecurrenceFrequency.SEMIMONTHLY) return
    val haptics = LocalOrbitHaptics.current
    val modes = ScheduleMode.entries

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "SCHEDULE MODE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                val isSelected = scheduleMode == mode
                SegmentedButton(
                    selected = isSelected,
                    onClick = { haptics.tick(); onScheduleModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {},
                    label = {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        if (scheduleMode == ScheduleMode.MONTH_DAYS) {
            Spacer(modifier = Modifier.height(10.dp))
            val dayOptions = (1..31).toList() + listOf(-1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitDropdownField(
                    label = if (frequency == RecurrenceFrequency.SEMIMONTHLY) "First Day" else "Day of Month",
                    selectedText = if (monthDayOne == -1) "Final Day" else monthDayOne.toString(),
                    options = dayOptions,
                    optionLabel = { if (it == -1) "Final Day" else it.toString() },
                    onOptionSelected = onMonthDayOneChanged,
                    modifier = Modifier.weight(1f)
                )

                if (frequency == RecurrenceFrequency.SEMIMONTHLY) {
                    OrbitDropdownField(
                        label = "Second Day",
                        selectedText = monthDayTwo?.let { if (it == -1) "Final Day" else it.toString() } ?: "None",
                        options = dayOptions,
                        optionLabel = { if (it == -1) "Final Day" else it.toString() },
                        onOptionSelected = onMonthDayTwoChanged,
                        nullOptionLabel = "None",
                        onNullSelected = { onMonthDayTwoChanged(null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (scheduleMode == ScheduleMode.MONTH_WEEKDAYS) {
            Spacer(modifier = Modifier.height(10.dp))
            val ordinals = listOf(1, 2, 3, 4, 5, -1)
            val ordinalLabels = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 5 to "5th", -1 to "Last")
            val weekdays = (0..6).toList()
            val weekdayLabels = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitDropdownField(
                    label = if (frequency == RecurrenceFrequency.SEMIMONTHLY) "First Week" else "Week Ordinal",
                    selectedText = ordinalLabels[ordinalOne] ?: "1st",
                    options = ordinals,
                    optionLabel = { ordinalLabels[it] ?: it.toString() },
                    onOptionSelected = onOrdinalOneChanged,
                    modifier = Modifier.weight(1f)
                )

                if (frequency == RecurrenceFrequency.SEMIMONTHLY) {
                    OrbitDropdownField(
                        label = "Second Week",
                        selectedText = ordinalTwo?.let { ordinalLabels[it] ?: it.toString() } ?: "None",
                        options = ordinals,
                        optionLabel = { ordinalLabels[it] ?: it.toString() },
                        onOptionSelected = onOrdinalTwoChanged,
                        nullOptionLabel = "None",
                        onNullSelected = { onOrdinalTwoChanged(null) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OrbitDropdownField(
                    label = "Weekday",
                    selectedText = weekdayLabels.getOrElse(weekday) { "Friday" },
                    options = weekdays,
                    optionLabel = { weekdayLabels.getOrElse(it) { it.toString() } },
                    onOptionSelected = onWeekdayChanged,
                    modifier = Modifier.weight(1f)
                )
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
    var intervalText by remember { mutableStateOf(if (customInterval > 0) customInterval.toString() else "1") }
    LaunchedEffect(customInterval) {
        val parsed = intervalText.toIntOrNull()
        if (parsed != null && parsed != customInterval && customInterval > 0) {
            intervalText = customInterval.toString()
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = intervalText,
                onValueChange = { str ->
                    val digits = str.filter { it.isDigit() }.take(4)
                    intervalText = digits
                    digits.toIntOrNull()?.takeIf { it > 0 }?.let(onIntervalChanged)
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
                        onClick = { haptics.tick(); onStatusChanged(st) },
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
