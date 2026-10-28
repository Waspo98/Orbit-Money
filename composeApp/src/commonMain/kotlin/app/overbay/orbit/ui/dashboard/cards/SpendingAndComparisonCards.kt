package app.overbay.orbit.ui.dashboard.cards

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.BiggestTransactionsState
import app.overbay.orbit.ui.dashboard.MonthComparisonState
import app.overbay.orbit.ui.dashboard.TopSpendingState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Top Spending card showing total monthly spend and the top 7 spending categories
 * with category-colored progress bars and percentages.
 */
@Composable
fun TopSpendingCard(
    state: TopSpendingState,
    onClick: () -> Unit,
    onCategoryClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "TOP SPENDING THIS MONTH",
        actionText = "Browse",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitMiniStatRow(
                label = "Total Spend",
                value = MoneyUtils.formatCents(state.totalSpentCents),
                valueColor = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (state.categories.isEmpty()) {
                Text(
                    text = "No spending recorded this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.categories.forEachIndexed { index, cat ->
                    val color = cat.categoryColor.toComposeColor(MaterialTheme.colorScheme.primary)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                haptics.tick()
                                onCategoryClick(cat.categoryId)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val iconPrefix = CategoryEmojiMapper.resolveEmoji(cat.categoryIcon)?.let { "$it " } ?: ""
                                Text(
                                    text = "$iconPrefix${cat.categoryName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${MoneyUtils.formatCents(cat.spentCents)} (${cat.percentOfTotal.toInt()}%)",
                                style = TabularNumStyle.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OrbitPillProgressBar(
                            percent = (cat.percentOfTotal / 100.0).toFloat(),
                            customFillColor = color,
                            height = 6.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Biggest monthly transactions card with quick "Hide from dashboard" support.
 */
@Composable
fun BiggestTransactionsCard(
    state: BiggestTransactionsState,
    onClick: () -> Unit,
    onHideTransaction: (Long) -> Unit,
    onResetHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "BIGGEST MONTHLY TRANSACTIONS",
        actionText = "Browse",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.transactions.isEmpty()) {
                Text(
                    text = "No large expenses recorded this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.transactions.forEachIndexed { index, txn ->
                    val catPrefix = CategoryEmojiMapper.resolveEmoji(txn.categoryIcon)?.let { "$it " } ?: ""
                    val catLabel = txn.categoryName?.let { "$catPrefix$it" } ?: "❓ Uncategorized"
                    OrbitCompactListRow(
                        title = txn.merchant,
                        subtitle = "${txn.dateDisplay} · $catLabel",
                        amount = txn.formattedAmount,
                        amountColor = colors.expense,
                        showTopDivider = index > 0,
                        trailingAction = {
                            Text(
                                text = "Hide",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clickable {
                                        haptics.tick()
                                        onHideTransaction(txn.id)
                                    }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                    )
                }
            }

            if (state.hiddenCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.hiddenCount} transaction(s) hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Reset Hidden",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            haptics.tick()
                            onResetHidden()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 4-column Month vs Last Month comparison table card.
 */
@Composable
fun MonthComparisonCard(
    state: MonthComparisonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "MONTH VS LAST MONTH",
        actionText = "Details",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Metric | Current | Previous | Change
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "METRIC",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = state.currentMonthLabel.take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = state.previousMonthLabel.take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "CHANGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            state.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.metric,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = MoneyUtils.formatCents(row.currentCents),
                        style = TabularNumStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = MoneyUtils.formatCents(row.previousCents),
                        style = TabularNumStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    val deltaText = MoneyUtils.formatCents(row.deltaCents, signed = true)
                    Text(
                        text = deltaText,
                        style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (row.isPositiveImprovement) colors.income else colors.danger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

