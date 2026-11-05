package app.overbay.orbit

import app.overbay.orbit.ui.components.CanopyTransitionCoordinator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            OrbitNavKey.Settings
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
            resolveOrbitTransitionDirection(OrbitNavKey.Dashboard, OrbitNavKey.Settings)
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
            resolveOrbitTransitionDirection(OrbitNavKey.Settings, OrbitNavKey.Dashboard)
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
}
