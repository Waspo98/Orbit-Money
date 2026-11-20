package app.overbay.orbit.ui.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.settings.SettingsCluster
import app.overbay.orbit.ui.settings.SettingsSection
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.util.PlatformRuntime

fun LazyListScope.settingsIndexItems(
    uiState: SettingsUiState,
    selectedSection: SettingsSection?,
    onSelectSection: (SettingsSection) -> Unit,
    onSignOutClick: () -> Unit,
    onClearSearch: () -> Unit
) {
    val query = uiState.searchQuery.trim()
    if (query.isNotBlank()) {
        val matches = SettingsSection.search(query)
        if (matches.isEmpty()) {
            item(key = "settings_search_empty") {
                EmptyStateCard(
                    title = "No matching settings",
                    description = "Try searching for theme, backups, notifications, or SimpleFIN.",
                    icon = Icons.Default.Search,
                    actionButtonText = "Clear Search",
                    onActionClick = onClearSearch,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            item(key = "settings_search_header") {
                SettingsClusterHeader(title = "Matching Settings (${matches.size})")
            }
            item(key = "settings_search_results") {
                SettingsGroupCard {
                    matches.forEachIndexed { index, section ->
                        if (index > 0) SettingsInlineDivider()
                        SettingsNavRow(
                            icon = section.icon(),
                            title = section.title,
                            valueSummary = section.subtitle,
                            isSelected = selectedSection == section,
                            onClick = { onSelectSection(section) }
                        )
                    }
                }
            }
        }
        return
    }

    item(key = "settings_workspace_strip") {
        val isSelfHosted = uiState.storageMode == StorageMode.SELF_HOSTED
        val hostLabel = cleanHostLabel(uiState.preferences.selfHostedServerUrl)
        val (badgeText, badgeIsError) = when {
            !isSelfHosted -> "Offline" to false
            uiState.isSyncing -> "Syncing" to false
            uiState.syncStatus is SyncResult.Error -> "Error" to true
            else -> "Synced" to false
        }
        SettingsGroupCard {
            SettingsNavRow(
                icon = OrbitIcons.CloudSync,
                title = if (isSelfHosted) "Self-Hosted Server" else "On This Device",
                valueSummary = if (isSelfHosted) {
                    "Self-Hosted Server · $hostLabel"
                } else {
                    "On This Device · Offline"
                },
                badgeText = badgeText,
                badgeIsError = badgeIsError,
                isSelected = selectedSection == SettingsSection.SYNC_AND_SHARING,
                onClick = { onSelectSection(SettingsSection.SYNC_AND_SHARING) }
            )
        }
    }

    SettingsCluster.entries.forEach { cluster ->
        val sections = SettingsSection.entries.filter { it.cluster == cluster }
        item(key = "cluster_header_${cluster.name}") {
            SettingsClusterHeader(title = cluster.title)
        }
        item(key = "cluster_group_${cluster.name}") {
            SettingsGroupCard {
                sections.forEachIndexed { index, section ->
                    if (index > 0) SettingsInlineDivider()
                    val summary = sectionSummary(section, uiState)
                    val isErrorBadge = section == SettingsSection.SYNC_AND_SHARING &&
                        uiState.storageMode == StorageMode.SELF_HOSTED &&
                        uiState.syncStatus is SyncResult.Error
                    SettingsNavRow(
                        icon = section.icon(),
                        title = section.title,
                        valueSummary = summary,
                        badgeText = if (isErrorBadge) "Error" else null,
                        badgeIsError = isErrorBadge,
                        isSelected = selectedSection == section,
                        onClick = { onSelectSection(section) }
                    )
                }
                if (cluster == SettingsCluster.APP && uiState.storageMode == StorageMode.SELF_HOSTED) {
                    SettingsInlineDivider()
                    SettingsNavRow(
                        icon = OrbitIcons.SignOut,
                        title = "Sign Out",
                        valueSummary = "Disconnect this device from the server",
                        isDestructive = true,
                        onClick = onSignOutClick
                    )
                }
            }
        }
    }
}

private fun sectionSummary(section: SettingsSection, uiState: SettingsUiState): String {
    val prefs = uiState.preferences
    return when (section) {
        SettingsSection.APPEARANCE -> "${prefs.themePreset.displayName} · ${prefs.themeMode.shortLabel}"
        SettingsSection.NOTIFICATIONS -> {
            val notif = prefs.notificationPreferences
            if (!notif.enabled) {
                "Paused"
            } else {
                val activeCount = listOf(
                    notif.weeklySnapshot.enabled,
                    notif.income.enabled,
                    notif.syncIssues.enabled,
                    notif.accountSnapshots.enabled
                ).count { it }
                if (activeCount == 0) "Enabled" else "$activeCount alerts active"
            }
        }
        SettingsSection.WORKSPACE_NAVIGATION -> {
            val hiddenCount = prefs.hiddenRouteNames.size
            if (hiddenCount == 0) "Quick actions & More menu" else "$hiddenCount hidden in More menu"
        }
        SettingsSection.SECURITY -> {
            val hapticsLabel = if (prefs.hapticsEnabled) "Haptics On" else "Haptics Off"
            if (PlatformRuntime.isWeb) {
                hapticsLabel
            } else {
                val lockLabel = if (prefs.biometricLockEnabled) "App Lock On" else "App Lock Off"
                "$lockLabel · $hapticsLabel"
            }
        }
        SettingsSection.SYNC_AND_SHARING -> {
            if (uiState.storageMode == StorageMode.LOCAL) {
                "On This Device · Offline"
            } else {
                val statusLabel = when {
                    uiState.isSyncing -> "Syncing"
                    uiState.syncStatus is SyncResult.Error -> "Error"
                    else -> "Synced"
                }
                "Self-Hosted · $statusLabel"
            }
        }
        SettingsSection.BANK_FEEDS -> {
            if (!prefs.simpleFinToken.isNullOrBlank()) "Connected" else "Not connected"
        }
        SettingsSection.DATA_AND_BACKUPS -> {
            if (prefs.googleDriveSyncEnabled) "Drive sync on · Exports & rules" else "Exports, Drive & rules"
        }
        SettingsSection.ABOUT -> "v${PlatformRuntime.appVersionName}"
    }
}

private fun cleanHostLabel(rawUrl: String): String {
    val cleaned = rawUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    return cleaned.ifBlank { "Same Origin" }
}
