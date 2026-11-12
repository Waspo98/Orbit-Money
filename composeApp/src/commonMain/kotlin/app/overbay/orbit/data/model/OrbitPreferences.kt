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
enum class OrbitThemeMode(val displayName: String, val emoji: String) {
    SYSTEM("System Default", "💻"),
    LIGHT("Light", "☀️"),
    DARK("Dark", "🌙");

    val labelWithEmoji: String get() = "$emoji $displayName"
}

@Serializable
enum class TransactionQuickAction(val id: String, val label: String) {
    EDIT("edit", "Edit"),
    SPLIT("split", "Split"),
    CREATE_RULE("create_rule", "Create Rule"),
    IGNORE("ignore", "Ignore"),
    RECURRING("recurring", "Recurring"),
    TRANSFER("transfer", "Transfer"),
    MHA("mha", "MHA"),
    DELETE("delete", "Delete");

    companion object {
        fun fromId(id: String): TransactionQuickAction? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun resolveActions(
            pinnedIds: List<String>,
            orderIds: List<String>,
            mhaEnabled: Boolean
        ): Pair<List<TransactionQuickAction>, List<TransactionQuickAction>> {
            val orderIndex = orderIds.withIndex().associate { it.value.lowercase() to it.index }
            val orderedAll = entries.sortedWith(
                compareBy<TransactionQuickAction> { orderIndex[it.id] ?: (1000 + it.ordinal) }
                    .thenBy { it.ordinal }
            )
            val visible = if (mhaEnabled) orderedAll else orderedAll.filter { it != MHA }
            val pinnedSet = pinnedIds.map { it.lowercase() }.toSet()
            return visible.partition { it.id in pinnedSet }
        }
    }
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
    val cardRewardsToolsEnabled: Boolean? = null,
    val defaultCurrency: String = "USD",
    val defaultAccountId: Long? = null,
    val googleDriveSyncEnabled: Boolean = false,
    val lastGoogleDriveSyncAt: String? = null,
    val simpleFinToken: String? = null,
    val dashboardWidgets: List<DashboardWidgetConfig> = defaultDashboardWidgets(),
    val cardPerksWidgets: List<DashboardWidgetConfig> = defaultCardPerksWidgets(),
    val handledReviewTransactionIds: List<Long> = emptyList(),
    val onboardingCompleted: Boolean = false,
    val amoledDarkMode: Boolean = false,
    val hiddenRouteNames: List<String> = emptyList(),
    val moreRouteOrder: List<String> = emptyList(),
    val budgetSortOption: CategorySortOption = CategorySortOption.OVER_BUDGET_FIRST,
    val dashboardHiddenBiggestIds: List<Long> = emptyList(),
    val dashboardFocusedGoalId: Long? = null,
    val dashboardRetirementAge: Int = 67,
    val dashboardRetirementPreset: String = "BALANCED",
    val googleDriveClientId: String = "",
    val simpleFinCutoverDate: String? = null,
    val accountGroupWideSpans: Map<String, Int> = emptyMap(),
    val dismissedRecurringKeys: List<String> = emptyList(),
    val mutedSimpleFinAccounts: List<String> = emptyList(),
    val dismissedClosurePrompts: Map<String, String> = emptyMap(),
    val accountInstitutionLogoOverrides: Map<String, MerchantLogo> = emptyMap(),
    val includeHsaInRetirementRate: Boolean = true,
    val notificationPreferences: NotificationPreferencesConfig = NotificationPreferencesConfig(),
    val transactionPinnedActions: List<String> = defaultTransactionPinnedActions(),
    val transactionActionOrder: List<String> = defaultTransactionActionOrder(),
    val sharedSessionCachedAtEpochMs: Long? = null,
    val isSharedNonOwnerSession: Boolean = false,
    val isReadOnlySession: Boolean = false
) {
    companion object {
        fun defaultTransactionPinnedActions(): List<String> =
            listOf("edit", "split", "create_rule")

        fun defaultTransactionActionOrder(): List<String> =
            TransactionQuickAction.entries.map { it.id }

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

        fun defaultCardPerksWidgets(): List<DashboardWidgetConfig> = listOf(
            DashboardWidgetConfig("card_wallet_roi", "Card Wallet", isVisible = true, sortOrder = 0, wideSpan = 2),
            DashboardWidgetConfig("perks_this_period", "Perks This Period", isVisible = true, sortOrder = 1, wideSpan = 2),
            DashboardWidgetConfig("which_card_where", "Which Card Where", isVisible = true, sortOrder = 2, wideSpan = 1),
            DashboardWidgetConfig("welcome_bonuses", "Welcome Bonuses", isVisible = true, sortOrder = 3, wideSpan = 1),
            DashboardWidgetConfig("chase_524", "Chase 5/24 Status", isVisible = true, sortOrder = 4, wideSpan = 1),
            DashboardWidgetConfig("annual_fees_ahead", "Annual Fees Ahead", isVisible = true, sortOrder = 5, wideSpan = 1),
            DashboardWidgetConfig("payments_due", "Billing & Payments Due", isVisible = true, sortOrder = 6, wideSpan = 1)
        )
    }
}
