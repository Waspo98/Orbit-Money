package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.ReviewQueueCandidate
import app.overbay.orbit.domain.ReviewQueueEngine
import app.overbay.orbit.ui.dashboard.dialogs.ReviewSheetStage
import app.overbay.orbit.ui.dashboard.dialogs.resolveReviewSheetStage
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
        viewModel = DashboardViewModel(repository, testDispatcher)
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

        // Seed 2 uncategorized transactions (one null, one DB "Uncategorized") + 1 categorized + 1 transfer + 1 noisy merchant
        repository.saveCategory(Category(id = 99L, name = "Uncategorized", color = "#9E9E9E", icon = "❓"))
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
                originalCategoryId = 99L
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
        repository.saveTransaction(
            Transaction(
                id = 4L,
                accountId = 1L,
                originalDate = "2026-10-23",
                originalMerchant = "CHECKCARD 1022 CORNER MART",
                originalAmountCents = -800L,
                originalCategoryId = 1L
            )
        )
        repository.saveTransaction(
            Transaction(
                id = 5L,
                accountId = 1L,
                originalDate = "2026-10-24",
                originalMerchant = "Vault Transfer",
                originalAmountCents = -10000L,
                originalCategoryId = 99L,
                isTransfer = true
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Option A: reviewQueueCount reflects full ReviewQueueEngine count (2 uncategorized + 1 noisy merchant; excludes transfer 5L)
        assertEquals(3, viewModel.uiState.value.reviewQueueCount)

        // 1. Open review modal
        viewModel.openCategorizeReview()
        testScheduler.advanceUntilIdle()

        var review = viewModel.uiState.value.categorizeReview
        assertTrue(review.isOpen)
        assertFalse(review.allDone)
        assertEquals(3, review.items.size)
        assertEquals(4L, review.currentTxn?.id) // Newest date first (2026-10-23 noisy merchant)

        // Approve noisy merchant first (id = 4L)
        viewModel.approveReviewTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.reviewQueueCount)

        // 2. Approve first uncategorized transaction (id = 2L)
        viewModel.approveReviewTransaction()
        testScheduler.advanceUntilIdle()

        review = viewModel.uiState.value.categorizeReview
        assertEquals(2, review.currentIndex)
        assertEquals(2, review.batchReviewed)
        assertEquals(1L, review.currentTxn?.id)
        assertTrue(2L in repository.preferences.value.handledReviewTransactionIds)
        assertEquals(1, viewModel.uiState.value.reviewQueueCount)

        // 3. Go back to previous transaction
        viewModel.goBackReviewTransaction()
        testScheduler.advanceUntilIdle()

        review = viewModel.uiState.value.categorizeReview
        assertEquals(1, review.currentIndex)
        assertEquals(1, review.batchReviewed)
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
        assertEquals(0, viewModel.uiState.value.reviewQueueCount)

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
        assertTrue(3L in review.items.map { it.id })
        val bonusItem = review.items.first { it.id == 3L }
        assertEquals(listOf("fun_review"), bonusItem.reviewReasons)

        collectJob.cancel()
    }

    @Test
    fun testRapidConsecutiveApprovals_preserveAllHandledIdsAndIgnoreUncategorizedPattern() = runTest(testDispatcher) {
        repository.saveAccount(Account(id = 1L, name = "Checking", type = AccountType.CHECKING))
        val uncat = Category(id = 99L, name = "Uncategorized", color = "#9E9E9E", icon = "❓")
        val dining = Category(id = 2L, name = "Dining", color = "#F59E0B", icon = "🍽️")
        repository.saveCategory(uncat)
        repository.saveCategory(dining)

        // Even with 4 prior DB-Uncategorized rows for the same merchant, "Uncategorized" must never be suggested as dominant pattern
        repeat(4) { idx ->
            repository.saveTransaction(
                Transaction(
                    id = 200L + idx,
                    accountId = 1L,
                    originalDate = "2026-10-1${idx}",
                    originalMerchant = "Local Diner",
                    originalAmountCents = -1200L,
                    originalCategoryId = 99L
                )
            )
        }
        repository.saveTransaction(
            Transaction(
                id = 210L,
                accountId = 1L,
                originalDate = "2026-10-20",
                originalMerchant = "Local Diner",
                originalAmountCents = -1400L,
                originalCategoryId = 2L
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val queue = ReviewQueueEngine.buildReviewQueue(
            transactions = repository.transactions.value,
            categories = repository.categories.value,
            accounts = repository.accounts.value,
            excludedIds = emptySet()
        )
        assertFalse(queue.any { it.id == 210L }, "Properly categorized txn must not get 'Uncategorized' suggested back as dominant pattern")
        assertEquals(4, viewModel.uiState.value.reviewQueueCount)

        viewModel.openCategorizeReview()
        testScheduler.advanceUntilIdle()

        // Approve all 4 rapidly without advancing scheduler between swipes
        repeat(4) { viewModel.approveReviewTransaction() }
        testScheduler.advanceUntilIdle()

        assertEquals(setOf(200L, 201L, 202L, 203L), repository.preferences.value.handledReviewTransactionIds.toSet())
        assertEquals(0, viewModel.uiState.value.reviewQueueCount)

        collectJob.cancel()
    }

    @Test
    fun resolveReviewSheetStage_updatesHeaderTitleOutsideSwipeQuestion() {
        val candidate = ReviewQueueCandidate(
            id = 500L,
            merchant = "Coffee Shop",
            originalMerchant = "COFFEE SHOP",
            originalDescription = null,
            amountCents = -500L,
            isExpense = true,
            formattedAmount = "-$5.00",
            dateIso = "2026-11-01",
            dateDisplay = "Nov 1, 2026",
            accountId = 1L,
            accountName = "Checking",
            categoryId = null,
            categoryName = null,
            categoryColor = "",
            categoryIcon = null,
            suggestedCategoryId = null,
            reviewReasons = listOf("uncategorized"),
            merchantLogoUrl = null
        )

        val swipe = CategorizeReviewState(items = listOf(candidate), batchReviewed = 3)
        assertEquals(ReviewSheetStage.SWIPE_STAGE, resolveReviewSheetStage(swipe))
        assertEquals("Is this category correct?", resolveReviewSheetStage(swipe).headerTitle)

        val picker = swipe.copy(isCategoryPickerOpen = true)
        assertEquals(ReviewSheetStage.CATEGORY_PICKER, resolveReviewSheetStage(picker))
        assertEquals("Select Category", resolveReviewSheetStage(picker).headerTitle)

        val batchComplete = CategorizeReviewState(items = listOf(candidate), batchReviewed = 10)
        assertEquals(ReviewSheetStage.BATCH_COMPLETE, resolveReviewSheetStage(batchComplete))
        assertEquals("Stack Complete", resolveReviewSheetStage(batchComplete).headerTitle)

        val allDone = CategorizeReviewState(allDone = true)
        assertEquals(ReviewSheetStage.ALL_DONE, resolveReviewSheetStage(allDone))
        assertEquals("All Caught Up", resolveReviewSheetStage(allDone).headerTitle)
    }
}
