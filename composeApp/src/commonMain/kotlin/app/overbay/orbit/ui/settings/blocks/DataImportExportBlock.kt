package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
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
    modifier: Modifier = Modifier
) {
    val fileHelper = LocalFileImportExportHelper.current
    val scope = rememberCoroutineScope()

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
                    onClick = onReapplyAllRules,
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
                    text = "PORTABLE WORKSPACE BACKUP (.ORBIT.JSON)",
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
        }
    }
}
