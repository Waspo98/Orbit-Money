package app.overbay.orbit

import kotlinx.serialization.Serializable

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
        val categoryId: Long? = null
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
}
