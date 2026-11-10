package app.overbay.orbit.ui.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.NotificationTypeSchedule
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitTextField

enum class NotificationScheduleType {
    WEEKLY_SNAPSHOT,
    INCOME,
    SYNC_ISSUES,
    ACCOUNT_SNAPSHOTS
}

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
    val title = when (notificationType) {
        NotificationScheduleType.WEEKLY_SNAPSHOT -> "Weekly Snapshot Schedule"
        NotificationScheduleType.INCOME -> "Income Alert Timing"
        NotificationScheduleType.SYNC_ISSUES -> "Bank Sync Issue Alerts"
        NotificationScheduleType.ACCOUNT_SNAPSHOTS -> "Account Snapshot Reminders"
    }

    val subtitle = when (notificationType) {
        NotificationScheduleType.WEEKLY_SNAPSHOT -> "Choose the day of the week and delivery time for your calm weekly summary"
        NotificationScheduleType.INCOME -> "Receive alerts after SimpleFIN bank sync or at a scheduled time"
        NotificationScheduleType.SYNC_ISSUES -> "Immediate alerts when bank connections fail or expire"
        NotificationScheduleType.ACCOUNT_SNAPSHOTS -> "Set reminder frequency and pick accounts that need manual balance snapshots"
    }

    // Weekly Snapshot State
    var selectedDayOfWeek by remember(schedule.dayOfWeek) { mutableIntStateOf(schedule.dayOfWeek ?: 1) }

    // Time State (default 08:00 or 09:00)
    var timeText by remember(schedule.time) {
        mutableStateOf(if (schedule.time.isNullOrBlank() || schedule.time == "after_sync") "09:00" else schedule.time!!)
    }

    // Income Timing Mode
    var isScheduledIncome by remember(schedule.time) {
        mutableStateOf(schedule.time != null && schedule.time != "after_sync")
    }

    // Account Snapshots Cadence & Day of Month
    var cadence by remember { mutableStateOf("monthly") }
    var dayOfMonthText by remember(schedule.dayOfWeek) {
        mutableStateOf((schedule.dayOfWeek ?: 1).coerceIn(1, 28).toString())
    }
    var selectedAccountIds by remember(schedule.accountIds) {
        mutableStateOf(schedule.accountIds.toSet())
    }

    val daysOfWeek = remember {
        listOf(
            0 to "Sunday",
            1 to "Monday",
            2 to "Tuesday",
            3 to "Wednesday",
            4 to "Thursday",
            5 to "Friday",
            6 to "Saturday"
        )
    }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OrbitSheetHeader(
                title = title,
                subtitle = subtitle
            )

            when (notificationType) {
                NotificationScheduleType.WEEKLY_SNAPSHOT -> {
                    OrbitDropdownField(
                        label = "Day of Week",
                        selectedText = daysOfWeek.find { it.first == selectedDayOfWeek }?.second ?: "Monday",
                        options = daysOfWeek,
                        optionLabel = { it.second },
                        onOptionSelected = { selectedDayOfWeek = it.first }
                    )

                    OrbitTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Delivery Time (HH:MM)") },
                        placeholder = { Text("08:00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                NotificationScheduleType.INCOME -> {
                    Text(
                        text = "NOTIFICATION TIMING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isScheduledIncome,
                            onClick = { isScheduledIncome = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {},
                            label = { Text("After Sync", style = MaterialTheme.typography.labelSmall) }
                        )
                        SegmentedButton(
                            selected = isScheduledIncome,
                            onClick = { isScheduledIncome = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {},
                            label = { Text("Scheduled Time", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    if (isScheduledIncome) {
                        OrbitTextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text("Delivery Time (HH:MM)") },
                            placeholder = { Text("09:00") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Income alerts are sent immediately after transactions sync via SimpleFIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                NotificationScheduleType.SYNC_ISSUES -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = OrbitIcons.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Sync issue alerts are sent immediately when a bank sync fails or credentials expire, so there is no schedule to adjust.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                NotificationScheduleType.ACCOUNT_SNAPSHOTS -> {
                    Text(
                        text = "CADENCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = cadence == "monthly",
                            onClick = { cadence = "monthly" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {},
                            label = { Text("Monthly", style = MaterialTheme.typography.labelSmall) }
                        )
                        SegmentedButton(
                            selected = cadence == "quarterly",
                            onClick = { cadence = "quarterly" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {},
                            label = { Text("Quarterly", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    OrbitTextField(
                        value = dayOfMonthText,
                        onValueChange = { dayOfMonthText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Day of Month (1 - 28)") },
                        placeholder = { Text("1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OrbitTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Reminder Time (HH:MM)") },
                        placeholder = { Text("09:00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TRACKED ACCOUNTS (${selectedAccountIds.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (accounts.isEmpty()) {
                        Text(
                            text = "No accounts available in workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            accounts.forEach { account ->
                                val isChecked = selectedAccountIds.contains(account.id)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedAccountIds = if (isChecked) {
                                                    selectedAccountIds - account.id
                                                } else {
                                                    selectedAccountIds + account.id
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            val sub = listOfNotNull(account.institution?.takeIf { it.isNotBlank() }, account.type.name).joinToString(" · ")
                                            if (sub.isNotBlank()) {
                                                Text(
                                                    text = sub,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedAccountIds = if (checked) {
                                                    selectedAccountIds + account.id
                                                } else {
                                                    selectedAccountIds - account.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val updated = when (notificationType) {
                            NotificationScheduleType.WEEKLY_SNAPSHOT -> schedule.copy(
                                dayOfWeek = selectedDayOfWeek,
                                time = timeText.ifBlank { "08:00" }
                            )
                            NotificationScheduleType.INCOME -> schedule.copy(
                                time = if (isScheduledIncome) timeText.ifBlank { "09:00" } else "after_sync"
                            )
                            NotificationScheduleType.SYNC_ISSUES -> schedule
                            NotificationScheduleType.ACCOUNT_SNAPSHOTS -> {
                                val parsedDay = dayOfMonthText.toIntOrNull()?.coerceIn(1, 28) ?: 1
                                schedule.copy(
                                    dayOfWeek = parsedDay,
                                    time = timeText.ifBlank { "09:00" },
                                    accountIds = selectedAccountIds.toList()
                                )
                            }
                        }
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Schedule")
                }
            }
        }
    }
}
