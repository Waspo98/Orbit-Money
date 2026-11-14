package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitExpandableChevron
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Lazy-list extension emitting Budgeted Categories (unified grouped card), collapsed-by-default
 * Unbudgeted Spending, collapsed-by-default Inactive categories, and Spending Mix at the bottom.
 */
fun LazyListScope.budgetCategoryGroupItems(
    budgeted: List<CategoryBudgetOverviewItem>,
    unbudgeted: List<CategoryBudgetOverviewItem>,
    inactive: List<CategoryBudgetOverviewItem>,
    expandedCategoryId: Long?,
    categoryTransactions: Map<Long, List<Transaction>>,
    showUnbudgeted: Boolean,
    showInactive: Boolean,
    onToggleUnbudgeted: () -> Unit,
    onToggleInactive: () -> Unit,
    onToggleExpand: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onViewTransactions: (Long) -> Unit,
    monthProgressFraction: Float? = null,
    isWideScreen: Boolean = false
) {
    if (budgeted.isNotEmpty()) {
        val totalBudgeted = budgeted.sumOf { it.amountCents }
        item(key = "budgeted_categories_card", contentType = "budgeted_card") {
            OrbitSectionCard(
                title = "Budgeted Categories",
                subtitle = "${budgeted.size} ${if (budgeted.size == 1) "category" else "categories"} • ${MoneyUtils.formatCents(totalBudgeted, showCents = false)}",
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    budgeted.forEachIndexed { index, item ->
                        key(item.category.id) {
                            BudgetCategoryRow(
                                item = item,
                                isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                                isInspected = isWideScreen && item.category.id == expandedCategoryId,
                                transactions = categoryTransactions[item.category.id] ?: emptyList(),
                                onClick = { onToggleExpand(item.category.id) },
                                onEditBudget = { onEditBudget(item) },
                                onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                                onViewTransactions = { onViewTransactions(item.category.id) },
                                showTopDivider = index > 0,
                                monthProgressFraction = monthProgressFraction
                            )
                        }
                    }
                }
            }
        }
    }

    if (unbudgeted.isNotEmpty()) {
        item(key = "unbudgeted_categories_section", contentType = "unbudgeted_section") {
            UnbudgetedCategoriesSection(
                unbudgeted = unbudgeted,
                expanded = showUnbudgeted,
                onToggleSection = onToggleUnbudgeted,
                expandedCategoryId = expandedCategoryId,
                categoryTransactions = categoryTransactions,
                onToggleExpand = onToggleExpand,
                onEditBudget = onEditBudget,
                onViewTransactions = onViewTransactions,
                isWideScreen = isWideScreen,
                modifier = Modifier.animateItem(
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
                expanded = showInactive,
                onToggleSection = onToggleInactive,
                expandedCategoryId = expandedCategoryId,
                categoryTransactions = categoryTransactions,
                onToggleExpand = onToggleExpand,
                onEditBudget = onEditBudget,
                onDeleteBudget = onDeleteBudget,
                onViewTransactions = onViewTransactions,
                monthProgressFraction = monthProgressFraction,
                isWideScreen = isWideScreen,
                modifier = Modifier.animateItem(
                    fadeInSpec = OrbitSprings.FloatEffectsDefault,
                    placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                    fadeOutSpec = OrbitSprings.FloatEffectsFast
                )
            )
        }
    }

    val allActive = (budgeted + unbudgeted).filter { it.spentCents > 0L }
    if (allActive.isNotEmpty()) {
        item(key = "budget_spending_mix", contentType = "spending_mix") {
            SpendingMixPanel(
                items = allActive,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
            )
        }
    }
}

@Composable
private fun UnbudgetedCategoriesSection(
    unbudgeted: List<CategoryBudgetOverviewItem>,
    expanded: Boolean,
    onToggleSection: () -> Unit,
    expandedCategoryId: Long?,
    categoryTransactions: Map<Long, List<Transaction>>,
    onToggleExpand: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit,
    isWideScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val totalUnbudgeted = unbudgeted.sumOf { it.spentCents }
    val totalLabel = MoneyUtils.formatCents(totalUnbudgeted, signed = totalUnbudgeted < 0L)

    Column(modifier = modifier.fillMaxWidth()) {
        AccordionToggleButton(
            title = "Unbudgeted Spending (${unbudgeted.size})",
            trailingValue = totalLabel,
            expanded = expanded,
            onClick = {
                haptics.tick()
                onToggleSection()
            }
        )

        OrbitAccordionVisibility(visible = expanded) {
            OrbitSectionCard(
                showHeaderDivider = false,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    unbudgeted.forEachIndexed { index, item ->
                        key(item.category.id) {
                            UnbudgetedCategoryRow(
                                item = item,
                                isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                                isInspected = isWideScreen && item.category.id == expandedCategoryId,
                                transactions = categoryTransactions[item.category.id] ?: emptyList(),
                                onClick = { onToggleExpand(item.category.id) },
                                onSetBudget = { onEditBudget(item) },
                                onViewTransactions = { onViewTransactions(item.category.id) },
                                showTopDivider = index > 0
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InactiveBudgetCategoriesSection(
    inactive: List<CategoryBudgetOverviewItem>,
    expanded: Boolean,
    onToggleSection: () -> Unit,
    expandedCategoryId: Long?,
    categoryTransactions: Map<Long, List<Transaction>>,
    onToggleExpand: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onViewTransactions: (Long) -> Unit,
    monthProgressFraction: Float?,
    isWideScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth()) {
        AccordionToggleButton(
            title = if (expanded) {
                "Hide ${inactive.size} inactive categories"
            } else {
                "Show ${inactive.size} categories with no activity"
            },
            trailingValue = null,
            expanded = expanded,
            onClick = {
                haptics.tick()
                onToggleSection()
            }
        )

        OrbitAccordionVisibility(visible = expanded) {
            OrbitSectionCard(
                showHeaderDivider = false,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    inactive.forEachIndexed { index, item ->
                        key(item.category.id) {
                            if (item.amountCents > 0L) {
                                BudgetCategoryRow(
                                    item = item,
                                    isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                                    isInspected = isWideScreen && item.category.id == expandedCategoryId,
                                    transactions = categoryTransactions[item.category.id] ?: emptyList(),
                                    onClick = { onToggleExpand(item.category.id) },
                                    onEditBudget = { onEditBudget(item) },
                                    onDeleteBudget = { item.budgetId?.let(onDeleteBudget) },
                                    onViewTransactions = { onViewTransactions(item.category.id) },
                                    showTopDivider = index > 0,
                                    monthProgressFraction = monthProgressFraction
                                )
                            } else {
                                UnbudgetedCategoryRow(
                                    item = item,
                                    isExpanded = !isWideScreen && item.category.id == expandedCategoryId,
                                    isInspected = isWideScreen && item.category.id == expandedCategoryId,
                                    transactions = categoryTransactions[item.category.id] ?: emptyList(),
                                    onClick = { onToggleExpand(item.category.id) },
                                    onSetBudget = { onEditBudget(item) },
                                    onViewTransactions = { onViewTransactions(item.category.id) },
                                    showTopDivider = index > 0
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionToggleButton(
    title: String,
    trailingValue: String?,
    expanded: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (trailingValue != null) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingValue != null) {
                    Text(
                        text = trailingValue,
                        style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                OrbitExpandableChevron(expanded = expanded)
            }
        }
    }
}
