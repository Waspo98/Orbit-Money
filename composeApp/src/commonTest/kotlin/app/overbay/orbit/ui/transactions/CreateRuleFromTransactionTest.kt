package app.overbay.orbit.ui.transactions

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.network.RuleDto
import app.overbay.orbit.data.network.RuleWireMapper
import app.overbay.orbit.ui.rules.RuleDraftDefaults
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRuleFromTransactionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: TransactionsViewModel
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = TransactionsViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testOpenCreateRuleModal_exposesTransactionWithoutSavingPrematurely() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveTransaction(
            Transaction(
                id = 101L,
                accountId = 1L,
                originalDate = "2026-06-01",
                originalMerchant = "AMZN MKTP US*99",
                originalAmountCents = -3499L
            )
        )
        val initialRuleCount = repository.rules.value.size

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.openTransactionEditor(101L)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)

        viewModel.openCreateRuleModal(101L)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditorSheet, "Mobile transaction editor sheet must close when opening rule modal")
        assertEquals(101L, viewModel.uiState.value.ruleDraftTransactionId)
        assertNotNull(viewModel.uiState.value.ruleDraftTransaction)
        assertEquals(initialRuleCount, repository.rules.value.size, "Opening modal must not prematurely save a rule")

        viewModel.closeCreateRuleModal()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.ruleDraftTransactionId)

        collectJob.cancel()
    }

    @Test
    fun testMakeRuleDraftFromTransaction_prefillsOriginalMerchantAndEffectiveRename() {
        val txn = Transaction(
            id = 42L,
            accountId = 2L,
            originalDate = "2026-06-02",
            originalMerchant = "  SQ *BLUE BOTTLE #04  ",
            editedMerchant = "Blue Bottle Coffee",
            editedMerchantSource = "user",
            editSource = EditSource.USER,
            originalAmountCents = -675L
        )

        val draft = RuleDraftDefaults.makeRuleDraftFromTransaction(txn, nextPriority = 30)

        assertEquals(0L, draft.id)
        assertEquals("Blue Bottle Coffee", draft.name, "Draft rule name defaults to final output merchant name")
        assertEquals(30, draft.priority)
        assertEquals(1, draft.conditions.size)
        assertEquals(RuleConditionField.MERCHANT, draft.conditions[0].field)
        assertEquals(RuleOperator.CONTAINS, draft.conditions[0].operator)
        assertEquals("SQ *BLUE BOTTLE #04", draft.conditions[0].value)

        assertEquals(1, draft.actions.size)
        assertEquals(RuleActionType.RENAME, draft.actions[0].type)
        assertEquals("Blue Bottle Coffee", draft.actions[0].value)
        assertEquals("Blue Bottle Coffee", draft.actions[0].textValue)
    }

    @Test
    fun testConditionAndActionFromTransaction_seedsFieldsWhenSwitchingInModal() {
        val accounts = listOf(
            Account(id = 1L, name = "Checking", type = AccountType.CHECKING),
            Account(id = 2L, name = "Sapphire Card", type = AccountType.CREDIT)
        )
        val categories = listOf(
            Category(id = 10L, name = "Dining"),
            Category(id = 20L, name = "Groceries")
        )
        val txn = Transaction(
            id = 88L,
            accountId = 2L,
            originalDate = "2026-06-03",
            originalMerchant = "TRADER JOE'S #112",
            originalDescription = "POS DEBIT TRADER JOES 112 CHICAGO IL",
            originalAmountCents = -4250L,
            originalCategoryId = null,
            editedCategoryId = 20L,
            editedCategoryIdSource = "user",
            editSource = EditSource.USER
        )

        val descCond = RuleDraftDefaults.conditionFromTransaction(
            field = RuleConditionField.ORIGINAL_DESCRIPTION,
            sourceTransaction = txn,
            accounts = accounts,
            categories = categories
        )
        assertEquals(RuleOperator.CONTAINS, descCond.operator)
        assertEquals("POS DEBIT TRADER JOES 112 CHICAGO IL", descCond.value)

        val amtCond = RuleDraftDefaults.conditionFromTransaction(
            field = RuleConditionField.AMOUNT,
            sourceTransaction = txn,
            accounts = accounts,
            categories = categories
        )
        assertEquals(RuleOperator.EQUALS, amtCond.operator)
        assertEquals("42.50", amtCond.value)
        assertEquals(4250L, amtCond.amountCentsValue)

        val betweenCond = RuleDraftDefaults.withOperator(amtCond, RuleOperator.BETWEEN)
        assertEquals(RuleOperator.BETWEEN, betweenCond.operator)
        assertEquals("42.50,42.50", betweenCond.value)
        assertEquals(4250L, betweenCond.minAmountCentsValue)
        assertEquals(4250L, betweenCond.maxAmountCentsValue)

        val acctCond = RuleDraftDefaults.conditionFromTransaction(
            field = RuleConditionField.ACCOUNT_ID,
            sourceTransaction = txn,
            accounts = accounts,
            categories = categories
        )
        assertEquals(RuleOperator.IS, acctCond.operator)
        assertEquals(2L, acctCond.targetId)
        assertEquals("2", acctCond.value)

        val catCond = RuleDraftDefaults.conditionFromTransaction(
            field = RuleConditionField.CATEGORY_ID,
            sourceTransaction = txn,
            accounts = accounts,
            categories = categories
        )
        assertEquals(RuleOperator.IS, catCond.operator)
        assertEquals(20L, catCond.targetId)
        assertEquals("20", catCond.value)

        val catAction = RuleDraftDefaults.actionForType(
            type = RuleActionType.CATEGORIZE,
            sourceTransaction = txn,
            categories = categories
        )
        assertEquals(20L, catAction.targetCategoryId)
        assertEquals("20", catAction.value)
    }

    @Test
    fun testDefaultAndResolveRuleName_usesOutputMerchantOrOriginalMerchantAndAllowsCustomOverwrite() {
        val accounts = listOf(Account(id = 2L, name = "Sapphire", type = AccountType.CREDIT))
        val categories = listOf(Category(id = 10L, name = "Coffee"))
        val conditions = listOf(RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, "SQ *COFFEE"))
        val sourceTxn = Transaction(
            id = 77L,
            accountId = 2L,
            originalDate = "2026-06-04",
            originalMerchant = "SQ *COFFEE #88",
            editedMerchant = "Morning Coffee",
            editedMerchantSource = "user",
            editSource = EditSource.USER,
            originalAmountCents = -525L
        )

        // 1. When a RENAME action is present with non-blank text, default name is the new merchant name
        val renameActions = listOf(RuleAction(RuleActionType.RENAME, value = "Morning Coffee", textValue = "Morning Coffee"))
        assertEquals("Morning Coffee", RuleDraftDefaults.defaultRuleName(conditions, renameActions, sourceTxn))
        assertEquals(
            "Morning Coffee",
            RuleDraftDefaults.resolveRuleName("   ", conditions, renameActions, accounts, categories, sourceTxn)
        )

        // 2. When NO RENAME action is applied (e.g. categorize-only), default name is original merchant name
        val categorizeActions = listOf(RuleAction(RuleActionType.CATEGORIZE, value = "10", targetCategoryId = 10L))
        assertEquals("SQ *COFFEE #88", RuleDraftDefaults.defaultRuleName(conditions, categorizeActions, sourceTxn))
        assertEquals(
            "SQ *COFFEE #88",
            RuleDraftDefaults.resolveRuleName("", conditions, categorizeActions, accounts, categories, sourceTxn)
        )

        // 3. Without a source transaction or RENAME action, falls back to MERCHANT condition value
        assertEquals("SQ *COFFEE", RuleDraftDefaults.defaultRuleName(conditions, categorizeActions, null))

        // 4. Non-merchant condition without source transaction falls back to condition summary on save
        val amountConditions = listOf(RuleCondition(RuleConditionField.AMOUNT, RuleOperator.GREATER_THAN, "100.00", amountCentsValue = 10000L))
        assertEquals("", RuleDraftDefaults.defaultRuleName(amountConditions, categorizeActions, null))
        assertEquals(
            "Amount is greater than \"100.00\"",
            RuleDraftDefaults.resolveRuleName("", amountConditions, categorizeActions, accounts, categories, null)
        )

        // 5. User-entered custom name always overwrites default
        assertEquals(
            "Custom Household Coffee Rule",
            RuleDraftDefaults.resolveRuleName("  Custom Household Coffee Rule  ", conditions, renameActions, accounts, categories, sourceTxn)
        )
    }

    @Test
    fun testSaveRuleFromTransaction_savesClosesModalAndReappliesRulesImmediately() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveCategory(Category(id = 15L, name = "Coffee Shops"))
        repository.saveTransaction(
            Transaction(
                id = 201L,
                accountId = 1L,
                originalDate = "2026-06-05",
                originalMerchant = "SQ *ROASTERY #1",
                originalAmountCents = -550L
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 202L,
                accountId = 1L,
                originalDate = "2026-06-06",
                originalMerchant = "SQ *ROASTERY #2",
                originalAmountCents = -625L
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.openCreateRuleModal(201L)
        testScheduler.advanceUntilIdle()

        val sourceTxn = viewModel.uiState.value.ruleDraftTransaction!!
        val draft = RuleDraftDefaults.makeRuleDraftFromTransaction(sourceTxn, viewModel.uiState.value.nextRulePriority)
        assertEquals("SQ *ROASTERY #1", draft.name)
        assertEquals(1, viewModel.previewRuleMatchCount(draft.conditions))
        assertEquals(listOf(201L), viewModel.previewRuleMatches(draft.conditions).map { it.id })

        val broadenedConditions = listOf(draft.conditions.first().copy(value = "SQ *ROASTERY"))
        assertEquals(2, viewModel.previewRuleMatchCount(broadenedConditions))
        assertEquals(
            listOf(202L, 201L),
            viewModel.previewRuleMatches(broadenedConditions).map { it.id },
            "Matching transactions preview window lists matches newest-first"
        )

        val finalizedRule = draft.copy(
            name = "Local Roastery",
            conditions = broadenedConditions,
            actions = listOf(
                RuleAction(RuleActionType.RENAME, value = "Local Roastery", textValue = "Local Roastery"),
                RuleAction(RuleActionType.CATEGORIZE, value = "15", targetCategoryId = 15L)
            )
        )

        viewModel.saveRuleFromTransaction(finalizedRule)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.ruleDraftTransactionId, "Rule modal must close on save")
        assertTrue(repository.rules.value.any { it.name == "Local Roastery" })

        val updated201 = repository.transactions.value.first { it.id == 201L }
        val updated202 = repository.transactions.value.first { it.id == 202L }
        assertEquals("Local Roastery", updated201.effectiveMerchant)
        assertEquals(15L, updated201.effectiveCategoryId)
        assertEquals("Local Roastery", updated202.effectiveMerchant)
        assertEquals(15L, updated202.effectiveCategoryId)

        collectJob.cancel()
    }

    @Test
    fun testRuleWireMapper_roundTripsBackendWireFormat() {
        val domainRule = TransactionRule(
            id = 9L,
            name = "Groceries Range",
            priority = 20,
            conditions = listOf(
                RuleCondition(RuleConditionField.MERCHANT, RuleOperator.EQUALS, value = "COSTCO"),
                RuleCondition(
                    field = RuleConditionField.AMOUNT,
                    operator = RuleOperator.BETWEEN,
                    value = "25.00,150.50",
                    minAmountCentsValue = 2500L,
                    maxAmountCentsValue = 15050L
                ),
                RuleCondition(
                    field = RuleConditionField.ACCOUNT_ID,
                    operator = RuleOperator.IS,
                    value = "3",
                    targetId = 3L
                )
            ),
            actions = listOf(
                RuleAction(RuleActionType.RENAME, value = "Costco Wholesale", textValue = "Costco Wholesale"),
                RuleAction(RuleActionType.CATEGORIZE, value = "12", targetCategoryId = 12L)
            ),
            enabled = true
        )

        val dto = RuleWireMapper.toDto(domainRule)
        assertEquals("equals", dto.conditions[0].operator)
        assertEquals("between", dto.conditions[1].operator)
        val rangeArr = dto.conditions[1].value as JsonArray
        assertEquals(25.0, (rangeArr[0] as JsonPrimitive).double)
        assertEquals(150.5, (rangeArr[1] as JsonPrimitive).double)
        assertEquals("account_id", dto.conditions[2].field)
        assertEquals(3L, (dto.conditions[2].value as JsonPrimitive).long)

        assertEquals("rename", dto.actions[0].type)
        assertEquals("categorize", dto.actions[1].type)
        assertEquals(12L, (dto.actions[1].value as JsonPrimitive).long)

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<RuleDto>(encoded).toDomain()

        assertEquals(RuleOperator.EQUALS, decoded.conditions[0].operator)
        assertEquals(2500L, decoded.conditions[1].minAmountCentsValue)
        assertEquals(15050L, decoded.conditions[1].maxAmountCentsValue)
        assertEquals(3L, decoded.conditions[2].targetId)
        assertEquals("Costco Wholesale", decoded.actions[0].textValue)
        assertEquals(12L, decoded.actions[1].targetCategoryId)
    }
}
