package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitTextField
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.bankFeedsSectionItems(
    preferences: OrbitPreferences,
    syncLogs: List<SimpleFinSyncLogEntry>,
    isLogsExpanded: Boolean,
    isConnecting: Boolean,
    isSyncing: Boolean,
    onSetupBridge: (token: String, cutoverDate: String?) -> Unit,
    onTriggerSync: () -> Unit,
    onRequestDisconnect: () -> Unit,
    onToggleLogsExpanded: () -> Unit,
    onUnmuteAccount: (String) -> Unit
) {
    val isConnected = !preferences.simpleFinToken.isNullOrBlank()

    item(key = "bank_feeds_connection") {
        val uriHandler = LocalUriHandler.current
        var rawToken by remember(preferences.simpleFinToken) { mutableStateOf(preferences.simpleFinToken.orEmpty()) }
        var showReconnectEditor by remember { mutableStateOf(false) }
        val parsedCutover = remember(preferences.simpleFinCutoverDate) {
            preferences.simpleFinCutoverDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        var selectedCutoverDate by remember(parsedCutover) { mutableStateOf(parsedCutover) }
        val btnShape = RoundedCornerShape(14.dp)

        OrbitSectionCard(
            title = "SimpleFIN Bank Feeds",
            subtitle = "Connect read-only bank feeds without third-party aggregators"
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isConnected) {
                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://beta-bridge.simplefin.org/") },
                        shape = btnShape,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Open SimpleFIN Bridge", maxLines = 1, overflow = TextOverflow.Ellipsis) }

                    SimpleFinBridgeForm(
                        rawToken = rawToken,
                        onTokenChange = { rawToken = it },
                        selectedCutoverDate = selectedCutoverDate,
                        onCutoverSelected = { selectedCutoverDate = it },
                        isConnecting = isConnecting,
                        submitLabel = "Connect SimpleFIN",
                        showCutoverHeading = true,
                        onSubmit = { onSetupBridge(rawToken.trim(), selectedCutoverDate.toString()) }
                    )
                } else {
                    Surface(
                        shape = btnShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Bank Feed Active", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Cutover: ${preferences.simpleFinCutoverDate ?: "Not set"}", style = TabularNumStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onTriggerSync, enabled = !isSyncing, shape = btnShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Sync Now", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onRequestDisconnect,
                            shape = btnShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Disconnect", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }

                    HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                    DisclosureToggleRow(title = "Reconnect or Update Cutover Date", expanded = showReconnectEditor, onToggle = { showReconnectEditor = !showReconnectEditor })
                    OrbitAccordionVisibility(visible = showReconnectEditor) {
                        SimpleFinBridgeForm(
                            rawToken = rawToken,
                            onTokenChange = { rawToken = it },
                            selectedCutoverDate = selectedCutoverDate,
                            onCutoverSelected = { selectedCutoverDate = it },
                            isConnecting = isConnecting,
                            submitLabel = "Update Connection",
                            showCutoverHeading = false,
                            onSubmit = { onSetupBridge(rawToken.trim(), selectedCutoverDate.toString()) }
                        )
                    }
                }
            }
        }
    }

    if (isConnected && syncLogs.isNotEmpty()) {
        item(key = "bank_feeds_logs") {
            OrbitSectionCard(title = "Sync History (${syncLogs.size})", subtitle = "Recent SimpleFIN import runs") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DisclosureToggleRow(
                        title = if (isLogsExpanded) "Hide Sync History" else "Show Sync History",
                        expanded = isLogsExpanded,
                        onToggle = onToggleLogsExpanded
                    )

                    OrbitAccordionVisibility(visible = isLogsExpanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            syncLogs.take(20).forEachIndexed { index, log ->
                                val isError = log.status.contains("error", ignoreCase = true)
                                val subtitleText = buildString {
                                    append("${log.accountsSynced} accounts · +${log.transactionsInserted} new")
                                    if (log.transactionsUpdated > 0) append(" · ${log.transactionsUpdated} updated")
                                    if (!log.errorMessage.isNullOrBlank()) append(" · Error: ${log.errorMessage}")
                                }
                                OrbitCompactListRow(
                                    title = log.startedAt.take(19).replace("T", " "),
                                    subtitle = subtitleText,
                                    showTopDivider = index > 0,
                                    trailingContent = {
                                        OrbitBadge(
                                            text = log.status.uppercase(),
                                            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (preferences.mutedSimpleFinAccounts.isNotEmpty()) {
        item(key = "bank_feeds_muted") {
            OrbitSectionCard(title = "Muted Sync Warnings", subtitle = "Tap an account to resume warnings") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    preferences.mutedSimpleFinAccounts.forEach { accountLabel ->
                        InputChip(
                            selected = true,
                            onClick = { onUnmuteAccount(accountLabel) },
                            label = { Text(accountLabel, style = MaterialTheme.typography.labelMedium) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Unmute $accountLabel", modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureToggleRow(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SimpleFinBridgeForm(
    rawToken: String,
    onTokenChange: (String) -> Unit,
    selectedCutoverDate: LocalDate,
    onCutoverSelected: (LocalDate) -> Unit,
    isConnecting: Boolean,
    submitLabel: String,
    showCutoverHeading: Boolean,
    onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OrbitTextField(
            value = rawToken,
            onValueChange = onTokenChange,
            label = { Text("SimpleFIN Setup Token") },
            placeholder = { Text("https://bridge.simplefin.org/simplefin/claim/...") },
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        if (showCutoverHeading) {
            Text("Sync Cutover Date", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        DateFieldButton(date = selectedCutoverDate, onDateSelected = onCutoverSelected, label = "Import transactions on or after")
        Button(
            onClick = onSubmit,
            enabled = rawToken.isNotBlank() && !isConnecting,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(submitLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
