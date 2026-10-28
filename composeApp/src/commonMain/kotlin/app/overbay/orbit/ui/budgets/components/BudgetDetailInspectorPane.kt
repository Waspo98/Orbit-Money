package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.budgets.BudgetsUiState
import app.overbay.orbit.ui.budgets.BudgetsViewModel
import app.overbay.orbit.ui.budgets.dialogs.BudgetAmountEditorContent
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class BudgetInspectorSubMode { DETAIL, EDIT }

/**
 * Right-hand morphing inspector pane for Budgets on unfolded phones, tablets, and desktops.
 * Shows the category budget breakdown + full monthly transactions list by default when a category row is tapped,
 * and morphs smoothly into the BudgetAmountEditorContent when editing or adding a budget.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun BudgetDetailInspectorPane(
    state: BudgetsUiState,
    viewModel: BudgetsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var cachedItem by remember { mutableStateOf<CategoryBudgetOverviewItem?>(null) }
    val activeItem = state.inspectingItem
    if (activeItem != null) {
        cachedItem = activeItem
    }
    val inspecting = activeItem ?: cachedItem

    val subMode = if (state.showEditorSheet) BudgetInspectorSubMode.EDIT else BudgetInspectorSubMode.DETAIL

    BackHandler(enabled = subMode == BudgetInspectorSubMode.EDIT && state.expandedCategoryId != null) {
        haptics.tick()
        viewModel.closeBudgetEditor()
    }

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxSize(),
        label = "budgetInspectorSubModeTransition"
    ) { mode ->
        when (mode) {
            BudgetInspectorSubMode.EDIT -> {
                val editTarget = state.editingItem ?: inspecting
                if (editTarget != null) {
                    val avgSpend = viewModel.calculate3MonthAverageSpend(editTarget.category.id)
                    BudgetAmountEditorContent(
                        item = editTarget,
                        threeMonthAvgCents = avgSpend,
                        onSave = { catId, amountCents, rollover ->
                            viewModel.saveBudget(catId, amountCents, rollover)
                        },
                        onDelete = { budgetId ->
                            viewModel.deleteBudget(budgetId)
                        },
                        onDismiss = {
                            if (state.expandedCategoryId != null) viewModel.closeBudgetEditor() else onClosePane()
                        }
                    )
                }
            }
            BudgetInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    val avgSpend = viewModel.calculate3MonthAverageSpend(inspecting.category.id)
                    val txns = state.categoryTransactions[inspecting.category.id] ?: emptyList()
                    BudgetReadOnlyDetailContent(
                        item = inspecting,
                        monthLabel = state.monthLabel,
                        threeMonthAvgCents = avgSpend,
                        transactions = txns,
                        onClose = onClosePane,
                        onEdit = { viewModel.openBudgetEditor(inspecting) },
                        onDelete = { inspecting.budgetId?.let { viewModel.deleteBudget(it) } },
                        onViewTransactions = {
                            onNavigate(OrbitNavKey.Transactions(categoryId = inspecting.category.id))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetReadOnlyDetailContent(
    item: CategoryBudgetOverviewItem,
    monthLabel: String,
    threeMonthAvgCents: Cents,
    transactions: List<Transaction>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewTransactions: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val categoryColor = try {
        val hex = item.category.color.trim().removePrefix("#")
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val isOverBudget = item.spentCents > item.amountCents && item.amountCents > 0L
    val isWatch = item.paceStatus == BudgetPaceStatus.WATCH
    val statusColor = when {
        isOverBudget -> colors.danger
        isWatch -> colors.warning
        else -> colors.income
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.category.labelWithEmoji,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { haptics.tick(); onClose() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            val remainingText = if (isOverBudget) {
                "${MoneyUtils.formatCents(item.spentCents - item.amountCents)} over budget"
            } else if (item.amountCents > 0L) {
                "${MoneyUtils.formatCents(item.amountCents - item.spentCents)} remaining"
            } else {
                "No monthly limit configured"
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "SPENT THIS MONTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MoneyUtils.formatCents(item.spentCents),
                            style = TabularNumStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = remainingText,
                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                val progress = if (item.amountCents > 0L) {
                    (item.spentCents.toFloat() / item.amountCents.toFloat()).coerceIn(0f, 1f)
                } else 1f
                OrbitPillProgressBar(
                    percent = progress,
                    overBudget = isOverBudget,
                    customFillColor = statusColor,
                    height = 8.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (item.amountCents > 0L) "Budgeted: ${MoneyUtils.formatCents(item.amountCents)}" else "Unbudgeted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (threeMonthAvgCents > 0L) {
                        Text(
                            text = "3-Mo Avg: ${MoneyUtils.formatCents(threeMonthAvgCents, showCents = false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BudgetPillAction(
                    label = if (item.amountCents > 0L) "Edit Budget" else "Set Budget",
                    onClick = onEdit
                )
                BudgetPillAction(
                    label = "View in Transactions",
                    onClick = onViewTransactions
                )
                if (item.budgetId != null) {
                    BudgetPillAction(
                        label = "Delete Budget",
                        isDestructive = true,
                        onClick = onDelete
                    )
                }
            }
        }

        item {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "MONTHLY TRANSACTIONS (${transactions.size})",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions recorded for this category in $monthLabel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(transactions, key = { it.id }) { txn ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = txn.effectiveMerchant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = txn.effectiveDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                            style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = colors.expense
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetPillAction(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = { haptics.tick(); onClick() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (isDestructive) OrbitTheme.colors.danger else OrbitTheme.colors.cardBorder),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
        )
    }
}
