package app.overbay.orbit.ui.categories

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
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
class CategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: CategoriesViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = CategoriesViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testGroupingAndAggregation_groupsInto4CategoriesAccurately() = runTest(testDispatcher) {
        // Setup categories
        val catSalary = Category(id = 1L, name = "Salary Income", isIncome = true, isTransfer = false)
        val catRent = Category(id = 2L, name = "Apartment Rent", isIncome = false, isTransfer = false)
        val catDining = Category(id = 3L, name = "Restaurants & Bars", isIncome = false, isTransfer = false)
        val catTransfer = Category(id = 4L, name = "Savings Transfer", isIncome = false, isTransfer = true)

        repository.saveCategory(catSalary)
        repository.saveCategory(catRent)
        repository.saveCategory(catDining)
        repository.saveCategory(catTransfer)

        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))

        // Save transactions with spend
        repository.saveTransaction(Transaction(id = 10L, accountId = 1L, originalDate = "2026-05-01", originalMerchant = "Employer", originalAmountCents = 500000L, originalCategoryId = 1L))
        repository.saveTransaction(Transaction(id = 20L, accountId = 1L, originalDate = "2026-05-02", originalMerchant = "Landlord", originalAmountCents = -120000L, originalCategoryId = 2L))
        repository.saveTransaction(Transaction(id = 21L, accountId = 1L, originalDate = "2026-05-03", originalMerchant = "Water Utility", originalAmountCents = -8000L, originalCategoryId = 2L))
        repository.saveTransaction(Transaction(id = 30L, accountId = 1L, originalDate = "2026-05-04", originalMerchant = "Bistro", originalAmountCents = -4500L, originalCategoryId = 3L))
        repository.saveTransaction(Transaction(id = 40L, accountId = 1L, originalDate = "2026-05-05", originalMerchant = "Savings Transfer", originalAmountCents = 50000L, originalCategoryId = 4L, editedIsTransfer = true))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.totalCategoriesCount)
        assertEquals(4, state.groups.size)

        val incomeGroup = state.groups.first { it.title == "Income" }
        assertEquals(1, incomeGroup.items.size)
        assertEquals(500000L, incomeGroup.totalCents)
        assertEquals(1, incomeGroup.items[0].transactionCount)

        val livingGroup = state.groups.first { it.title == "Living Needs (Fixed)" }
        assertEquals(1, livingGroup.items.size)
        assertEquals(128000L, livingGroup.totalCents)
        assertEquals(2, livingGroup.items[0].transactionCount)

        val discretionaryGroup = state.groups.first { it.title == "Discretionary (Variable)" }
        assertEquals(1, discretionaryGroup.items.size)
        assertEquals(4500L, discretionaryGroup.totalCents)
        assertEquals(1, discretionaryGroup.items[0].transactionCount)

        val transferGroup = state.groups.first { it.title == "Transfers & Savings" }
        assertEquals(1, transferGroup.items.size)
        assertEquals(50000L, transferGroup.totalCents)
        assertEquals(1, transferGroup.items[0].transactionCount)

        collectJob.cancel()
    }

    @Test
    fun testEditorDialog_addEditCloseFlows() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Open add category
        viewModel.openAddCategory()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorDialog)
        assertNull(viewModel.uiState.value.editingCategory)

        // 2. Save new category
        viewModel.saveCategory(
            id = 0L,
            name = "Coffee",
            color = "#A855F7",
            icon = "LocalCafe",
            isTransfer = false,
            isIncome = false,
            mhaEligible = false
        )
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditorDialog)
        val created = repository.categories.value.firstOrNull { it.name == "Coffee" }
        assertNotNull(created)
        assertEquals("#A855F7", created.color)

        // 3. Open edit category
        viewModel.openEditCategory(created)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorDialog)
        assertEquals(created.id, viewModel.uiState.value.editingCategory?.id)

        // 4. Update category
        viewModel.saveCategory(
            id = created.id,
            name = "Coffee & Tea",
            color = "#EC4899",
            icon = "LocalCafe",
            isTransfer = false,
            isIncome = false,
            mhaEligible = true
        )
        testScheduler.advanceUntilIdle()

        val updated = repository.categories.value.first { it.id == created.id }
        assertEquals("Coffee & Tea", updated.name)
        assertEquals("#EC4899", updated.color)
        assertTrue(updated.mhaDefaultEligible)

        // 5. Close editor
        viewModel.openAddCategory()
        viewModel.closeEditorDialog()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showEditorDialog)

        collectJob.cancel()
    }

    @Test
    fun testDeleteCategory_requestConfirmDismissFlows() = runTest(testDispatcher) {
        val cat = Category(id = 99L, name = "To Delete", isIncome = false, isTransfer = false)
        repository.saveCategory(cat)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Request delete
        viewModel.requestDeleteCategory(99L)
        testScheduler.advanceUntilIdle()
        assertEquals(99L, viewModel.uiState.value.deleteConfirmCategoryId)

        // 2. Dismiss
        viewModel.dismissDeleteCategory()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.deleteConfirmCategoryId)

        // 3. Delete confirmed
        viewModel.requestDeleteCategory(99L)
        viewModel.deleteCategory(99L)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteConfirmCategoryId)
        assertFalse(repository.categories.value.any { it.id == 99L })

        collectJob.cancel()
    }
}
