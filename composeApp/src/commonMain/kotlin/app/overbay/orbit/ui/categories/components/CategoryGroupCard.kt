package app.overbay.orbit.ui.categories.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.categories.CategoryGroupDisplay
import app.overbay.orbit.ui.categories.CategorySummary
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CategoryGroupCard(
    group: CategoryGroupDisplay,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    inspectingCategoryId: Long? = null,
    onInspectCategory: ((Category) -> Unit)? = null,
    isReorderMode: Boolean = false,
    groupDragHandle: (@Composable () -> Unit)? = null,
    onMoveCategory: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val categoryReorderState = rememberDragReorderState(
        items = group.items,
        keySelector = { it.category.id },
        onMove = onMoveCategory,
        itemSpacingDp = 6.dp
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (groupDragHandle != null) {
                        groupDragHandle()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${group.items.size} categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = MoneyUtils.formatCents(group.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Rows
            group.items.forEachIndexed { index, summary ->
                key(summary.category.id) {
                    CategoryRowItem(
                        summary = summary,
                        isInspected = inspectingCategoryId == summary.category.id,
                        onClick = {
                            haptics.tick()
                            if (onInspectCategory != null) {
                                onInspectCategory(summary.category)
                            } else {
                                onEditCategory(summary.category)
                            }
                        },
                        onEdit = {
                            haptics.tick()
                            onEditCategory(summary.category)
                        },
                        onDelete = {
                            haptics.reject()
                            onDeleteCategory(summary.category.id)
                        },
                        dragHandle = if (isReorderMode) {
                            { OrbitDragHandle(state = categoryReorderState, key = summary.category.id) }
                        } else null,
                        modifier = Modifier.dragReorderItem(
                            state = if (isReorderMode) categoryReorderState else null,
                            key = summary.category.id
                        )
                    )
                    if (index < group.items.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRowItem(
    summary: CategorySummary,
    isInspected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cat = summary.category
    val catColor = try {
        val hex = cat.color.trim().removePrefix("#")
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isInspected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        border = if (isInspected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dragHandle != null) {
                dragHandle()
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Tinted Emoji Badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cat.displayEmoji ?: "#",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = catColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Category Name & Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (cat.mhaDefaultEligible) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "MHA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${summary.transactionCount} txns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary.ruleCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${summary.ruleCount} rule${if (summary.ruleCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Total Spend in this category
            Text(
                text = MoneyUtils.formatCents(summary.totalAmountCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Edit icon
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
