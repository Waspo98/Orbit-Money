package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitDropdownMenu
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.ui.dashboard.DashboardRecentTransactionItem
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Single-row inline drawer for [RecentTransactionsCard]: a category selector pill
 * (opening the searchable picker sheet), an Edit button, and a 3-dot overflow menu.
 */
@Composable
internal fun RecentTransactionLeanDrawer(
    item: DashboardRecentTransactionItem,
    categoryLabel: String,
    categoryColor: Color,
    mhaEnabled: Boolean,
    onOpenCategoryPicker: () -> Unit,
    onEdit: () -> Unit,
    onRequestMarkRecurring: () -> Unit,
    onViewDetails: () -> Unit,
    onToggleTransfer: () -> Unit,
    onToggleIgnore: () -> Unit,
    onToggleMha: () -> Unit,
    onRequestResetEdits: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerCategorySelector(
                categoryLabel = categoryLabel,
                categoryColor = categoryColor,
                onClick = onOpenCategoryPicker,
                modifier = Modifier.weight(1f)
            )

            DrawerActionButton(
                label = "Edit",
                icon = Icons.Default.Edit,
                onClick = onEdit
            )

            Box {
                val moreInteraction = remember { MutableInteractionSource() }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .orbitTactilePress(interactionSource = moreInteraction)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = moreInteraction,
                            indication = LocalIndication.current
                        ) {
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

                OrbitDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DrawerOverflowItem(
                        label = "View Details",
                        icon = Icons.Default.Info,
                        onClick = {
                            menuExpanded = false
                            onViewDetails()
                        }
                    )
                    DrawerOverflowItem(
                        label = "Mark as Recurring",
                        icon = OrbitIcons.Upcoming,
                        onClick = {
                            menuExpanded = false
                            onRequestMarkRecurring()
                        }
                    )
                    DrawerOverflowItem(
                        label = if (item.isTransfer) "Unmark Transfer" else "Mark as Transfer",
                        icon = if (item.isTransfer) Icons.Default.Check else Icons.Default.Refresh,
                        isActive = item.isTransfer,
                        onClick = {
                            menuExpanded = false
                            onToggleTransfer()
                        }
                    )
                    DrawerOverflowItem(
                        label = if (item.isIgnored) "Unignore" else "Ignore",
                        icon = Icons.Default.Clear,
                        onClick = {
                            menuExpanded = false
                            onToggleIgnore()
                        }
                    )
                    if (mhaEnabled) {
                        DrawerOverflowItem(
                            label = if (item.isMhaEligible) "Remove MHA" else "Mark as MHA",
                            icon = if (item.isMhaEligible) Icons.Default.Check else OrbitIcons.MhaTracker,
                            isActive = item.isMhaEligible,
                            onClick = {
                                menuExpanded = false
                                onToggleMha()
                            }
                        )
                    }
                    if (item.hasEdits) {
                        DrawerOverflowItem(
                            label = "Reset Edits",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                menuExpanded = false
                                onRequestResetEdits()
                            }
                        )
                    }
                    HorizontalDivider(color = colors.cardBorder)
                    DrawerOverflowItem(
                        label = "Delete",
                        icon = Icons.Default.Delete,
                        isDestructive = true,
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerCategorySelector(
    categoryLabel: String,
    categoryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = categoryColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.45f)),
        modifier = modifier
            .height(48.dp)
            .orbitTactilePress(interactionSource = interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                haptics.tick()
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = categoryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Change Category",
                tint = categoryColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DrawerActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier
            .height(48.dp)
            .orbitTactilePress(interactionSource = interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                haptics.tick()
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DrawerOverflowItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isDestructive: Boolean = false
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val textTint = when {
        isDestructive -> colors.danger
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconTint = when {
        isDestructive -> colors.danger
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = textTint,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = {
            haptics.tick()
            onClick()
        }
    )
}
