package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingDirection
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Empirical challenge suite for OrbitWorkspaceSnapshot serialization and DemoSeedGenerator.
 */
class SnapshotAndSeedStressTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // -------------------------------------------------------------------------
    // 1. DemoSeedGenerator Tests
    // -------------------------------------------------------------------------

    @Test
    fun testDemoSeedGenerator_producesPopulatedWorkspace() {
        val snapshot = DemoSeedGenerator.generateDemoSnapshot("2026-04-15")

        assertEquals(1, snapshot.schemaVersion)
        assertTrue(snapshot.accounts.isNotEmpty(), "Demo accounts must not be empty")
        assertTrue(snapshot.categories.isNotEmpty(), "Demo categories must not be empty")
        assertTrue(snapshot.budgets.isNotEmpty(), "Demo budgets must not be empty")
        assertTrue(snapshot.transactions.isNotEmpty(), "Demo transactions must not be empty")
        assertTrue(snapshot.rules.isNotEmpty(), "Demo rules must not be empty")
        assertTrue(snapshot.upcomingItems.isNotEmpty(), "Demo upcoming items must not be empty")
        assertTrue(snapshot.members.isNotEmpty(), "Demo members must not be empty")
        assertNotNull(snapshot.mhaTaxProfile, "MHA tax profile must be set")
    }

    @Test
    fun testDemoSeedGenerator_accountsDiversityAndBalances() {
        val snapshot = DemoSeedGenerator.generateDemoSnapshot()
        val accounts = snapshot.accounts

        assertEquals(9, accounts.size)

        val types = accounts.map { it.type }.toSet()
        assertTrue(types.contains(AccountType.CHECKING))
        assertTrue(types.contains(AccountType.SAVINGS))
        assertTrue(types.contains(AccountType.CREDIT))
        assertTrue(types.contains(AccountType.INVESTMENT))
        assertTrue(types.contains(AccountType.LOAN))
        assertTrue(types.contains(AccountType.MORTGAGE))
        assertTrue(types.contains(AccountType.CASH))
        assertTrue(types.contains(AccountType.OTHER))

        // Check mortgage account with home equity
        val mortgage = accounts.first { it.type == AccountType.MORTGAGE }
        assertNotNull(mortgage.estimatedValueCents)
        assertTrue(mortgage.estimatedValueCents!! > 0L)
        assertNotNull(mortgage.homeEquityCents)
        assertEquals(78600_00L, mortgage.homeEquityCents)
    }

    @Test
    fun testDemoSeedGenerator_categoriesAndBudgets() {
        val snapshot = DemoSeedGenerator.generateDemoSnapshot()
        assertEquals(11, snapshot.categories.size)
        assertEquals(6, snapshot.budgets.size)

        // All budget category IDs must refer to existing categories
        val categoryIds = snapshot.categories.map { it.id }.toSet()
        snapshot.budgets.forEach { budget ->
            assertTrue(categoryIds.contains(budget.categoryId), "Budget category ID ${budget.categoryId} must exist in categories")
            assertTrue(budget.amountCents > 0L, "Budget amount must be positive")
        }
    }

    @Test
    fun testDemoSeedGenerator_transactionsAndTransfers() {
        val snapshot = DemoSeedGenerator.generateDemoSnapshot()
        val txns = snapshot.transactions

        assertEquals(6, txns.size)

        // Find transfer pair
        val transfer1 = txns.firstOrNull { it.id == 105L }
        val transfer2 = txns.firstOrNull { it.id == 106L }

        assertNotNull(transfer1)
        assertNotNull(transfer2)
        assertTrue(transfer1.isTransfer)
        assertTrue(transfer2.isTransfer)
        assertEquals(106L, transfer1.transferPairId)
        assertEquals(105L, transfer2.transferPairId)
        assertEquals(-500_00L, transfer1.originalAmountCents)
        assertEquals(500_00L, transfer2.originalAmountCents)
        assertEquals(-transfer1.originalAmountCents, transfer2.originalAmountCents)
    }

    // -------------------------------------------------------------------------
    // 2. OrbitWorkspaceSnapshot JSON Serialization & Deserialization
    // -------------------------------------------------------------------------

    @Test
    fun testSnapshotSerialization_roundTripDemoSnapshot() {
        val original = DemoSeedGenerator.generateDemoSnapshot("2026-04-15")

        // 1. Serialize to JSON
        val encodedJson = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), original)
        assertTrue(encodedJson.isNotEmpty())
        assertTrue(encodedJson.contains("Demo Everyday Checking"))
        assertTrue(encodedJson.contains("Grace Harbor Payroll"))

        // 2. Deserialize back from JSON
        val decoded = json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), encodedJson)

        // 3. Verify integrity
        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.accounts.size, decoded.accounts.size)
        assertEquals(original.categories.size, decoded.categories.size)
        assertEquals(original.budgets.size, decoded.budgets.size)
        assertEquals(original.transactions.size, decoded.transactions.size)
        assertEquals(original.rules.size, decoded.rules.size)
        assertEquals(original.upcomingItems.size, decoded.upcomingItems.size)
        assertEquals(original.members.size, decoded.members.size)
        assertEquals(original.mhaTaxProfile.state, decoded.mhaTaxProfile.state)
        assertEquals(original, decoded)
    }

    @Test
    fun testSnapshotSerialization_emptySnapshotRoundTrip() {
        val emptySnapshot = OrbitWorkspaceSnapshot.empty("2026-10-25T12:00:00Z")
        val encoded = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), emptySnapshot)
        val decoded = json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), encoded)

        assertEquals(emptySnapshot.schemaVersion, decoded.schemaVersion)
        assertEquals(emptySnapshot.exportedAt, decoded.exportedAt)
        assertEquals(emptySnapshot.household.name, decoded.household.name)
        assertTrue(decoded.accounts.isEmpty())
        assertTrue(decoded.transactions.isEmpty())
        assertEquals(emptySnapshot, decoded)
    }

    @Test
    fun testSnapshotSerialization_resilienceToUnknownKeys() {
        // Forward compatibility: when new features add fields, existing client ignores unknown keys
        val jsonWithExtra = """
        {
            "schemaVersion": 1,
            "exportedAt": "2026-10-25T12:00:00Z",
            "unknownFutureField": "should_be_ignored",
            "futureObject": { "nested": 123 },
            "accounts": [],
            "categories": [],
            "transactions": []
        }
        """.trimIndent()

        val decoded = json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), jsonWithExtra)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("2026-10-25T12:00:00Z", decoded.exportedAt)
        assertTrue(decoded.accounts.isEmpty())
    }

    @Test
    fun testSnapshotSerialization_specialCharactersAndExtremeAmounts() {
        val specialTxn = Transaction(
            id = 999L,
            accountId = 1L,
            originalDate = "2026-04-15",
            originalMerchant = "Special Café & \"Boulangerie\" ☕",
            originalAmountCents = -9223372036854775807L / 4, // Large Long cents
            notes = "Multi-line\nNotes with\ttabs & Unicode \uD83D\uDE80",
            source = "test"
        )
        val testSnapshot = OrbitWorkspaceSnapshot(
            schemaVersion = 1,
            transactions = listOf(specialTxn)
        )

        val encoded = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), testSnapshot)
        val decoded = json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), encoded)

        assertEquals(1, decoded.transactions.size)
        val resultTxn = decoded.transactions[0]
        assertEquals("Special Café & \"Boulangerie\" ☕", resultTxn.originalMerchant)
        assertEquals(specialTxn.originalAmountCents, resultTxn.originalAmountCents)
        assertEquals("Multi-line\nNotes with\ttabs & Unicode \uD83D\uDE80", resultTxn.notes)
    }
}
