package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.budgets.SpendingMixMode
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import kotlin.math.roundToInt

private const val OTHER_SLICE_KEY = "other"

internal data class SpendingMixSlice(
    val key: String,
    val label: String,
    val centerLabel: String,
    val spentCents: Cents,
    val percentInt: Int,
    val color: Color,
    val item: CategoryBudgetOverviewItem? = null,
    val otherItems: List<CategoryBudgetOverviewItem> = emptyList()
)

/**
 * Interactive Spending Mix card offering both a tappable Category Donut breakdown
 * and a Budget Coverage lens showing how much spend falls inside category budgets.
 */
@Composable
internal fun SpendingMixPanel(
    items: List<CategoryBudgetOverviewItem>,
    totalBudgetedCents: Cents,
    mode: SpendingMixMode,
    selectedKey: String?,
    onSelectMode: (SpendingMixMode) -> Unit,
    onToggleSlice: (String) -> Unit,
    onClearSelection: () -> Unit,
    onInspectCategory: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sorted = items.sortedByDescending { it.spentCents }
    val totalSpent = sorted.sumOf { it.spentCents }
    if (totalSpent <= 0L) return

    val topItems = sorted.take(5)
    val otherItems = sorted.drop(5)
    val otherSpent = otherItems.sumOf { it.spentCents }
    val otherColor = MaterialTheme.colorScheme.outlineVariant

    val slices = buildList {
        topItems.forEach { item ->
            val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            add(
                SpendingMixSlice(
                    key = "cat:${item.category.id}",
                    label = item.category.labelWithEmoji,
                    centerLabel = item.category.displayEmoji ?: item.category.name,
                    spentCents = item.spentCents,
                    percentInt = pct,
                    color = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary),
                    item = item
                )
            )
        }
        if (otherSpent > 0L) {
            val pct = ((otherSpent.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            add(
                SpendingMixSlice(
                    key = OTHER_SLICE_KEY,
                    label = "Other (${otherItems.size})",
                    centerLabel = "Other (${otherItems.size})",
                    spentCents = otherSpent,
                    percentInt = pct,
                    color = otherColor,
                    otherItems = otherItems
                )
            )
        }
    }

    OrbitSectionCard(
        title = "SPENDING MIX",
        subtitle = "${MoneyUtils.formatCents(totalSpent, showCents = false)} total spent",
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitSegmentedTabRow(
                items = listOf(SpendingMixMode.BY_CATEGORY, SpendingMixMode.COVERAGE),
                selected = mode,
                onSelect = onSelectMode,
                label = { tab ->
                    when (tab) {
                        SpendingMixMode.BY_CATEGORY -> "By Category"
                        SpendingMixMode.COVERAGE -> "Coverage"
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { orbitFadeContentTransform() },
                label = "spendingMixTabContent"
            ) { currentMode ->
                when (currentMode) {
                    SpendingMixMode.BY_CATEGORY -> {
                        val activeSlice = slices.find { it.key == selectedKey }
                        SpendingMixCategoryTab(
                            slices = slices,
                            totalSpent = totalSpent,
                            totalCategoryCount = sorted.size,
                            activeSlice = activeSlice,
                            onToggleSlice = onToggleSlice,
                            onClearSelection = onClearSelection,
                            onInspectCategory = onInspectCategory,
                            onEditBudget = onEditBudget,
                            onViewTransactions = onViewTransactions
                        )
                    }
                    SpendingMixMode.COVERAGE -> {
                        SpendingMixCoverageTab(
                            items = sorted,
                            totalBudgetedCents = totalBudgetedCents,
                            onEditBudget = onEditBudget,
                            onViewTransactions = onViewTransactions
                        )
                    }
                }
            }
        }
    }
}
