package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.muteSimpleFinAccountWarnings
import app.overbay.orbit.domain.SimpleFinWarningParser
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.launch

/**
 * Global status banners (with safe status-bar padding) for offline / read-only sessions,
 * plus a popup dialog for SimpleFIN bank sync issues with per-account permanent muting.
 */
@Composable
fun GlobalStatusBanners(
    repository: OrbitRepository,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOffline by repository.isOffline.collectAsStateWithLifecycle()
    val preferences by repository.preferences.collectAsStateWithLifecycle()
    val simpleFinStatus by repository.simpleFinStatus.collectAsStateWithLifecycle()
    val accounts by repository.accounts.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalOrbitHaptics.current

    var dismissedOffline by remember { mutableStateOf(false) }
    var dismissedReadOnly by remember { mutableStateOf(false) }
    var dismissedSimpleFin by remember(simpleFinStatus.lastSyncError) { mutableStateOf(false) }

    val showOfflineBanner = isOffline && preferences.storageMode == StorageMode.SELF_HOSTED && !dismissedOffline
    val showReadOnlyBanner = preferences.isReadOnlySession && !dismissedReadOnly

    val unmutedSimpleFinIssues = remember(
        simpleFinStatus.lastSyncError,
        accounts,
        preferences.mutedSimpleFinAccounts
    ) {
        SimpleFinWarningParser.parseUnmutedIssues(
            rawError = simpleFinStatus.lastSyncError,
            knownAccounts = accounts,
            mutedAccounts = preferences.mutedSimpleFinAccounts
        )
    }

    val showSimpleFinDialog = unmutedSimpleFinIssues.isNotEmpty() && !dismissedSimpleFin

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Offline Mode in Self-Hosted Environment
        AnimatedVisibility(
            visible = showOfflineBanner,
            enter = expandVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeIn(),
            exit = shrinkVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeOut()
        ) {
            val note = if (preferences.isSharedNonOwnerSession) " (Read-only offline session)" else ""
            BannerCard(
                icon = Icons.Default.Warning,
                iconTint = OrbitTheme.colors.warning,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = "Offline Mode",
                subtitle = "Showing cached workspace snapshot$note",
                applyTopStatusBarPadding = true,
                onDismiss = { dismissedOffline = true }
            )
        }

        // 2. Read-Only Partner Session
        AnimatedVisibility(
            visible = showReadOnlyBanner,
            enter = expandVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeIn(),
            exit = shrinkVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeOut()
        ) {
            BannerCard(
                icon = Icons.Default.Info,
                iconTint = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                title = "View-Only Household Access",
                subtitle = "You have view-only access to this shared household workspace.",
                applyTopStatusBarPadding = !showOfflineBanner,
                onDismiss = { dismissedReadOnly = true }
            )
        }
    }

    // 3. SimpleFIN Bank Sync Issue Popup Dialog (replaces top banner)
    if (showSimpleFinDialog) {
        var doNotWarnAgain by remember(unmutedSimpleFinIssues) { mutableStateOf(false) }
        val distinctAccountLabels = remember(unmutedSimpleFinIssues) {
            unmutedSimpleFinIssues.map { it.accountLabel }.distinct()
        }

        val commitMuteIfChecked: () -> Unit = {
            if (doNotWarnAgain && distinctAccountLabels.isNotEmpty()) {
                coroutineScope.launch {
                    repository.muteSimpleFinAccountWarnings(distinctAccountLabels)
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                commitMuteIfChecked()
                dismissedSimpleFin = true
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Bank Sync Issue",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Bank Sync Issue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unmutedSimpleFinIssues.forEach { issue ->
                                Text(
                                    text = issue.rawMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptics.tick()
                                doNotWarnAgain = !doNotWarnAgain
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = if (distinctAccountLabels.size > 1) {
                                        "Do not warn me about these accounts again"
                                    } else {
                                        "Do not warn me about this account again"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = distinctAccountLabels.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = doNotWarnAgain,
                                onCheckedChange = { checked ->
                                    haptics.tick()
                                    doNotWarnAgain = checked
                                }
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptics.tick()
                        commitMuteIfChecked()
                        dismissedSimpleFin = true
                        repository.requestOpenSettingsSubpage("data")
                        onNavigate(OrbitNavKey.Settings)
                    }
                ) {
                    Text("Manage Sync")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.confirm()
                        commitMuteIfChecked()
                        dismissedSimpleFin = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Dismiss")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun BannerCard(
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    title: String,
    subtitle: String,
    applyTopStatusBarPadding: Boolean = false,
    onDismiss: () -> Unit
) {
    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyTopStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
