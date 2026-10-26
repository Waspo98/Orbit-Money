package app.overbay.orbit.ui.transactions

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.TransactionFilterDraft
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
class TransactionsViewModelTest {

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
        viewModel = TransactionsViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_loadsTransactionsAndCalculatesTotals() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        val catGroceries = Category(id = 10L, name = "Groceries", isIncome = false, isTransfer = false)
        val catSalary = Category(id = 20L, name = "Salary", isIncome = true, isTransfer = false)
        val catTransfer = Category(id = 30L, name = "Transfer", isIncome = false, isTransfer = true)

        repository.saveAccount(checking)
        repository.saveCategory(catGroceries)
        repository.saveCategory(catSalary)
        repository.saveCategory(catTransfer)

        repository.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "2026-05-01",
                originalMerchant = "Supermarket",
                originalAmountCents = -5000L,
                originalCategoryId = 10L
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 2L,
                accountId = 1L,
                originalDate = "2026-05-05",
                originalMerchant = "Employer",
                originalAmountCents = 20000L,
                originalCategoryId = 20L
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 3L,
                accountId = 1L,
                originalDate = "2026-05-10",
                originalMerchant = "Internal Transfer",
                originalAmountCents = 10000L,
                originalCategoryId = 30L,
                editedIsTransfer = true
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.transactions.size)
        assertEquals(3, state.filteredTransactions.size)
        assertEquals(-5000L, state.totalExpenseCents)
        assertEquals(20000L, state.totalIncomeCents)
        assertEquals(15000L, state.netCashflowCents)
        // Check sorting: 2026-05-10 first, then 2026-05-05, then 2026-05-01
        assertEquals(3L, state.filteredTransactions[0].id)
        assertEquals(2L, state.filteredTransactions[1].id)
        assertEquals(1L, state.filteredTransactions[2].id)

        collectJob.cancel()
    }

    @Test
    fun testSearchQuery_filtersByMerchantNotesAndCategory() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        val catFood = Category(id = 10L, name = "Food & Dining", isIncome = false, isTransfer = false)
        repository.saveAccount(checking)
        repository.saveCategory(catFood)

        repository.saveTransaction(
            Transaction(id = 1L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Target", originalAmountCents = -2500L, originalCategoryId = 10L)
        )
        repository.saveTransaction(
            Transaction(id = 2L, accountId = 1L, originalDate = "2026-05-02", originalMerchant = "Coffee Shop", originalAmountCents = -600L, originalCategoryId = 10L, notes = "Project meeting")
        )
        repository.saveTransaction(
            Transaction(id = 3L, accountId = 1L, originalDate = "2026-05-03", originalMerchant = "Gas Station", originalAmountCents = -4500L, originalCategoryId = null)
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Search by merchant
        viewModel.setSearchQuery("target")
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredTransactions.size)
        assertEquals(1L, viewModel.uiState.value.filteredTransactions[0].id)

        // 2. Search by notes
        viewModel.setSearchQuery("meeting")
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredTransactions.size)
        assertEquals(2L, viewModel.uiState.value.filteredTransactions[0].id)

        // 3. Search by category name
        viewModel.setSearchQuery("dining")
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredTransactions.size)

        // 4. Active search mode toggle and exit clears query
        viewModel.setSearchActive(true)
        viewModel.setSearchQuery("target")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals("target", viewModel.uiState.value.searchQuery)

        viewModel.setSearchActive(false)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertEquals("", viewModel.uiState.value.searchQuery)

        // 5. Clear filter
        viewModel.clearFilters()
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredTransactions.size)

        collectJob.cancel()
    }

    @Test
    fun testQuickFilters_filteringBehavior() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        val cat = Category(id = 10L, name = "Groceries", isIncome = false, isTransfer = false)
        repository.saveAccount(checking)
        repository.saveCategory(cat)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Groceries", originalAmountCents = -3000L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-05-02", originalMerchant = "Paycheck", originalAmountCents = 50000L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "2026-05-03", originalMerchant = "Transfer", originalAmountCents = 1000L, editedIsTransfer = true))
        repository.saveTransaction(Transaction(id = 4L, accountId = 1L, originalDate = "2026-05-04", originalMerchant = "Unknown", originalAmountCents = -1500L, originalCategoryId = null))
        repository.saveTransaction(Transaction(id = 5L, accountId = 1L, originalDate = "2026-05-05", originalMerchant = "Pending Card", originalAmountCents = -2000L, isPending = true))
        repository.saveTransaction(Transaction(id = 6L, accountId = 1L, originalDate = "2026-05-06", originalMerchant = "MHA Medical", originalAmountCents = -8000L, editedMhaEligible = true))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.setQuickFilter(QuickFilter.EXPENSES)
        testScheduler.advanceUntilIdle()
        val expenses = viewModel.uiState.value.filteredTransactions
        assertTrue(expenses.all { it.isExpense && !it.effectiveIsTransfer })

        viewModel.setQuickFilter(QuickFilter.INCOME)
        testScheduler.advanceUntilIdle()
        val income = viewModel.uiState.value.filteredTransactions
        assertTrue(income.all { it.isIncome && !it.effectiveIsTransfer })

        viewModel.setQuickFilter(QuickFilter.TRANSFERS)
        testScheduler.advanceUntilIdle()
        val transfers = viewModel.uiState.value.filteredTransactions
        assertEquals(1, transfers.size)
        assertEquals(3L, transfers[0].id)

        viewModel.setQuickFilter(QuickFilter.UNCATEGORIZED)
        testScheduler.advanceUntilIdle()
        val uncat = viewModel.uiState.value.filteredTransactions
        assertTrue(uncat.all { it.effectiveCategoryId == null && !it.effectiveIsTransfer })

        viewModel.setQuickFilter(QuickFilter.PENDING)
        testScheduler.advanceUntilIdle()
        val pending = viewModel.uiState.value.filteredTransactions
        assertEquals(1, pending.size)
        assertEquals(5L, pending[0].id)

        viewModel.setQuickFilter(QuickFilter.MHA)
        testScheduler.advanceUntilIdle()
        val mha = viewModel.uiState.value.filteredTransactions
        assertEquals(1, mha.size)
        assertEquals(6L, mha[0].id)

        collectJob.cancel()
    }

    @Test
    fun testSelectionAndBatchOperations_provenancePreserved() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        val catOld = Category(id = 10L, name = "Old Cat", isIncome = false, isTransfer = false)
        val catNew = Category(id = 20L, name = "New Cat", isIncome = false, isTransfer = false)
        repository.saveAccount(checking)
        repository.saveCategory(catOld)
        repository.saveCategory(catNew)

        repository.saveTransaction(Transaction(id = 1L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Store A", originalAmountCents = -1000L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 1L, originalDate = "2026-05-02", originalMerchant = "Store B", originalAmountCents = -2000L, originalCategoryId = 10L))
        repository.saveTransaction(Transaction(id = 3L, accountId = 1L, originalDate = "2026-05-03", originalMerchant = "Store C", originalAmountCents = -3000L, originalCategoryId = 10L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Select toggle
        viewModel.toggleTransactionSelection(1L)
        viewModel.toggleTransactionSelection(2L)
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedTransactionIds)

        // 2. Batch categorize
        viewModel.batchCategorize(20L)
        testScheduler.advanceUntilIdle()

        // Selection should be cleared
        assertTrue(viewModel.uiState.value.selectedTransactionIds.isEmpty())

        val t1 = repository.transactions.value.first { it.id == 1L }
        val t2 = repository.transactions.value.first { it.id == 2L }
        val t3 = repository.transactions.value.first { it.id == 3L }

        assertEquals(20L, t1.effectiveCategoryId)
        assertEquals(10L, t1.originalCategoryId, "Original category must be preserved")
        assertEquals(EditSource.USER, t1.editSource)
        assertEquals("user", t1.editedCategoryIdSource)

        assertEquals(20L, t2.effectiveCategoryId)
        assertEquals(10L, t3.effectiveCategoryId, "Unselected transaction must remain untouched")

        // 3. Batch mark transfer
        viewModel.toggleTransactionSelection(1L)
        viewModel.batchMarkTransfer(true)
        testScheduler.advanceUntilIdle()

        val t1Transfer = repository.transactions.value.first { it.id == 1L }
        assertTrue(t1Transfer.effectiveIsTransfer)
        assertFalse(t1Transfer.originalIsTransfer)

        // 4. Batch toggle MHA
        viewModel.toggleTransactionSelection(1L)
        viewModel.batchToggleMha()
        testScheduler.advanceUntilIdle()

        val t1Mha = repository.transactions.value.first { it.id == 1L }
        assertEquals(true, t1Mha.effectiveMhaEligible)

        // 5. Batch mark ignored
        viewModel.toggleTransactionSelection(3L)
        viewModel.batchMarkIgnored(true)
        testScheduler.advanceUntilIdle()

        val t3Ignored = repository.transactions.value.first { it.id == 3L }
        assertTrue(t3Ignored.effectiveIsIgnored)

        // 6. Select all and batch delete
        viewModel.selectAll()
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.selectedTransactionIds.size)

        viewModel.batchDelete()
        testScheduler.advanceUntilIdle()
        assertTrue(repository.transactions.value.isEmpty())
        assertTrue(viewModel.uiState.value.selectedTransactionIds.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun testSaveTransaction_editAndResetPreservesProvenance() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        val cat1 = Category(id = 10L, name = "Cat 1", isIncome = false, isTransfer = false)
        val cat2 = Category(id = 20L, name = "Cat 2", isIncome = false, isTransfer = false)
        repository.saveAccount(checking)
        repository.saveCategory(cat1)
        repository.saveCategory(cat2)

        val originalTxn = Transaction(
            id = 100L,
            accountId = 1L,
            originalDate = "2026-05-01",
            originalMerchant = "Original Merchant",
            originalAmountCents = -4500L,
            originalCategoryId = 10L,
            source = "csv"
        )
        repository.saveTransaction(originalTxn)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // User edits transaction
        viewModel.saveTransaction(
            id = 100L,
            merchant = "Edited Store",
            amountCents = -5000L,
            date = "2026-05-02",
            categoryId = 20L,
            notes = "Weekly grocery trip",
            isTransfer = false,
            isIgnored = false,
            mhaEligible = true
        )
        testScheduler.advanceUntilIdle()

        val editedTxn = repository.transactions.value.first { it.id == 100L }
        assertEquals("Edited Store", editedTxn.effectiveMerchant)
        assertEquals("Original Merchant", editedTxn.originalMerchant, "Original merchant must NOT be overwritten")
        assertEquals(-5000L, editedTxn.effectiveAmountCents)
        assertEquals(-4500L, editedTxn.originalAmountCents, "Original amount must NOT be overwritten")
        assertEquals("2026-05-02", editedTxn.effectiveDate)
        assertEquals("2026-05-01", editedTxn.originalDate)
        assertEquals(20L, editedTxn.effectiveCategoryId)
        assertEquals(10L, editedTxn.originalCategoryId)
        assertEquals("Weekly grocery trip", editedTxn.notes)
        assertEquals(EditSource.USER, editedTxn.editSource)
        assertEquals(true, editedTxn.effectiveMhaEligible)

        // Reset transaction to original
        viewModel.resetTransaction(100L)
        testScheduler.advanceUntilIdle()

        val resetTxn = repository.transactions.value.first { it.id == 100L }
        assertEquals("Original Merchant", resetTxn.effectiveMerchant)
        assertNull(resetTxn.editedMerchant)
        assertEquals(-4500L, resetTxn.effectiveAmountCents)
        assertNull(resetTxn.editedAmountCents)
        assertEquals("2026-05-01", resetTxn.effectiveDate)
        assertNull(resetTxn.editedDate)
        assertEquals(10L, resetTxn.effectiveCategoryId)
        assertNull(resetTxn.editedCategoryId)
        assertNull(resetTxn.editedMhaEligible)

        collectJob.cancel()
    }

    @Test
    fun testSplitTransaction_centsConservedAndChildCreated() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        val catFood = Category(id = 10L, name = "Groceries", isIncome = false, isTransfer = false)
        val catHobby = Category(id = 20L, name = "Hobbies", isIncome = false, isTransfer = false)
        repository.saveAccount(checking)
        repository.saveCategory(catFood)
        repository.saveCategory(catHobby)

        val parentTxn = Transaction(
            id = 50L,
            accountId = 1L,
            originalDate = "2026-05-01",
            originalMerchant = "Department Store",
            originalAmountCents = -10000L,
            originalCategoryId = 10L
        )
        repository.saveTransaction(parentTxn)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Split into -6000L (Groceries) and -4000L (Hobbies)
        val splits = listOf(
            Pair(-6000L, 10L),
            Pair(-4000L, 20L)
        )
        viewModel.splitTransaction(50L, splits)
        testScheduler.advanceUntilIdle()

        val allTxns = repository.transactions.value
        assertEquals(2, allTxns.size, "Split must result in parent + child transaction")

        val updatedParent = allTxns.first { it.id == 50L }
        assertEquals(-6000L, updatedParent.effectiveAmountCents)
        assertEquals(10L, updatedParent.effectiveCategoryId)

        val child = allTxns.first { it.id != 50L }
        assertEquals(-4000L, child.effectiveAmountCents)
        assertEquals(20L, child.effectiveCategoryId)
        assertEquals("split", child.source)
        assertTrue(child.originalDescription?.contains("Split from #50") == true, "Child must reference parent id in description")

        // Cents conservation check
        val sumCents = updatedParent.effectiveAmountCents + child.effectiveAmountCents
        assertEquals(-10000L, sumCents, "Total sum of split lines must equal original amount")

        collectJob.cancel()
    }

    @Test
    fun testImportCsvTransactions_createsTransactionsAndAppliesRules() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        repository.saveAccount(checking)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val imported = listOf(
            Transaction(id = 0L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Shop 1", originalAmountCents = -1500L),
            Transaction(id = 0L, accountId = 1L, originalDate = "2026-05-02", originalMerchant = "Shop 2", originalAmountCents = -2500L)
        )
        viewModel.importCsvTransactions(imported)
        testScheduler.advanceUntilIdle()

        assertEquals(2, repository.transactions.value.size)

        collectJob.cancel()
    }
}
