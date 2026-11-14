package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.MerchantBudgetMixItem
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitActionChip
import kotlin.math.roundToInt

/**
 * Expandable callout card below the Spending Mix Donut displaying details and quick actions
 * for the selected Category, Merchant, or "Other" slice.
 */
@Composable
internal fun SpendingMixSelectionStrip(
    slice: SpendingMixSlice,
    totalSpent: Cents,
    onInspectCategory: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            when {
                slice.categoryItem != null -> CategorySliceDetails(
                    item = slice.categoryItem,
                    percentInt = slice.percentInt,
                    onInspectCategory = onInspectCategory,
                    onEditBudget = onEditBudget,
                    onViewTransactions = onViewTransactions
                )
                slice.merchantItem != null -> MerchantSliceDetails(
                    item = slice.merchantItem,
                    percentInt = slice.percentInt,
                    badgeColor = slice.color,
                    onInspectCategory = onInspectCategory,
                    onViewTransactions = onViewTransactions
                )
                slice.otherCategories.isNotEmpty() -> OtherMixBreakdownList(
                    header = "OTHER CATEGORIES (${slice.otherCategories.size})",
                    rows = slice.otherCategories.map { other ->
                        Triple(other.category.labelWithEmoji, other.spentCents) {
                            onInspectCategory(other.category.id)
                        }
                    },
                    totalSpent = totalSpent
                )
                else -> OtherMixBreakdownList(
                    header = "OTHER MERCHANTS (${slice.otherMerchants.size})",
                    rows = slice.otherMerchants.map { other ->
                        Triple("${other.merchant} · ${other.transactionCount}x", other.spentCents) {
                            onViewTransactions(other.primaryCategoryId ?: 0L)
                        }
                    },
                    totalSpent = totalSpent
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySliceDetails(
    item: CategoryBudgetOverviewItem,
    percentInt: Int,
    onInspectCategory: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit
) {
    val colors = OrbitTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            CategoryEmojiBadge(category = item.category, size = 30.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val budgetContext = if (item.amountCents > 0L) {
                    "${(item.utilizationRatio * 100.0).roundToInt()}% of ${MoneyUtils.formatCents(item.amountCents, showCents = false)} budget"
                } else {
                    "Unbudgeted · Everything Else"
                }
                Text(
                    text = budgetContext,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = if (item.amountCents > 0L && item.spentCents > item.amountCents) {
                        colors.danger
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Text(
            text = "${MoneyUtils.formatCents(item.spentCents)} ($percentInt%)",
            style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OrbitActionChip(label = "Inspect", onClick = { onInspectCategory(item.category.id) })
        if (item.category.id > 0L) {
            OrbitActionChip(
                label = if (item.amountCents > 0L) "Edit Budget" else "+ Budget",
                onClick = { onEditBudget(item) }
            )
        }
        OrbitActionChip(label = "Transactions", onClick = { onViewTransactions(item.category.id) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantSliceDetails(
    item: MerchantBudgetMixItem,
    percentInt: Int,
    badgeColor: Color,
    onInspectCategory: (Long) -> Unit,
    onViewTransactions: (Long) -> Unit
) {
    val avgPerVisit = if (item.transactionCount > 0) item.spentCents / item.transactionCount else 0L
    val catEmoji = CategoryEmojiMapper.resolveEmoji(item.primaryCategoryIcon)
    val catTint = item.primaryCategoryColor.toComposeColor(badgeColor)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(catTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = catEmoji ?: item.merchant.take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = catTint
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.merchant,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val visits = "${item.transactionCount} visit${if (item.transactionCount == 1) "" else "s"}"
                Text(
                    text = "$visits · avg ${MoneyUtils.formatCents(avgPerVisit, showCents = false)} · ${item.primaryCategoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "${MoneyUtils.formatCents(item.spentCents)} ($percentInt%)",
            style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val catId = item.primaryCategoryId
        if (catId != null && catId > 0L) {
            OrbitActionChip(
                label = "Inspect ${item.primaryCategoryName}",
                onClick = { onInspectCategory(catId) }
            )
            OrbitActionChip(
                label = "Transactions",
                onClick = { onViewTransactions(catId) }
            )
        } else {
            OrbitActionChip(
                label = "Transactions",
                onClick = { onViewTransactions(0L) }
            )
        }
    }
}

@Composable
private fun OtherMixBreakdownList(
    header: String,
    rows: List<Triple<String, Cents, () -> Unit>>,
    totalSpent: Cents
) {
    Text(
        text = header,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    rows.forEachIndexed { index, (label, spentCents, onClick) ->
        val pct = ((spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
        if (index > 0) {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = "${MoneyUtils.formatCents(spentCents, showCents = false)} ($pct%)",
                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
