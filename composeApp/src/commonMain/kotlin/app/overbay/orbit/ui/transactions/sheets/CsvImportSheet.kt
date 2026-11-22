package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.*
import app.overbay.orbit.domain.CsvColumnMapping
import app.overbay.orbit.domain.CsvImportEngine
import app.overbay.orbit.domain.CsvImportFormat
import app.overbay.orbit.domain.CsvImportPreview
import app.overbay.orbit.domain.CsvInspectionResult
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CsvImportSheet(
    accounts: List<Account>,
    categories: List<Category>,
    existingTransactions: List<Transaction>,
    onImportConfirmed: (transactions: List<Transaction>, rules: List<TransactionRule>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onImportPreview: ((fileName: String, preview: CsvImportPreview, fallbackAccountId: Long?, includeRules: Boolean) -> Unit)? = null,
    isImporting: Boolean = false,
    externalError: String? = null,
    importBatches: List<ImportBatchRecord> = emptyList(),
    onUndoBatch: ((Long) -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val fileHelper = LocalFileImportExportHelper.current

    var csvText by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("import.csv") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var inspection by remember { mutableStateOf<CsvInspectionResult?>(null) }
    var activeMapping by remember { mutableStateOf<CsvColumnMapping?>(null) }
    var mapperExpanded by remember { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<CsvImportPreview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var includeRules by remember { mutableStateOf(true) }

    val sampleCsv = remember {
        "Date,Account Type,Account Name,Institution Name,Name,Amount,Description,Category\n" +
        "2026-10-15,Checking,Everyday Checking,Chase,Whole Foods,-45.50,GROCERY STORE,Groceries\n" +
        "2026-10-16,Credit Card,Sapphire Reserve,Chase,Chevron,-35.00,GAS STATION,Auto & Gas\n" +
        "2026-10-18,Checking,Everyday Checking,Chase,Direct Deposit,2500.00,PAYROLL DEPOSIT,Income"
    }

    fun parseCsv(overrideMap: CsvColumnMapping? = null) {
        if (csvText.isBlank()) {
            errorMessage = "Please choose a CSV file or paste CSV data."
            previewResult = null
            inspection = null
            return
        }
        try {
            val inspected = CsvImportEngine.inspectCsv(csvText)
            inspection = inspected
            val chosenMapping = overrideMap ?: inspected.suggestedMapping
            activeMapping = chosenMapping
            if (overrideMap == null) {
                mapperExpanded = inspected.detectedFormat == CsvImportFormat.GENERIC
            }
            val existingExtIds = existingTransactions.mapNotNull { it.externalId }.toSet()
            val catMap = categories.associateBy { it.name.lowercase() }
            val preview = CsvImportEngine.buildPreview(csvText, existingExtIds, catMap, chosenMapping)
            previewResult = preview
            errorMessage = null
            haptics.confirm()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to parse CSV"
            previewResult = null
            haptics.reject()
        }
    }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            val preview = previewResult
            OrbitModalFooter(
                confirmText = when {
                    isImporting -> "Importing..."
                    preview == null -> "Preview Import"
                    else -> "Import (${preview.transactions.size})"
                },
                onDismiss = onDismiss,
                onConfirm = {
                    if (preview == null) {
                        parseCsv(activeMapping)
                    } else if (onImportPreview != null) {
                        onImportPreview(fileName, preview, selectedAccountId, includeRules)
                    } else {
                        val fallbackId = selectedAccountId ?: 1L
                        val accountName = accounts.find { it.id == fallbackId }?.name ?: ""
                        val assignedTxns = preview.transactions.map {
                            it.copy(accountId = fallbackId, accountName = it.accountName.ifBlank { accountName })
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
                    }
                },
                confirmEnabled = !isImporting && if (preview == null) csvText.isNotBlank() else preview.transactions.isNotEmpty()
            )
        }
    ) {
        OrbitSheetHeader(
            title = "Import Transactions CSV",
            subtitle = "Rocket Money, Mint, Monarch, YNAB, or any bank CSV",
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        fileHelper.pickAndImportFile("text/csv") { content ->
                            fileName = "imported.csv"
                            csvText = content
                            parseCsv()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Choose CSV File")
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        fileName = "sample_rocket_money.csv"
                        csvText = sampleCsv
                        parseCsv()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Load Sample CSV")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (accounts.isNotEmpty() && (previewResult == null || previewResult?.accounts.isNullOrEmpty())) {
                Text(
                    text = "FALLBACK ACCOUNT (WHEN CSV HAS NO ACCOUNT COLUMN)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            OrbitTextField(
                value = csvText,
                onValueChange = {
                    csvText = it
                    if (previewResult != null) previewResult = null
                    if (inspection != null) inspection = null
                },
                label = { Text("CSV Data (Header row required)") },
                placeholder = { Text("Paste CSV rows from Rocket Money, Mint, Monarch, YNAB, or your bank...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                maxLines = 8
            )

            val currentInspection = inspection
            val currentMapping = activeMapping
            OrbitAccordionVisibility(visible = currentInspection != null && currentMapping != null) {
                if (currentInspection != null && currentMapping != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CsvColumnMapperPanel(
                            detectedFormat = currentInspection.detectedFormat,
                            headers = currentInspection.headers,
                            mapping = currentMapping,
                            sampleRows = currentInspection.sampleRows,
                            expanded = mapperExpanded,
                            onExpandedChange = { mapperExpanded = it },
                            onMappingChange = { updated ->
                                activeMapping = updated
                                parseCsv(updated)
                            }
                        )
                    }
                }
            }

            val displayError = errorMessage ?: externalError
            var lastError by remember { mutableStateOf<String?>(null) }
            if (displayError != null) lastError = displayError
            OrbitAccordionVisibility(visible = displayError != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                text = displayError ?: lastError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            var cachedPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
            if (previewResult != null) cachedPreview = previewResult
            OrbitAccordionVisibility(visible = previewResult != null) {
                val preview = previewResult ?: cachedPreview ?: return@OrbitAccordionVisibility
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                    text = "IMPORT SUMMARY (${preview.detectedFormat.displayName.uppercase()})",
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
                            if (preview.accounts.isNotEmpty()) {
                                Text(
                                    text = "• Accounts detected: ${preview.accounts.size} (${preview.accounts.joinToString { it.name }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (preview.earliestTransactionDateIso != null && preview.latestTransactionDateIso != null) {
                                Text(
                                    text = "• Date range: ${preview.earliestTransactionDateIso} to ${preview.latestTransactionDateIso}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OrbitAccordionVisibility(visible = preview.duplicateRowCount > 0) {
                                Text(
                                    text = "• Duplicates skipped: ${preview.duplicateRowCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            OrbitAccordionVisibility(visible = preview.invalidRowCount > 0) {
                                Text(
                                    text = "• Invalid rows skipped: ${preview.invalidRowCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            OrbitAccordionVisibility(visible = preview.rules.isNotEmpty()) {
                                Column {
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
                    }
                }
            }

            OrbitAccordionVisibility(visible = importBatches.isNotEmpty() && onUndoBatch != null) {
                if (onUndoBatch != null) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        ImportBatchesSection(
                            batches = importBatches,
                            onUndoBatch = onUndoBatch
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
