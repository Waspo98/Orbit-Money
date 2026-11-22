package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import kotlin.math.abs

data class CandidateImportAccount(
    val key: String,
    val name: String,
    val institution: String?,
    val last4: String?,
    val type: AccountType,
    val rowCount: Int
)

data class CandidateImportRule(
    val originalName: String,
    val customName: String,
    val friendlyRuleName: String
)

data class CsvInspectionResult(
    val headers: List<String>,
    val sampleRows: List<Map<String, String>>,
    val detectedFormat: CsvImportFormat,
    val suggestedMapping: CsvColumnMapping
)

data class CsvImportPreview(
    val accounts: List<CandidateImportAccount>,
    val rules: List<CandidateImportRule>,
    val transactions: List<Transaction>,
    val totalRowCount: Int,
    val invalidRowCount: Int,
    val duplicateRowCount: Int,
    val detectedFormat: CsvImportFormat = CsvImportFormat.ROCKET_MONEY,
    val usedMapping: CsvColumnMapping? = null,
    val headers: List<String> = emptyList(),
    val sampleRows: List<Map<String, String>> = emptyList(),
    val transactionAccountKeys: Map<Long, String> = emptyMap(),
    val earliestTransactionDateIso: String? = null,
    val latestTransactionDateIso: String? = null
)

object CsvImportEngine {

    val REQUIRED_HEADERS = listOf(
        "Date", "Account Type", "Account Name", "Institution Name", "Name", "Amount", "Description", "Category"
    )

    fun parseCsvHeaders(csvContent: String): List<String> {
        val firstLine = csvContent.lines().firstOrNull { it.isNotBlank() } ?: return emptyList()
        return parseCsvLine(firstLine)
            .map { it.trim().removePrefix("\uFEFF") }
            .dropLastWhile { it.isEmpty() }
            .filter { it.isNotEmpty() }
    }

    fun parseCsvRows(csvContent: String): List<Map<String, String>> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headers = parseCsvLine(lines.first())
            .map { it.trim().removePrefix("\uFEFF") }
            .dropLastWhile { it.isEmpty() }
        val rows = mutableListOf<Map<String, String>>()

