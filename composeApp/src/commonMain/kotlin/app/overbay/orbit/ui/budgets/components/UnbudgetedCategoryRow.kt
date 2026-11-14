package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Compact row for unbudgeted categories showing spent/refund summary and a concise "+ Budget" chip
 * without misleading full progress bars or truncated subtext.
 */
@Composable
fun UnbudgetedCategoryRow(
    item: CategoryBudgetOverviewItem,
    isExpanded: Boolean,
    transactions: List<Transaction>,
    onClick: () -> Unit,
    onSetBudget: () -> Unit,
    onViewTransactions: () -> Unit,
    isInspected: Boolean = false,
    showTopDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val rowHighlightColor by animateColorAsState(
        targetValue = if (isInspected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "unbudgetedRowHighlightColor"
    )

    val amountSummary = if (item.spentCents < 0L) {
        "Net refund +${MoneyUtils.formatCents(-item.spentCents)}"
    } else {
        MoneyUtils.formatCents(item.spentCents)
    }
    val txnLabel = "${item.transactionCount} txn${if (item.transactionCount == 1) "" else "s"}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowHighlightColor, RoundedCornerShape(10.dp))
    ) {
        if (showTopDivider) {
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.tick()
                    onClick()
                }
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 10.dp)
            ) {
                CategoryEmojiBadge(category = item.category)
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$amountSummary · $txnLabel",
                        style = TabularNumStyle.copy(fontSize = 12.sp),
                        color = if (item.spentCents < 0L) colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OrbitActionChip(
                label = "+ Budget",
                onClick = {
                    haptics.tick()
                    onSetBudget()
                }
            )
        }

        BudgetCategoryExpandedDrawer(
            item = item,
            isExpanded = isExpanded,
            transactions = transactions,
            onEditBudget = onSetBudget,
            onDeleteBudget = {},
            onViewTransactions = onViewTransactions
        )
    }
}
