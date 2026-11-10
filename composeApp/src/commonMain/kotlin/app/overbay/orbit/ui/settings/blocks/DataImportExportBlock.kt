package app.overbay.orbit.ui.settings.blocks

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalFileImportExportHelper
import kotlinx.coroutines.launch

@Composable
fun DataImportExportBlock(
    onReapplyAllRules: () -> Unit,
    onExportCsv: suspend () -> String,
    onExportBudgetingJson: suspend () -> String,
    onExportBackupJson: () -> String,
    onPickRestoreFile: (String) -> Unit,
    onOpenRestoreTextDialog: () -> Unit,
    onLoadSampleData: (() -> Unit)? = null,
    onResetWorkspace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fileHelper = LocalFileImportExportHelper.current
    val scope = rememberCoroutineScope()
    var showReapplyConfirm by remember { mutableStateOf(false) }

    if (showReapplyConfirm) {
        OrbitConfirmationDialog(
            title = "Reapply All Rules?",
            message = "Reapply all enabled rules to every transaction? Manual edits will stay protected, but rule-owned edits may change.",
            confirmText = "Reapply Rules",
            onConfirm = {
                showReapplyConfirm = false
                onReapplyAllRules()
            },
            onDismiss = { showReapplyConfirm = false }
        )
    }

    OrbitSectionCard(
        title = "Data Management & Backups",
        subtitle = "Rules automation, spreadsheet CSV export, and full snapshot backups",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Rules maintenance
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "RULES MAINTENANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Re-evaluate all historical transactions against your active categorization and rename rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = { showReapplyConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reapply All Rules")
                }
            }

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            // Budgeting data export (CSV / JSON)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SPREADSHEET EXPORT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Export clean budgeting rows containing dates, accounts, merchants, categories, and amounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csv = onExportCsv()
                                fileHelper.exportFile("orbit-transactions.csv", csv, "text/csv")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = onExportBudgetingJson()
                                fileHelper.exportFile("orbit-budgeting.json", json, "application/json")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export JSON")
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            // Full snapshot backup & restore
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Full Workspace Backup",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Complete portable backup of accounts, transactions, rules, budgets, goals, and upcoming items.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = OrbitIcons.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Note: SimpleFIN bank connection credentials are not included in backups and must be reconnected after restoring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val backup = onExportBackupJson()
                            fileHelper.exportFile("orbit-backup.orbit.json", backup, "application/json")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export Backup")
                    }

                    OutlinedButton(
                        onClick = {
                            fileHelper.pickAndImportFile("application/json") { raw ->
                                onPickRestoreFile(raw)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Restore Backup")
                    }
                }
            }

            if (onLoadSampleData != null || onResetWorkspace != null) {
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "WORKSPACE SANDBOX & RESET",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Populate realistic sample household data or reset this workspace to a clean empty state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onLoadSampleData != null) {
                            OutlinedButton(
                                onClick = onLoadSampleData,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Load Sample Data")
                            }
                        }
                        if (onResetWorkspace != null) {
                            OutlinedButton(
                                onClick = onResetWorkspace,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Reset Workspace",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
