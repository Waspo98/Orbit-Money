package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AccountType

enum class CsvImportFormat(val id: String, val displayName: String) {
    ROCKET_MONEY("rocket_money", "Rocket Money"),
    MINT("mint", "Mint"),
    MONARCH("monarch", "Monarch Money"),
    YNAB("ynab", "YNAB"),
    GENERIC("csv", "Generic CSV")
}

data class CsvColumnMapping(
    val dateColumn: String = "",
    val merchantColumn: String = "",
    val amountColumn: String? = null,
    val inflowColumn: String? = null,
    val outflowColumn: String? = null,
    val descriptionColumn: String? = null,
    val categoryColumn: String? = null,
    val accountNameColumn: String? = null,
    val accountTypeColumn: String? = null,
    val institutionColumn: String? = null,
    val accountNumberColumn: String? = null,
    val customNameColumn: String? = null,
    val notesColumn: String? = null,
    val transactionTypeColumn: String? = null,
    val invertSign: Boolean = false
) {
    val isValid: Boolean
        get() = dateColumn.isNotBlank() &&
            merchantColumn.isNotBlank() &&
            (!amountColumn.isNullOrBlank() || (!inflowColumn.isNullOrBlank() && !outflowColumn.isNullOrBlank()))
}

object CsvFormatDetector {

    fun detectFormat(headers: List<String>): Pair<CsvImportFormat, CsvColumnMapping> {
        val byLower = headers.associateBy { it.trim().lowercase() }
        fun col(vararg names: String): String? = names.firstNotNullOfOrNull { byLower[it.lowercase()] }

        // 1. Rocket Money
        val rmDate = col("Date")
        val rmAcctType = col("Account Type")
        val rmAcctName = col("Account Name")
        val rmInst = col("Institution Name")
        val rmName = col("Name")
        val rmAmount = col("Amount")
        val rmDesc = col("Description")
        val rmCat = col("Category")
        if (rmDate != null && rmAcctType != null && rmAcctName != null && rmInst != null &&
            rmName != null && rmAmount != null && rmDesc != null && rmCat != null
        ) {
            return CsvImportFormat.ROCKET_MONEY to CsvColumnMapping(
                dateColumn = rmDate,
                merchantColumn = rmName,
                amountColumn = rmAmount,
                descriptionColumn = rmDesc,
                categoryColumn = rmCat,
                accountNameColumn = rmAcctName,
                accountTypeColumn = rmAcctType,
                institutionColumn = rmInst,
                accountNumberColumn = col("Account Number"),
                customNameColumn = col("Custom Name"),
                notesColumn = col("Note", "Notes")
            )
        }

        // 2. YNAB
        val ynabDate = col("Date")
        val ynabPayee = col("Payee")
        val ynabOutflow = col("Outflow")
        val ynabInflow = col("Inflow")
        if (ynabDate != null && ynabPayee != null && ynabOutflow != null && ynabInflow != null) {
            return CsvImportFormat.YNAB to CsvColumnMapping(
                dateColumn = ynabDate,
                merchantColumn = ynabPayee,
                inflowColumn = ynabInflow,
                outflowColumn = ynabOutflow,
                descriptionColumn = col("Memo") ?: ynabPayee,
                categoryColumn = col("Category", "Category Group/Category"),
                accountNameColumn = col("Account"),
                notesColumn = col("Memo")
            )
        }

        // 3. Monarch Money
        val monDate = col("Date")
        val monMerchant = col("Merchant")
        val monAmount = col("Amount")
        val monAccount = col("Account")
        if (monDate != null && monMerchant != null && monAmount != null && monAccount != null) {
            return CsvImportFormat.MONARCH to CsvColumnMapping(
                dateColumn = monDate,
                merchantColumn = monMerchant,
                amountColumn = monAmount,
                descriptionColumn = col("Original Statement", "Description") ?: monMerchant,
                categoryColumn = col("Category"),
                accountNameColumn = monAccount,
                notesColumn = col("Notes", "Note")
            )
        }

        // 4. Mint
        val mintDate = col("Date")
        val mintDesc = col("Description")
        val mintAmount = col("Amount")
        val mintTxType = col("Transaction Type")
        if (mintDate != null && mintDesc != null && mintAmount != null && mintTxType != null) {
            return CsvImportFormat.MINT to CsvColumnMapping(
                dateColumn = mintDate,
                merchantColumn = mintDesc,
                amountColumn = mintAmount,
                descriptionColumn = col("Original Description") ?: mintDesc,
                categoryColumn = col("Category"),
                accountNameColumn = col("Account Name", "Account"),
                notesColumn = col("Notes", "Note"),
                transactionTypeColumn = mintTxType
            )
        }

        // 5. Generic Bank CSV best-effort mapping
        val genericDate = col("Date", "Transaction Date", "Post Date", "Posted Date", "Posting Date", "Trans Date")
            ?: headers.firstOrNull { it.contains("date", ignoreCase = true) }.orEmpty()
        val genericMerchant = col("Merchant", "Payee", "Name", "Description", "Original Description", "Memo", "Title")
            ?: headers.firstOrNull { h ->
                h != genericDate && (h.contains("desc", ignoreCase = true) || h.contains("merch", ignoreCase = true) || h.contains("name", ignoreCase = true) || h.contains("payee", ignoreCase = true))
            }
            ?: headers.firstOrNull { it != genericDate }.orEmpty()
        val genericAmount = col("Amount", "Transaction Amount", "Amount (USD)", "Value", "Total")
            ?: headers.firstOrNull { it.contains("amount", ignoreCase = true) }
        val genericInflow = if (genericAmount == null) col("Credit", "Inflow", "Deposit", "Deposits") else null
        val genericOutflow = if (genericAmount == null) col("Debit", "Outflow", "Withdrawal", "Withdrawals") else null

        return CsvImportFormat.GENERIC to CsvColumnMapping(
            dateColumn = genericDate,
            merchantColumn = genericMerchant,
            amountColumn = genericAmount,
            inflowColumn = genericInflow,
            outflowColumn = genericOutflow,
            descriptionColumn = col("Description", "Original Description", "Original Statement", "Memo") ?: genericMerchant,
            categoryColumn = col("Category", "Subcategory"),
            accountNameColumn = col("Account Name", "Account"),
            accountTypeColumn = col("Account Type", "Type"),
            institutionColumn = col("Institution Name", "Institution", "Bank"),
            notesColumn = col("Notes", "Note", "Memo"),
            transactionTypeColumn = col("Transaction Type", "Debit/Credit")
        )
    }

