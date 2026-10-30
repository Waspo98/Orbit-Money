package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.DashboardRecentTransactionItem
import app.overbay.orbit.ui.dashboard.UncategorizedTransactionsState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Uncategorized transactions card listing up to 5 transactions requiring a category.
 */
@Composable
fun UncategorizedCard(
    state: UncategorizedTransactionsState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "UNCATEGORIZED TRANSACTIONS",
        actionText = "Categorize",
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
                label = "Needs category",
                value = "${state.count} items",
                valueColor = if (state.count > 0) colors.warning else colors.income
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (state.transactions.isEmpty()) {
                Text(
                    text = "All transactions are neatly categorized! 🎉",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.transactions.take(5).forEachIndexed { index, txn ->
                    OrbitCompactListRow(
                        title = txn.merchant,
                        subtitle = "${txn.dateDisplay} · ${txn.accountName}",
                        amount = txn.formattedAmount,
                        amountColor = if (txn.isExpense) colors.expense else colors.income,
                        showTopDivider = index > 0
                    )
                }
            }
        }
    }
}

/**
 * Quick category check launch card opening the swipeable review modal.
 * Matches main's `CategorizeRecentCard` with stacked 3-card graphic and copy.
 */
@Composable
fun CategorizeRecentCard(
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "CATEGORIZE RECENT TRANSACTIONS",
        actionText = "Open",
        onActionClick = onOpenReview,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onOpenReview()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReviewLaunchGraphic()

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick category check",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Swipe through recent transactions that need a little human judgment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun ReviewLaunchGraphic(
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val cardBorderColor = primary.copy(alpha = 0.36f)
    val cardShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier.size(width = 84.dp, height = 78.dp),
        contentAlignment = Alignment.Center
    ) {
        // Left ghost card (-17dp X, -22deg, danger soft background)
        Surface(
            shape = cardShape,
            color = colors.danger.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = 1.dp,
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .offset(x = (-17).dp)
                .graphicsLayer {
                    rotationZ = -22f
                    alpha = 0.75f
                }
        ) {}

        // Right ghost card (+18dp X, +20deg, accent soft background)
        Surface(
            shape = cardShape,
            color = primary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = 1.dp,
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .offset(x = 18.dp)
                .graphicsLayer {
                    rotationZ = 20f
                    alpha = 0.75f
                }
        ) {}

        // Main center card (-8deg, two bottom pill bars)
        Surface(
            shape = cardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .graphicsLayer {
                    rotationZ = -8f
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.45f))
                )
            }
        }
    }
}

/**
 * Editorial Recent Transactions card showing up to 10 latest transactions
 * with inline expandable quick actions (categorize, transfer, ignore, mha, recurring, reset, delete).
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
        title = "RECENT TRANSACTIONS",
        actionText = "See All",
        onActionClick = onViewAll,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onViewAll()
            }
    ) {
        if (transactions.isEmpty()) {
            Text(
                text = "No recent transactions recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                transactions.take(10).forEachIndexed { index, txn ->
                    val isExpanded = expandedTransactionId == txn.id
                    val catColor = txn.categoryColor.toComposeColor(MaterialTheme.colorScheme.primary)
                    val catPrefix = CategoryEmojiMapper.resolveEmoji(txn.categoryIcon)?.let { "$it " } ?: ""
                    val catLabel = txn.categoryName?.let { "$catPrefix$it" } ?: "❓ Uncategorized"

                    OrbitCompactListRow(
                        title = txn.merchant,
                        subtitle = "${txn.dateDisplay} · $catLabel",
                        amount = txn.formattedAmount,
                        amountDetail = txn.accountName,
                        amountColor = if (txn.isExpense) colors.expense else colors.income,
                        showTopDivider = index > 0,
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                        },
                        trailingAction = {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    expandedTransactionId = if (isExpanded) null else txn.id
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Quick actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            haptics.tick()
                            onSelectTransaction(txn.id)
                        }
                    )

                    if (isExpanded) {
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
                            onDelete = { onDelete(txn.id) }
                        )
                    }
                }
            }
        }
    }
}

