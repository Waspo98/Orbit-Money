package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvImportEngineTest {

    private val sampleCategories = listOf(
        Category(id = 10L, name = "Groceries"),
        Category(id = 20L, name = "Income", isIncome = true)
    ).associateBy { it.name.lowercase() }

    @Test
    fun detectsAndParsesMintDebitCreditSigns() {
        val mintCsv = """
            Date,Description,Original Description,Amount,Transaction Type,Category,Account Name,Labels,Notes
            10/14/2026,Whole Foods,WHOLE FOODS MKT,82.40,debit,Groceries,Chase Checking,,Weekly run
            10/15/2026,Paycheck,ACME CORP PAYROLL,3100.00,credit,Income,Chase Checking,,
        """.trimIndent()

        val preview = CsvImportEngine.buildPreview(mintCsv, categoriesByName = sampleCategories)
        assertEquals(CsvImportFormat.MINT, preview.detectedFormat)
        assertEquals(2, preview.transactions.size)
        assertEquals("2026-10-14", preview.earliestTransactionDateIso)
        assertEquals("2026-10-15", preview.latestTransactionDateIso)
        assertEquals(-8240L, preview.transactions[0].originalAmountCents)
        assertEquals(310000L, preview.transactions[1].originalAmountCents)
        assertEquals(10L, preview.transactions[0].originalCategoryId)
        assertEquals(1, preview.accounts.size)
        assertEquals(AccountType.CHECKING, preview.accounts.first().type)
    }

    @Test
    fun detectsAndParsesYnabInflowOutflowColumns() {
        val ynabCsv = """
            Account,Flag,Date,Payee,Category Group/Category,Category Group,Category,Memo,Outflow,Inflow,Cleared
            Sapphire Reserve,,11/02/2026,Trader Joe's,Everyday: Groceries,Everyday,Groceries,Snacks,${'$'}48.15,${'$'}0.00,Cleared
            Ally Checking,,11/05/2026,Employer,Inflow: Ready to Assign,Inflow,Income,Payday,${'$'}0.00,"${'$'}2,450.00",Cleared
        """.trimIndent()

        val preview = CsvImportEngine.buildPreview(ynabCsv, categoriesByName = sampleCategories)
        assertEquals(CsvImportFormat.YNAB, preview.detectedFormat)
        assertEquals(2, preview.transactions.size)
        assertEquals(-4815L, preview.transactions[0].originalAmountCents)
        assertEquals(245000L, preview.transactions[1].originalAmountCents)
        assertEquals("2026-11-05", preview.latestTransactionDateIso)
        assertEquals(2, preview.accounts.size)
        assertEquals(AccountType.CREDIT, preview.accounts.first { it.name == "Sapphire Reserve" }.type)
    }

    @Test
    fun detectsAndParsesMonarchCsv() {
        val monarchCsv = """
            Date,Merchant,Category,Account,Original Statement,Notes,Amount,Tags
            2026-09-20,Costco,Groceries,Venture X,COSTCO WHSE #123,Bulk,-142.30,
        """.trimIndent()

        val preview = CsvImportEngine.buildPreview(monarchCsv, categoriesByName = sampleCategories)
        assertEquals(CsvImportFormat.MONARCH, preview.detectedFormat)
        assertEquals(1, preview.transactions.size)
        assertEquals(-14230L, preview.transactions.first().originalAmountCents)
        assertEquals("Bulk", preview.transactions.first().notes)
    }

    @Test
    fun genericColumnMapperSupportsSignInversionAndCustomHeaders() {
        val bankCsv = """
            Posted On,Vendor Name,Charge Amount
            08/19/2026,Local Bakery,12.50
            08/21/2026,Refund Bakery,-12.50
        """.trimIndent()

        val inspection = CsvImportEngine.inspectCsv(bankCsv)
        assertEquals(CsvImportFormat.GENERIC, inspection.detectedFormat)
        assertEquals(3, inspection.headers.size)

        val customMapping = CsvColumnMapping(
            dateColumn = "Posted On",
            merchantColumn = "Vendor Name",
            amountColumn = "Charge Amount",
            invertSign = true
        )
        val preview = CsvImportEngine.buildPreview(bankCsv, overrideMapping = customMapping)
        assertEquals(2, preview.transactions.size)
        assertEquals(-1250L, preview.transactions[0].originalAmountCents)
        assertEquals(1250L, preview.transactions[1].originalAmountCents)
        assertNotNull(preview.latestTransactionDateIso)
        assertTrue(preview.accounts.isEmpty())
    }

    @Test
    fun trailingCommaHeadersAndInvalidInflowOutflowCellsHandledSafely() {
        val csv = """
            Account,Date,Payee,Outflow,Inflow,
            Checking,11/02/2026,Valid Row,25.00,,
            Checking,11/03/2026,Bad Row,N/A,,
        """.trimIndent()

        val preview = CsvImportEngine.buildPreview(csv)
        assertEquals(CsvImportFormat.YNAB, preview.detectedFormat)
        assertEquals(1, preview.transactions.size)
        assertEquals(-2500L, preview.transactions.single().originalAmountCents)
        assertEquals(1, preview.invalidRowCount)
    }

    @Test
    fun rejectsImpossibleCalendarDates() {
        assertNull(CsvFormatDetector.normalizeDateToIso("2026-02-31"))
        assertNull(CsvFormatDetector.normalizeDateToIso("04/31/2026"))
        assertEquals("2024-02-29", CsvFormatDetector.normalizeDateToIso("2024-02-29"))
    }

    @Test
    fun parsesCurrencyAndParentheticalNegativeAmounts() {
        assertEquals(-4815L, CsvFormatDetector.parseSignedMoneyToCents("$(48.15)"))
        assertEquals(-4815L, CsvFormatDetector.parseSignedMoneyToCents("($48.15)"))
        assertEquals(-123450L, CsvFormatDetector.parseSignedMoneyToCents("$(1,234.50)"))
        assertEquals(4815L, CsvFormatDetector.parseSignedMoneyToCents("$48.15"))
    }
}
