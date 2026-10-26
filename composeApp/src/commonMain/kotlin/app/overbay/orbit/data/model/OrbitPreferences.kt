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
    val sortOrder: Int = 0
)

@Serializable
data class OrbitPreferences(
    val storageMode: StorageMode = StorageMode.LOCAL,
    val selfHostedServerUrl: String = "",
    val themePreset: OrbitThemePreset = OrbitThemePreset.ORBIT_EMERALD,
    val themeMode: OrbitThemeMode = OrbitThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
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
            DashboardWidgetConfig("cashflow_hero", "Net Cashflow", isVisible = true, sortOrder = 0),
            DashboardWidgetConfig("budget_pulse", "Budget Pulse", isVisible = true, sortOrder = 1),
            DashboardWidgetConfig("upcoming_bills", "Upcoming Bills & Income", isVisible = true, sortOrder = 2),
            DashboardWidgetConfig("recent_transactions", "Recent Transactions", isVisible = true, sortOrder = 3),
            DashboardWidgetConfig("accounts_overview", "Accounts Overview", isVisible = true, sortOrder = 4),
            DashboardWidgetConfig("goals_progress", "Savings Goals", isVisible = true, sortOrder = 5)
        )
    }
}
