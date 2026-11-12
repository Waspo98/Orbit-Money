package app.overbay.orbit.ui.transactions.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionQuickAction
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.util.LocalOrbitHaptics

fun transactionQuickActionIcon(
    action: TransactionQuickAction,
    isActiveToggle: Boolean = false
): ImageVector {
    if (isActiveToggle) return Icons.Default.Check
    return when (action) {
        TransactionQuickAction.EDIT -> Icons.Default.Edit
        TransactionQuickAction.SPLIT -> Icons.Default.Share
        TransactionQuickAction.CREATE_RULE -> OrbitIcons.Rules
        TransactionQuickAction.IGNORE -> Icons.Default.Clear
        TransactionQuickAction.RECURRING -> OrbitIcons.Upcoming
        TransactionQuickAction.TRANSFER -> Icons.Default.Refresh
        TransactionQuickAction.MHA -> OrbitIcons.MhaTracker
        TransactionQuickAction.DELETE -> Icons.Default.Delete
    }
}

/**
 * Horizontally scrollable pinned quick-action button strip paired with an anchored
 * right-aligned '...' overflow menu containing remaining actions and 'Customize Buttons…'.
 */
@Composable
fun TransactionQuickActionStrip(
    transaction: Transaction,
    pinnedIds: List<String>,
    orderIds: List<String>,
    mhaEnabled: Boolean,
    onEdit: () -> Unit,
    onSplit: () -> Unit,
    onCreateRule: () -> Unit,
    onMarkRecurring: () -> Unit,
    onToggleTransfer: () -> Unit,
    onToggleMha: () -> Unit,
    onToggleIgnore: () -> Unit,
    onDelete: () -> Unit,
    onCustomizeActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val (pinnedActions, overflowActions) = remember(pinnedIds, orderIds, mhaEnabled) {
        TransactionQuickAction.resolveActions(pinnedIds, orderIds, mhaEnabled)
    }

    fun isToggleActive(action: TransactionQuickAction): Boolean = when (action) {
        TransactionQuickAction.IGNORE -> transaction.effectiveIsIgnored
        TransactionQuickAction.TRANSFER -> transaction.effectiveIsTransfer
        TransactionQuickAction.MHA -> transaction.effectiveMhaEligible == true
        else -> false
    }

    fun chipLabelFor(action: TransactionQuickAction): String = when (action) {
        TransactionQuickAction.IGNORE -> if (transaction.effectiveIsIgnored) "Ignored" else "Ignore"
        else -> action.label
    }

    fun menuLabelFor(action: TransactionQuickAction): String = when (action) {
        TransactionQuickAction.RECURRING -> "Mark as Recurring"
        TransactionQuickAction.TRANSFER -> if (transaction.effectiveIsTransfer) "Unmark Transfer" else "Mark as Transfer"
        TransactionQuickAction.MHA -> if (transaction.effectiveMhaEligible == true) "MHA Eligible" else "Mark as MHA"
        TransactionQuickAction.IGNORE -> if (transaction.effectiveIsIgnored) "Unignore" else "Ignore"
        else -> action.label
    }

    fun invokeAction(action: TransactionQuickAction) {
        when (action) {
            TransactionQuickAction.EDIT -> onEdit()
            TransactionQuickAction.SPLIT -> onSplit()
            TransactionQuickAction.CREATE_RULE -> onCreateRule()
            TransactionQuickAction.IGNORE -> onToggleIgnore()
            TransactionQuickAction.RECURRING -> onMarkRecurring()
            TransactionQuickAction.TRANSFER -> onToggleTransfer()
            TransactionQuickAction.MHA -> onToggleMha()
            TransactionQuickAction.DELETE -> onDelete()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pinnedActions.isEmpty()) {
                Text(
                    text = "Tap ••• for actions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pinnedActions.forEach { action ->
                    val active = isToggleActive(action)
                    val destructive = action == TransactionQuickAction.DELETE
                    OrbitActionChip(
                        label = chipLabelFor(action),
                        leadingIcon = transactionQuickActionIcon(action, isActiveToggle = active),
                        isDestructive = destructive,
                        containerColor = when {
                            destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            active -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = when {
                            destructive -> MaterialTheme.colorScheme.error
                            active -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        onClick = { invokeAction(action) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
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
                        contentDescription = "More Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                overflowActions.forEach { action ->
                    val active = isToggleActive(action)
                    val destructive = action == TransactionQuickAction.DELETE
                    val tint = when {
                        destructive -> colors.danger
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = menuLabelFor(action),
                                color = tint,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = transactionQuickActionIcon(action),
                                contentDescription = null,
                                tint = if (destructive) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (active) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            haptics.tick()
                            menuExpanded = false
                            invokeAction(action)
                        }
                    )
                }

                if (overflowActions.isNotEmpty()) {
                    HorizontalDivider(color = OrbitTheme.colors.cardBorder)
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Customize Buttons…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = OrbitIcons.ViewSliders,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        haptics.tick()
                        menuExpanded = false
                        onCustomizeActions()
                    }
                )
            }
        }
    }
}
