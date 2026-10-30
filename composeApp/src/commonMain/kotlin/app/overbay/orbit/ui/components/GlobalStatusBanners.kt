package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme

/**
 * Sticky global status notification canopy banners for network offline states,
 * read-only partner access, and SimpleFIN bank sync issues.
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

    var dismissedOffline by remember { mutableStateOf(false) }
    var dismissedReadOnly by remember { mutableStateOf(false) }
    var dismissedSimpleFin by remember(simpleFinStatus.lastSyncError) { mutableStateOf(false) }

    val showOfflineBanner = isOffline && preferences.storageMode == StorageMode.SELF_HOSTED && !dismissedOffline
    val showReadOnlyBanner = preferences.isReadOnlySession && !dismissedReadOnly
    val simpleFinError = simpleFinStatus.lastSyncError
    val showSimpleFinBanner = !simpleFinError.isNullOrBlank() && !dismissedSimpleFin

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
                onDismiss = { dismissedReadOnly = true }
            )
        }

        // 3. SimpleFIN Bank Sync Issue
        AnimatedVisibility(
            visible = showSimpleFinBanner,
            enter = expandVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeIn(),
            exit = shrinkVertically(animationSpec = OrbitSprings.spatialDefault()) + fadeOut()
        ) {
            BannerCard(
                icon = Icons.Default.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                title = "Bank Sync Issue",
                subtitle = simpleFinError ?: "Sync error encountered",
                actionLabel = "Manage",
                onAction = {
                    repository.requestOpenSettingsSubpage("data")
                    onNavigate(OrbitNavKey.Settings)
                },
                onDismiss = { dismissedSimpleFin = true }
            )
        }
    }
}

@Composable
private fun BannerCard(
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelSmall)
                }
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
