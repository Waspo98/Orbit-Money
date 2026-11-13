package app.overbay.orbit.ui.trends.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.CategorySpendingTrend
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.UNCATEGORIZED_CATEGORY_ID
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.ui.components.charts.ChartScrubberHeader
import app.overbay.orbit.ui.components.charts.DeltaTone
import app.overbay.orbit.ui.components.charts.SpendingBarItem
import app.overbay.orbit.ui.components.charts.SpendingTrendsBarChart
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
fun CategoryHistoryCard(
    categoryTrends: List<CategorySpendingTrend>,
    selectedTrend: CategorySpendingTrend?,
    onSelectCategory: (Long) -> Unit,
    onOpenBudgetModal: () -> Unit,
    scrubbedBarItem: SpendingBarItem?,
    scrubbedIndex: Int?,
    onScrubBar: (Int?, SpendingBarItem?) -> Unit,
    onInspectMonth: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isScrubbing = scrubbedBarItem != null

    val barItems = remember(selectedTrend) {
        selectedTrend?.monthlySeries?.map { pt ->
            SpendingBarItem(
                label = formatMonthLabel(pt.month),
                expenseCents = pt.spentCents,
                budgetCents = selectedTrend.budgetAmountCents
            )
        } ?: emptyList()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header with Category Dropdown & Edit Budget Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val canEditBudget = selectedTrend != null && selectedTrend.category.id != UNCATEGORIZED_CATEGORY_ID
                var lastBudgetLabel by remember { mutableStateOf("Add Budget") }
                if (selectedTrend != null) {
                    lastBudgetLabel = if (selectedTrend.budgetAmountCents != null) "Edit Budget" else "Add Budget"
                }
                OrbitScaleBadgeVisibility(visible = canEditBudget) {
                    Button(
                        onClick = {
                            haptics.tick()
                            onOpenBudgetModal()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        AnimatedContent(
                            targetState = lastBudgetLabel,
                            transitionSpec = { orbitFadeContentTransform() },
                            label = "categoryHistoryBudgetLabel"
                        ) { label ->
                            Text(text = label)
                        }
                    }
                }
            }

            if (categoryTrends.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                OrbitDropdownField(
                    label = null,
                    selectedText = selectedTrend?.category?.name ?: "Select Category",
                    options = categoryTrends,
                    optionLabel = { it.category.name },
                    onOptionSelected = { onSelectCategory(it.category.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTrend != null) {
                // 4-Tile Category Stats Card
                CategoryTrendStatsCard(
                    trend = selectedTrend,
                    onOpenBudgetModal = onOpenBudgetModal
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrubber Header
                val headerValue = if (scrubbedBarItem != null) {
                    MoneyUtils.formatCents(scrubbedBarItem.expenseCents)
                } else {
                    MoneyUtils.formatCents(selectedTrend.totalSpentCents)
                }

                val deltaText = if (scrubbedBarItem != null) {
                    if (scrubbedBarItem.budgetCents != null && scrubbedBarItem.budgetCents > 0L) {
                        val diff = scrubbedBarItem.expenseCents - scrubbedBarItem.budgetCents
                        if (diff > 0) "Over budget by ${MoneyUtils.formatCents(diff)}"
                        else "Under budget by ${MoneyUtils.formatCents(abs(diff))}"
                    } else null
                } else {
                    "${MoneyUtils.formatCents(selectedTrend.averageSpentCents)} / mo avg"
                }

                val deltaTone = if (scrubbedBarItem?.budgetCents != null) {
                    if (scrubbedBarItem.expenseCents > scrubbedBarItem.budgetCents) DeltaTone.NEGATIVE else DeltaTone.POSITIVE
                } else DeltaTone.NEUTRAL

                ChartScrubberHeader(
                    title = selectedTrend.category.name,
                    formattedValue = headerValue,
                    deltaText = deltaText,
                    deltaTone = deltaTone,
                    isScrubbing = isScrubbing,
                    scrubDateText = scrubbedBarItem?.label
                )

                val scrubbedMonth = scrubbedIndex?.let { idx ->
                    selectedTrend.monthlySeries.getOrNull(idx)?.month
                }
                var lastInspectMonth by remember { mutableStateOf("") }
                if (scrubbedMonth != null) lastInspectMonth = scrubbedMonth

                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    OrbitScaleBadgeVisibility(visible = scrubbedMonth != null) {
                        val monthToInspect = scrubbedMonth ?: lastInspectMonth
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable {
                                haptics.tick()
                                if (monthToInspect.isNotEmpty()) onInspectMonth(monthToInspect)
                            }
                        ) {
                            Text(
                                text = "Inspect Month ($monthToInspect)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Single-Bar Category Chart Canvas
                if (barItems.isNotEmpty()) {
                    SpendingTrendsBarChart(
                        items = barItems,
                        modifier = Modifier.fillMaxWidth(),
                        height = 200.dp,
                        barColor = selectedTrend.category.color.toComposeColor(),
                        expenseColor = OrbitColorTokens.Red,
                        onItemSelected = onScrubBar
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Breakdown Ranking List
                CategoryBreakdownList(
                    categoryTrends = categoryTrends,
                    selectedCategoryId = selectedTrend.category.id,
                    onSelectCategory = onSelectCategory
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No spending categories in this range",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun formatMonthLabel(yearMonth: String): String {
    val monthPart = yearMonth.split("-").getOrNull(1) ?: return yearMonth
    return when (monthPart) {
        "01" -> "Jan"
        "02" -> "Feb"
        "03" -> "Mar"
        "04" -> "Apr"
        "05" -> "May"
        "06" -> "Jun"
        "07" -> "Jul"
        "08" -> "Aug"
        "09" -> "Sep"
        "10" -> "Oct"
        "11" -> "Nov"
        "12" -> "Dec"
        else -> monthPart
    }
}
