package app.overbay.orbit.ui.categories.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.categories.CategoryGroupDisplay
import app.overbay.orbit.ui.categories.CategorySummary
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.SelectableListRow
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
    showMhaBadge: Boolean = false,
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
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = OrbitSprings.IntSizeSpatialDefault)
        ) {
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

                RollingTickerCents(
                    cents = group.totalCents,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                        showMhaBadge = showMhaBadge,
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
    showMhaBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cat = summary.category
    val catColor = cat.color.toComposeColor(MaterialTheme.colorScheme.primary)
    val subtitle = buildString {
        append("${summary.transactionCount} txns")
        if (summary.ruleCount > 0) {
            append(" • ${summary.ruleCount} rule${if (summary.ruleCount == 1) "" else "s"}")
        }
    }

    SelectableListRow(
        title = cat.name,
        subtitle = subtitle,
        isSelected = isInspected,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragHandle != null) {
                    dragHandle()
                    Spacer(modifier = Modifier.width(8.dp))
                }
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
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showMhaBadge && cat.mhaDefaultEligible) {
                    OrbitBadge(
                        text = "MHA",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (showMhaBadge && cat.mhaDefaultIgnored) {
                    OrbitBadge(
                        text = "MHA Ignored",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = MoneyUtils.formatCents(summary.totalAmountCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
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
    )
}
