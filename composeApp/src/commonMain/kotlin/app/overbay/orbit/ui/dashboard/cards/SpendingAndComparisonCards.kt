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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.isUncategorizedCategoryName
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitAnimatedEntryColumn
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.rememberAnimatedExternalEntries
import app.overbay.orbit.ui.dashboard.BiggestTransactionsState
import app.overbay.orbit.ui.dashboard.TopSpendingState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Top Spending card showing animated total monthly spend, a proportional spectrum bar,
 * and the top 7 spending categories with category-colored bars and percentages.
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
        title = "Top Spending This Month",
        actionText = "Browse",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Spend",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RollingTickerCents(
                    cents = state.totalSpentCents,
                    style = TabularNumStyle.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (state.categories.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No spending recorded this month yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OrbitStackedSegmentBar(
                    segments = state.categories.map { cat ->
                        OrbitBarSegment(
                            key = cat.categoryId,
                            fraction = (cat.percentOfTotal / 100.0).toFloat(),
                            color = cat.categoryColor.toComposeColor(MaterialTheme.colorScheme.primary)
                        )
                    },
                    height = 7.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                state.categories.forEach { cat ->
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
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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
 * Biggest monthly transactions card with category avatars, per-row inspection,
 * and quick "Hide from dashboard" support.
 */
@Composable
fun BiggestTransactionsCard(
    state: BiggestTransactionsState,
    onClick: () -> Unit,
    onSelectTransaction: (Long) -> Unit = {},
    onHideTransaction: (Long) -> Unit,
    onResetHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Biggest Monthly Transactions",
        actionText = "View All",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        val (txnEntries, onTxnRemoveFinished) = rememberAnimatedExternalEntries(
            items = state.transactions,
            itemKey = { it.id }
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitAccordionVisibility(visible = state.transactions.isEmpty()) {
                Text(
                    text = "No large expenses recorded this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OrbitAnimatedEntryColumn(
                entries = txnEntries,
                onRemoveFinished = onTxnRemoveFinished,
                minActiveCount = 0,
                bringIntoViewOnAdd = false
            ) { entry, displayIndex, _ ->
                val txn = entry.value
                val catColor = txn.categoryColor.toComposeColor(MaterialTheme.colorScheme.primary)
                val catEmoji = CategoryEmojiMapper.resolveEmoji(txn.categoryIcon)
                    ?: if (txn.categoryName.isNullOrBlank() || isUncategorizedCategoryName(txn.categoryName)) "❓" else "💸"
                val catLabel = txn.categoryName ?: "Uncategorized"
                val detailSubtitle = buildString {
                    append(txn.dateDisplay)
                    append(" · ")
                    append(catLabel)
                }

                OrbitCompactListRow(
                    title = txn.merchant,
                    subtitle = detailSubtitle,
                    amount = txn.formattedAmount,
                    amountDetail = txn.accountName.ifBlank { null },
                    amountColor = colors.expense,
                    showTopDivider = displayIndex > 0,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(catColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = catEmoji, fontSize = 14.sp)
                        }
                    },
                    onClick = {
                        haptics.tick()
                        onSelectTransaction(txn.id)
                    },
                    trailingAction = {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onHideTransaction(txn.id)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hide from dashboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }

            OrbitAccordionVisibility(visible = state.hiddenCount > 0) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val noun = if (state.hiddenCount == 1) "transaction" else "transactions"
                        Text(
                            text = "${state.hiddenCount} hidden $noun",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Reset Hidden",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptics.tick()
                                    onResetHidden()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

