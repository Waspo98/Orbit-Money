package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitExpandableChevron
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.DashboardRecentTransactionItem
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial Recent Transactions card showing up to 5 latest transactions
 * with tinted category emoji avatars, pending pills, and inline expandable quick actions.
 */
@Composable
fun RecentTransactionsCard(
    transactions: List<DashboardRecentTransactionItem>,
    mhaEnabled: Boolean = false,
    categories: List<Category> = emptyList(),
    onSelectTransaction: (Long) -> Unit,
    onViewAll: () -> Unit,
    onQuickCategorize: (Long, Long) -> Unit = { _, _ -> },
    onToggleTransfer: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleIgnore: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleMha: (Long, Boolean) -> Unit = { _, _ -> },
    onMarkRecurring: (Long) -> Unit = {},
    onResetEdits: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var expandedTransactionId by remember { mutableStateOf<Long?>(null) }

    OrbitSectionCard(
        title = "Recent Transactions",
        actionText = "See All",
        onActionClick = onViewAll,
        onCardClick = onViewAll,
        modifier = modifier
    ) {
        if (transactions.isEmpty()) {
            Text(
                text = "No recent transactions recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                transactions.take(10).forEachIndexed { index, txn ->
                    val isExpanded = expandedTransactionId == txn.id
                    val catColor = txn.categoryColor.toComposeColor(MaterialTheme.colorScheme.primary)
                    val emoji = CategoryEmojiMapper.resolveEmoji(txn.categoryIcon)
                        ?: if (txn.categoryName.isNullOrBlank() || txn.categoryName == "Uncategorized") "❓" else "💳"
                    val catLabel = txn.categoryName ?: "Uncategorized"
                    val subtitleText = buildString {
                        append(txn.dateDisplay)
                        append(" · ")
                        append(catLabel)
                        if (txn.isPending) append(" · Pending")
                    }

                    OrbitCompactListRow(
                        title = txn.merchant,
                        subtitle = subtitleText,
                        amount = txn.formattedAmount,
                        amountDetail = txn.accountName,
                        amountColor = if (txn.isExpense) colors.expense else colors.income,
                        showTopDivider = index > 0,
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(catColor.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 15.sp)
                            }
                        },
                        trailingAction = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (txn.isPending) {
                                    OrbitBadge(
                                        text = "Pending",
                                        containerColor = colors.warning.copy(alpha = 0.14f),
                                        contentColor = colors.warning
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        expandedTransactionId = if (isExpanded) null else txn.id
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    OrbitExpandableChevron(
                                        expanded = isExpanded,
                                        contentDescription = "Quick actions"
                                    )
                                }
                            }
                        },
                        onClick = {
                            haptics.tick()
                            onSelectTransaction(txn.id)
                        }
                    )

                    OrbitAccordionVisibility(visible = isExpanded) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            RecentTransactionQuickActionsRow(
                                transaction = txn,
                                mhaEnabled = mhaEnabled,
                                categories = categories,
                                onQuickCategorize = { catId -> onQuickCategorize(txn.id, catId) },
                                onToggleTransfer = { isTr -> onToggleTransfer(txn.id, isTr) },
                                onToggleIgnore = { isIgn -> onToggleIgnore(txn.id, isIgn) },
                                onToggleMha = { isMha -> onToggleMha(txn.id, isMha) },
                                onMarkRecurring = { onMarkRecurring(txn.id) },
                                onResetEdits = { onResetEdits(txn.id) },
                                onInspectDetails = { onSelectTransaction(txn.id) },
                                onDelete = { onDelete(txn.id) },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
