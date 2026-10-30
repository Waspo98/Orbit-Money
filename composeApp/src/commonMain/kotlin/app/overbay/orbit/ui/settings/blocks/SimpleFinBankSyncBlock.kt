package app.overbay.orbit.ui.settings.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimpleFinBankSyncBlock(
    token: String?,
    cutoverDate: String? = null,
    syncLogs: List<SimpleFinSyncLogEntry> = emptyList(),
    isLogsExpanded: Boolean = false,
    mutedAccounts: List<String> = emptyList(),
    onUnmuteAccount: (String) -> Unit = {},
    onTokenChange: (String?) -> Unit = {},
    onSetupBridge: (token: String, cutoverDate: String?) -> Unit = { _, _ -> },
    onTriggerSync: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onToggleLogsExpanded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var rawToken by remember(token) { mutableStateOf(token ?: "") }
    val isConnected = !token.isNullOrBlank()

    val parsedCutover = remember(cutoverDate) {
        if (!cutoverDate.isNullOrBlank()) {
            runCatching { LocalDate.parse(cutoverDate) }.getOrNull()
        } else null
    } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    var selectedCutoverDate by remember(parsedCutover) { mutableStateOf(parsedCutover) }

    OrbitSectionCard(
        title = "SimpleFIN Bank Feeds",
        subtitle = "Connect read-only bank data directly without third-party aggregators",
        modifier = modifier
    ) {
        // Status Badge Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) "Bank Feed Active" else "No Connection Configured",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isConnected) "Direct SimpleFIN bridge configured" else "Obtain a claim token from bridge.simplefin.org",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isConnected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Setup Token input
        OutlinedTextField(
            value = rawToken,
            onValueChange = { rawToken = it },
            label = { Text("SimpleFIN Setup Token") },
            placeholder = { Text("https://bridge.simplefin.org/simplefin/claim/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Cutover Date Picker
        Text(
            text = "SYNC CUTOVER DATE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        DateFieldButton(
            date = selectedCutoverDate,
            onDateSelected = { selectedCutoverDate = it },
            label = "Import transactions on or after"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Save / Connect, Sync Now, Disconnect
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isConnected) {
                Button(
                    onClick = onTriggerSync,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sync Now")
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = {
                        onSetupBridge(rawToken, selectedCutoverDate.toString())
                        onTokenChange(rawToken.ifBlank { null })
                    },
                    enabled = rawToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect SimpleFIN")
                }
            }
        }

        // Expandable Sync History Logs
        if (isConnected && syncLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleLogsExpanded)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYNC HISTORY (${syncLogs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isLogsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isLogsExpanded,
                enter = expandVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeIn(),
                exit = shrinkVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        syncLogs.take(5).forEachIndexed { idx, log ->
                            if (idx > 0) HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.startedAt.take(19).replace("T", " "),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${log.accountsSynced} accounts · +${log.transactionsInserted} new",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = log.status.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.status.contains("error", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Muted SimpleFIN Account Warnings (tap chip to unmute)
        if (mutedAccounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "MUTED SYNC WARNINGS (${mutedAccounts.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mutedAccounts.forEach { accountLabel ->
                    AssistChip(
                        onClick = { onUnmuteAccount(accountLabel) },
                        label = { Text(accountLabel, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unmute $accountLabel",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
