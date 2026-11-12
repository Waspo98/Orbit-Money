package app.overbay.orbit.ui.transactions

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.OrbitStorageEngine
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionQuickAction
import app.overbay.orbit.data.network.TransactionDto
import app.overbay.orbit.data.resetTransactionFields
import app.overbay.orbit.ui.components.TransactionFilterDraft
import app.overbay.orbit.ui.transactions.components.FieldEditProvenance
import app.overbay.orbit.ui.transactions.components.parseFieldProvenance
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionQuickActionsAndProvenanceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: TransactionsViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = TransactionsViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testResolveActions_defaultsAndMhaGating() {
        val (pinnedOff, overflowOff) = TransactionQuickAction.resolveActions(
            pinnedIds = OrbitPreferences.defaultTransactionPinnedActions(),
            orderIds = OrbitPreferences.defaultTransactionActionOrder(),
            mhaEnabled = false
        )
        assertEquals(
            listOf(TransactionQuickAction.EDIT, TransactionQuickAction.SPLIT, TransactionQuickAction.CREATE_RULE),
            pinnedOff
        )
        assertEquals(
            listOf(
                TransactionQuickAction.IGNORE,
                TransactionQuickAction.RECURRING,
                TransactionQuickAction.TRANSFER,
                TransactionQuickAction.DELETE
            ),
            overflowOff
        )

        val (_, overflowOn) = TransactionQuickAction.resolveActions(
            pinnedIds = OrbitPreferences.defaultTransactionPinnedActions(),
            orderIds = OrbitPreferences.defaultTransactionActionOrder(),
            mhaEnabled = true
        )
        assertTrue(overflowOn.contains(TransactionQuickAction.MHA))
    }

    @Test
    fun testResolveActions_customOrderEmptyPinnedAndAllPinned() {
        val customOrder = listOf("delete", "ignore", "transfer", "mha", "edit")
        val (pinnedCustom, overflowCustom) = TransactionQuickAction.resolveActions(
            pinnedIds = listOf("ignore", "edit"),
            orderIds = customOrder,
            mhaEnabled = true
        )
        // 'ignore' precedes 'edit' in customOrder
        assertEquals(listOf(TransactionQuickAction.IGNORE, TransactionQuickAction.EDIT), pinnedCustom)
        // Missing enum entries append deterministically after customOrder
        assertEquals(TransactionQuickAction.DELETE, overflowCustom.first())
        assertTrue(overflowCustom.contains(TransactionQuickAction.SPLIT))
        assertTrue(overflowCustom.contains(TransactionQuickAction.CREATE_RULE))

        // Empty pinned -> all visible move to overflow
        val (nonePinned, allOverflow) = TransactionQuickAction.resolveActions(
            pinnedIds = emptyList(),
            orderIds = OrbitPreferences.defaultTransactionActionOrder(),
            mhaEnabled = false
        )
        assertTrue(nonePinned.isEmpty())
        assertEquals(7, allOverflow.size)
    }

    @Test
    fun testParseFieldProvenance_distinguishesManualAndAutoRule() {
        assertEquals(FieldEditProvenance.None, parseFieldProvenance(isEdited = false, rawSource = "user"))
        assertEquals(FieldEditProvenance.Manual, parseFieldProvenance(isEdited = true, rawSource = "user"))
        assertEquals(FieldEditProvenance.Manual, parseFieldProvenance(isEdited = true, rawSource = null))
        assertEquals(FieldEditProvenance.AutoRule(42L), parseFieldProvenance(isEdited = true, rawSource = "rule:42"))
        assertEquals(FieldEditProvenance.AutoRule(null), parseFieldProvenance(isEdited = true, rawSource = "rule:unknown"))
    }

    @Test
    fun testQuickSetCategory_preservesOriginalProvenanceAndSupportsUncategorized() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveCategory(Category(id = 10L, name = "Dining", icon = "🍽️"))
        repository.saveCategory(Category(id = 20L, name = "Groceries", icon = "🛒"))
        repository.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "2026-11-10",
                originalMerchant = "PORTILLOS #48",
                originalAmountCents = -3369L,
                originalCategoryId = 10L
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.openCategoryPicker(1L)
        testScheduler.advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.categoryPickerTransactionId)

        viewModel.quickSetCategory(1L, 20L)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.categoryPickerTransactionId)
        val updated = viewModel.uiState.value.transactions.single { it.id == 1L }
        assertEquals(10L, updated.originalCategoryId)
        assertEquals(20L, updated.editedCategoryId)
        assertEquals("user", updated.editedCategoryIdSource)
        assertEquals(20L, updated.effectiveCategoryId)
        assertEquals(EditSource.USER, updated.editSource)
        assertNull(updated.editedMerchant)
        assertNull(updated.editedDate)
        assertNull(updated.editedAmountCents)

        // Setting explicitly to Uncategorized (null) overrides originalCategoryId = 10L
        viewModel.quickSetCategory(1L, null)
        testScheduler.advanceUntilIdle()

        val clearedToUncat = viewModel.uiState.value.transactions.single { it.id == 1L }
        assertEquals(10L, clearedToUncat.originalCategoryId)
        assertNull(clearedToUncat.editedCategoryId)
        assertEquals("user", clearedToUncat.editedCategoryIdSource)
        assertNull(clearedToUncat.effectiveCategoryId)

        collectJob.cancel()
    }

    @Test
    fun testSaveTransactionQuickActions_updatesPreferencesReactively() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.openCustomizeActionsSheet()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showCustomizeActionsSheet)

        val customPinned = listOf("ignore", "transfer", "edit", "split")
        val customOrder = listOf("ignore", "transfer", "edit", "split", "create_rule", "recurring", "mha", "delete")
        viewModel.saveTransactionQuickActions(customPinned, customOrder)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCustomizeActionsSheet)
        assertEquals(customPinned, viewModel.uiState.value.transactionPinnedActions)
        assertEquals(customOrder, viewModel.uiState.value.transactionActionOrder)
        assertEquals(customPinned, repository.preferences.value.transactionPinnedActions)

        collectJob.cancel()
    }

    @Test
    fun testResetConfirmationFlow_stagesCancelsAndConfirmsFieldAndFullReset() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveCategory(Category(id = 10L, name = "Dining", icon = "🍽️"))
        repository.saveCategory(Category(id = 20L, name = "Groceries", icon = "🛒"))
        repository.saveTransaction(
            Transaction(
                id = 5L,
                accountId = 1L,
                originalDate = "2026-11-10",
                originalMerchant = "PORTILLOS #48",
                originalAmountCents = -3369L,
                originalCategoryId = 10L,
                editedMerchant = "Portillo's",
                editedMerchantSource = "user",
                editedCategoryId = 20L,
                editedCategoryIdSource = "user",
                editSource = EditSource.USER
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        // 1. Request single-field reset -> stages PendingResetConfirm without mutating yet
        viewModel.requestResetField(5L, "category")
        testScheduler.advanceUntilIdle()
        assertEquals(PendingResetConfirm(5L, "category"), viewModel.uiState.value.pendingResetConfirm)
        assertEquals("Category", viewModel.uiState.value.pendingResetConfirm?.fieldLabel)
        assertEquals(20L, viewModel.uiState.value.transactions.single { it.id == 5L }.effectiveCategoryId)

        // 2. Dismissing cancels without mutating
        viewModel.dismissPendingReset()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingResetConfirm)
        assertEquals(20L, viewModel.uiState.value.transactions.single { it.id == 5L }.effectiveCategoryId)

        // 3. Re-request and confirm single-field reset -> reverts only category, preserves merchant edit
        viewModel.requestResetField(5L, "category")
        viewModel.confirmPendingReset()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingResetConfirm)
        val afterCategoryReset = viewModel.uiState.value.transactions.single { it.id == 5L }
        assertEquals(10L, afterCategoryReset.effectiveCategoryId)
        assertEquals("Portillo's", afterCategoryReset.effectiveMerchant)

        // 4. Request full Restore All -> confirm reverts remaining edits
        viewModel.requestResetTransaction(5L)
        testScheduler.advanceUntilIdle()
        assertEquals(PendingResetConfirm(5L, null), viewModel.uiState.value.pendingResetConfirm)
        assertNull(viewModel.uiState.value.pendingResetConfirm?.fieldLabel)

        viewModel.confirmPendingReset()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingResetConfirm)
        val afterFullReset = viewModel.uiState.value.transactions.single { it.id == 5L }
        assertEquals("PORTILLOS #48", afterFullReset.effectiveMerchant)
        assertEquals(EditSource.NONE, afterFullReset.editSource)

        collectJob.cancel()
    }

    @Test
    fun testResetTransactionFields_normalizesShortKeysForSelfHostedBackend() = runTest(testDispatcher) {
        val capturedCalls = mutableListOf<Pair<Long, List<String>>>()
        val baseEngine = repository.localEngine
        repository.activeEngine = object : OrbitStorageEngine by baseEngine {
            override suspend fun resetTransaction(id: Long, fields: List<String>): Result<Transaction> {
                capturedCalls.add(id to fields)
                return baseEngine.resetTransaction(id, fields)
            }
        }

        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveTransaction(
            Transaction(
                id = 9L,
                accountId = 1L,
                originalDate = "2026-11-10",
                originalMerchant = "TRADER JOES",
                originalAmountCents = -5420L,
                editedCategoryId = 20L,
                editedCategoryIdSource = "user",
                editedIsTransfer = true,
                editedIsTransferSource = "user",
                editedIsIgnored = true,
                editedIsIgnoredSource = "user",
                editedMhaEligible = true,
                editedMhaEligibleSource = "user",
                editedDate = "2026-11-11",
                editSource = EditSource.USER
            )
        )

        repository.resetTransactionFields(9L, listOf("category", "transfer", "ignored", "mha"))
        assertEquals(
            listOf(9L to listOf("category_id", "is_transfer", "is_ignored", "mha_eligible")),
            capturedCalls
        )

        // Local-only field ("date") resets locally without sending invalid empty/unsupported reset payload to server
        capturedCalls.clear()
        repository.resetTransactionFields(9L, listOf("date"))
        assertTrue(capturedCalls.isEmpty())
    }

    @Test
    fun testSelfHostedDtoProvenance_preservesUncategorizedOriginAndUneditedMhaDefault() {
        // 1. Unedited bank transaction from backend has mha_eligible=false and edited_mha_eligible_source=null
        val uneditedDomain = TransactionDto(
            id = 100L,
            accountId = 1L,
            date = "2026-11-10",
            amount = -25.00,
            merchant = "COFFEE SHOP",
            originalMerchant = "COFFEE SHOP",
            categoryId = null,
            originalCategoryId = null,
            mhaEligible = false,
            editedMhaEligible = null,
            editedMhaEligibleSource = null
        ).toDomain()

        assertEquals(EditSource.NONE, uneditedDomain.editSource)
        assertNull(uneditedDomain.editedMhaEligibleSource)
        assertEquals(
            FieldEditProvenance.None,
            parseFieldProvenance(uneditedDomain.editedMhaEligibleSource != null, uneditedDomain.editedMhaEligibleSource)
        )

        val editedFiltered = TransactionsFilterHelper.filterTransactions(
            transactions = listOf(uneditedDomain),
            query = "",
            quick = QuickFilter.ALL,
            draft = TransactionFilterDraft(hasEditsFilter = "edited"),
            categoryMap = emptyMap()
        )
        assertTrue(editedFiltered.isEmpty())

        // 2. Imported uncategorized transaction categorized later must preserve originalCategoryId = null
        val categorizedFromUncat = TransactionDto(
            id = 101L,
            accountId = 1L,
            date = "2026-11-10",
            amount = -42.50,
            merchant = "ALDI",
            originalMerchant = "ALDI",
            categoryId = 20L,
            originalCategoryId = null,
            editedCategoryId = 20L,
            editedCategoryIdSource = "user",
            mhaEligible = false
        ).toDomain()

        assertNull(categorizedFromUncat.originalCategoryId)
        assertEquals(20L, categorizedFromUncat.effectiveCategoryId)
    }

    @Test
    fun testQuickSetCategory_noOpWhenUnchangedPreservesUneditedState() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveCategory(Category(id = 10L, name = "Dining", icon = "🍽️"))
        repository.saveTransaction(
            Transaction(
                id = 7L,
                accountId = 1L,
                originalDate = "2026-11-10",
                originalMerchant = "CAFE",
                originalAmountCents = -1200L,
                originalCategoryId = 10L
            )
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        // Saving the already-effective category on an unedited row must remain a no-op
        viewModel.quickSetCategory(7L, 10L)
        testScheduler.advanceUntilIdle()

        val unchanged = viewModel.uiState.value.transactions.single { it.id == 7L }
        assertNull(unchanged.editedCategoryId)
        assertNull(unchanged.editedCategoryIdSource)
        assertEquals(EditSource.NONE, unchanged.editSource)

        collectJob.cancel()
    }
}
