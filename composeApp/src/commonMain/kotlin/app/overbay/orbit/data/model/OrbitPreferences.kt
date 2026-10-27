package app.overbay.orbit.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class OrbitThemePreset(val displayName: String) {
    ORBIT_EMERALD("Orbit Emerald"),
    OCEAN_SLATE("Ocean Slate"),
    WARM_TERRACOTTA("Warm Terracotta"),
    ROYAL_INDIGO("Royal Indigo"),
    MONOCHROME_INK("Monochrome Ink")
}

@Serializable
enum class OrbitThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

@Serializable
data class DashboardWidgetConfig(
    val widgetId: String,
    val title: String,
    val isVisible: Boolean = true,
    val sortOrder: Int = 0,
    val wideSpan: Int = 1
)

@Serializable
data class OrbitPreferences(
    val storageMode: StorageMode = StorageMode.LOCAL,
    val selfHostedServerUrl: String = "",
    val themePreset: OrbitThemePreset = OrbitThemePreset.ORBIT_EMERALD,
    val themeMode: OrbitThemeMode = OrbitThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val biometricTimeoutSeconds: Int = 300, // 5 minutes
    val hapticsEnabled: Boolean = true,
    val mhaTrackerEnabled: Boolean = false,
    val defaultCurrency: String = "USD",
    val defaultAccountId: Long? = null,
    val googleDriveSyncEnabled: Boolean = false,
    val lastGoogleDriveSyncAt: String? = null,
    val simpleFinToken: String? = null,
    val dashboardWidgets: List<DashboardWidgetConfig> = defaultDashboardWidgets(),
    val onboardingCompleted: Boolean = false
) {
    companion object {
        fun defaultDashboardWidgets(): List<DashboardWidgetConfig> = listOf(
            DashboardWidgetConfig("accounts", "Accounts", isVisible = true, sortOrder = 0),
            DashboardWidgetConfig("budget_pulse", "Budget Pulse", isVisible = true, sortOrder = 1),
            DashboardWidgetConfig("top_spending", "Top Spending", isVisible = true, sortOrder = 2),
            DashboardWidgetConfig("this_month", "This Month", isVisible = true, sortOrder = 3),
            DashboardWidgetConfig("biggest_transactions", "Biggest Monthly Transactions", isVisible = true, sortOrder = 4),
            DashboardWidgetConfig("subscriptions", "Subscriptions / Recurring", isVisible = true, sortOrder = 5),
            DashboardWidgetConfig("uncategorized", "Uncategorized Transactions", isVisible = true, sortOrder = 6),
            DashboardWidgetConfig("categorize_recent", "Categorize Recent Transactions", isVisible = true, sortOrder = 7),
            DashboardWidgetConfig("month_comparison", "Month vs Last Month", isVisible = true, sortOrder = 8),
            DashboardWidgetConfig("goals_progress", "Goals Progress", isVisible = true, sortOrder = 9),
            DashboardWidgetConfig("goal_focus", "Goal Focus", isVisible = true, sortOrder = 10),
            DashboardWidgetConfig("retirement", "Retirement Snapshot", isVisible = true, sortOrder = 11),
            DashboardWidgetConfig("mha", "MHA Tracker Summary", isVisible = true, sortOrder = 12),
            DashboardWidgetConfig("upcoming_bills", "Upcoming: Bills and Income", isVisible = true, sortOrder = 13),
            DashboardWidgetConfig("mortgage", "Mortgage Snapshot", isVisible = true, sortOrder = 14),
            DashboardWidgetConfig("recent_transactions", "Recent Transactions", isVisible = true, sortOrder = 15, wideSpan = 2)
        )
    }
}
