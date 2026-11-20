package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.settings.NotificationScheduleType
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner
import app.overbay.orbit.util.LocalOrbitHaptics

private val DAY_ABBREVS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

fun LazyListScope.notificationsSectionItems(
    preferences: OrbitPreferences,
    pushConfig: PushNotificationConfigInfo,
    isTesting: Boolean,
    onSendTestNotification: () -> Unit,
    onUpdateNotificationPreferences: (NotificationPreferencesConfig) -> Unit,
    onOpenScheduleSheet: (NotificationScheduleType) -> Unit
) {
    val config = preferences.notificationPreferences

    item(key = "notifications_master") {
        val haptics = LocalOrbitHaptics.current
        OrbitSectionCard(
            title = "Notifications & Delivery",
            subtitle = "Weekly summaries, deposit alerts, and balance reminders"
        ) {
            OrbitSwitchRow(
                title = "Enable Notifications",
                subtitle = "Receive calm summaries and helpful account reminders",
                checked = config.enabled,
                onCheckedChange = { onUpdateNotificationPreferences(config.copy(enabled = it)) },
                showContainer = false
            )

            OrbitAccordionVisibility(visible = config.enabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                    OrbitSwitchRow(
                        title = "Show Dollar Amounts",
                        subtitle = "Include exact balances and figures in notification previews",
                        checked = config.showAmounts,
                        onCheckedChange = { onUpdateNotificationPreferences(config.copy(showAmounts = it)) },
                        showContainer = false
                    )
                    HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    OrbitMiniStatRow(
                        label = "Delivery Method",
                        value = if (pushConfig.configured) "Push Notifications Ready" else "Local Device Alerts",
                        valueColor = if (pushConfig.configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OrbitMiniStatRow(
                        label = "Connected Devices",
                        value = "${pushConfig.activeSubscriptionCount} ${if (pushConfig.activeSubscriptionCount == 1) "device" else "devices"}"
                    )

                    val canSendTest = !isTesting && (!pushConfig.configured || pushConfig.activeSubscriptionCount > 0)
                    if (pushConfig.configured && pushConfig.activeSubscriptionCount == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsCalloutBanner(
                            icon = OrbitIcons.InfoCircle,
                            text = "No active push devices registered yet. Open Orbit on a supported device to register push notifications."
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { haptics.tick(); onSendTestNotification() },
                        enabled = canSendTest,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = "Send Test Notification")
                    }
                }
            }
        }
    }

    if (config.enabled) {
        item(key = "notifications_schedules") {
            OrbitSectionCard(
                title = "Scheduled Alerts",
                subtitle = "Tap a schedule pill to customize timing and tracked accounts"
            ) {
                val weeklyDay = DAY_ABBREVS.getOrElse(config.weeklySnapshot.dayOfWeek ?: 1) { "Mon" }
                val weeklyTime = format12HourTime(config.weeklySnapshot.time, "8:00 AM")
                ScheduledAlertRow(
                    title = "Weekly Summary",
                    subtitle = "Weekly recap of net worth, savings progress, and top spending",
                    checked = config.weeklySnapshot.enabled,
                    onCheckedChange = { onUpdateNotificationPreferences(config.copy(weeklySnapshot = config.weeklySnapshot.copy(enabled = it))) },
                    schedulePillLabel = "$weeklyDay · $weeklyTime",
                    onCustomize = { onOpenScheduleSheet(NotificationScheduleType.WEEKLY_SNAPSHOT) }
                )
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                val isIncomeScheduled = config.income.timing == "scheduled" ||
                    (config.income.timing == null && !config.income.time.isNullOrBlank() && config.income.time != "after_sync")
                val incomeLabel = if (isIncomeScheduled) "Scheduled · ${format12HourTime(config.income.time, "9:00 AM")}" else "After bank sync"
                ScheduledAlertRow(
                    title = "Income & Paycheck Alerts",
                    subtitle = "Notify when paychecks and deposits over $100 post",
                    checked = config.income.enabled,
                    onCheckedChange = { onUpdateNotificationPreferences(config.copy(income = config.income.copy(enabled = it))) },
                    schedulePillLabel = incomeLabel,
                    onCustomize = { onOpenScheduleSheet(NotificationScheduleType.INCOME) }
                )
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                ScheduledAlertRow(
                    title = "Bank Sync Issues",
                    subtitle = "Notify when bank connections require attention",
                    checked = config.syncIssues.enabled,
                    onCheckedChange = { onUpdateNotificationPreferences(config.copy(syncIssues = config.syncIssues.copy(enabled = it))) },
                    passiveBadgeLabel = "Immediate",
                    onCustomize = null
                )
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                val snapshotDay = config.accountSnapshots.dayOfMonth ?: config.accountSnapshots.dayOfWeek ?: 1
                val snapshotCadence = if (config.accountSnapshots.cadence == "quarterly") "Quarterly" else "Monthly"
                val snapshotTime = format12HourTime(config.accountSnapshots.time, "9:00 AM")
                val snapshotCount = config.accountSnapshots.accountIds.size
                val acctSuffix = if (snapshotCount == 1) "1 acct" else "$snapshotCount accts"
                ScheduledAlertRow(
                    title = "Manual Balance Reminders",
                    subtitle = "Periodic reminder to update balances on manual accounts",
                    checked = config.accountSnapshots.enabled,
                    onCheckedChange = { onUpdateNotificationPreferences(config.copy(accountSnapshots = config.accountSnapshots.copy(enabled = it))) },
                    schedulePillLabel = "$snapshotCadence · Day $snapshotDay · $snapshotTime · $acctSuffix",
                    onCustomize = { onOpenScheduleSheet(NotificationScheduleType.ACCOUNT_SNAPSHOTS) }
                )
            }
        }
    }
}

@Composable
private fun ScheduledAlertRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    schedulePillLabel: String? = null,
    passiveBadgeLabel: String? = null,
    onCustomize: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (schedulePillLabel != null && onCustomize != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { haptics.tick(); onCustomize() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = OrbitIcons.ViewSliders, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                        Text(
                            text = schedulePillLabel,
                            style = TabularNumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (passiveBadgeLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OrbitBadge(text = passiveBadgeLabel)
            }
        }

        Switch(checked = checked, onCheckedChange = { haptics.tick(); onCheckedChange(it) })
    }
}

private fun format12HourTime(raw: String?, fallback: String): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank() || trimmed == "after_sync") return fallback
    val parts = trimmed.split(":")
    if (parts.size != 2) return fallback
    val hour24 = parts[0].toIntOrNull() ?: return fallback
    val minute = parts[1].toIntOrNull() ?: return fallback
    if (hour24 !in 0..23 || minute !in 0..59) return fallback
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "$hour12:${minute.toString().padStart(2, '0')} $period"
}
