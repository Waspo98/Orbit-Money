package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.CandidateImportRule
import app.overbay.orbit.domain.CsvImportEngine
import app.overbay.orbit.domain.CsvImportPreview
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportSheet(
    accounts: List<Account>,
    categories: List<Category>,
    existingTransactions: List<Transaction>,
    onImportConfirmed: (transactions: List<Transaction>, rules: List<TransactionRule>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val fileHelper = LocalFileImportExportHelper.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var csvText by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var previewResult by remember { mutableStateOf<CsvImportPreview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var includeRules by remember { mutableStateOf(true) }

    val sampleCsv = remember {
        """Date,Account Type,Account Name,Institution Name,Name,Amount,Description,Category
2026-10-15,Checking,Everyday Checking,Chase,Whole Foods,-45.50,GROCERY STORE,Groceries
2026-10-16,Credit Card,Sapphire Reserve,Chase,Chevron,-35.00,GAS STATION,Auto & Gas
2026-10-18,Checking,Everyday Checking,Chase,Direct Deposit,2500.00,PAYROLL DEPOSIT,Income"""
    }

    fun parseCsv() {
        if (csvText.isBlank()) {
            errorMessage = "Please paste CSV text or load sample data."
            previewResult = null
            return
        }
        try {
            val existingExtIds = existingTransactions.mapNotNull { it.externalId }.toSet()
            val catMap = categories.associateBy { it.name.lowercase() }
            val preview = CsvImportEngine.buildPreview(csvText, existingExtIds, catMap)
            previewResult = preview
            errorMessage = null
            haptics.confirm()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to parse CSV"
            previewResult = null
            haptics.reject()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Import Transactions CSV",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        haptics.tick()
                        fileHelper.pickAndImportFile("text/csv") { content ->
                            csvText = content
                            parseCsv()
                        }
                    }) {
                        Text("Choose CSV File")
                    }
                    TextButton(onClick = {
                        haptics.tick()
                        csvText = sampleCsv
                        parseCsv()
                    }) {
                        Text("Load Sample CSV")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Account Selector
            if (accounts.isNotEmpty()) {
                Text(
                    text = "TARGET ACCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { acct ->
                        FilterChip(
                            selected = selectedAccountId == acct.id,
                            onClick = {
                                haptics.tick()
                                selectedAccountId = acct.id
                            },
                            label = { Text(acct.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // CSV Text Area
            OutlinedTextField(
                value = csvText,
                onValueChange = {
                    csvText = it
                    if (previewResult != null) previewResult = null
                },
                label = { Text("CSV Data (Header row required)") },
                placeholder = { Text("Paste Date, Account Type, Account Name, Institution Name, Name, Amount, Description, Category...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                maxLines = 8
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Parse / Preview Button (Title Case)
            if (previewResult == null) {
                Button(
                    onClick = { parseCsv() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Preview",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Import")
                }
            }

            // Error Message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Preview Result Card
            val preview = previewResult
            if (preview != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "IMPORT SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Transactions ready to import: ${preview.transactions.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Total rows in file: ${preview.totalRowCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (preview.duplicateRowCount > 0) {
                            Text(
                                text = "• Duplicates skipped: ${preview.duplicateRowCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (preview.invalidRowCount > 0) {
                            Text(
                                text = "• Invalid rows skipped: ${preview.invalidRowCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (preview.rules.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = includeRules,
                                    onCheckedChange = { includeRules = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Generate ${preview.rules.size} merchant renaming rule(s)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            haptics.confirm()
                            val accountName = accounts.find { it.id == selectedAccountId }?.name ?: ""
                            val assignedTxns = preview.transactions.map {
                                it.copy(accountId = selectedAccountId, accountName = accountName)
                            }
                            val generatedRules = if (includeRules) {
                                preview.rules.map { candidate ->
                                    TransactionRule(
                                        name = candidate.friendlyRuleName,
                                        conditions = listOf(
                                            RuleCondition(
                                                field = RuleConditionField.MERCHANT,
                                                operator = RuleOperator.CONTAINS,
                                                value = candidate.originalName
                                            )
                                        ),
                                        actions = listOf(
                                            RuleAction(
                                                type = RuleActionType.RENAME,
                                                value = candidate.customName,
                                                textValue = candidate.customName
                                            )
                                        ),
                                        enabled = true
                                    )
                                }
                            } else emptyList()

                            onImportConfirmed(assignedTxns, generatedRules)
                        },
                        enabled = preview.transactions.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import (${preview.transactions.size})")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
