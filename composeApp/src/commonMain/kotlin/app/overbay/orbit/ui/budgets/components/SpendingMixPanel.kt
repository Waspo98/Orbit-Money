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
import app.overbay.orbit.data.model.MerchantBudgetMixItem
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
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
    val centerTitle: String,
    val spentCents: Cents,
    val percentInt: Int,
    val color: Color,
    val categoryItem: CategoryBudgetOverviewItem? = null,
    val merchantItem: MerchantBudgetMixItem? = null,
    val otherCategories: List<CategoryBudgetOverviewItem> = emptyList(),
    val otherMerchants: List<MerchantBudgetMixItem> = emptyList()
)

/**
 * Interactive Spending Mix card offering switchable Category and Merchant Donut breakdowns
 * with live center readouts, tappable legend pills, and direct inspect/budget/transaction actions.
 */
@Composable
internal fun SpendingMixPanel(
    items: List<CategoryBudgetOverviewItem>,
    merchants: List<MerchantBudgetMixItem>,
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
    val sortedCategories = items.sortedByDescending { it.spentCents }
    val categoryTotal = sortedCategories.sumOf { it.spentCents }
    if (categoryTotal <= 0L) return

    val sortedMerchants = merchants.filter { it.spentCents > 0L }.sortedByDescending { it.spentCents }
    val merchantTotal = sortedMerchants.sumOf { it.spentCents }.takeIf { it > 0L } ?: categoryTotal
    val otherColor = MaterialTheme.colorScheme.outlineVariant

    val merchantPalette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        OrbitTheme.colors.warning,
        MaterialTheme.colorScheme.secondary,
        OrbitTheme.colors.income
    )

    val categorySlices = buildCategorySlices(sortedCategories, categoryTotal, otherColor, MaterialTheme.colorScheme.primary)
    val merchantSlices = buildMerchantSlices(sortedMerchants, merchantTotal, merchantPalette, otherColor)
    val headerTotal = when (mode) {
        SpendingMixMode.CATEGORIES -> categoryTotal
        SpendingMixMode.MERCHANTS -> merchantTotal
    }

    OrbitSectionCard(
        title = "SPENDING MIX",
        subtitle = "${MoneyUtils.formatCents(headerTotal, showCents = false)} total spent",
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitSegmentedTabRow(
                items = listOf(SpendingMixMode.CATEGORIES, SpendingMixMode.MERCHANTS),
                selected = mode,
                onSelect = onSelectMode,
                label = { tab ->
                    when (tab) {
                        SpendingMixMode.CATEGORIES -> "Categories"
                        SpendingMixMode.MERCHANTS -> "Merchants"
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { orbitFadeContentTransform() },
                label = "spendingMixTabContent"
            ) { currentMode ->
                val slices = when (currentMode) {
                    SpendingMixMode.CATEGORIES -> categorySlices
                    SpendingMixMode.MERCHANTS -> merchantSlices
                }
                val activeTotal = when (currentMode) {
                    SpendingMixMode.CATEGORIES -> categoryTotal
                    SpendingMixMode.MERCHANTS -> merchantTotal
                }
                val entityLabel = when (currentMode) {
                    SpendingMixMode.CATEGORIES -> "${sortedCategories.size} active"
                    SpendingMixMode.MERCHANTS -> "${sortedMerchants.size} merchants"
                }
                val activeSlice = slices.find { it.key == selectedKey }

                SpendingMixCategoryTab(
                    slices = slices,
                    totalSpent = activeTotal,
                    entitySummary = entityLabel,
                    activeSlice = activeSlice,
                    onToggleSlice = onToggleSlice,
                    onClearSelection = onClearSelection,
                    onInspectCategory = onInspectCategory,
                    onEditBudget = onEditBudget,
                    onViewTransactions = onViewTransactions
                )
            }
        }
    }
}

private fun buildCategorySlices(
    sorted: List<CategoryBudgetOverviewItem>,
    totalSpent: Cents,
    otherColor: Color,
    fallbackColor: Color
): List<SpendingMixSlice> {
    val top = sorted.take(5)
    val rest = sorted.drop(5)
    val restSpent = rest.sumOf { it.spentCents }
    return buildList {
        top.forEach { item ->
            val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            add(
                SpendingMixSlice(
                    key = "cat:${item.category.id}",
                    label = item.category.labelWithEmoji,
                    centerTitle = item.category.displayEmoji?.let { "$it ${item.category.name}" } ?: item.category.name,
                    spentCents = item.spentCents,
                    percentInt = pct,
                    color = item.category.color.toComposeColor(fallbackColor),
                    categoryItem = item
                )
            )
        }
        if (restSpent > 0L) {
            val pct = ((restSpent.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            add(
                SpendingMixSlice(
                    key = OTHER_SLICE_KEY,
                    label = "Other (${rest.size})",
                    centerTitle = "Other (${rest.size})",
                    spentCents = restSpent,
                    percentInt = pct,
                    color = otherColor,
                    otherCategories = rest
                )
            )
        }
    }
}

private fun buildMerchantSlices(
    sorted: List<MerchantBudgetMixItem>,
    totalSpent: Cents,
    palette: List<Color>,
    otherColor: Color
): List<SpendingMixSlice> {
    val top = sorted.take(5)
    val rest = sorted.drop(5)
    val restSpent = rest.sumOf { it.spentCents }
    return buildList {
        top.forEachIndexed { index, item ->
            val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            add(
                SpendingMixSlice(
                    key = "merch:${item.merchant.lowercase()}",
                    label = item.merchant,
                    centerTitle = item.merchant,
                    spentCents = item.spentCents,
                    percentInt = pct,
                    color = palette[index % palette.size],
                    merchantItem = item
                )
            )
        }
        if (restSpent > 0L) {
            val pct = ((restSpent.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            add(
                SpendingMixSlice(
                    key = OTHER_SLICE_KEY,
                    label = "Other (${rest.size})",
                    centerTitle = "Other (${rest.size})",
                    spentCents = restSpent,
                    percentInt = pct,
                    color = otherColor,
                    otherMerchants = rest
                )
            )
        }
    }
}
