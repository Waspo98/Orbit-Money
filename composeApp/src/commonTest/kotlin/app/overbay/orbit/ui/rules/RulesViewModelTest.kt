package app.overbay.orbit.ui.rules

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: RulesViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = RulesViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testRulesSorting_sortedByPriorityDescending() = runTest(testDispatcher) {
        val rule1 = TransactionRule(id = 1L, name = "Low Priority", priority = 10, enabled = true)
        val rule2 = TransactionRule(id = 2L, name = "High Priority", priority = 100, enabled = true)
        val rule3 = TransactionRule(id = 3L, name = "Medium Priority", priority = 50, enabled = true)

        repository.saveRule(rule1)
        repository.saveRule(rule2)
        repository.saveRule(rule3)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val rules = viewModel.uiState.value.rules
        assertEquals(3, rules.size)
        assertEquals(2L, rules[0].id) // 100
        assertEquals(3L, rules[1].id) // 50
        assertEquals(1L, rules[2].id) // 10

        collectJob.cancel()
    }

    @Test
    fun testToggleRuleEnabled_updatesRepository() = runTest(testDispatcher) {
        val rule = TransactionRule(id = 10L, name = "Auto Categorize", priority = 10, enabled = true)
        repository.saveRule(rule)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.toggleRuleEnabled(10L, enabled = false)
        testScheduler.advanceUntilIdle()

        val updated = repository.rules.value.first { it.id == 10L }
        assertFalse(updated.enabled)

        collectJob.cancel()
    }

    @Test
    fun testLiveMatchCountPreview_matchesExistingTransactions() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Uber Trip", originalAmountCents = -2500L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-05-02", originalMerchant = "Uber Eats", originalAmountCents = -3500L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "2026-05-03", originalMerchant = "Lyft Ride", originalAmountCents = -1800L))

        val conditions = listOf(
            RuleCondition(
                field = RuleField.MERCHANT,
                operator = RuleOperator.CONTAINS,
                value = "Uber"
            )
        )

        val matchCount = viewModel.previewMatchCount(conditions)
        assertEquals(2, matchCount, "Should match both Uber transactions")

        val emptyConditionsMatch = viewModel.previewMatchCount(emptyList())
        assertEquals(0, emptyConditionsMatch)
    }

    @Test
    fun testSaveAndEditRule_crudSheetFlows() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Open add rule
        viewModel.openAddRule()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertEquals(10, viewModel.uiState.value.editingRule?.priority)

        // 2. Save rule
        val newRule = TransactionRule(
            id = 0L,
            name = "Coffee Categorizer",
            priority = 20,
            enabled = true,
            conditions = listOf(RuleCondition(RuleField.MERCHANT, RuleOperator.CONTAINS, "Starbucks")),
            actions = listOf(RuleAction(RuleActionType.SET_CATEGORY, "5"))
        )
        viewModel.saveRule(newRule)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditorSheet)
        val created = repository.rules.value.firstOrNull { it.name == "Coffee Categorizer" }
        assertNotNull(created)
        assertEquals(20, created.priority)

        // 3. Open edit rule
        viewModel.openEditRule(created)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertEquals(created.id, viewModel.uiState.value.editingRule?.id)

        // 4. Update rule
        viewModel.saveRule(created.copy(name = "Updated Coffee", priority = 30))
        testScheduler.advanceUntilIdle()

        val updated = repository.rules.value.first { it.id == created.id }
        assertEquals("Updated Coffee", updated.name)
        assertEquals(30, updated.priority)

        collectJob.cancel()
    }

    @Test
    fun testDeleteRule_requestDismissConfirmFlows() = runTest(testDispatcher) {
        val rule = TransactionRule(id = 55L, name = "Delete Me", priority = 10, enabled = true)
        repository.saveRule(rule)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Request delete
        viewModel.requestDeleteRule(55L)
        testScheduler.advanceUntilIdle()
        assertEquals(55L, viewModel.uiState.value.deleteConfirmRuleId)

        // Dismiss
        viewModel.dismissDeleteRule()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.deleteConfirmRuleId)

        // Confirm delete
        viewModel.deleteRule(55L)
        testScheduler.advanceUntilIdle()
        assertFalse(repository.rules.value.any { it.id == 55L })

        collectJob.cancel()
    }

    @Test
    fun testRunRulesNow_executesRuleMatchingOnTransactions() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        val catGas = Category(id = 77L, name = "Gas", isIncome = false, isTransfer = false)
        repository.saveCategory(catGas)

        // Rule to set category to 77L for Chevron
        val rule = TransactionRule(
            id = 1L,
            name = "Gas Rule",
            priority = 10,
            enabled = true,
            conditions = listOf(RuleCondition(RuleField.MERCHANT, RuleOperator.CONTAINS, "Chevron")),
            actions = listOf(RuleAction(RuleActionType.SET_CATEGORY, "77"))
        )
        repository.saveRule(rule)

        // Transaction matching rule
        repository.saveTransaction(
            Transaction(
                id = 101L,
                accountId = 1L,
                originalDate = "2026-05-01",
                originalMerchant = "Chevron Gas Station",
                originalAmountCents = -4500L,
                originalCategoryId = null
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.runRulesNow()
        testScheduler.advanceUntilIdle()

        val matchedTxn = repository.transactions.value.first { it.id == 101L }
        assertEquals(77L, matchedTxn.effectiveCategoryId, "Rule must categorize Chevron transaction")

        collectJob.cancel()
    }
}
