package app.overbay.orbit

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import kotlinx.serialization.Serializable

enum class OrbitTransitionDirection {
    /** Viewfinder moves right (e.g. Dashboard -> Transactions): new page enters from right (+X), old exits left (-X). */
    VIEWFINDER_RIGHT,
    /** Viewfinder moves left (e.g. Transactions -> Dashboard): new page enters from left (-X), old exits right (+X). */
    VIEWFINDER_LEFT,
    /** Entering or switching to a secondary ('More') destination: subtle vertical push + fade. */
    MORE_PUSH,
    /** Returning from a secondary ('More') destination to a primary tab: subtle vertical pop + fade. */
    MORE_POP,
    /** Same tab or fallback crossfade. */
    CROSSFADE
}

fun resolveOrbitTransitionDirection(
    initialState: OrbitNavKey,
    targetState: OrbitNavKey
): OrbitTransitionDirection {
    val fromTab = initialState.primaryTabIndex
    val toTab = targetState.primaryTabIndex
    return when {
        fromTab != null && toTab != null -> when {
            toTab > fromTab -> OrbitTransitionDirection.VIEWFINDER_RIGHT
            toTab < fromTab -> OrbitTransitionDirection.VIEWFINDER_LEFT
            else -> OrbitTransitionDirection.CROSSFADE
        }
        toTab == null && fromTab != null -> OrbitTransitionDirection.MORE_PUSH
        toTab != null && fromTab == null -> OrbitTransitionDirection.MORE_POP
        initialState != targetState -> OrbitTransitionDirection.MORE_PUSH
        else -> OrbitTransitionDirection.CROSSFADE
    }
}

fun AnimatedContentTransitionScope<OrbitNavKey>.orbitNavigationTransitionSpec(): ContentTransform {
    val direction = resolveOrbitTransitionDirection(initialState, targetState)
    val snappySlideSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 650f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
    val fadeSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    return when (direction) {
        OrbitTransitionDirection.VIEWFINDER_RIGHT -> {
            slideInHorizontally(
                animationSpec = snappySlideSpring,
                initialOffsetX = { fullWidth -> fullWidth }
            ) togetherWith slideOutHorizontally(
                animationSpec = snappySlideSpring,
                targetOffsetX = { fullWidth -> -fullWidth }
            )
        }
        OrbitTransitionDirection.VIEWFINDER_LEFT -> {
            slideInHorizontally(
                animationSpec = snappySlideSpring,
                initialOffsetX = { fullWidth -> -fullWidth }
            ) togetherWith slideOutHorizontally(
                animationSpec = snappySlideSpring,
                targetOffsetX = { fullWidth -> fullWidth }
            )
        }
        OrbitTransitionDirection.MORE_PUSH -> {
            (fadeIn(animationSpec = fadeSpring) + slideInVertically(
                animationSpec = snappySlideSpring,
                initialOffsetY = { fullHeight -> fullHeight / 12 }
            )) togetherWith fadeOut(animationSpec = fadeSpring)
        }
        OrbitTransitionDirection.MORE_POP -> {
            fadeIn(animationSpec = fadeSpring) togetherWith (
                fadeOut(animationSpec = fadeSpring) + slideOutVertically(
                    animationSpec = snappySlideSpring,
                    targetOffsetY = { fullHeight -> fullHeight / 12 }
                )
            )
        }
        OrbitTransitionDirection.CROSSFADE -> {
            fadeIn(animationSpec = fadeSpring) togetherWith fadeOut(animationSpec = fadeSpring)
        }
    }
}

@Serializable
sealed interface OrbitNavKey {
    @Serializable
    data object Login : OrbitNavKey

    @Serializable
    data object Onboarding : OrbitNavKey

    @Serializable
    data object Dashboard : OrbitNavKey

    @Serializable
    data class Transactions(
        val transactionId: Long? = null,
        val accountId: Long? = null,
        val uncategorizedOnly: Boolean = false,
        val categoryId: Long? = null,
        val startDate: String? = null,
        val endDate: String? = null,
        val sortOption: String? = null,
        val openAddSheet: Boolean = false,
        val includeTransfers: Boolean = true,
        val includeIgnored: Boolean = true
    ) : OrbitNavKey

    @Serializable
    data object Budgets : OrbitNavKey

    @Serializable
    data class Accounts(val accountId: Long? = null) : OrbitNavKey

    @Serializable
    data object Upcoming : OrbitNavKey

    @Serializable
    data object Goals : OrbitNavKey

    @Serializable
    data object Categories : OrbitNavKey

    @Serializable
    data object Rules : OrbitNavKey

    @Serializable
    data object SpendingTrends : OrbitNavKey

    @Serializable
    data object NetWorth : OrbitNavKey

    @Serializable
    data object MhaTracker : OrbitNavKey

    @Serializable
    data object HousingCalculator : OrbitNavKey

    @Serializable
    data object RetirementCalculator : OrbitNavKey

    @Serializable
    data object Household : OrbitNavKey

    @Serializable
    data object Settings : OrbitNavKey

    /**
     * Left-to-right spatial index (0..3) for the 4 primary navigation tabs,
     * or null for secondary ("More") and auth/onboarding destinations.
     */
    val primaryTabIndex: Int?
        get() = when (this) {
            Dashboard -> 0
            is Transactions -> 1
            Budgets -> 2
            is Accounts -> 3
            else -> null
        }

    val title: String
        get() = when (this) {
            Login -> "Sign In"
            Onboarding -> "Welcome"
            Dashboard -> "Dashboard"
            is Transactions -> "Transactions"
            Budgets -> "Budgets"
            is Accounts -> "Accounts"
            Upcoming -> "Upcoming"
            Goals -> "Goals"
            Categories -> "Categories"
            Rules -> "Rules"
            SpendingTrends -> "Spending Trends"
            NetWorth -> "Net Worth"
            MhaTracker -> "MHA Tracker"
            HousingCalculator -> "Housing Calculator"
            RetirementCalculator -> "Retirement Calculator"
            Household -> "Household"
            Settings -> "Settings"
        }

    val routeName: String
        get() = when (this) {
            Login -> "login"
            Onboarding -> "onboarding"
            Dashboard -> "dashboard"
            is Transactions -> "transactions"
            Budgets -> "budgets"
            is Accounts -> "accounts"
            Upcoming -> "upcoming"
            Goals -> "goals"
            Categories -> "categories"
            Rules -> "rules"
            SpendingTrends -> "spending_trends"
            NetWorth -> "net_worth"
            MhaTracker -> "mha"
            HousingCalculator -> "housing_calculator"
            RetirementCalculator -> "retirement_calculator"
            Household -> "household"
            Settings -> "settings"
        }
}
