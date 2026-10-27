package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.ReviewQueueEngine
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
class CategorizeRecentReviewTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: DashboardViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = DashboardViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testReviewQueueEngine_detectsUncategorizedNoisyMerchantAndChangedPattern() {
        val categories = listOf(
            Category(id = 1L, name = "Groceries", color = "#22C55E", icon = "🛒"),
            Category(id = 2L, name = "Dining", color = "#F59E0B", icon = "🍽️"),
            Category(id = 99L, name = "Uncategorized", color = "#9E9E9E", icon = "❓")
        )
        val accounts = listOf(
            Account(id = 1L, name = "Checking", type = AccountType.CHECKING)
        )

        val transactions = listOf(
            // 1. Uncategorized (null categoryId)
            Transaction(
                id = 101L,
                accountId = 1L,
                originalDate = "2026-10-25",
                originalMerchant = "Corner Bakery",
                originalAmountCents = -1450L,
                originalCategoryId = null
            ),
            // 2. Noisy merchant (POS DEBIT hint)
            Transaction(
                id = 102L,
                accountId = 1L,
                originalDate = "2026-10-24",
                originalMerchant = "POS DEBIT 883921 LOCAL MARKET",
                originalAmountCents = -3200L,
                originalCategoryId = 1L
            ),
            // 3. Credit card payment (should NOT be flagged as merchant_review)
            Transaction(
                id = 103L,
                accountId = 1L,
                originalDate = "2026-10-23",
                originalMerchant = "CHASE CREDIT CRD AUTOPAY 994821",
                originalAmountCents = -50000L,
                originalCategoryId = 1L
            ),
            // 4-6. Historical pattern for Trader Joes -> Groceries (id = 1L)
            Transaction(
                id = 104L,
                accountId = 1L,
                originalDate = "2026-10-10",
                originalMerchant = "Trader Joes #123",
                originalAmountCents = -6500L,
                originalCategoryId = 1L
            ),
            Transaction(
                id = 105L,
                accountId = 1L,
                originalDate = "2026-10-12",
                originalMerchant = "Trader Joes #456",
                originalAmountCents = -5400L,
                originalCategoryId = 1L
            ),
            Transaction(
                id = 106L,
                accountId = 1L,
                originalDate = "2026-10-15",
                originalMerchant = "Trader Joes",
                originalAmountCents = -7200L,
                originalCategoryId = 1L
            ),
            // 7. Outlier Trader Joes categorized as Dining (id = 2L) -> should trigger changed_pattern suggesting 1L
            Transaction(
                id = 107L,
                accountId = 1L,
                originalDate = "2026-10-26",
                originalMerchant = "Trader Joes #789",
                originalAmountCents = -4100L,
                originalCategoryId = 2L
            )
        )

        val queue = ReviewQueueEngine.buildReviewQueue(
            transactions = transactions,
            categories = categories,
            accounts = accounts,
            excludedIds = emptySet(),
            funMode = false,
            limit = 10
        )

        val byId = queue.associateBy { it.id }
        assertTrue(101L in byId, "Uncategorized transaction must be in review queue")
        assertTrue(byId[101L]!!.reviewReasons.contains("uncategorized"))

        assertTrue(102L in byId, "Noisy merchant transaction must be in review queue")
        assertTrue(byId[102L]!!.reviewReasons.contains("merchant_review"))

        assertFalse(103L in byId, "Credit card payment must be excluded from noisy merchant check")

