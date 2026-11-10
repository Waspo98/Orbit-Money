package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.settings.dialogs.NotificationScheduleSheet
import app.overbay.orbit.ui.settings.dialogs.NotificationScheduleType

@Composable
fun PushNotificationsBlock(
    preferences: OrbitPreferences,
    pushConfig: PushNotificationConfigInfo,
    isTesting: Boolean,
    onSendTestNotification: () -> Unit,
    onUpdateNotificationPreferences: (NotificationPreferencesConfig) -> Unit,
    accounts: List<Account> = emptyList(),
    modifier: Modifier = Modifier
) {
    val config = preferences.notificationPreferences
    var activeScheduleType by remember { mutableStateOf<NotificationScheduleType?>(null) }

    activeScheduleType?.let { type ->
        val currentSchedule = when (type) {
            NotificationScheduleType.WEEKLY_SNAPSHOT -> config.weeklySnapshot
            NotificationScheduleType.INCOME -> config.income
            NotificationScheduleType.SYNC_ISSUES -> config.syncIssues
            NotificationScheduleType.ACCOUNT_SNAPSHOTS -> config.accountSnapshots
        }
        NotificationScheduleSheet(
            notificationType = type,
            schedule = currentSchedule,
            accounts = accounts,
            onSave = { updatedSchedule ->
                val updatedConfig = when (type) {
                    NotificationScheduleType.WEEKLY_SNAPSHOT -> config.copy(weeklySnapshot = updatedSchedule)
                    NotificationScheduleType.INCOME -> config.copy(income = updatedSchedule)
                    NotificationScheduleType.SYNC_ISSUES -> config.copy(syncIssues = updatedSchedule)
                    NotificationScheduleType.ACCOUNT_SNAPSHOTS -> config.copy(accountSnapshots = updatedSchedule)
                }
                onUpdateNotificationPreferences(updatedConfig)
                activeScheduleType = null
            },
            onDismiss = { activeScheduleType = null }
        )
    }

    OrbitSectionCard(
        title = "Notifications & Reminders",
        subtitle = "Weekly summaries, deposit alerts, and account reminders",
        modifier = modifier
    ) {
        OrbitSwitchRow(
            title = "Enable Notifications",
            subtitle = "Receive weekly summaries and helpful account reminders",
            checked = config.enabled,
            onCheckedChange = { onUpdateNotificationPreferences(config.copy(enabled = it)) }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OrbitMiniStatRow(
                    label = "Delivery Method",
                    value = if (pushConfig.configured) "Push Notifications Ready" else "Local Device Alerts",
                    valueColor = if (pushConfig.configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                OrbitMiniStatRow(
                    label = "Connected Devices",
                    value = "${pushConfig.activeSubscriptionCount} devices"
                )

                Button(
                    onClick = onSendTestNotification,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    Text("Send Test Notification")
                }
            }
        }

        if (config.enabled) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            OrbitSwitchRow(
                title = "Show Dollar Amounts",
                subtitle = "Include exact balances and transaction figures in notification previews",
                checked = config.showAmounts,
                onCheckedChange = { onUpdateNotificationPreferences(config.copy(showAmounts = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Scheduled Alerts",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val weeklyDay = dayNames.getOrElse(config.weeklySnapshot.dayOfWeek ?: 1) { "Mon" }
            val weeklyTime = config.weeklySnapshot.time ?: "08:00"

            NotificationPreferenceRow(
                title = "Weekly Summary",
                subtitle = "Weekly recap of net worth changes, savings progress, and top spending categories",
                checked = config.weeklySnapshot.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(weeklySnapshot = config.weeklySnapshot.copy(enabled = it)))
                },
                onCustomize = { activeScheduleType = NotificationScheduleType.WEEKLY_SNAPSHOT },
                scheduleChipText = "$weeklyDay at $weeklyTime"
            )

            Spacer(modifier = Modifier.height(10.dp))

            val isIncomeScheduled = config.income.time != null && config.income.time != "after_sync"
            val incomeScheduleLabel = if (isIncomeScheduled) "Scheduled at ${config.income.time}" else "After bank sync"

            NotificationPreferenceRow(
                title = "Income & Paycheck Alerts",
                subtitle = "Notify when paychecks and deposits over $100 post",
                checked = config.income.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(income = config.income.copy(enabled = it)))
                },
                onCustomize = { activeScheduleType = NotificationScheduleType.INCOME },
                scheduleChipText = incomeScheduleLabel
            )

            Spacer(modifier = Modifier.height(10.dp))

            NotificationPreferenceRow(
                title = "Bank Sync Issues",
                subtitle = "Notify when bank connections require attention",
                checked = config.syncIssues.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(syncIssues = config.syncIssues.copy(enabled = it)))
                },
                onCustomize = { activeScheduleType = NotificationScheduleType.SYNC_ISSUES },
                scheduleChipText = "Immediate"
            )

            Spacer(modifier = Modifier.height(10.dp))

            val snapshotDay = config.accountSnapshots.dayOfWeek ?: 1
            val snapshotTime = config.accountSnapshots.time ?: "09:00"
            val snapshotCount = config.accountSnapshots.accountIds.size
            val snapshotLabel = "Day $snapshotDay at $snapshotTime · $snapshotCount accts"

            NotificationPreferenceRow(
                title = "Manual Balance Reminders",
                subtitle = "Periodic reminder to update balances on manual accounts",
                checked = config.accountSnapshots.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(accountSnapshots = config.accountSnapshots.copy(enabled = it)))
                },
                onCustomize = { activeScheduleType = NotificationScheduleType.ACCOUNT_SNAPSHOTS },
                scheduleChipText = snapshotLabel
            )
        }
    }
}

@Composable
private fun NotificationPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onCustomize: () -> Unit,
    scheduleChipText: String? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCustomize)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (scheduleChipText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "⚙ $scheduleChipText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
