package app.overbay.orbit.ui.settings.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.NotificationTypeSchedule
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.ui.settings.NotificationScheduleType
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner
import app.overbay.orbit.util.LocalOrbitHaptics

private data class TimeSlotOption(
    val canonical: String,
    val displayLabel: String
)

private val DAYS_OF_WEEK = listOf(
    0 to "Sunday", 1 to "Monday", 2 to "Tuesday", 3 to "Wednesday",
    4 to "Thursday", 5 to "Friday", 6 to "Saturday"
)

private val DAYS_OF_MONTH = (1..28).toList()

/**
 * Modal bottom sheet for configuring notification delivery schedule and tracked accounts.
 */
@Composable
fun NotificationScheduleSheet(
    notificationType: NotificationScheduleType,
    schedule: NotificationTypeSchedule,
    accounts: List<Account>,
    onSave: (NotificationTypeSchedule) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    val title = when (notificationType) {
        NotificationScheduleType.WEEKLY_SNAPSHOT -> "Weekly Snapshot Schedule"
        NotificationScheduleType.INCOME -> "Income Alert Timing"
        NotificationScheduleType.ACCOUNT_SNAPSHOTS -> "Account Snapshot Reminders"
    }
    val subtitle = when (notificationType) {
        NotificationScheduleType.WEEKLY_SNAPSHOT ->
            "Choose the day of the week and delivery time for your calm weekly summary."
        NotificationScheduleType.INCOME ->
            "Receive alerts after SimpleFIN bank sync or at a scheduled time."
        NotificationScheduleType.ACCOUNT_SNAPSHOTS ->
            "Set reminder frequency and pick accounts that need manual balance snapshots."
    }

    val initialTime = remember(schedule.time, notificationType) {
        val fallback = if (notificationType == NotificationScheduleType.WEEKLY_SNAPSHOT) "08:00" else "09:00"
        normalizeCanonicalTime(schedule.time) ?: fallback
    }
    val timeSlots = remember(initialTime) { buildTimeSlotOptions(initialTime) }

    var selectedDayOfWeek by remember(schedule.dayOfWeek) {
        mutableIntStateOf((schedule.dayOfWeek ?: 1).coerceIn(0, 6))
    }
    var selectedTime by remember(initialTime) { mutableStateOf(initialTime) }
    var isScheduledIncome by remember(schedule.timing, schedule.time) {
        mutableStateOf(
            schedule.timing == "scheduled" ||
                (schedule.timing == null && !schedule.time.isNullOrBlank() && schedule.time != "after_sync")
        )
    }
    var cadence by remember(schedule.cadence) {
        mutableStateOf(if (schedule.cadence == "quarterly") "quarterly" else "monthly")
    }
    var selectedDayOfMonth by remember(schedule.dayOfMonth, schedule.dayOfWeek) {
        mutableIntStateOf((schedule.dayOfMonth ?: schedule.dayOfWeek ?: 1).coerceIn(1, 28))
    }
    var selectedAccountIds by remember(schedule.accountIds) {
        mutableStateOf(schedule.accountIds.toSet())
    }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Save Schedule",
        onConfirm = {
            val updated = when (notificationType) {
                NotificationScheduleType.WEEKLY_SNAPSHOT -> schedule.copy(
                    dayOfWeek = selectedDayOfWeek,
                    time = selectedTime
                )
                NotificationScheduleType.INCOME -> schedule.copy(
                    timing = if (isScheduledIncome) "scheduled" else "after_sync",
                    time = if (isScheduledIncome) selectedTime else null
                )
                NotificationScheduleType.ACCOUNT_SNAPSHOTS -> schedule.copy(
                    cadence = cadence,
                    dayOfMonth = selectedDayOfMonth,
                    dayOfWeek = selectedDayOfMonth,
                    time = selectedTime,
                    accountIds = selectedAccountIds.toList()
                )
            }
            onSave(updated)
        },
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        bodySpacing = 14.dp
    ) {
        when (notificationType) {
            NotificationScheduleType.WEEKLY_SNAPSHOT -> {
                OrbitDropdownField(
                    label = "Day of Week",
                    selectedText = DAYS_OF_WEEK.firstOrNull { it.first == selectedDayOfWeek }?.second ?: "Monday",
                    options = DAYS_OF_WEEK,
                    optionLabel = { it.second },
                    onOptionSelected = { selectedDayOfWeek = it.first }
                )

                OrbitDropdownField(
                    label = "Delivery Time",
                    selectedText = formatTimeSlotLabel(selectedTime),
                    options = timeSlots,
                    optionLabel = { it.displayLabel },
                    onOptionSelected = { selectedTime = it.canonical }
                )
            }

            NotificationScheduleType.INCOME -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrbitFieldLabel(text = "Notification Timing")
                    OrbitSegmentedTabRow(
                        items = listOf(false, true),
                        selected = isScheduledIncome,
                        onSelect = { isScheduledIncome = it },
                        label = { scheduled -> if (scheduled) "Scheduled" else "After Sync" }
                    )
                }

                AnimatedContent(
                    targetState = isScheduledIncome,
                    transitionSpec = { orbitMorphContentTransform(clip = false) },
                    label = "incomeAlertTimingContent"
                ) { scheduled ->
                    if (scheduled) {
                        OrbitDropdownField(
                            label = "Delivery Time",
                            selectedText = formatTimeSlotLabel(selectedTime),
                            options = timeSlots,
                            optionLabel = { it.displayLabel },
                            onOptionSelected = { selectedTime = it.canonical }
                        )
                    } else {
                        SettingsCalloutBanner(
                            icon = OrbitIcons.InfoCircle,
                            text = "Income alerts are sent immediately after transactions sync via SimpleFIN."
                        )
                    }
                }
            }

            NotificationScheduleType.ACCOUNT_SNAPSHOTS -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrbitFieldLabel(text = "Cadence")
                    OrbitSegmentedTabRow(
                        items = listOf("monthly", "quarterly"),
                        selected = cadence,
                        onSelect = { cadence = it },
                        label = { item -> if (item == "quarterly") "Quarterly" else "Monthly" }
                    )
                }

                OrbitDropdownField(
                    label = "Day of Month",
                    selectedText = "Day $selectedDayOfMonth",
                    options = DAYS_OF_MONTH,
                    optionLabel = { "Day $it" },
                    onOptionSelected = { selectedDayOfMonth = it }
                )

                OrbitDropdownField(
                    label = "Reminder Time",
                    selectedText = formatTimeSlotLabel(selectedTime),
                    options = timeSlots,
                    optionLabel = { it.displayLabel },
                    onOptionSelected = { selectedTime = it.canonical }
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrbitFieldLabel(text = "Tracked Accounts (${selectedAccountIds.size})")
                    if (accounts.isEmpty()) {
                        SettingsCalloutBanner(
                            icon = OrbitIcons.InfoCircle,
                            text = "No accounts available in workspace."
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                accounts.forEachIndexed { index, account ->
                                    if (index > 0) {
                                        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                                    }
                                    val isChecked = selectedAccountIds.contains(account.id)
                                    val toggleAccount = {
                                        haptics.tick()
                                        selectedAccountIds = if (isChecked) selectedAccountIds - account.id else selectedAccountIds + account.id
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = toggleAccount)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 10.dp)
                                        ) {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val subtitleText = listOfNotNull(
                                                account.institution?.takeIf { it.isNotBlank() },
                                                account.type.displayName
                                            ).joinToString(" · ")
                                            if (subtitleText.isNotBlank()) {
                                                Text(
                                                    text = subtitleText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { toggleAccount() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeCanonicalTime(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank() || trimmed == "after_sync") return null
    val parts = trimmed.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun buildTimeSlotOptions(savedTime: String): List<TimeSlotOption> {
    val slots = LinkedHashSet<String>(49)
    for (hour in 0..23) {
        val hh = hour.toString().padStart(2, '0')
        slots.add("$hh:00")
        slots.add("$hh:30")
    }
    slots.add(savedTime)
    return slots
        .sorted()
        .map { canonical -> TimeSlotOption(canonical = canonical, displayLabel = formatTimeSlotLabel(canonical)) }
}

private fun formatTimeSlotLabel(canonical: String): String {
    val parts = canonical.split(":")
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: 9
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val mm = minute.toString().padStart(2, '0')
    return "$hour12:$mm $period"
}
