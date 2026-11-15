package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.addBalanceRecord
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
        viewModel = AccountsViewModel(
            repository = repository,
            computeDispatcher = testDispatcher,
            todayFlow = flowOf(LocalDateUtils.todayLocalDate())
        )
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

    @Test
    fun testRequestDeleteAccount_setsDeleteWarning524DateWhenWithin24Months() = runTest(testDispatcher) {
        val today = LocalDateUtils.todayLocalDate()
        val opened12MonthsAgo = LocalDateUtils.formatIsoDate(LocalDateUtils.addMonthsClamped(today, -12))
        val opened30MonthsAgo = LocalDateUtils.formatIsoDate(LocalDateUtils.addMonthsClamped(today, -30))

        val recentCard = Account(
            id = 50L,
            name = "Recent Card",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 50L,
                strategy = CreditCardStrategy(openedDate = opened12MonthsAgo)
            )
        )
        val oldCard = Account(
            id = 51L,
            name = "Old Card",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 51L,
                strategy = CreditCardStrategy(openedDate = opened30MonthsAgo)
            )
        )
        repository.saveAccount(recentCard)
        repository.saveAccount(oldCard)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Request delete on recent card (< 24 months)
        viewModel.requestDeleteAccount(50L)
        testScheduler.advanceUntilIdle()
        assertEquals(50L, viewModel.uiState.value.deleteConfirmAccountId)
        assertNotNull(viewModel.uiState.value.deleteWarning524Date)

        // Dismiss clears both
        viewModel.dismissDeleteAccount()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.deleteConfirmAccountId)
        assertNull(viewModel.uiState.value.deleteWarning524Date)

        // 2. Request delete on old card (> 24 months) -> warning date is null
        viewModel.requestDeleteAccount(51L)
        testScheduler.advanceUntilIdle()
        assertEquals(51L, viewModel.uiState.value.deleteConfirmAccountId)
        assertNull(viewModel.uiState.value.deleteWarning524Date)

        viewModel.dismissDeleteAccount()
        collectJob.cancel()
    }

    @Test
    fun testRewardsToolsEnabled_autoMode_and_explicitOverride() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. By default with no strategy data on any account, auto mode resolves to false
        assertFalse(viewModel.uiState.value.rewardsToolsEnabled)

        // 2. Add an account with strategy openedDate -> auto mode resolves to true
        val card = Account(
            id = 100L,
            name = "Chase Sapphire",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 100L,
                strategy = CreditCardStrategy(openedDate = "2025-01-15")
            )
        )
        repository.saveAccount(card)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rewardsToolsEnabled)

        // 3. Explicit preference false overrides auto mode
        repository.updatePreferences(repository.preferences.value.copy(cardRewardsToolsEnabled = false))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.rewardsToolsEnabled)

        // 4. Explicit preference true forces it on
        repository.updatePreferences(repository.preferences.value.copy(cardRewardsToolsEnabled = true))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.rewardsToolsEnabled)

        collectJob.cancel()
    }

    @Test
    fun testOpenPerkUsage_populatesTargetAndCloseClearsIt() = runTest(testDispatcher) {
        val benefit = CardBenefit(id = "b_dining", title = "Dining Credit", periodValueCents = 2500L)
        val card = Account(
            id = 200L,
            name = "Amex Gold",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 200L,
                benefits = listOf(benefit)
            )
        )
        repository.saveAccount(card)
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.perkUsageTarget)

        viewModel.openPerkUsage(200L, "b_dining")
        testScheduler.advanceUntilIdle()

        val target = viewModel.uiState.value.perkUsageTarget
        assertNotNull(target)
        assertEquals(200L, target.accountId)
        assertEquals("b_dining", target.benefitId)
        assertEquals("Dining Credit", target.title)
        assertEquals(2500L, target.valueCents)

        viewModel.closePerkUsage()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.perkUsageTarget)

        collectJob.cancel()
    }

    @Test
    fun testCreditCardsHub_computesHouseholdCreditAndCycles() = runTest(testDispatcher) {
        val card = Account(
            id = 300L,
            name = "Chase Sapphire Reserve",
            type = AccountType.CREDIT,
            currentBalanceCents = -250000L,
            creditCardProfile = CreditCardProfile(
                accountId = 300L,
                cardName = "Sapphire Reserve",
                creditLimitCents = 1000000L,
                statementClosingDay = 15,
                paymentDueDay = 10,
                annualFeeCents = 55000L,
                annualFeePostDate = "2026-11-20"
            )
        )
        repository.saveAccount(card)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.householdCredit)
        assertEquals(250000L, state.householdCredit?.totalOwedCents)
        assertEquals(1000000L, state.householdCredit?.totalLimitCents)
        assertEquals(2500, state.householdCredit?.utilizationBps) // 25%

        assertEquals(1, state.cardCycles.size)
        val cycle = state.cardCycles.first()
        assertEquals(300L, cycle.accountId)
        assertEquals(250000L, cycle.owedNowCents)

        collectJob.cancel()
    }

    @Test
    fun testCreditCardsHub_controlsAndChase524HolderSelection() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showUsedPerks)
        viewModel.toggleShowUsedPerks()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showUsedPerks)

        assertEquals("", viewModel.uiState.value.whichCardQuery)
        viewModel.cardActions.setWhichCardQuery("Dining")
        testScheduler.advanceUntilIdle()
        assertEquals("Dining", viewModel.uiState.value.whichCardQuery)

        assertEquals(PerkPeriodFilter.ALL, viewModel.uiState.value.perkPeriodFilter)
        viewModel.cardActions.selectPerkFilter(PerkPeriodFilter.EXPIRING_SOON)
        testScheduler.advanceUntilIdle()
        assertEquals(PerkPeriodFilter.EXPIRING_SOON, viewModel.uiState.value.perkPeriodFilter)

        assertFalse(viewModel.uiState.value.showCardPerksCustomize)
        viewModel.cardActions.setCardPerksCustomize(true)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showCardPerksCustomize)

        assertNull(viewModel.uiState.value.chase524SelectedHolderKey)
        viewModel.selectChase524Holder("m:20")
        testScheduler.advanceUntilIdle()
        assertEquals("m:20", viewModel.uiState.value.chase524SelectedHolderKey)

        collectJob.cancel()
    }

    @Test
    fun testDetailTabAndBalanceHistoryRouting_inlineForStandardAndSheetForCredit() = runTest(testDispatcher) {
        val checking = Account(id = 401L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 10000L)
        val savings = Account(id = 402L, name = "Savings", type = AccountType.SAVINGS, currentBalanceCents = 50000L)
        val credit = Account(id = 403L, name = "Sapphire", type = AccountType.CREDIT, currentBalanceCents = -2500L)
        repository.saveAccount(checking)
        repository.saveAccount(savings)
        repository.saveAccount(credit)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        assertEquals(AccountDetailTab.TRANSACTIONS, viewModel.uiState.value.activeDetailTab)

        viewModel.selectAccountForInspect(401L)
        testScheduler.advanceUntilIdle()
        assertEquals(AccountDetailTab.TRANSACTIONS, viewModel.uiState.value.activeDetailTab)

        viewModel.selectDetailTab(AccountDetailTab.BALANCE_HISTORY)
        testScheduler.advanceUntilIdle()
        assertEquals(AccountDetailTab.BALANCE_HISTORY, viewModel.uiState.value.activeDetailTab)

        // Selecting a different account resets tab back to Transactions
        viewModel.selectAccountForInspect(402L)
        testScheduler.advanceUntilIdle()
        assertEquals(AccountDetailTab.TRANSACTIONS, viewModel.uiState.value.activeDetailTab)

        // openBalanceHistory on a standard account opens inline BALANCE_HISTORY tab without showHistorySheet
        viewModel.openBalanceHistory(checking)
        testScheduler.advanceUntilIdle()
        assertEquals(401L, viewModel.uiState.value.selectedAccountId)
        assertEquals(AccountDetailTab.BALANCE_HISTORY, viewModel.uiState.value.activeDetailTab)
        assertFalse(viewModel.uiState.value.showHistorySheet)

        // openBalanceHistory on a credit card opens the dedicated history sub-mode
        viewModel.openBalanceHistory(credit)
        testScheduler.advanceUntilIdle()
        assertEquals(403L, viewModel.uiState.value.selectedAccountId)
        assertTrue(viewModel.uiState.value.showHistorySheet)
        assertEquals(403L, viewModel.uiState.value.historyAccount?.id)

        // openBalanceHistory on a standalone property stays on Transactions tab without opening sheet
        val property = Account(id = 404L, name = "Primary Home", type = AccountType.OTHER, estimatedValueCents = 45_000_000L, isProperty = true)
        repository.saveAccount(property)
        viewModel.openBalanceHistory(property)
        testScheduler.advanceUntilIdle()
        assertEquals(404L, viewModel.uiState.value.selectedAccountId)
        assertEquals(AccountDetailTab.TRANSACTIONS, viewModel.uiState.value.activeDetailTab)
        assertFalse(viewModel.uiState.value.showHistorySheet)

        collectJob.cancel()
    }

    @Test
    fun testBalanceHistory_defaultsToChangesOnlyAndTogglesAllSnapshots() = runTest(testDispatcher) {
        val hysa = repository.saveAccount(
            Account(id = 501L, name = "Betterment High Yield", type = AccountType.SAVINGS, currentBalanceCents = 0L)
        )
        val other = repository.saveAccount(
            Account(id = 502L, name = "Checking", type = AccountType.CHECKING, currentBalanceCents = 100_000L)
        )
        repository.addBalanceRecord(hysa.id, "2026-10-01", 5_000_000L)
        repository.addBalanceRecord(hysa.id, "2026-11-01", 5_040_377L)
        repository.addBalanceRecord(hysa.id, "2026-11-03", 5_040_377L)
        repository.addBalanceRecord(hysa.id, "2026-11-14", 5_040_377L)

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.openBalanceHistory(hysa)
        testScheduler.advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertFalse(initialState.showAllBalanceSnapshots)
        assertEquals(2, initialState.unchangedBalanceSnapshotCount)
        assertEquals(listOf("2026-11-01", "2026-10-01"), initialState.displayedBalanceRecords.map { it.recordDate })

        viewModel.toggleShowAllBalanceSnapshots()
        testScheduler.advanceUntilIdle()

        val expandedState = viewModel.uiState.value
        assertTrue(expandedState.showAllBalanceSnapshots)
        assertEquals(2, expandedState.unchangedBalanceSnapshotCount)
        assertEquals(
            listOf("2026-11-14", "2026-11-03", "2026-11-01", "2026-10-01"),
            expandedState.displayedBalanceRecords.map { it.recordDate }
        )

        // Switching to another account resets the snapshot expansion toggle
        viewModel.selectAccountForInspect(other.id)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showAllBalanceSnapshots)

        collectJob.cancel()
    }

    @Test
    fun testToggleCardBenefit_threeStateProgressionAndUndo() = runTest(testDispatcher) {
        val card = repository.saveAccount(
            Account(
                id = 601L,
                name = "Gold Card",
                type = AccountType.CREDIT,
                creditCardProfile = app.overbay.orbit.data.model.CreditCardProfile(
                    accountId = 601L,
                    cardName = "Gold Card",
                    benefits = listOf(
                        app.overbay.orbit.data.model.CardBenefit(
                            id = "dining",
                            title = "Dining Credit",
                            periodValueCents = 2_500L,
                            cadence = app.overbay.orbit.data.model.BenefitCadence.MONTHLY
                        )
                    )
                )
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 0 -> 2500L
        viewModel.toggleCardBenefit(card.id, "dining")
        testScheduler.advanceUntilIdle()
        val fullPerk = viewModel.uiState.value.cardPortfolioSummary.currentPeriodPerks.single { it.benefitId == "dining" }
        assertEquals(2_500L, fullPerk.usedCents)
        assertTrue(fullPerk.isFullyUsed)
        val firstUndo = viewModel.uiState.value.lastPerkUndoEvent
        assertNotNull(firstUndo)
        assertEquals(null, firstUndo.previousAmountCents)
        assertEquals(2_500L, firstUndo.newAmountCents)
        assertEquals("Marked Dining Credit used ($25)", formatPerkToggleUndoMessage(firstUndo))

        // Undo 2500L -> null (0L)
        viewModel.undoLastPerkToggle(firstUndo)
        testScheduler.advanceUntilIdle()
        val clearedPerk = viewModel.uiState.value.cardPortfolioSummary.currentPeriodPerks.single { it.benefitId == "dining" }
        assertEquals(0L, clearedPerk.usedCents)
        assertFalse(clearedPerk.isFullyUsed)
        assertEquals(null, viewModel.uiState.value.lastPerkUndoEvent)

        // Partial 700L -> toggle finishes to 2500L -> Undo restores 700L -> toggle twice clears to 0L
        viewModel.setCardBenefitUsage(card.id, "dining", 700L)
        testScheduler.advanceUntilIdle()
        val partialPerk = viewModel.uiState.value.cardPortfolioSummary.currentPeriodPerks.single { it.benefitId == "dining" }
        assertEquals(700L, partialPerk.usedCents)
        assertFalse(partialPerk.isFullyUsed)

        viewModel.toggleCardBenefit(card.id, "dining")
        testScheduler.advanceUntilIdle()
        val completedFromPartial = viewModel.uiState.value.cardPortfolioSummary.currentPeriodPerks.single { it.benefitId == "dining" }
        assertEquals(2_500L, completedFromPartial.usedCents)
        val partialUndo = viewModel.uiState.value.lastPerkUndoEvent
        assertNotNull(partialUndo)
        assertEquals(700L, partialUndo.previousAmountCents)
        assertEquals(2_500L, partialUndo.newAmountCents)

        viewModel.undoLastPerkToggle(partialUndo)
        testScheduler.advanceUntilIdle()
        val restoredPartial = viewModel.uiState.value.cardPortfolioSummary.currentPeriodPerks.single { it.benefitId == "dining" }
        assertEquals(700L, restoredPartial.usedCents)
        assertEquals(null, viewModel.uiState.value.lastPerkUndoEvent)

        // Partial -> full -> toggle again -> null
        viewModel.toggleCardBenefit(card.id, "dining")
        testScheduler.advanceUntilIdle()
        viewModel.toggleCardBenefit(card.id, "dining")
        testScheduler.advanceUntilIdle()
        val toggledClear = viewModel.uiState.value.cardPortfolioSummary.currentPeriodPerks.single { it.benefitId == "dining" }
        assertEquals(0L, toggledClear.usedCents)
        assertFalse(toggledClear.isFullyUsed)

        collectJob.cancel()
    }

    @Test
    fun testOpenCreditCardProfile_deepLinksToTabAndTemplatePicker() = runTest(testDispatcher) {
        val card = repository.saveAccount(
            Account(
                id = 602L,
                name = "Venture X",
                type = AccountType.CREDIT
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.openCreditCardProfile(
            account = card,
            initialTab = app.overbay.orbit.ui.accounts.cards.CardEditorTab.BILLING,
            forceTemplatePicker = true
        )
        testScheduler.advanceUntilIdle()

        val draft = viewModel.uiState.value.cardEditorDraft
        assertNotNull(draft)
        assertEquals(602L, draft.accountId)
        assertEquals(app.overbay.orbit.ui.accounts.cards.CardEditorTab.BILLING, draft.activeTab)
        assertEquals(app.overbay.orbit.ui.accounts.cards.CardEditorStep.PICK_TEMPLATE, draft.step)

        collectJob.cancel()
    }

    @Test
    fun testToggleCardBenefit_nonMonetaryPerkOmitsPennyCopyAndRespectsOriginDismissal() = runTest(testDispatcher) {
        val card = repository.saveAccount(
            Account(
                id = 603L,
                name = "Airline Card",
                type = AccountType.CREDIT,
                creditCardProfile = CreditCardProfile(
                    accountId = 603L,
                    cardName = "Airline Card",
                    benefits = listOf(
                        CardBenefit(
                            id = "companion_pass",
                            title = "Companion Certificate",
                            periodValueCents = 0L,
                            cadence = app.overbay.orbit.data.model.BenefitCadence.ANNUAL
                        )
                    )
                )
            )
        )

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.toggleCardBenefit(card.id, "companion_pass", PerkToggleOrigin.ACCOUNTS)
        testScheduler.advanceUntilIdle()

        val usedEvent = viewModel.uiState.value.lastPerkUndoEvent
        assertNotNull(usedEvent)
        assertFalse(usedEvent.isMonetaryValue)
        assertEquals(1L, usedEvent.newAmountCents)
        assertEquals(PerkToggleOrigin.ACCOUNTS, usedEvent.origin)
        assertEquals("Marked Companion Certificate used", formatPerkToggleUndoMessage(usedEvent))

        // Dismissing CARD_PERKS origin must NOT clear an ACCOUNTS event, whereas ACCOUNTS dismissal clears it
        viewModel.dismissPerkUndo(PerkToggleOrigin.CARD_PERKS)
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.lastPerkUndoEvent)

        viewModel.dismissPerkUndo(PerkToggleOrigin.ACCOUNTS)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastPerkUndoEvent)

        // Toggling again clears back to null and formats clear copy cleanly
        viewModel.toggleCardBenefit(card.id, "companion_pass", PerkToggleOrigin.CARD_PERKS)
        testScheduler.advanceUntilIdle()
        val clearedEvent = viewModel.uiState.value.lastPerkUndoEvent
        assertNotNull(clearedEvent)
        assertNull(clearedEvent.newAmountCents)
        assertEquals("Cleared Companion Certificate", formatPerkToggleUndoMessage(clearedEvent))

        collectJob.cancel()
    }
}

