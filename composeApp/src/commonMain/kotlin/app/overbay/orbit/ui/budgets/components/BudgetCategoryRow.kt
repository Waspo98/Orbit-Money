package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial category budget row with expandable inline transaction preview
 * and direct Edit/Delete budget actions.
 * Strictly under the 400-line ceiling.
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
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val categoryColor = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)
    val isOverBudget = item.spentCents > item.amountCents && item.amountCents > 0L
    val isWatch = item.paceStatus == BudgetPaceStatus.WATCH

    val statusColor = when {
        isOverBudget -> colors.danger
        isWatch -> colors.warning
        else -> colors.income
    }

    val rowBgColor = if (isInspected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val rowBorderColor = if (isInspected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        OrbitTheme.colors.cardBorder
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = rowBgColor,
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, rowBorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Summary Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.tick()
                        onClick()
                    }
                    .padding(16.dp)
            ) {
                // Top Row: Category dot/icon + name + remaining/over
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(categoryColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.category.labelWithEmoji,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Remaining text
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

                // Progress Bar
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

                // Bottom Subtext: Spent vs Budgeted & Txn count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.amountCents > 0L) {
                        Text(
                            text = "${MoneyUtils.formatCents(item.spentCents)} of ${MoneyUtils.formatCents(item.amountCents)}",
                            style = TabularNumStyle.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No budget set",
                                style = TabularNumStyle.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                            Surface(
                                onClick = {
                                    haptics.tick()
                                    onEditBudget()
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "+ Set Budget",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${item.transactionCount} transaction${if (item.transactionCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Inline Expandable Detail Preview
            AnimatedVisibility(visible = isExpanded) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "RECENT TRANSACTIONS",
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
                                    amountColor = colors.expense,
                                    showTopDivider = index > 0
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Edit Budget, Delete Budget, View in Transactions
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

                                if (item.budgetId != null) {
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
                                text = "View all in Transactions →",
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
    }
}