        for (i in 1 until lines.size) {
            val values = parseCsvLine(lines[i])
            if (values.size >= headers.size) {
                val row = headers.mapIndexed { idx, h -> h to values[idx].trim() }.toMap()
                rows.add(row)
            }
        }
        return rows
    }

    fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    values.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        values.add(sb.toString())
        return values
    }

    fun inspectCsv(csvContent: String): CsvInspectionResult {
        val headers = parseCsvHeaders(csvContent)
        val rows = parseCsvRows(csvContent)
        val (format, mapping) = CsvFormatDetector.detectFormat(headers)
        return CsvInspectionResult(
            headers = headers,
            sampleRows = rows.take(3),
            detectedFormat = format,
            suggestedMapping = mapping
        )
    }

    fun buildPreview(
        csvContent: String,
        existingExternalIds: Set<String> = emptySet(),
        categoriesByName: Map<String, Category> = emptyMap(),
        overrideMapping: CsvColumnMapping? = null
    ): CsvImportPreview {
        val headers = parseCsvHeaders(csvContent)
        val rows = parseCsvRows(csvContent)
        if (rows.isEmpty() || headers.isEmpty()) {
            return CsvImportPreview(emptyList(), emptyList(), emptyList(), 0, 0, 0)
        }

        val (detectedFormat, autoMapping) = CsvFormatDetector.detectFormat(headers)
        val mapping = overrideMapping ?: autoMapping
        if (!mapping.isValid) {
            throw IllegalArgumentException(
                "Could not identify Date, Merchant, and Amount columns. Please map your CSV columns below."
            )
        }

        val accountsMap = mutableMapOf<String, CandidateImportAccount>()
        val rulesMap = mutableMapOf<String, String>()
        val transactions = mutableListOf<Transaction>()
        val txAccountKeys = mutableMapOf<Long, String>()
        val seenExternalIds = HashSet<String>(existingExternalIds)

        var invalidRows = 0
        var duplicateRows = 0
        var syntheticId = 1L
        var earliestDate: String? = null
        var latestDate: String? = null

        for (row in rows) {
            val rawDate = row[mapping.dateColumn].orEmpty()
            val dateIso = CsvFormatDetector.normalizeDateToIso(rawDate)
            val rawMerchant = row[mapping.merchantColumn].orEmpty().trim()
            val description = mapping.descriptionColumn?.let { row[it]?.trim() }?.takeIf { it.isNotBlank() } ?: rawMerchant
            val merchant = rawMerchant.ifBlank { description.ifBlank { "Imported Transaction" } }

            val amountCents = resolveRowAmountCents(row, mapping)
            if (dateIso == null || amountCents == null) {
                invalidRows++
                continue
            }

            val acctNameRaw = mapping.accountNameColumn?.let { row[it]?.trim() }.orEmpty()
            val acctTypeStr = mapping.accountTypeColumn?.let { row[it]?.trim() }.orEmpty()
            val institution = mapping.institutionColumn?.let { row[it]?.trim() }?.takeIf { it.isNotBlank() }
            val last4 = mapping.accountNumberColumn?.let { row[it]?.trim() }?.takeIf { it.isNotBlank() }
            val acctKey = if (acctNameRaw.isNotBlank()) {
                "${institution.orEmpty()}|$acctNameRaw|${last4.orEmpty()}"
            } else {
                ""
            }

            if (acctKey.isNotBlank()) {
                val existingAcct = accountsMap[acctKey]
                if (existingAcct != null) {
                    accountsMap[acctKey] = existingAcct.copy(rowCount = existingAcct.rowCount + 1)
                } else {
                    accountsMap[acctKey] = CandidateImportAccount(
                        key = acctKey,
                        name = acctNameRaw,
                        institution = institution,
                        last4 = last4,
                        type = CsvFormatDetector.guessAccountType(acctTypeStr, acctNameRaw, institution),
                        rowCount = 1
                    )
                }
            }

            val customName = mapping.customNameColumn?.let { row[it]?.trim() }?.takeIf { it.isNotBlank() }
            if (customName != null && customName != merchant) {
                rulesMap[merchant] = customName
            }

            val extId = computeContentHash(dateIso, amountCents, description, acctKey)
            if (!seenExternalIds.add(extId)) {
                duplicateRows++
                continue
            }

            val categoryName = mapping.categoryColumn?.let { row[it]?.trim() }.orEmpty()
            val normalizedCatKey = categoryName.substringAfter(':').substringAfter('/').trim().lowercase()
            val matchedCategory = categoriesByName[categoryName.lowercase()] ?: categoriesByName[normalizedCatKey]
            val notes = mapping.notesColumn?.let { row[it]?.trim() }?.takeIf { it.isNotBlank() }

            if (earliestDate == null || dateIso < earliestDate) earliestDate = dateIso
            if (latestDate == null || dateIso > latestDate) latestDate = dateIso

            val txId = syntheticId++
            if (acctKey.isNotBlank()) {
                txAccountKeys[txId] = acctKey
            }

            transactions.add(
                Transaction(
                    id = txId,
                    accountId = 0L,
                    accountName = acctNameRaw,
                    originalDate = dateIso,
                    originalMerchant = merchant,
                    originalAmountCents = amountCents,
                    originalCategoryId = matchedCategory?.id,
                    originalDescription = description,
                    notes = notes,
                    isTransfer = matchedCategory?.isTransfer == true,
                    isIgnored = false,
                    editedMerchant = customName,
                    editedMerchantSource = if (customName != null) "rule:auto_imported" else null,
                    editSource = if (customName != null) EditSource.RULE else EditSource.NONE,
                    externalId = extId,
                    source = "csv_import"
                )
            )
        }

        val candidateRules = rulesMap.map { (ruleName, customName) ->
            CandidateImportRule(
                originalName = ruleName,
                customName = customName,
                friendlyRuleName = "Auto: $ruleName -> $customName"
            )
        }

        return CsvImportPreview(
            accounts = accountsMap.values.toList(),
            rules = candidateRules,
            transactions = transactions,
            totalRowCount = rows.size,
            invalidRowCount = invalidRows,
            duplicateRowCount = duplicateRows,
            detectedFormat = detectedFormat,
            usedMapping = mapping,
            headers = headers,
            sampleRows = rows.take(3),
            transactionAccountKeys = txAccountKeys,
            earliestTransactionDateIso = earliestDate,
            latestTransactionDateIso = latestDate
        )
    }

    private fun resolveRowAmountCents(row: Map<String, String>, mapping: CsvColumnMapping): Cents? {
        val rawCents = if (!mapping.inflowColumn.isNullOrBlank() && !mapping.outflowColumn.isNullOrBlank()) {
            val inflowRaw = row[mapping.inflowColumn].orEmpty().trim()
            val outflowRaw = row[mapping.outflowColumn].orEmpty().trim()
            if (inflowRaw.isBlank() && outflowRaw.isBlank()) {
                return null
            }
            val inflow = if (inflowRaw.isBlank()) 0L else {
                CsvFormatDetector.parseSignedMoneyToCents(inflowRaw) ?: return null
            }
            val outflow = if (outflowRaw.isBlank()) 0L else {
                CsvFormatDetector.parseSignedMoneyToCents(outflowRaw) ?: return null
            }
            abs(inflow) - abs(outflow)
        } else {
            val col = mapping.amountColumn ?: return null
            val parsed = CsvFormatDetector.parseSignedMoneyToCents(row[col].orEmpty()) ?: return null
            val txType = mapping.transactionTypeColumn?.let { row[it]?.trim()?.lowercase() }.orEmpty()
            when {
                txType == "debit" || txType == "expense" || txType == "withdrawal" -> -abs(parsed)
                txType == "credit" || txType == "income" || txType == "deposit" -> abs(parsed)
                else -> parsed
            }
        }
        return if (mapping.invertSign) -rawCents else rawCents
    }

    fun guessAccountType(rmType: String, accountName: String, institution: String?): AccountType =
        CsvFormatDetector.guessAccountType(rmType, accountName, institution)

    fun computeContentHash(date: String, amountCents: Cents, description: String, accountKey: String): String {
        val input = "$date|$amountCents|$description|$accountKey"
        return "hash_" + abs(input.hashCode()).toString(16).padStart(8, '0')
    }
}