    fun normalizeDateToIso(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val datePart = trimmed.substringBefore('T').substringBefore(' ').trim()

        // YYYY-MM-DD or YYYY/MM/DD
        val isoParts = datePart.split('-', '/')
        if (isoParts.size == 3 && isoParts[0].length == 4) {
            val y = isoParts[0].toIntOrNull() ?: return null
            val m = isoParts[1].toIntOrNull() ?: return null
            val d = isoParts[2].toIntOrNull() ?: return null
            return formatValidatedIso(y, m, d)
        }

        // MM/DD/YYYY or M/D/YYYY or MM-DD-YYYY
        if (isoParts.size == 3 && isoParts[2].length == 4) {
            val m = isoParts[0].toIntOrNull() ?: return null
            val d = isoParts[1].toIntOrNull() ?: return null
            val y = isoParts[2].toIntOrNull() ?: return null
            return formatValidatedIso(y, m, d)
        }

        // MM/DD/YY
        if (isoParts.size == 3 && isoParts[2].length == 2) {
            val m = isoParts[0].toIntOrNull() ?: return null
            val d = isoParts[1].toIntOrNull() ?: return null
            val yy = isoParts[2].toIntOrNull() ?: return null
            val y = if (yy >= 70) 1900 + yy else 2000 + yy
            return formatValidatedIso(y, m, d)
        }
        return null
    }

    fun parseSignedMoneyToCents(raw: String): Cents? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val isParenNegative = trimmed.startsWith("(") && trimmed.endsWith(")")
        val stripped = trimmed
            .removePrefix("(")
            .removeSuffix(")")
            .replace("$", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (stripped.isBlank() || stripped.toDoubleOrNull() == null) return null
        val cents = MoneyUtils.dollarsToCents(stripped)
        return if (isParenNegative && cents > 0L) -cents else cents
    }

    fun guessAccountType(typeHint: String, accountName: String, institution: String?): AccountType {
        val hintLower = typeHint.trim().lowercase()
        if (hintLower.contains("credit")) return AccountType.CREDIT
        if (hintLower.contains("mortgage")) return AccountType.MORTGAGE
        if (hintLower.contains("loan")) return AccountType.LOAN
        if (hintLower.contains("invest") || hintLower.contains("retire") || hintLower.contains("brokerage")) {
            return AccountType.INVESTMENT
        }
        if (hintLower.contains("saving")) return AccountType.SAVINGS
        if (hintLower.contains("checking")) return AccountType.CHECKING

        val text = "$accountName ${institution.orEmpty()}".lowercase()
        return when {
            Regex("credit|card|sapphire|amex|visa|mastercard|discover|venture|freedom").containsMatchIn(text) -> AccountType.CREDIT
            Regex("mortgage").containsMatchIn(text) -> AccountType.MORTGAGE
            Regex("loan|heloc").containsMatchIn(text) -> AccountType.LOAN
            Regex("betterment|vanguard|fidelity|schwab|ira|401k|roth|brokerage|taxable|hsa").containsMatchIn(text) -> AccountType.INVESTMENT
            Regex("savings|reserve|money market").containsMatchIn(text) -> AccountType.SAVINGS
            else -> AccountType.CHECKING
        }
    }

    private fun formatValidatedIso(y: Int, m: Int, d: Int): String? {
        if (y !in 1970..2100 || m !in 1..12 || d !in 1..31) return null
        val mm = m.toString().padStart(2, '0')
        val dd = d.toString().padStart(2, '0')
        return "$y-$mm-$dd"
    }
}
