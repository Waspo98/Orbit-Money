package app.overbay.orbit.ui.trends.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.charts.ChartScrubberHeader
import app.overbay.orbit.ui.components.charts.DeltaTone
import app.overbay.orbit.ui.components.charts.SpendingBarItem
import app.overbay.orbit.ui.components.charts.SpendingTrendsBarChart
import app.overbay.orbit.ui.trends.SpendingTrendsUiState
import app.overbay.orbit.ui.trends.TrendsChartMode
import app.overbay.orbit.ui.trends.TrendsPeriod
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsChartSection(
    state: SpendingTrendsUiState,
    onSelectPeriod: (TrendsPeriod) -> Unit,
    onSelectChartMode: (TrendsChartMode) -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onScrubBar: (Int?, SpendingBarItem?) -> Unit,
    onInspectMonth: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isCategoryMode = state.chartMode == TrendsChartMode.CATEGORY_TREND
    val selectedTrend = state.selectedCategoryTrend
    val isScrubbing = state.scrubbedBarItem != null
    val scrubItem = state.scrubbedBarItem

    val barItems = remember(state.overview, state.chartMode, state.selectedCategoryId) {
        if (isCategoryMode) {
            selectedTrend?.monthlySeries?.map { pt ->
                SpendingBarItem(
                    label = formatMonthLabel(pt.month),
                    expenseCents = pt.spentCents,
                    budgetCents = selectedTrend.budgetAmountCents
                )
            } ?: emptyList()
        } else {
            state.overview.flowSeries.map { flow ->
                SpendingBarItem(
                    label = formatMonthLabel(flow.month),
                    expenseCents = flow.expensesCents,
                    incomeCents = flow.incomeCents
                )
            }
        }
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
            val headerTitle = if (isCategoryMode) {
                selectedTrend?.category?.name ?: "Category Trend"
            } else {
                "Income vs Expenses Flow"
            }

            val headerValue = if (scrubItem != null) {
                if (isCategoryMode) {
                    MoneyUtils.formatCents(scrubItem.expenseCents)
                } else {
                    val net = (scrubItem.incomeCents ?: 0L) - scrubItem.expenseCents
                    (if (net > 0L) "+" else "") + MoneyUtils.formatCents(net)
                }
            } else {
                if (isCategoryMode) {
                    MoneyUtils.formatCents(selectedTrend?.totalSpentCents ?: 0L)
                } else {
                    (if (state.totalPeriodNetCents > 0L) "+" else "") + MoneyUtils.formatCents(state.totalPeriodNetCents)
                }
            }

            val deltaText: String? = if (scrubItem != null) {
                if (isCategoryMode && scrubItem.budgetCents != null && scrubItem.budgetCents > 0L) {
                    val diff = scrubItem.expenseCents - scrubItem.budgetCents
                    if (diff > 0) "Over budget by ${MoneyUtils.formatCents(diff)}"
                    else "Under budget by ${MoneyUtils.formatCents(abs(diff))}"
                } else if (!isCategoryMode && scrubItem.incomeCents != null) {
                    "Inc ${MoneyUtils.formatCents(scrubItem.incomeCents)} • Exp ${MoneyUtils.formatCents(scrubItem.expenseCents)}"
                } else null
            } else {
                if (isCategoryMode) {
                    "${MoneyUtils.formatCents(selectedTrend?.averageSpentCents ?: 0L)} / mo avg"
                } else {
                    "${state.overallSavingsRatePercent.toInt()}% savings rate"
                }
            }

            val deltaTone = if (isCategoryMode) {
                if (scrubItem?.budgetCents != null) {
                    if (scrubItem.expenseCents > scrubItem.budgetCents) DeltaTone.NEGATIVE else DeltaTone.POSITIVE
                } else DeltaTone.NEUTRAL
            } else {
                if (state.totalPeriodNetCents >= 0L) DeltaTone.POSITIVE else DeltaTone.NEGATIVE
            }

            ChartScrubberHeader(
                title = headerTitle,
                formattedValue = headerValue,
                deltaText = deltaText,
                deltaTone = deltaTone,
                isScrubbing = isScrubbing,
                scrubDateText = scrubItem?.label
            )

            val scrubbedMonth = state.scrubbedIndex?.let { idx ->
                if (isCategoryMode) {
                    selectedTrend?.monthlySeries?.getOrNull(idx)?.month
                } else {
                    state.overview.flowSeries.getOrNull(idx)?.month
                }
            }

            if (scrubbedMonth != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable {
                            haptics.tick()
                            onInspectMonth(scrubbedMonth)
                        }
                    ) {
                        Text(
                            text = "Inspect Month ($scrubbedMonth)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector Pills & Category Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendsChartMode.entries.forEach { mode ->
                        val isSelected = mode == state.chartMode
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                haptics.tick()
                                onSelectChartMode(mode)
                            }
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (isCategoryMode && selectedTrend != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(selectedTrend.category.color.toComposeColor())
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedTrend.category.labelWithEmoji,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    onSelectChartMode(TrendsChartMode.CASH_FLOW)
                                },
                                modifier = Modifier.size(18.dp).padding(start = 4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizon Range Tabs (1M, 3M, 6M, 1Y, ALL)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TrendsPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = period == state.selectedPeriod,
                        onClick = {
                            haptics.tick()
                            onSelectPeriod(period)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TrendsPeriod.entries.size
                        ),
                        icon = {},
                        label = { Text(period.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bar Chart Canvas
            if (barItems.isNotEmpty()) {
                SpendingTrendsBarChart(
                    items = barItems,
                    modifier = Modifier.fillMaxWidth(),
                    height = 200.dp,
                    expenseColor = OrbitColorTokens.Red,
                    incomeColor = OrbitColorTokens.Green,
                    onItemSelected = onScrubBar
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transaction data for this time horizon",
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
