package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
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
class AccountsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: AccountsViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = AccountsViewModel(repository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_groupsAccountsAndCalculatesTotals() = runTest(testDispatcher) {
        val checking = Account(id = 1L, name = "Primary Checking", type = AccountType.CHECKING, currentBalanceCents = 500000L) // $5,000
        val savings = Account(id = 2L, name = "High Yield Savings", type = AccountType.SAVINGS, currentBalanceCents = 1500000L) // $15,000
        val creditCard = Account(id = 3L, name = "Sapphire Preferred", type = AccountType.CREDIT, currentBalanceCents = -120000L) // -$1,200
        val investment = Account(id = 4L, name = "Vanguard Brokerage", type = AccountType.INVESTMENT, currentBalanceCents = 4000000L) // $40,000
        val mortgage = Account(id = 5L, name = "Primary Home Mortgage", type = AccountType.MORTGAGE, currentBalanceCents = -30000000L, estimatedValueCents = 45000000L) // Equity $150,000

        repository.saveAccount(checking)
        repository.saveAccount(savings)
        repository.saveAccount(creditCard)
        repository.saveAccount(investment)
        repository.saveAccount(mortgage)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.accounts.size)

        // 4 distinct groups: Cash & Checking, Credit Cards, Investments, Real Estate
        assertEquals(4, state.accountGroups.size)

        val cashGroup = state.accountGroups.find { it.category == AccountTypeCategory.CASH_AND_CHECKING }
        assertNotNull(cashGroup)
        assertEquals(2, cashGroup.accounts.size)
        assertEquals(2000000L, cashGroup.totalCents) // 5k + 15k

        val creditGroup = state.accountGroups.find { it.category == AccountTypeCategory.CREDIT_CARDS }
        assertNotNull(creditGroup)
        assertEquals(1, creditGroup.accounts.size)
        assertEquals(-120000L, creditGroup.totalCents)

        val realEstateGroup = state.accountGroups.find { it.category == AccountTypeCategory.REAL_ESTATE }
        assertNotNull(realEstateGroup)
        assertEquals(1, realEstateGroup.accounts.size)
        assertEquals(15000000L, realEstateGroup.totalCents) // 450k - 300k = 150k equity

        // Assets = 5k + 15k + 40k + 450k = 510,000 ($5,100.00 in k)
        // Liabilities = 1.2k + 300k = 301,200
        // Net worth = Assets - Liabilities = 510k - 301.2k = 208.8k ($208,800.00)
        assertTrue(state.totalAssetsCents > 0L)
        assertTrue(state.totalLiabilitiesCents > 0L)
        assertEquals(state.totalAssetsCents - state.totalLiabilitiesCents, state.netWorthCents)

        collectJob.cancel()
    }

    @Test
    fun testAccountCrud_createEditDelete() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Open Add
        viewModel.openAddAccount(AccountType.SAVINGS)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        assertNotNull(viewModel.uiState.value.editingAccount)
        assertEquals(AccountType.SAVINGS, viewModel.uiState.value.editingAccount?.type)

        // Save
        val newAcct = Account(id = 10L, name = "Travel Fund", type = AccountType.SAVINGS, currentBalanceCents = 250000L)
        viewModel.saveAccount(newAcct)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showEditorSheet)
        assertEquals(1, repository.accounts.value.size)

        // Edit
        val updated = newAcct.copy(currentBalanceCents = 300000L)
        viewModel.openEditAccount(updated)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEditorSheet)
        viewModel.saveAccount(updated)
        testScheduler.advanceUntilIdle()
        assertEquals(300000L, repository.accounts.value.first().currentBalanceCents)

        // Delete
        viewModel.deleteAccount(10L)
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.accounts.value.size)

        collectJob.cancel()
    }

    @Test
    fun testCreditCardProfile_savingAndUtilization() = runTest(testDispatcher) {
        val cardAcct = Account(
            id = 20L,
            name = "Chase Sapphire",
            type = AccountType.CREDIT,
            currentBalanceCents = -150000L // -$1,500
        )
        repository.saveAccount(cardAcct)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.openCreditCardProfile(cardAcct)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showCardProfileSheet)

        val profile = CreditCardProfile(
            accountId = 20L,
            cardName = "Chase Sapphire Reserve",
            issuerName = "Chase",
            network = "Visa",
            annualFeeCents = 55000L, // $550
            annualFeePostDate = "2026-12-01",
            creditLimitCents = 1000000L, // $10,000
            statementClosingDay = 15,
            paymentDueDay = 10,
            rewardCategories = listOf(
                RewardCategory(category = "Dining", multiplier = 3.0),
                RewardCategory(category = "Travel", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(title = "Annual Travel Credit", annualCreditValueCents = 30000L, usedThisYear = true)
            )
        )

        viewModel.saveCreditCardProfile(profile)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCardProfileSheet)
        val savedAcct = repository.accounts.value.find { it.id == 20L }
        assertNotNull(savedAcct)
        assertNotNull(savedAcct.creditCardProfile)
        assertEquals("Chase Sapphire Reserve", savedAcct.creditCardProfile?.cardName)
        assertEquals(55000L, savedAcct.creditCardProfile?.annualFeeCents)
        assertEquals(0.15, savedAcct.creditCardProfile?.creditUtilizationRatio(cardAcct.currentBalanceCents))

        collectJob.cancel()
    }

    @Test
    fun testMergeAccounts_repointsTransactionsAndCombinesBalances() = runTest(testDispatcher) {
        val oldAcct = Account(id = 30L, name = "Old Checking", type = AccountType.CHECKING, currentBalanceCents = 20000L)
        val newAcct = Account(id = 31L, name = "New Checking", type = AccountType.CHECKING, currentBalanceCents = 80000L)
        repository.saveAccount(oldAcct)
        repository.saveAccount(newAcct)

        val txn1 = Transaction(id = 101L, accountId = 30L, originalDate = "2026-10-01", originalMerchant = "Cafe", originalAmountCents = -500L)
        val txn2 = Transaction(id = 102L, accountId = 31L, originalDate = "2026-10-02", originalMerchant = "Market", originalAmountCents = -2000L)
        repository.saveTransaction(txn1)
        repository.saveTransaction(txn2)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Merge old into new with combineBalances = true
        viewModel.openMergeDialog(oldAcct)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showMergeDialog)

        viewModel.mergeAccountInto(sourceAccountId = 30L, targetAccountId = 31L, combineBalances = true)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showMergeDialog)

        // Old account is removed
        assertNull(repository.accounts.value.find { it.id == 30L })
        val target = repository.accounts.value.find { it.id == 31L }
        assertNotNull(target)
        // Balances combined: 20k + 80k = 100k
        assertEquals(100000L, target.currentBalanceCents)

        // All transactions now belong to target account 31
        val txns = repository.transactions.value
        assertEquals(2, txns.size)
        assertTrue(txns.all { it.accountId == 31L })

        collectJob.cancel()
    }

    @Test
    fun testSelectAccount_updatesTransactionsList() = runTest(testDispatcher) {
        val acct1 = Account(id = 40L, name = "Acct 1", type = AccountType.CHECKING, currentBalanceCents = 1000L)
        val acct2 = Account(id = 41L, name = "Acct 2", type = AccountType.CHECKING, currentBalanceCents = 2000L)
        repository.saveAccount(acct1)
        repository.saveAccount(acct2)

        repository.saveTransaction(Transaction(id = 1L, accountId = 40L, originalDate = "2026-10-01", originalMerchant = "Coffee", originalAmountCents = -400L))
        repository.saveTransaction(Transaction(id = 2L, accountId = 41L, originalDate = "2026-10-02", originalMerchant = "Groceries", originalAmountCents = -5000L))

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.selectAccount(40L)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.selectedAccountTransactions.size)
        assertEquals(1L, viewModel.uiState.value.selectedAccountTransactions.first().id)

        viewModel.selectAccount(41L)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.selectedAccountTransactions.size)
        assertEquals(2L, viewModel.uiState.value.selectedAccountTransactions.first().id)

        collectJob.cancel()
    }
}
