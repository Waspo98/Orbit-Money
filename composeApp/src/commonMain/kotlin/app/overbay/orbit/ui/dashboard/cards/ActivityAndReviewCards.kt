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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import app.overbay.orbit.theme.OrbitTheme
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
 * Quick category check launch card opening the swipeable review sheet.
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Quick category check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Swipe through recent transactions that need a little human judgment to keep your budgets crisp.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    onOpenReview()
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Start Review",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Editorial Recent Transactions card showing up to 10 latest transactions
 * with 3-line layout (Merchant, Date · Category, Account + Amount).
 */
@Composable
fun RecentTransactionsCard(
    transactions: List<DashboardRecentTransactionItem>,
    onSelectTransaction: (Long) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "RECENT TRANSACTIONS",
        actionText = "See all",
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
                    val catColor = parseHexColor(txn.categoryColor) ?: MaterialTheme.colorScheme.primary
                    OrbitCompactListRow(
                        title = txn.merchant,
                        subtitle = "${txn.dateDisplay} · ${txn.categoryName ?: "Uncategorized"}",
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
                        onClick = {
                            haptics.tick()
                            onSelectTransaction(txn.id)
                        }
                    )
                }
            }
        }
    }
}

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank() || !hex.startsWith("#")) return null
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or colorInt)
        } else if (clean.length == 8) {
            Color(colorInt)
        } else null
    } catch (_: Exception) {
        null
    }
}
