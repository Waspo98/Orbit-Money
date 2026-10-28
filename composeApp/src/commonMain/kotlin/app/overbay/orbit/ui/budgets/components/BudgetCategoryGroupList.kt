package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Categorized budget groups list with Spending Mix strip, sort chips,
 * and inline expandable category rows.
 * Conforms strictly to the < 400-line ceiling.
 */
@Composable
fun BudgetCategoryGroupList(
    budgeted: List<CategoryBudgetOverviewItem>,
    unbudgeted: List<CategoryBudgetOverviewItem>,
    inactive: List<CategoryBudgetOverviewItem>,
    activeSort: CategorySortOption,
    expandedCategoryId: Long?,
    categoryTransactions: Map<Long, List<Transaction>>,
    onSortSelected: (CategorySortOption) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onViewTransactions: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showInactive by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Spending Mix Panel
        val allActive = (budgeted + unbudgeted).filter { it.spentCents > 0L }
        if (allActive.isNotEmpty()) {
            SpendingMixPanel(items = allActive)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sort Option Filter Chips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortFilterChip(
                label = "Over Budget First",
                selected = activeSort == CategorySortOption.OVER_BUDGET_FIRST,
                onClick = { onSortSelected(CategorySortOption.OVER_BUDGET_FIRST) }
            )

            SortFilterChip(
                label = "Least Room Left",
                selected = activeSort == CategorySortOption.LEAST_ROOM_LEFT,
                onClick = { onSortSelected(CategorySortOption.LEAST_ROOM_LEFT) }
            )

            SortFilterChip(
                label = "Most Spent",
                selected = activeSort == CategorySortOption.MOST_SPENT,
                onClick = { onSortSelected(CategorySortOption.MOST_SPENT) }
            )

            SortFilterChip(
                label = "Name A-Z",
                selected = activeSort == CategorySortOption.NAME_A_TO_Z,
                onClick = { onSortSelected(CategorySortOption.NAME_A_TO_Z) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Budgeted Section
        if (budgeted.isNotEmpty()) {
            val totalBudgeted = budgeted.sumOf { it.amountCents }
            SectionHeader(
                title = "Budgeted Categories",
                subtitle = "${budgeted.size} categories • ${MoneyUtils.formatCents(totalBudgeted, showCents = false)}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            budgeted.forEach { item ->
                BudgetCategoryRow(
                    item = item,
                    isExpanded = item.category.id == expandedCategoryId,
                    transactions = categoryTransactions[item.category.id] ?: emptyList(),
                    onClick = { onToggleExpand(item.category.id) },
                    onEditBudget = { onEditBudget(item) },
                    onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                    onViewTransactions = { onViewTransactions(item.category.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Unbudgeted Spending Section
        if (unbudgeted.isNotEmpty()) {
            val totalUnbudgeted = unbudgeted.sumOf { it.spentCents }
            SectionHeader(
                title = "Unbudgeted Spending",
                subtitle = "${unbudgeted.size} categories • ${MoneyUtils.formatCents(totalUnbudgeted, showCents = false)}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            unbudgeted.forEach { item ->
                BudgetCategoryRow(
                    item = item,
                    isExpanded = item.category.id == expandedCategoryId,
                    transactions = categoryTransactions[item.category.id] ?: emptyList(),
                    onClick = { onToggleExpand(item.category.id) },
                    onEditBudget = { onEditBudget(item) },
                    onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                    onViewTransactions = { onViewTransactions(item.category.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Inactive Categories Section (collapsible)
        if (inactive.isNotEmpty()) {
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
                    Icon(
                        imageVector = if (showInactive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showInactive) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    inactive.forEach { item ->
                        BudgetCategoryRow(
                            item = item,
                            isExpanded = item.category.id == expandedCategoryId,
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
            // Multi-segment horizontal strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                sorted.forEach { item ->
                    val weight = (item.spentCents.toFloat() / totalSpent.toFloat()).coerceAtLeast(0.01f)
                    val color = parseHexColor(item.category.color) ?: MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend of top spending categories
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sorted.take(5).forEach { item ->
                    val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
                    val color = parseHexColor(item.category.color) ?: MaterialTheme.colorScheme.primary

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

@Composable
private fun SortFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
    )
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
