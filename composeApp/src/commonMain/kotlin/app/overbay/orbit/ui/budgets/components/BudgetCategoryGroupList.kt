package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitExpandableChevron
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Lazy-list extension that emits Spending Mix, Budgeted rows, Unbudgeted rows,
 * and Inactive categories as individual virtualized LazyColumn items.
 */
fun LazyListScope.budgetCategoryGroupItems(
    budgeted: List<CategoryBudgetOverviewItem>,
    unbudgeted: List<CategoryBudgetOverviewItem>,
    inactive: List<CategoryBudgetOverviewItem>,
    expandedCategoryId: Long?,
    categoryTransactions: Map<Long, List<Transaction>>,
    onToggleExpand: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onViewTransactions: (Long) -> Unit,
    isWideScreen: Boolean = false
) {
    val allActive = (budgeted + unbudgeted).filter { it.spentCents > 0L }
    if (allActive.isNotEmpty()) {
        item(key = "budget_spending_mix", contentType = "spending_mix") {
            SpendingMixPanel(
                items = allActive,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    if (budgeted.isNotEmpty()) {
        val totalBudgeted = budgeted.sumOf { it.amountCents }
        item(key = "budgeted_section_header", contentType = "section_header") {
            SectionHeader(
                title = "Budgeted Categories",
                subtitle = "${budgeted.size} categories • ${MoneyUtils.formatCents(totalBudgeted, showCents = false)}"
            )
        }

        itemsIndexed(
            items = budgeted,
            key = { _, item -> "budgeted_${item.category.id}" },
            contentType = { _, _ -> "budget_category_row" }
        ) { index, item ->
            BudgetCategoryRow(
                item = item,
                isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                isInspected = isWideScreen && item.category.id == expandedCategoryId,
                transactions = categoryTransactions[item.category.id] ?: emptyList(),
                onClick = { onToggleExpand(item.category.id) },
                onEditBudget = { onEditBudget(item) },
                onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                onViewTransactions = { onViewTransactions(item.category.id) },
                modifier = (if (index == budgeted.lastIndex) Modifier.padding(bottom = 8.dp) else Modifier)
                    .animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
            )
        }
    }

    if (unbudgeted.isNotEmpty()) {
        val totalUnbudgeted = unbudgeted.sumOf { it.spentCents }
        item(key = "unbudgeted_section_header", contentType = "section_header") {
            SectionHeader(
                title = "Unbudgeted Spending",
                subtitle = "${unbudgeted.size} categories • ${MoneyUtils.formatCents(totalUnbudgeted, showCents = false)}"
            )
        }

        itemsIndexed(
            items = unbudgeted,
            key = { _, item -> "unbudgeted_${item.category.id}" },
            contentType = { _, _ -> "budget_category_row" }
        ) { index, item ->
            BudgetCategoryRow(
                item = item,
                isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                isInspected = isWideScreen && item.category.id == expandedCategoryId,
                transactions = categoryTransactions[item.category.id] ?: emptyList(),
                onClick = { onToggleExpand(item.category.id) },
                onEditBudget = { onEditBudget(item) },
                onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                onViewTransactions = { onViewTransactions(item.category.id) },
                modifier = (if (index == unbudgeted.lastIndex) Modifier.padding(bottom = 8.dp) else Modifier)
                    .animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
            )
        }
    }

    if (inactive.isNotEmpty()) {
        item(key = "inactive_categories_section", contentType = "inactive_section") {
            InactiveBudgetCategoriesSection(
                inactive = inactive,
                expandedCategoryId = expandedCategoryId,
                categoryTransactions = categoryTransactions,
                onToggleExpand = onToggleExpand,
                onEditBudget = onEditBudget,
                onDeleteBudget = onDeleteBudget,
                onViewTransactions = onViewTransactions,
                isWideScreen = isWideScreen
            )
        }
    }
}

@Composable
private fun InactiveBudgetCategoriesSection(
    inactive: List<CategoryBudgetOverviewItem>,
    expandedCategoryId: Long?,
    categoryTransactions: Map<Long, List<Transaction>>,
    onToggleExpand: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onViewTransactions: (Long) -> Unit,
    isWideScreen: Boolean
) {
    val haptics = LocalOrbitHaptics.current
    var showInactive by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                haptics.tick()
                showInactive = !showInactive
            },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showInactive) {
                        "Hide ${inactive.size} inactive categories"
                    } else {
                        "Show ${inactive.size} categories with no activity"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                OrbitExpandableChevron(expanded = showInactive)
            }
        }

        OrbitAccordionVisibility(visible = showInactive) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                inactive.forEach { item ->
                    BudgetCategoryRow(
                        item = item,
                        isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                        isInspected = isWideScreen && item.category.id == expandedCategoryId,
                        transactions = categoryTransactions[item.category.id] ?: emptyList(),
                        onClick = { onToggleExpand(item.category.id) },
                        onEditBudget = { onEditBudget(item) },
                        onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                        onViewTransactions = { onViewTransactions(item.category.id) }
                    )
                }
            }
        }
    }
}

/**
 * Multi-segment colored horizontal strip showing each category's proportion
 * of total spent + top spending categories legend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendingMixPanel(
    items: List<CategoryBudgetOverviewItem>,
    modifier: Modifier = Modifier
) {
    val sorted = items.sortedByDescending { it.spentCents }
    val totalSpent = sorted.sumOf { it.spentCents }
    if (totalSpent <= 0L) return

    OrbitSectionCard(
        title = "SPENDING MIX",
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OrbitStackedSegmentBar(
                segments = sorted.map { item ->
                    OrbitBarSegment(
                        key = item.category.id,
                        fraction = (item.spentCents.toFloat() / totalSpent.toFloat()).coerceAtLeast(0.01f),
                        color = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)
                    )
                },
                height = 10.dp,
                normalizeToFullWidth = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sorted.take(5).forEach { item ->
                    val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
                    val color = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.category.labelWithEmoji,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$pct%",
                            style = TabularNumStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
