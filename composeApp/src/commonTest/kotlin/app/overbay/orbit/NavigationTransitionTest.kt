package app.overbay.orbit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