        assertTrue(107L in byId, "Changed category pattern transaction must be in review queue")
        assertTrue(byId[107L]!!.reviewReasons.contains("changed_pattern"))
        assertEquals(1L, byId[107L]!!.suggestedCategoryId)
    }

    @Test
    fun testDashboardViewModel_categorizeReviewWorkflow_approveRejectConfirmBackAndFunMode() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        repository.saveCategory(Category(id = 1L, name = "Groceries", color = "#22C55E", icon = "🛒"))
        repository.saveCategory(Category(id = 2L, name = "Dining", color = "#F59E0B", icon = "🍽️"))

        // Seed 2 uncategorized transactions and 1 categorized transaction
        repository.saveTransaction(
            Transaction(
                id = 1L,
                accountId = 1L,
                originalDate = "2026-10-20",
                originalMerchant = "Coffee Shop",
                originalAmountCents = -550L,
                originalCategoryId = null
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 2L,
                accountId = 1L,
                originalDate = "2026-10-21",
                originalMerchant = "Fresh Market",
                originalAmountCents = -4200L,
                originalCategoryId = null
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 3L,
                accountId = 1L,
                originalDate = "2026-10-22",
                originalMerchant = "Normal Store",
                originalAmountCents = -1500L,
                originalCategoryId = 1L
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Open review modal
        viewModel.openCategorizeReview()
        testScheduler.advanceUntilIdle()

        var review = viewModel.uiState.value.categorizeReview
        assertTrue(review.isOpen)
        assertFalse(review.allDone)
        assertEquals(2, review.items.size)
        assertEquals(2L, review.currentTxn?.id) // Newest date first (2026-10-21)

        // 2. Approve first transaction (id = 2L)
        viewModel.approveReviewTransaction()
        testScheduler.advanceUntilIdle()

        review = viewModel.uiState.value.categorizeReview
        assertEquals(1, review.currentIndex)
        assertEquals(1, review.batchReviewed)
        assertEquals(1L, review.currentTxn?.id)
        assertTrue(2L in repository.preferences.value.handledReviewTransactionIds)

        // 3. Go back to previous transaction
        viewModel.goBackReviewTransaction()
        testScheduler.advanceUntilIdle()

        review = viewModel.uiState.value.categorizeReview
        assertEquals(0, review.currentIndex)
        assertEquals(0, review.batchReviewed)
        assertEquals(2L, review.currentTxn?.id)

        // Advance again to second transaction (id = 1L)
        viewModel.approveReviewTransaction()
        testScheduler.advanceUntilIdle()

        // 4. Reject second transaction (id = 1L) -> opens category picker
        viewModel.rejectReviewTransaction()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.categorizeReview.isCategoryPickerOpen)

        // Request confirmation for Dining (categoryId = 2L)
        viewModel.requestConfirmReviewCategory(2L)
        testScheduler.advanceUntilIdle()
        assertEquals(2L, viewModel.uiState.value.categorizeReview.pendingConfirmCategoryId)

        // Cancel confirmation ("Keep Looking")
        viewModel.cancelConfirmReviewCategory()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.categorizeReview.pendingConfirmCategoryId)
        assertTrue(viewModel.uiState.value.categorizeReview.isCategoryPickerOpen)

        // Request and confirm category change ("Change Category")
        viewModel.requestConfirmReviewCategory(2L)
        viewModel.confirmReviewCategory()
        testScheduler.advanceUntilIdle()

        review = viewModel.uiState.value.categorizeReview
        assertFalse(review.isCategoryPickerOpen)
        assertNull(review.pendingConfirmCategoryId)
        assertTrue(review.isBatchComplete)

        // Verify transaction 1L provenance & category update
        val updatedTxn1 = repository.transactions.value.find { it.id == 1L }!!
        assertEquals(2L, updatedTxn1.editedCategoryId)
        assertEquals("user", updatedTxn1.editedCategoryIdSource)
        assertEquals(EditSource.USER, updatedTxn1.editSource)
        assertEquals(2L, updatedTxn1.effectiveCategoryId)
        assertTrue(1L in repository.preferences.value.handledReviewTransactionIds)

        // 5. Load more transactions -> all review candidates are now handled, so allDone = true
        viewModel.loadMoreReviewTransactions()
        testScheduler.advanceUntilIdle()
        review = viewModel.uiState.value.categorizeReview
        assertTrue(review.allDone)

        // 6. Start Fun Mode ("Yes I need the dopamine") -> loads remaining non-handled transaction (id = 3L)
        viewModel.startFunReviewMode()
        testScheduler.advanceUntilIdle()
        review = viewModel.uiState.value.categorizeReview
        assertTrue(review.funMode)
        assertFalse(review.allDone)
        assertEquals(1, review.items.size)
        assertEquals(3L, review.currentTxn?.id)
        assertEquals(listOf("fun_review"), review.currentTxn?.reviewReasons)

        collectJob.cancel()
    }
}
