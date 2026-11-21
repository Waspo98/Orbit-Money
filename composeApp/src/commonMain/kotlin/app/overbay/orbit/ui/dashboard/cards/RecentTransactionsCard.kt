package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.isUncategorizedCategoryName
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.ui.dashboard.DashboardRecentTransactionItem
import app.overbay.orbit.ui.dashboard.RecentTransactionConfirmKind
import app.overbay.orbit.ui.dashboard.RecentTransactionsState
import app.overbay.orbit.ui.transactions.components.TransactionMerchantMark
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Dashboard Recent Transactions card showing up to 10 latest transactions with 3-line ledger rows,
 * shared display-only merchant logos, and an inline lean quick-action drawer.
 */
@Composable
fun RecentTransactionsCard(
    state: RecentTransactionsState,
    mhaEnabled: Boolean = false,
    onToggleExpand: (Long) -> Unit,
    onOpenCategoryPicker: (Long) -> Unit,
    onEditTransaction: (Long) -> Unit,
    onViewDetails: (Long) -> Unit,
    onToggleTransfer: (Long, Boolean) -> Unit,
    onToggleIgnore: (Long, Boolean) -> Unit,
    onToggleMha: (Long, Boolean) -> Unit,
    onRequestConfirm: (Long, String, RecentTransactionConfirmKind) -> Unit,
    onReportLogoStatus: (String, String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Recent Transactions",
        actionText = "See All",
        onActionClick = onViewAll,
        onCardClick = null,
        edgeToEdgeContent = true,
        modifier = modifier
    ) {
        if (state.transactions.isEmpty()) {
            Text(
                text = "No recent transactions recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.transactions.forEachIndexed { index, item ->
                    val isExpanded = state.expandedTransactionId == item.id
                    val isFirst = index == 0
                    val isLast = index == state.transactions.lastIndex
                    val catColor = (item.category?.color ?: item.categoryColor)
                        .toComposeColor(MaterialTheme.colorScheme.primary)
                    val categoryLabel = resolveCategoryLabel(item)

                    RecentTransactionRow(
                        item = item,
                        categoryLabel = categoryLabel,
                        categoryColor = catColor,
                        isFirst = isFirst,
                        isLast = isLast,
                        isExpanded = isExpanded,
                        onReportLogoStatus = onReportLogoStatus,
                        onClick = { onToggleExpand(item.id) }
                    )

                    OrbitAccordionVisibility(visible = isExpanded) {
                        RecentTransactionLeanDrawer(
                            item = item,
                            categoryLabel = categoryLabel,
                            categoryColor = catColor,
                            mhaEnabled = mhaEnabled,
                            onOpenCategoryPicker = { onOpenCategoryPicker(item.id) },
                            onEdit = { onEditTransaction(item.id) },
                            onRequestMarkRecurring = {
                                onRequestConfirm(item.id, item.merchant, RecentTransactionConfirmKind.MARK_RECURRING)
                            },
                            onViewDetails = { onViewDetails(item.id) },
                            onToggleTransfer = { onToggleTransfer(item.id, !item.isTransfer) },
                            onToggleIgnore = { onToggleIgnore(item.id, !item.isIgnored) },
                            onToggleMha = { onToggleMha(item.id, !item.isMhaEligible) },
                            onRequestResetEdits = {
                                onRequestConfirm(item.id, item.merchant, RecentTransactionConfirmKind.RESET_EDITS)
                            },
                            onRequestDelete = {
                                onRequestConfirm(item.id, item.merchant, RecentTransactionConfirmKind.DELETE)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun resolveCategoryLabel(item: DashboardRecentTransactionItem): String {
    item.category?.let { return it.labelWithEmoji }
    if (item.isTransfer) return "🔄 Transfer"
    val rawName = item.categoryName
    if (!rawName.isNullOrBlank() && !isUncategorizedCategoryName(rawName)) {
        val emoji = CategoryEmojiMapper.resolveEmoji(item.categoryIcon)
        return if (!emoji.isNullOrBlank()) "$emoji $rawName" else rawName
    }
    return "❓ Uncategorized"
}

@Composable
private fun RecentTransactionRow(
    item: DashboardRecentTransactionItem,
    categoryLabel: String,
    categoryColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isExpanded: Boolean,
    onReportLogoStatus: (String, String) -> Unit,
    onClick: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val rowInteractionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isFirst) {
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = LocalIndication.current
                ) {
                    haptics.tick()
                    onClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .orbitTactilePress(
                        pressedScale = 0.985f,
                        interactionSource = rowInteractionSource
                    )
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        top = if (isFirst) 12.dp else 10.dp,
                        bottom = if (isLast && !isExpanded) 14.dp else 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
            TransactionMerchantMark(
                transaction = item.transaction,
                categoryIcon = item.category?.displayEmoji ?: item.categoryIcon,
                categoryColor = categoryColor,
                size = 40.dp,
                cornerRadius = 10.dp,
                onFindLogo = null,
                onUseCategoryIcon = null,
                onReportLogoStatus = onReportLogoStatus
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isIgnored) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.isIgnored) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPending) {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = colors.warning,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.dateDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = categoryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.accountName.ifBlank { "Account" },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            val amountColor = when {
                item.isTransfer -> MaterialTheme.colorScheme.secondary
                item.isIncome -> colors.income
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = item.formattedAmount,
                style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = amountColor,
                maxLines = 1
            )
            }
        }
    }
}
