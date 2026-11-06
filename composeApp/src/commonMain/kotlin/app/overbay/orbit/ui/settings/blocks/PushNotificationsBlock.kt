package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow

@Composable
fun PushNotificationsBlock(
    preferences: OrbitPreferences,
    pushConfig: PushNotificationConfigInfo,
    isTesting: Boolean,
    onSendTestNotification: () -> Unit,
    onUpdateNotificationPreferences: (NotificationPreferencesConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = preferences.notificationPreferences

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

            OrbitSwitchRow(
                title = "Weekly Summary",
                subtitle = "Weekly recap of net worth changes, savings progress, and top spending categories",
                checked = config.weeklySnapshot.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(weeklySnapshot = config.weeklySnapshot.copy(enabled = it)))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            OrbitSwitchRow(
                title = "Income & Paycheck Alerts",
                subtitle = "Notify when paychecks and deposits over $100 post",
                checked = config.income.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(income = config.income.copy(enabled = it)))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            OrbitSwitchRow(
                title = "Bank Sync Issues",
                subtitle = "Notify when bank connections require attention",
                checked = config.syncIssues.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(syncIssues = config.syncIssues.copy(enabled = it)))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            OrbitSwitchRow(
                title = "Manual Balance Reminders",
                subtitle = "Periodic reminder to update balances on manual accounts",
                checked = config.accountSnapshots.enabled,
                onCheckedChange = {
                    onUpdateNotificationPreferences(config.copy(accountSnapshots = config.accountSnapshots.copy(enabled = it)))
                }
            )
        }
    }
}
