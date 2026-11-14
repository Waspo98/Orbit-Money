package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Budget-coverage lens for [SpendingMixPanel]: shows how much spending falls inside
 * budgeted categories vs. Everything Else, highlights the most overweight budgeted category,
 * and surfaces top unbudgeted categories with 1-tap "+ Budget" actions.
 */
@Composable
internal fun SpendingMixCoverageTab(
    items: List<CategoryBudgetOverviewItem>,
    totalBudgetedCents: Cents,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val totalSpent = items.sumOf { it.spentCents }
    if (totalSpent <= 0L) return

    val budgetedItems = items.filter { it.amountCents > 0L }
    val unbudgetedItems = items.filter { it.amountCents <= 0L }.sortedByDescending { it.spentCents }

    val budgetedSpent = budgetedItems.sumOf { it.spentCents }
    val unbudgetedSpent = unbudgetedItems.sumOf { it.spentCents }

    val coveredPct = ((budgetedSpent.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    val uncoveredPct = (100 - coveredPct).coerceIn(0, 100)

    val coveredColor = MaterialTheme.colorScheme.primary
    val unbudgetedColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "$coveredPct% covered by budgets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$uncoveredPct% Everything Else",
                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OrbitStackedSegmentBar(
            segments = listOf(
                OrbitBarSegment("covered", budgetedSpent.toFloat(), coveredColor),
                OrbitBarSegment("everything_else", unbudgetedSpent.toFloat(), unbudgetedColor)
            ),
            height = 12.dp,
            normalizeToFullWidth = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(coveredColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${MoneyUtils.formatCents(budgetedSpent, showCents = false)} budgeted",
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(unbudgetedColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${MoneyUtils.formatCents(unbudgetedSpent, showCents = false)} unbudgeted",
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Overweight budgeted category spotlight (share of spending >> share of budget)
        val overweight = if (totalBudgetedCents > 0L) {
            budgetedItems
                .map { item ->
                    val spendShare = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
                    val budgetShare = ((item.amountCents.toDouble() / totalBudgetedCents.toDouble()) * 100.0).roundToInt()
                    Triple(item, spendShare, budgetShare)
                }
                .filter { (_, spendShare, budgetShare) -> spendShare - budgetShare >= 8 }
                .maxByOrNull { (_, spendShare, budgetShare) -> spendShare - budgetShare }
        } else null

        if (overweight != null) {
            val (item, spendShare, budgetShare) = overweight
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${item.category.labelWithEmoji} is $spendShare% of spending vs. $budgetShare% of your budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }

        if (unbudgetedItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "TOP UNBUDGETED",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            unbudgetedItems.take(3).forEachIndexed { index, item ->
                val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
                if (index > 0) {
                    HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.tick()
                            onViewTransactions(item.category.id)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        CategoryEmojiBadge(category = item.category, size = 28.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = item.category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${MoneyUtils.formatCents(item.spentCents)} · $pct% of spend",
                                style = TabularNumStyle.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    if (item.category.id > 0L) {
                        OrbitActionChip(
                            label = "+ Budget",
                            onClick = { onEditBudget(item) }
                        )
                    } else {
                        OrbitActionChip(
                            label = "Categorize",
                            onClick = { onViewTransactions(item.category.id) }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "100% of this month's spending is tracked inside category budgets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.income,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
    }
}
