package app.overbay.orbit.data

import app.overbay.orbit.data.network.AccountDto
import app.overbay.orbit.data.network.AuthConfigResponseDto
import app.overbay.orbit.data.network.MonthlyBudgetOverviewDto
import app.overbay.orbit.data.network.RuleListResponseDto
import app.overbay.orbit.data.network.ServerUrlNormalizer
import app.overbay.orbit.data.network.TransactionListResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteBackendSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun testSqliteAccountIntegerBooleansDecodeCleanly() {
        val raw = """
            {
              "id": 4,
              "household_id": 1,
              "name": "Chase Sapphire Reserve",
              "type": "credit",
              "current_balance": -1425.67,
              "currency": "USD",
              "is_manual": 0,
              "is_archived": 1,
              "institution_name": "Chase"
            }
        """.trimIndent()
        val dto = json.decodeFromString<AccountDto>(raw)
        val domain = dto.toDomain()
        assertEquals(4L, domain.id)
        assertEquals(-142567L, domain.currentBalanceCents)
        assertFalse(domain.isManual)
        assertTrue(domain.isArchived)
    }

    @Test
    fun testSqliteTransactionsResponseDecodesIntegerBooleansAndPagination() {
        val raw = """
            {
              "items": [
                {
                  "id": 1024,
                  "household_id": 1,
                  "account_id": 4,
                  "date": "2026-10-15",
                  "original_date": "2026-10-14",
                  "edited_date": "2026-10-15",
                  "merchant": "Whole Foods Market",
                  "original_merchant": "WHOLEFDS MKT #102",
                  "edited_merchant": "Whole Foods Market",
                  "edited_merchant_source": "rule",
                  "amount": -84.32,
                  "original_amount": -84.32,
                  "category_id": 12,
                  "original_category_id": null,
                  "edited_category_id": 12,
                  "edited_category_id_source": "user",
                  "is_transfer": 0,
                  "original_is_transfer": 0,
                  "edited_is_transfer": null,
                  "is_ignored": 1,
                  "original_is_ignored": 0,
                  "edited_is_ignored": 1,
                  "edited_is_ignored_source": "user",
                  "mha_eligible": 1,
                  "edited_mha_eligible": 1,
                  "edited_mha_eligible_source": "rule",
                  "notes": "Weekly groceries",
                  "source": "simplefin"
                }
              ],
              "total": 950,
              "page": 1,
              "limit": 200,
              "totalPages": 5
            }
        """.trimIndent()

        val response = json.decodeFromString<TransactionListResponseDto>(raw)
        assertEquals(950, response.total)
        assertEquals(5, response.totalPages)
        assertEquals(1, response.items.size)

        val txn = response.items.first().toDomain()
        assertEquals(1024L, txn.id)
        assertEquals(-8432L, txn.originalAmountCents)
        assertEquals("2026-10-14", txn.originalDate)
        assertEquals("2026-10-15", txn.editedDate)
        assertEquals("WHOLEFDS MKT #102", txn.originalMerchant)
        assertEquals("Whole Foods Market", txn.editedMerchant)
        assertFalse(txn.originalIsTransfer)
        assertNull(txn.editedIsTransfer)
        assertTrue(txn.effectiveIsIgnored)
        assertEquals(true, txn.effectiveMhaEligible)
    }

    @Test
    fun testSqliteBudgetOverviewDecodesAmountAndIntegerRollover() {
        val raw = """
            {
              "month": "2026-10",
              "budgeted": [
                {
                  "budget_id": 5,
                  "category": { "id": 12, "name": "Groceries", "color": "#22c55e" },
                  "amount": 650.0,
                  "spent": 412.45,
                  "rollover": 1
                }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<MonthlyBudgetOverviewDto>(raw)
        val budget = dto.toDomainList().first()
        assertEquals(12L, budget.categoryId)
        assertEquals(65000L, budget.amountCents)
        assertEquals(41245L, budget.spentCents)
        assertTrue(budget.rollover)
    }

    @Test
    fun testSqliteRulesDecodeFlexibleConditionValuesAndActionWithoutValue() {
        val raw = """
            {
              "items": [
                {
                  "id": 7,
                  "name": "Auto Transfer Rule",
                  "priority": 10,
                  "enabled": 1,
                  "conditions": [
                    { "field": "amount", "operator": "between", "value": [-500, -100] }
                  ],
                  "actions": [
                    { "type": "mark_transfer" },
                    { "type": "categorize", "value": 12 }
                  ]
                }
              ],
              "total": 1
            }
        """.trimIndent()

        val response = json.decodeFromString<RuleListResponseDto>(raw)
        val rule = response.items.first().toDomain()
        assertEquals(7L, rule.id)
        assertTrue(rule.enabled)
        assertEquals(1, rule.conditions.size)
        assertEquals("-500,-100", rule.conditions.first().value)
        assertEquals(2, rule.actions.size)
        assertEquals("", rule.actions[0].value)
        assertEquals("12", rule.actions[1].value)
    }

    @Test
    fun testAuthConfigAndServerUrlNormalizer() {
        val rawAuth = """
            {
              "authProvider": "oidc",
              "oidcEnabled": true,
              "oidcLoginLabel": "Continue with Authentik",
              "oidcLoginUrl": "/api/auth/oidc/login"
            }
        """.trimIndent()
        val cfg = json.decodeFromString<AuthConfigResponseDto>(rawAuth)
        assertTrue(cfg.oidcEnabled)
        assertEquals("Continue with Authentik", cfg.oidcLoginLabel)

        assertEquals("http://192.168.1.50:5019", ServerUrlNormalizer.normalize("192.168.1.50:5019/api/"))
        assertEquals("https://orbit.example.com", ServerUrlNormalizer.normalize("orbit.example.com/"))
    }
}
