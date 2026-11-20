package app.overbay.orbit

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.ui.accounts.PerkToggleOrigin
import app.overbay.orbit.ui.components.CanopyTransitionCoordinator
import app.overbay.orbit.ui.components.TransactionFilterDraft
import app.overbay.orbit.ui.components.canopyBackdropFillSize
import app.overbay.orbit.ui.components.canopyBackdropFillTopLeft
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTransitionTest {

    @Test
    fun `primary tabs have deterministic left-to-right spatial indices`() {
        assertEquals(0, OrbitNavKey.Dashboard.primaryTabIndex)
        assertEquals(1, OrbitNavKey.Transactions().primaryTabIndex)
        assertEquals(1, OrbitNavKey.Transactions(transactionId = 99L).primaryTabIndex)
        assertEquals(2, OrbitNavKey.Budgets.primaryTabIndex)
        assertEquals(3, OrbitNavKey.Accounts().primaryTabIndex)
        assertEquals(3, OrbitNavKey.Accounts(accountId = 7L).primaryTabIndex)
    }

    @Test
    fun `secondary and auth destinations have null primaryTabIndex`() {
        val secondaryKeys = listOf(
            OrbitNavKey.Login,
            OrbitNavKey.Onboarding,
            OrbitNavKey.Upcoming,
            OrbitNavKey.Goals,
            OrbitNavKey.Categories,
            OrbitNavKey.Rules,
            OrbitNavKey.SpendingTrends,
            OrbitNavKey.NetWorth,
            OrbitNavKey.MhaTracker,
            OrbitNavKey.HousingCalculator,
            OrbitNavKey.RetirementCalculator,
            OrbitNavKey.Household,
            OrbitNavKey.Settings()
        )
        secondaryKeys.forEach { key ->
            assertNull(key.primaryTabIndex, "Expected null primaryTabIndex for $key")
        }
    }

    @Test
    fun `moving from left primary tab to right primary tab slides viewfinder right`() {
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_RIGHT,
            resolveOrbitTransitionDirection(OrbitNavKey.Dashboard, OrbitNavKey.Transactions())
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_RIGHT,
            resolveOrbitTransitionDirection(OrbitNavKey.Dashboard, OrbitNavKey.Budgets)
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_RIGHT,
            resolveOrbitTransitionDirection(OrbitNavKey.Dashboard, OrbitNavKey.Accounts())
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_RIGHT,
            resolveOrbitTransitionDirection(OrbitNavKey.Transactions(), OrbitNavKey.Budgets)
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_RIGHT,
            resolveOrbitTransitionDirection(OrbitNavKey.Budgets, OrbitNavKey.Accounts(accountId = 12L))
        )
    }

    @Test
    fun `moving from right primary tab to left primary tab slides viewfinder left`() {
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_LEFT,
            resolveOrbitTransitionDirection(OrbitNavKey.Accounts(), OrbitNavKey.Budgets)
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_LEFT,
            resolveOrbitTransitionDirection(OrbitNavKey.Accounts(), OrbitNavKey.Dashboard)
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_LEFT,
            resolveOrbitTransitionDirection(OrbitNavKey.Budgets, OrbitNavKey.Transactions())
        )
        assertEquals(
            OrbitTransitionDirection.VIEWFINDER_LEFT,
            resolveOrbitTransitionDirection(OrbitNavKey.Transactions(transactionId = 5L), OrbitNavKey.Dashboard)
        )
    }

    @Test
    fun `same-tab parameter changes use crossfade without sliding`() {
        assertEquals(
            OrbitTransitionDirection.CROSSFADE,
            resolveOrbitTransitionDirection(
                OrbitNavKey.Transactions(),
                OrbitNavKey.Transactions(transactionId = 42L)
            )
        )
        assertEquals(
            OrbitTransitionDirection.CROSSFADE,
            resolveOrbitTransitionDirection(
                OrbitNavKey.Accounts(),
                OrbitNavKey.Accounts(accountId = 3L)
            )
        )
    }

    @Test
    fun `more destinations use push and pop transitions instead of horizontal tab slide`() {
        assertEquals(
            OrbitTransitionDirection.MORE_PUSH,
            resolveOrbitTransitionDirection(OrbitNavKey.Dashboard, OrbitNavKey.Settings())
        )
        assertEquals(
            OrbitTransitionDirection.MORE_PUSH,
            resolveOrbitTransitionDirection(OrbitNavKey.Accounts(), OrbitNavKey.NetWorth)
        )
        assertEquals(
            OrbitTransitionDirection.MORE_PUSH,
            resolveOrbitTransitionDirection(OrbitNavKey.Upcoming, OrbitNavKey.Goals)
        )
        assertEquals(
            OrbitTransitionDirection.MORE_POP,
            resolveOrbitTransitionDirection(OrbitNavKey.Settings(), OrbitNavKey.Dashboard)
        )
        assertEquals(
            OrbitTransitionDirection.MORE_POP,
            resolveOrbitTransitionDirection(OrbitNavKey.NetWorth, OrbitNavKey.Accounts())
        )
    }

    @Test
    fun `canopy coordinator synchronizes outgoing and incoming header heights across transitions`() = runTest {
        val coordinator = CanopyTransitionCoordinator(initialKeyId = 0)

        // At rest on Dashboard (key 0, height 280px)
        assertEquals(280, coordinator.resolveDisplayHeightPx(screenKeyId = 0, naturalHeightPx = 280))
        assertEquals(280, coordinator.sharedDisplayHeightPx)

        // Scroll collapse on Dashboard (280px -> 180px) snaps immediately with zero lag
        assertEquals(180, coordinator.resolveDisplayHeightPx(screenKeyId = 0, naturalHeightPx = 180))

        // Navigate to Transactions (key 1, height 520px)
        coordinator.onTargetKeyChanged(newKeyId = 1)
        assertEquals(0f, coordinator.transitionFraction)

        // Frame 0: both Dashboard (0) and Transactions (1) resolve to the starting height (180px)
        val outFrame0 = coordinator.resolveDisplayHeightPx(screenKeyId = 0, naturalHeightPx = 180)
        val inFrame0 = coordinator.resolveDisplayHeightPx(screenKeyId = 1, naturalHeightPx = 520)
        assertEquals(180, outFrame0)
        assertEquals(180, inFrame0)
        assertEquals(520, coordinator.targetHeightPx)

        // Mid-transition (50%): both Dashboard and Transactions resolve to 350px
        coordinator.updateTransitionFraction(0.5f)
        assertEquals(350, coordinator.resolveDisplayHeightPx(screenKeyId = 0, naturalHeightPx = 180))
        assertEquals(350, coordinator.resolveDisplayHeightPx(screenKeyId = 1, naturalHeightPx = 520))

        // Complete transition animation
        coordinator.updateTransitionFraction(1f)
        assertEquals(1f, coordinator.transitionFraction)
        assertEquals(520, coordinator.resolveDisplayHeightPx(screenKeyId = 0, naturalHeightPx = 180))
        assertEquals(520, coordinator.resolveDisplayHeightPx(screenKeyId = 1, naturalHeightPx = 520))
        assertTrue(!coordinator.isTransitioning)
    }

    @Test
    fun `canopy backdrop fill bounds cover full local width for negative zero and positive slide offsets`() {
        val width = 1080f
        val height = 420f
        val offsets = listOf(-1080f, -10.5f, -0.5f, 0f, 0.5f, 10.5f, 1080f)

        offsets.forEach { slideOffsetX ->
            val topLeft = canopyBackdropFillTopLeft(slideOffsetX)
            val fillSize = canopyBackdropFillSize(width, height)
            val localLeft = -slideOffsetX + topLeft.x
            val localRight = localLeft + fillSize.width

            assertTrue(localLeft <= 0f, "Left edge gap for slideOffsetX=$slideOffsetX: localLeft=$localLeft")
            assertTrue(localRight >= width, "Right edge gap for slideOffsetX=$slideOffsetX: localRight=$localRight")
            assertEquals(0f, topLeft.y)
            assertEquals(height, fillSize.height)
        }
    }

    @Test
    fun `canopy transition threshold stays sub-pixel relative to IntOffset slide settling`() {
        assertTrue(CanopyTransitionCoordinator.CANOPY_TRANSITION_THRESHOLD < 1f / 1080f)
    }

    @Test
    fun `touching bottom tab opens destination at top while system back restores prior scroll position`() {
        val controller = OrbitBackStackController(OrbitNavKey.Dashboard)
        val initialDashboardId = controller.currentEntry.id
        controller.scrollStateFor(initialDashboardId).apply {
            listSnapshots["default"] = 6 to 140
            savedHeroOffsetPx = 154f
        }

        // Navigate to Transactions via bottom tab -> fresh entry starting at (0, 0)
        controller.navigate(OrbitNavKey.Transactions(), fromTab = true)
        val txId = controller.currentEntry.id
        assertNotEquals(initialDashboardId, txId)
        assertNull(controller.scrollStateFor(txId).listSnapshots["default"])
        assertEquals(0f, controller.scrollStateFor(txId).savedHeroOffsetPx)

        // Scroll down Transactions, then tap Dashboard bottom tab -> fresh top Dashboard entry
        controller.scrollStateFor(txId).listSnapshots["default"] = 12 to 48
        controller.navigate(OrbitNavKey.Dashboard, fromTab = true)
        val freshDashboardId = controller.currentEntry.id
        assertNotEquals(initialDashboardId, freshDashboardId)
        assertEquals(OrbitNavKey.Dashboard, controller.currentKey)
        assertNull(controller.scrollStateFor(freshDashboardId).listSnapshots["default"])
        assertEquals(0f, controller.scrollStateFor(freshDashboardId).savedHeroOffsetPx)

        // Back once -> Transactions at saved position (12, 48)
        assertTrue(controller.popBack())
        assertEquals(txId, controller.currentEntry.id)
        assertEquals(12 to 48, controller.scrollStateFor(txId).listSnapshots["default"])

        // Back again -> original Dashboard at saved position (6, 140) and pruned popped entries
        assertTrue(controller.popBack())
        assertEquals(initialDashboardId, controller.currentEntry.id)
        assertEquals(6 to 140, controller.scrollStateFor(initialDashboardId).listSnapshots["default"])
        assertEquals(154f, controller.scrollStateFor(initialDashboardId).savedHeroOffsetPx)
        assertEquals(1, controller.trackedScrollEntryCount())
        assertFalse(controller.popBack())
    }

    @Test
    fun `re-tapping active bottom tab triggers scroll-to-top and deep-link reset clears scroll`() {
        val controller = OrbitBackStackController(OrbitNavKey.Dashboard)
        val dashScroll = controller.scrollStateFor(controller.currentEntry.id)
        dashScroll.listSnapshots["default"] = 4 to 80

        // Re-tapping Dashboard from bottom bar triggers smooth scroll-to-top without pushing
        controller.navigate(OrbitNavKey.Dashboard, fromTab = true)
        assertEquals(1, controller.entries.size)
        assertEquals(1, dashScroll.scrollToTopRequestId)

        // In-screen card navigation to filtered Transactions pushes a new entry
        controller.navigate(OrbitNavKey.Transactions(categoryId = 7L), fromTab = false)
        assertEquals(2, controller.entries.size)
        val filteredTxEntryId = controller.currentEntry.id
        controller.scrollStateFor(filteredTxEntryId).listSnapshots["default"] = 9 to 32

        // Tapping Transactions bottom tab clears filters on the same tab AND resets scroll to top
        controller.navigate(OrbitNavKey.Transactions(), fromTab = true)
        assertEquals(2, controller.entries.size)
        assertEquals(OrbitNavKey.Transactions(), controller.currentKey)
        val clearedTxScroll = controller.scrollStateFor(filteredTxEntryId)
        assertNull(clearedTxScroll.listSnapshots["default"])
        assertEquals(1, clearedTxScroll.scrollToTopRequestId)

        // System Back still returns to original Dashboard scroll position
        assertTrue(controller.popBack())
        assertEquals(4 to 80, controller.scrollStateFor(controller.currentEntry.id).listSnapshots["default"])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `popping back to unfiltered transactions restores prior state without leaking deep-link filters`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val storage = PlatformStorage()
        storage.clear()
        try {
            val repo = OrbitRepository(storage)
            repo.initialize()
            val controller = OrbitBackStackController(OrbitNavKey.Dashboard)
            val viewModels = OrbitViewModelHolder(repo)
            val coordinator = OrbitNavigationCoordinator(controller, viewModels)

            // 1. Open Transactions tab and type a manual search
            coordinator.navigate(OrbitNavKey.Transactions(), fromTab = true)
            viewModels.transactionsViewModel.setSearchQuery("Coffee")
            viewModels.transactionsViewModel.setSearchActive(true)

            // 2. Switch to Dashboard tab, then open a category deep link from Dashboard
            coordinator.navigate(OrbitNavKey.Dashboard, fromTab = true)
            coordinator.navigate(OrbitNavKey.Transactions(categoryId = 7L), fromTab = false)
            val deepLinkSnap = viewModels.transactionsViewModel.captureNavSnapshot()
            assertEquals(7L, deepLinkSnap.filterDraft.categoryId)
            assertEquals("", deepLinkSnap.searchQuery)
            assertFalse(deepLinkSnap.isSearchActive)

            // 3. System Back -> Dashboard -> earlier Transactions restores "Coffee" without categoryId=7L
            assertTrue(coordinator.popBack())
            assertEquals(OrbitNavKey.Dashboard, controller.currentKey)
            assertTrue(coordinator.popBack())
            assertEquals(OrbitNavKey.Transactions(), controller.currentKey)

            val restoredSnap = viewModels.transactionsViewModel.captureNavSnapshot()
            assertEquals("Coffee", restoredSnap.searchQuery)
            assertTrue(restoredSnap.isSearchActive)
            assertEquals(TransactionFilterDraft(), restoredSnap.filterDraft)
            assertEquals(1, controller.trackedViewStateEntryCount())

            // 4. Popping back past Transactions prunes its view state snapshot cleanly
            assertTrue(coordinator.popBack())
            assertEquals(OrbitNavKey.Dashboard, controller.currentKey)
            assertEquals(0, controller.trackedViewStateEntryCount())
        } finally {
            Dispatchers.resetMain()
            storage.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `navigating to accounts root and popping back restores prior account selection state`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val storage = PlatformStorage()
        storage.clear()
        try {
            val repo = OrbitRepository(storage)
            repo.initialize()
            val controller = OrbitBackStackController(OrbitNavKey.Accounts())
            val viewModels = OrbitViewModelHolder(repo)
            val coordinator = OrbitNavigationCoordinator(controller, viewModels)

            assertNull(viewModels.accountsViewModel.currentSelectedAccountId)

            // Navigate to Dashboard then open Account #5 deep link
            coordinator.navigate(OrbitNavKey.Dashboard, fromTab = true)
            coordinator.navigate(OrbitNavKey.Accounts(accountId = 5L), fromTab = false)
            assertEquals(5L, viewModels.accountsViewModel.currentSelectedAccountId)

            // Back -> Dashboard -> original Accounts() clears stale selectedAccountId
            assertTrue(coordinator.popBack())
            assertTrue(coordinator.popBack())
            assertNull(viewModels.accountsViewModel.currentSelectedAccountId)

            // Re-open deep link from Dashboard, switch to Dashboard, then tap Accounts bottom tab -> clears inspector
            coordinator.navigate(OrbitNavKey.Dashboard, fromTab = true)
            coordinator.navigate(OrbitNavKey.Accounts(accountId = 5L), fromTab = false)
            assertEquals(5L, viewModels.accountsViewModel.currentSelectedAccountId)
            coordinator.navigate(OrbitNavKey.Dashboard, fromTab = true)
            coordinator.navigate(OrbitNavKey.Accounts(), fromTab = true)
            assertNull(viewModels.accountsViewModel.currentSelectedAccountId)
        } finally {
            Dispatchers.resetMain()
            storage.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `leaving accounts or card perks dismisses pending perk undo event across navigation`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val storage = PlatformStorage()
        storage.clear()
        try {
            val repo = OrbitRepository(storage)
            repo.initialize()
            val savedCard = repo.saveAccount(
                Account(
                    id = 88L,
                    name = "Gold Card",
                    type = AccountType.CREDIT,
                    creditCardProfile = CreditCardProfile(
                        accountId = 88L,
                        benefits = listOf(
                            CardBenefit(
                                id = "dining",
                                title = "Dining Credit",
                                periodValueCents = 10_00L,
                                cadence = BenefitCadence.MONTHLY
                            )
                        )
                    )
                )
            )
            val controller = OrbitBackStackController(OrbitNavKey.Accounts())
            val viewModels = OrbitViewModelHolder(repo)
            val coordinator = OrbitNavigationCoordinator(controller, viewModels)

            viewModels.accountsViewModel.toggleCardBenefit(savedCard.id, "dining", PerkToggleOrigin.ACCOUNTS)
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModels.accountsViewModel.currentPerkUndoEvent)

            // Navigating from Accounts -> CardPerks dismisses pending undo before entering CardPerks
            coordinator.navigate(OrbitNavKey.CardPerks(), fromTab = false)
            assertNull(viewModels.accountsViewModel.currentPerkUndoEvent)

            // Toggling in CardPerks then popping back dismisses pending undo too
            viewModels.accountsViewModel.toggleCardBenefit(savedCard.id, "dining", PerkToggleOrigin.CARD_PERKS)
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModels.accountsViewModel.currentPerkUndoEvent)

            assertTrue(coordinator.popBack())
            assertNull(viewModels.accountsViewModel.currentPerkUndoEvent)
        } finally {
            Dispatchers.resetMain()
            storage.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `navigating to another settings section while in settings updates top frame without duplicating stack`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val storage = PlatformStorage()
        storage.clear()
        try {
            val repo = OrbitRepository(storage)
            repo.initialize()
            val controller = OrbitBackStackController(OrbitNavKey.Dashboard)
            val viewModels = OrbitViewModelHolder(repo)
            val coordinator = OrbitNavigationCoordinator(controller, viewModels)

            coordinator.navigate(OrbitNavKey.Settings(), fromTab = false)
            assertEquals(2, controller.entries.size)
            assertNull(viewModels.settingsViewModel.uiState.value.activeSection)

            // Simulate clicking "Manage Sync" banner while already on Settings
            coordinator.navigate(OrbitNavKey.Settings(app.overbay.orbit.ui.settings.SettingsSection.BANK_FEEDS), fromTab = false)
            assertEquals(2, controller.entries.size)
            assertEquals(
                app.overbay.orbit.ui.settings.SettingsSection.BANK_FEEDS,
                viewModels.settingsViewModel.uiState.value.activeSection
            )

            // Navigating away and back restores the active Settings section
            coordinator.navigate(OrbitNavKey.Budgets, fromTab = true)
            assertEquals(3, controller.entries.size)
            viewModels.settingsViewModel.openSection(null)
            assertTrue(coordinator.popBack())
            assertEquals(
                app.overbay.orbit.ui.settings.SettingsSection.BANK_FEEDS,
                viewModels.settingsViewModel.uiState.value.activeSection
            )

            // One more pop returns directly to Dashboard
            assertTrue(coordinator.popBack())
            assertEquals(OrbitNavKey.Dashboard, controller.currentKey)
        } finally {
            Dispatchers.resetMain()
            storage.clear()
        }
    }
}
