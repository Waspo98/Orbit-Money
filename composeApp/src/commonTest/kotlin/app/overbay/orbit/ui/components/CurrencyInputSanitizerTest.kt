package app.overbay.orbit.ui.components

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class CurrencyInputSanitizerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun sanitizeCurrencyInput_preservesMidStringCursorPosition() {
        // Inserting '9' after '1' in "123.45" -> "1923.45" with cursor at index 2
        val (sanitized, cursor) = sanitizeCurrencyInputWithCursor(
            input = "1923.45",
            cursorEnd = 2,
            allowNegative = false
        )
        assertEquals("1923.45", sanitized)
        assertEquals(2, cursor)
    }

    @Test
    fun sanitizeCurrencyInput_stripsInvalidCharsWithoutJumpingCursorToEnd() {
        // Typing invalid 'a' after "12" in "123.45" -> input "12a3.45" with cursor at 3
        val (sanitized, cursor) = sanitizeCurrencyInputWithCursor(
            input = "12a3.45",
            cursorEnd = 3,
            allowNegative = false
        )
        assertEquals("123.45", sanitized)
        assertEquals(2, cursor)
    }

    @Test
    fun sanitizeCurrencyInput_enforcesTwoDecimalPlacesAndDuplicateDots() {
        val (capped, cappedCursor) = sanitizeCurrencyInputWithCursor(
            input = "45.678",
            cursorEnd = 6,
            allowNegative = false
        )
        assertEquals("45.67", capped)
        assertEquals(5, cappedCursor)

        // Inserting a whole-dollar digit before '.' still works when 2 decimal places exist
        val (wholeInsert, wholeCursor) = sanitizeCurrencyInputWithCursor(
            input = "459.67",
            cursorEnd = 3,
            allowNegative = false
        )
        assertEquals("459.67", wholeInsert)
        assertEquals(3, wholeCursor)

        // Duplicate '.' is stripped cleanly without cursor jump
        val (dedupDot, dedupCursor) = sanitizeCurrencyInputWithCursor(
            input = "12.3.4",
            cursorEnd = 5,
            allowNegative = false
        )
        assertEquals("12.34", dedupDot)
        assertEquals(4, dedupCursor)
    }

    @Test
    fun sanitizeCurrencyInput_respectsAllowNegativeFlag() {
        val (allowed, allowedCursor) = sanitizeCurrencyInputWithCursor(
            input = "-45-2.10",
            cursorEnd = 5,
            allowNegative = true
        )
        assertEquals("-452.10", allowed)
        assertEquals(4, allowedCursor)

        val (disallowed, disallowedCursor) = sanitizeCurrencyInputWithCursor(
            input = "-45.10",
            cursorEnd = 3,
            allowNegative = false
        )
        assertEquals("45.10", disallowed)
        assertEquals(2, disallowedCursor)
    }

    @Test
    fun formatEditableCurrency_omitsZeroCentsOnWholeDollars() {
        assertEquals("", formatEditableCurrency(0L))
        assertEquals("45", formatEditableCurrency(4500L))
        assertEquals("-120", formatEditableCurrency(-12000L))
        assertEquals("45.67", formatEditableCurrency(4567L))
        assertEquals("0.05", formatEditableCurrency(5L))
    }

    @Test
    fun synchronousSearchInput_updatesImmediatelyBeforeAsyncFlowEmission() = runTest(testDispatcher) {
        val vm = TransactionsViewModel(repository, testDispatcher)
        val collector = launch(testDispatcher) { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        // Mutate search query without advancing testDispatcher: synchronous state updates in 0ms
        vm.setSearchQuery("starbucks")
        assertEquals("starbucks", vm.searchQueryInput)

        testScheduler.advanceUntilIdle()
        assertEquals("starbucks", vm.uiState.value.searchQuery)

        vm.setSearchActive(false)
        assertEquals("", vm.searchQueryInput)
        collector.cancel()
    }

    @Test
    fun cardEditorKeystrokes_updateCurrentDraftWithoutRecomputingAccountsUiState() = runTest(testDispatcher) {
        val card = Account(
            id = 77L,
            name = "Sapphire",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(accountId = 77L, cardName = "Sapphire Preferred")
        )
        repository.saveAccount(card)

        val vm = AccountsViewModel(
            repository = repository,
            computeDispatcher = testDispatcher,
            todayFlow = flowOf(LocalDateUtils.todayLocalDate())
        )
        var emissions = 0
        val collector = launch(testDispatcher) {
            vm.uiState.collect { emissions++ }
        }
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.showCardProfileSheet)

        // Opening the card editor updates currentDraft synchronously and flips showCardProfileSheet
        vm.cardEditor.open(card)
        assertNotNull(vm.cardEditor.currentDraft)
        assertEquals("Sapphire Preferred", vm.cardEditor.currentDraft?.cardName)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.showCardProfileSheet)
        assertEquals("Sapphire Preferred", vm.uiState.value.cardEditorDraft?.cardName)

        val emissionsAfterOpen = emissions

        // Typing characters updates currentDraft synchronously without triggering AccountsUiState recomputation
        vm.cardEditor.updateDraft { it.copy(cardName = "Sapphire Reserve") }
        vm.cardEditor.updateDraft { it.copy(cardName = "Sapphire Reserve!") }
        assertEquals("Sapphire Reserve!", vm.cardEditor.currentDraft?.cardName)
        testScheduler.advanceUntilIdle()
        assertEquals(emissionsAfterOpen, emissions)
        assertEquals("Sapphire Preferred", vm.uiState.value.cardEditorDraft?.cardName)

        // Closing the editor flips showCardProfileSheet back to false
        vm.cardEditor.clearDraft()
        assertNull(vm.cardEditor.currentDraft)
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.showCardProfileSheet)
        collector.cancel()
    }
}
