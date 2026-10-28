package app.overbay.orbit.ui.categories.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.categories.CategoriesUiState
import app.overbay.orbit.ui.categories.CategorySummary
import app.overbay.orbit.ui.categories.dialogs.CategoryEditorContent
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CategoryDetailInspectorPane(
    state: CategoriesUiState,
    onOpenEdit: (Category) -> Unit,
    onSaveCategory: (
        id: Long,
        name: String,
        color: String,
        icon: String?,
        isTransfer: Boolean,
        isIncome: Boolean,
        mhaEligible: Boolean
    ) -> Unit,
    onCloseEdit: () -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onViewTransactions: () -> Unit,
    onDismissPane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = when {
        state.showEditorDialog -> "EDIT"
        state.inspectingSummary != null -> "DETAIL"
        else -> "NONE"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = activeMode,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessLow))
            },
            label = "CategoryInspectorModeTransition"
        ) { mode ->
            when (mode) {
                "EDIT" -> {
                    CategoryEditorContent(
                        category = state.editingCategory,
                        onSave = onSaveCategory,
                        onDelete = { id -> onDeleteCategory(id) },
                        onDismiss = {
                            if (state.inspectingSummary != null) {
                                onCloseEdit()
                            } else {
                                onDismissPane()
                            }
                        }
                    )
                }
                "DETAIL" -> {
                    val summary = state.inspectingSummary
                    if (summary != null) {
                        CategoryInspectorDetailView(
                            summary = summary,
                            groupTitle = state.inspectingGroupTitle ?: "Category Cluster",
                            onOpenEdit = { onOpenEdit(summary.category) },
                            onDeleteCategory = { onDeleteCategory(summary.category.id) },
                            onViewTransactions = onViewTransactions,
                            onDismissPane = onDismissPane
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun CategoryInspectorDetailView(
    summary: CategorySummary,
    groupTitle: String,
    onOpenEdit: () -> Unit,
    onDeleteCategory: () -> Unit,
    onViewTransactions: () -> Unit,
    onDismissPane: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val cat = summary.category
    val catColor = try {
        val hex = cat.color.trim().removePrefix("#")
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat.displayEmoji ?: "#",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = catColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDismissPane) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Inspector",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Totals Summary Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL VOLUME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = MoneyUtils.formatCents(summary.totalAmountCents),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${summary.transactionCount} transaction${if (summary.transactionCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Category Behavior Flags Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "CLASSIFICATION & FLAGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                CategoryMetadataRow(
                    label = "Color Swatch",
                    value = cat.color.uppercase()
                )
                CategoryMetadataRow(
                    label = "Flow Type",
                    value = when {
                        cat.isIncome -> "Income"
                        cat.isTransfer -> "Transfer / Neutral"
                        else -> "Expense"
                    }
                )
                CategoryMetadataRow(
                    label = "Default MHA Eligible",
                    value = if (cat.mhaDefaultEligible) "Yes (MHA Parsonage)" else "No"
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    haptics.tick()
                    onOpenEdit()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Category")
            }

            OutlinedButton(
                onClick = {
                    haptics.tick()
                    onViewTransactions()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Transactions")
            }
        }

        OutlinedButton(
            onClick = {
                haptics.reject()
                onDeleteCategory()
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete Category")
        }
    }
}

@Composable
private fun CategoryMetadataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
