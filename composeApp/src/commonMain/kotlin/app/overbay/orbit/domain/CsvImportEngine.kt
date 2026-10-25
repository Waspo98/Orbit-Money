package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import kotlin.math.abs
import kotlin.math.roundToLong

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

data class CsvImportPreview(
    val accounts: List<CandidateImportAccount>,
    val rules: List<CandidateImportRule>,
    val transactions: List<Transaction>,
    val totalRowCount: Int,
    val invalidRowCount: Int,
    val duplicateRowCount: Int
)

object CsvImportEngine {

    val REQUIRED_HEADERS = listOf(
        "Date", "Account Type", "Account Name", "Institution Name", "Name", "Amount", "Description", "Category"
    )

    fun parseCsvRows(csvContent: String): List<Map<String, String>> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headers = parseCsvLine(lines.first()).map { it.trim() }
        val rows = mutableListOf<Map<String, String>>()

        for (i in 1 until lines.size) {
            val line = lines[i]
            val values = parseCsvLine(line)
            if (values.size == headers.size) {
                val row = headers.zip(values.map { it.trim() }).toMap()
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
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    values.add(sb.toString())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        values.add(sb.toString())
        return values
    }

    fun buildPreview(
        csvContent: String,
        existingExternalIds: Set<String> = emptySet(),
        categoriesByName: Map<String, Category> = emptyMap()
    ): CsvImportPreview {
        val rows = parseCsvRows(csvContent)
        if (rows.isEmpty()) {
            return CsvImportPreview(emptyList(), emptyList(), emptyList(), 0, 0, 0)
        }

        val firstRow = rows.first()
        val missingHeaders = REQUIRED_HEADERS.filter { !firstRow.containsKey(it) }
        if (missingHeaders.isNotEmpty()) {
            throw IllegalArgumentException("Missing required headers: ${missingHeaders.joinToString()}")
        }

        val accountsMap = mutableMapOf<String, CandidateImportAccount>()
        val rulesMap = mutableMapOf<String, String>()
        val transactions = mutableListOf<Transaction>()

        var invalidRows = 0
        var duplicateRows = 0
        var syntheticId = 1L

        for (row in rows) {
            val date = row["Date"] ?: ""
            val acctTypeStr = row["Account Type"] ?: ""
            val acctName = row["Account Name"]?.ifBlank { "Unknown Account" } ?: "Unknown Account"
            val institution = row["Institution Name"]?.ifBlank { null }
            val last4 = row["Account Number"]?.ifBlank { null }
            val merchant = row["Name"] ?: ""
            val customName = row["Custom Name"]?.ifBlank { null }
            val amountStr = row["Amount"] ?: ""
            val description = row["Description"] ?: ""
            val categoryName = row["Category"] ?: ""

            val acctKey = "$institution|$acctName|$last4"
            val guessedType = guessAccountType(acctTypeStr, acctName, institution)

            val existingAcct = accountsMap[acctKey]
            if (existingAcct != null) {
                accountsMap[acctKey] = existingAcct.copy(rowCount = existingAcct.rowCount + 1)
            } else {
                accountsMap[acctKey] = CandidateImportAccount(
                    key = acctKey,
                    name = acctName,
                    institution = institution,
                    last4 = last4,
                    type = guessedType,
                    rowCount = 1
                )
            }

            if (!customName.isNullOrBlank() && customName != merchant) {
                rulesMap[merchant] = customName
            }

            val amountVal = amountStr.replace("$", "").replace(",", "").toDoubleOrNull()
            if (amountVal == null || date.isBlank()) {
                invalidRows++
                continue
            }

            val amountCents = (amountVal * 100.0).roundToLong()
            val extId = computeContentHash(date, amountCents, description, acctKey)

            if (existingExternalIds.contains(extId)) {
                duplicateRows++
                continue
            }

            val matchedCategory = categoriesByName[categoryName.lowercase()]

            transactions.add(
                Transaction(
                    id = syntheticId++,
                    accountId = 0L,
                    originalDate = date,
                    originalMerchant = merchant,
                    originalAmountCents = amountCents,
                    originalCategoryId = matchedCategory?.id,
                    originalDescription = description,
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
            duplicateRowCount = duplicateRows
        )
    }

    fun guessAccountType(rmType: String, accountName: String, institution: String?): AccountType {
        if (rmType.equals("Credit Card", ignoreCase = true)) return AccountType.CREDIT
        if (!rmType.equals("Cash", ignoreCase = true)) return AccountType.OTHER

        val text = "$accountName ${institution.orEmpty()}".lowercase()
        return when {
            Regex("betterment|vanguard|fidelity|schwab|ira|401k|roth|brokerage|taxable").containsMatchIn(text) -> AccountType.INVESTMENT
            Regex("savings|reserve").containsMatchIn(text) -> AccountType.SAVINGS
            else -> AccountType.CHECKING
        }
    }

    fun computeContentHash(date: String, amountCents: Cents, description: String, accountKey: String): String {
        val input = "$date|$amountCents|$description|$accountKey"
        return "hash_" + abs(input.hashCode()).toString(16).padStart(8, '0')
    }
}
