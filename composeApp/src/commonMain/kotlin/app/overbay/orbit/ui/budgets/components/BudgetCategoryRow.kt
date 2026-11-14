package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Shared tinted emoji circle badge for category rows across Budgets.
 */
@Composable
internal fun CategoryEmojiBadge(
    category: Category,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val categoryColor = category.color.toComposeColor(MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(categoryColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = category.displayEmoji ?: "📁",
            fontSize = 15.sp
        )
    }
}

/**
 * Shared inline expandable transaction preview and budget action bar.
 */
@Composable
internal fun BudgetCategoryExpandedDrawer(
    item: CategoryBudgetOverviewItem,
    isExpanded: Boolean,
    transactions: List<Transaction>,
    onEditBudget: () -> Unit,
    onDeleteBudget: () -> Unit,
    onViewTransactions: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitAccordionVisibility(visible = isExpanded) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "RECENT TRANSACTIONS (${transactions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (transactions.isEmpty()) {
                    Text(
                        text = "No transactions recorded for this category.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    transactions.take(3).forEachIndexed { index, txn ->
                        OrbitCompactListRow(
                            title = txn.effectiveMerchant,
                            subtitle = txn.effectiveDate,
                            amount = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                            amountColor = if (txn.effectiveAmountCents > 0L) colors.income else colors.expense,
                            showTopDivider = index > 0
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                onEditBudget()
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (item.amountCents > 0L) "Edit Budget" else "Set Budget",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OrbitScaleBadgeVisibility(visible = item.budgetId != null) {
                            OutlinedButton(
                                onClick = {
                                    haptics.reject()
                                    onDeleteBudget()
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, colors.danger),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Delete Budget",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.danger
                                )
                            }
                        }
                    }

                    Text(
                        text = "View All →",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            haptics.tick()
                            onViewTransactions()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Budgeted category row designed for unified grouped cards with optional hairline top divider,
 * clean emoji badge, semantic progress bar with month pace tick, and inline expandable preview.
 */
@Composable
fun BudgetCategoryRow(
    item: CategoryBudgetOverviewItem,
    isExpanded: Boolean,
    transactions: List<Transaction>,
    onClick: () -> Unit,
    onEditBudget: () -> Unit,
    onDeleteBudget: () -> Unit,
    onViewTransactions: () -> Unit,
    isInspected: Boolean = false,
    showTopDivider: Boolean = false,
    monthProgressFraction: Float? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val isOverBudget = item.spentCents > item.amountCents && item.amountCents > 0L
    val isWatch = item.paceStatus == BudgetPaceStatus.WATCH

    val statusColor by animateColorAsState(
        targetValue = when {
            isOverBudget -> colors.danger
            isWatch -> colors.warning
            else -> colors.income
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "budgetStatusColor"
    )

    val rowHighlightColor by animateColorAsState(
        targetValue = if (isInspected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "budgetRowHighlightColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowHighlightColor, RoundedCornerShape(10.dp))
    ) {
        if (showTopDivider) {
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.tick()
                    onClick()
                }
                .padding(vertical = 10.dp)
        ) {
            // Top Row: Emoji badge + Name + Remaining/Over
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                ) {
                    CategoryEmojiBadge(category = item.category)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val remainingText = if (isOverBudget) {
                    "${MoneyUtils.formatCents(item.spentCents - item.amountCents)} over"
                } else if (item.amountCents > 0L) {
                    "${MoneyUtils.formatCents(item.amountCents - item.spentCents)} left"
                } else {
                    "${MoneyUtils.formatCents(item.spentCents)} spent"
                }

                Text(
                    text = remainingText,
                    style = TabularNumStyle.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (item.amountCents > 0L) {
                (item.spentCents.toFloat() / item.amountCents.toFloat()).coerceIn(0f, 1f)
            } else 0f

            OrbitPillProgressBar(
                percent = progress,
                overBudget = isOverBudget,
                customFillColor = statusColor,
                height = 7.dp,
                paceMarkerFraction = if (item.amountCents > 0L) monthProgressFraction else null
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.amountCents > 0L) {
                        "${MoneyUtils.formatCents(item.spentCents)} of ${MoneyUtils.formatCents(item.amountCents)}"
                    } else {
                        "No budget set"
                    },
                    style = TabularNumStyle.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val rightSubtext = if (item.amountCents > 0L) {
                    "${(item.utilizationRatio * 100.0).roundToInt()}% used"
                } else {
                    "${item.transactionCount} transaction${if (item.transactionCount == 1) "" else "s"}"
                }

                Text(
                    text = rightSubtext,
                    style = TabularNumStyle.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        BudgetCategoryExpandedDrawer(
            item = item,
            isExpanded = isExpanded,
            transactions = transactions,
            onEditBudget = onEditBudget,
            onDeleteBudget = onDeleteBudget,
            onViewTransactions = onViewTransactions
        )
    }
}
