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
        title = "Look and Feel",
        shortTitle = "Look & Feel",
        subtitle = "Theme mode, AMOLED dark, color palettes & haptics",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("look", "feel", "theme", "dark", "light", "system", "amoled", "black", "color", "palette", "emerald", "slate", "terracotta", "indigo", "monochrome", "wallpaper", "dynamic", "material you", "haptics", "vibration", "touch", "feedback")
    ),
    NOTIFICATIONS(
        title = "Notifications",
        shortTitle = "Notifications",
        subtitle = "Weekly summaries, paycheck alerts, sync warnings & balance reminders",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("notifications", "push", "alerts", "reminders", "weekly", "summary", "paycheck", "income", "amounts", "schedule", "test", "snapshot")
    ),
    WORKSPACE_NAVIGATION(
        title = "Turn On/Off App Features",
        shortTitle = "App Features",
        subtitle = "More menu destinations, MHA tracker & transaction quick actions",
        cluster = SettingsCluster.PERSONALIZE,
        searchKeywords = listOf("features", "navigation", "more", "menu", "reorder", "hide", "quick actions", "buttons", "mha", "ministerial", "housing", "clergy", "credit card", "goals", "upcoming", "household")
    ),
    SYNC_AND_SHARING(
        title = "Storage, Sync & Sharing",
        shortTitle = "Sync & Sharing",
        subtitle = "App lock, local or self-hosted server, SSO login & partner sharing",
        cluster = SettingsCluster.CONNECTIONS_DATA,
        searchKeywords = listOf("security", "biometric", "fingerprint", "face", "lock", "app lock", "timeout", "storage", "local", "offline", "self-hosted", "server", "url", "sync", "sso", "oidc", "login", "password", "sign out", "migrate", "partner", "sharing", "household", "invite", "permissions", "read only")
    ),
    BANK_FEEDS(
        title = "SimpleFIN Sync",
        shortTitle = "SimpleFIN Sync",
        subtitle = "Bank feed bridge connection, sync cutover, logs & muted warnings",
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
    );

    companion object {
        fun search(query: String): List<SettingsSection> {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return entries
            return entries.filter { section ->
                section.title.contains(trimmed, ignoreCase = true) ||
                    section.subtitle.contains(trimmed, ignoreCase = true) ||
                    section.searchKeywords.any { it.contains(trimmed, ignoreCase = true) }
            }
        }
    }
}
