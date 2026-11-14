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
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * By-Category tab for [SpendingMixPanel]: pairs [SpendingMixDonut] with tappable legend pills
 * and an expandable selection detail strip offering Inspect, + Budget / Edit, and Transactions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpendingMixCategoryTab(
    slices: List<SpendingMixSlice>,
    totalSpent: Cents,
    totalCategoryCount: Int,
    activeSlice: SpendingMixSlice?,
    onToggleSlice: (String) -> Unit,
    onClearSelection: () -> Unit,
    onInspectCategory: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpendingMixDonut(
            slices = slices,
            totalSpent = totalSpent,
            totalCategoryCount = totalCategoryCount,
            activeSlice = activeSlice,
            onToggleSlice = onToggleSlice,
            onClearSelection = onClearSelection
        )

        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice ->
                val isSelected = activeSlice?.key == slice.key
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        slice.color.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) slice.color else OrbitTheme.colors.cardBorder
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics.tick()
                            onToggleSlice(slice.key)
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${slice.percentInt}%",
                            style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        OrbitAccordionVisibility(visible = activeSlice != null) {
            if (activeSlice != null) {
                SpendingMixSelectionStrip(
                    slice = activeSlice,
                    totalSpent = totalSpent,
                    onInspectCategory = onInspectCategory,
                    onEditBudget = onEditBudget,
                    onViewTransactions = onViewTransactions,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendingMixSelectionStrip(
    slice: SpendingMixSlice,
    totalSpent: Cents,
    onInspectCategory: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            val item = slice.item
            if (item != null) {
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
                        text = "${MoneyUtils.formatCents(item.spentCents)} (${slice.percentInt}%)",
                        style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrbitActionChip(
                        label = "Inspect",
                        onClick = { onInspectCategory(item.category.id) }
                    )
                    if (item.category.id > 0L) {
                        OrbitActionChip(
                            label = if (item.amountCents > 0L) "Edit Budget" else "+ Budget",
                            onClick = { onEditBudget(item) }
                        )
                    }
                    OrbitActionChip(
                        label = "Transactions",
                        onClick = { onViewTransactions(item.category.id) }
                    )
                }
            } else {
                Text(
                    text = "OTHER CATEGORIES (${slice.otherItems.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                slice.otherItems.forEachIndexed { index, other ->
                    val pct = ((other.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
                    if (index > 0) {
                        HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectCategory(other.category.id) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = other.category.labelWithEmoji,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            text = "${MoneyUtils.formatCents(other.spentCents, showCents = false)} ($pct%)",
                            style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
