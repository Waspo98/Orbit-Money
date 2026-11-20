package app.overbay.orbit.ui.settings

import kotlinx.serialization.Serializable

enum class SettingsCluster(val title: String) {
    PERSONALIZE("Personalize"),
    CONNECTIONS_DATA("Connections & Data"),
    APP("App")
}

@Serializable
enum class SettingsSection(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val cluster: SettingsCluster,
    val searchKeywords: List<String>
) {
    APPEARANCE(
        title = "Appearance",
        shortTitle = "Appearance",
        subtitle = "Theme mode, AMOLED dark, color palettes & wallpaper colors",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("theme", "dark", "light", "system", "amoled", "black", "color", "palette", "emerald", "slate", "terracotta", "indigo", "monochrome", "wallpaper", "dynamic", "material you")
    ),
    NOTIFICATIONS(
        title = "Notifications",
        shortTitle = "Notifications",
        subtitle = "Weekly summaries, paycheck alerts, sync warnings & balance reminders",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("notifications", "push", "alerts", "reminders", "weekly", "summary", "paycheck", "income", "amounts", "schedule", "test", "snapshot")
    ),
    WORKSPACE_NAVIGATION(
        title = "Workspace & Navigation",
        shortTitle = "Workspace",
        subtitle = "Optional tools, transaction quick actions & More menu order",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("navigation", "more", "menu", "reorder", "hide", "quick actions", "buttons", "mha", "ministerial", "housing", "clergy", "rewards", "credit card", "5/24", "strategy")
    ),
    SECURITY(
        title = "Security & Feedback",
        shortTitle = "Security",
        subtitle = "Biometric app lock, auto-lock timeout & haptics",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("security", "biometric", "fingerprint", "face", "lock", "timeout", "haptics", "vibration", "touch")
    ),
    SYNC_AND_SHARING(
        title = "Storage, Sync & Sharing",
        shortTitle = "Sync & Sharing",
        subtitle = "Local or self-hosted server, SSO login & household partner access",
        cluster = SettingsCluster.CONNECTIONS_DATA,
        searchKeywords = listOf("storage", "local", "offline", "self-hosted", "server", "url", "sync", "sso", "oidc", "login", "password", "sign out", "migrate", "partner", "sharing", "household", "invite", "permissions", "read only")
    ),
    BANK_FEEDS(
        title = "Bank Feeds",
        shortTitle = "Bank Feeds",
        subtitle = "SimpleFIN bridge connection, sync cutover, logs & muted warnings",
        cluster = SettingsCluster.CONNECTIONS_DATA,
        searchKeywords = listOf("simplefin", "bank", "feeds", "bridge", "token", "cutover", "sync", "history", "logs", "muted", "warnings", "accounts")
    ),
    DATA_AND_BACKUPS(
        title = "Backups & Maintenance",
        shortTitle = "Backups",
        subtitle = "Google Drive sync, CSV/JSON exports, workspace backups, rules & reset",
        cluster = SettingsCluster.CONNECTIONS_DATA,
        searchKeywords = listOf("backup", "restore", "export", "csv", "json", "google drive", "cloud", "client id", "rules", "reapply", "sample", "demo", "reset", "wipe", "danger")
    ),
    ABOUT(
        title = "About Orbit Money",
        shortTitle = "About",
        subtitle = "Version info, walkthrough tour, support & account",
        cluster = SettingsCluster.APP,
        searchKeywords = listOf("about", "version", "beta", "developer", "tour", "walkthrough", "onboarding", "support", "donate", "sign out", "logout")
    )
}
