package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitDropdownMenu
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Sticky top header for Account and Credit Card detail inspectors/modals.
 * Pairs an optional leading brandmark + title/subtitle with 34dp-aligned Edit,
 * spring-animated overflow menu (`•••`), and Close (`✕`) controls.
 */
@Composable
fun AccountInspectorHeader(
    accountName: String,
    title: String,
    editLabel: String,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isArchived: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onAddBalanceRecord: (() -> Unit)? = null,
    onSnapshotReminder: (() -> Unit)? = null,
    onMerge: (() -> Unit)? = null,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    onToggleArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val sheetDismiss = LocalOrbitSheetDismiss.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }

    val hasOverflowMenu = onHistory != null ||
        onAddBalanceRecord != null ||
        onSnapshotReminder != null ||
        onMerge != null ||
        onUnlinkSimpleFin != null ||
        onToggleArchive != null ||
        onDelete != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .height(34.dp)
                    .orbitTactilePress()
                    .clip(CircleShape)
                    .clickable {
                        haptics.tick()
                        onEdit()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = editLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }
            }

            if (hasOverflowMenu) {
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptics.tick()
                                menuExpanded = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = OrbitIcons.MoreHoriz,
                                contentDescription = "More Account Actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    OrbitDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        onHistory?.let { action ->
                            OverflowActionItem("Balance History", OrbitIcons.SpendingTrends) {
                                menuExpanded = false
                                action()
                            }
                        }
                        onAddBalanceRecord?.let { action ->
                            OverflowActionItem("Log Balance", Icons.Default.Add) {
                                menuExpanded = false
                                action()
                            }
                        }
                        onSnapshotReminder?.let { action ->
                            OverflowActionItem("Balance Reminder", Icons.Default.Notifications) {
                                menuExpanded = false
                                sheetDismiss?.invoke(action) ?: action()
                            }
                        }
                        onMerge?.let { action ->
                            OverflowActionItem("Merge Account", OrbitIcons.Split) {
                                menuExpanded = false
                                action()
                            }
                        }
                        if (onUnlinkSimpleFin != null) {
                            OverflowActionItem("Unlink SimpleFIN", Icons.Default.Clear) {
                                menuExpanded = false
                                showUnlinkConfirm = true
                            }
                        }
                        onToggleArchive?.let { action ->
                            OverflowActionItem(
                                label = if (isArchived) "Unarchive Account" else "Archive Account",
                                icon = OrbitIcons.Accounts
                            ) {
                                menuExpanded = false
                                action()
                            }
                        }
                        onDelete?.let { action ->
                            HorizontalDivider(color = colors.cardBorder)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Delete Account",
                                        color = colors.danger,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = colors.danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    haptics.reject()
                                    menuExpanded = false
                                    action()
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        sheetDismiss?.invoke(onClose) ?: onClose()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showUnlinkConfirm && onUnlinkSimpleFin != null) {
        OrbitConfirmationDialog(
            title = "Unlink SimpleFIN?",
            message = "Unlinking \"$accountName\" from SimpleFIN will convert it to a manual account. Existing transactions and balance history will be preserved.",
            confirmText = "Unlink",
            isDestructive = false,
            onConfirm = {
                showUnlinkConfirm = false
                onUnlinkSimpleFin()
            },
            onDismiss = { showUnlinkConfirm = false }
        )
    }
}

@Composable
private fun OverflowActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = {
            haptics.tick()
            onClick()
        }
    )
}
