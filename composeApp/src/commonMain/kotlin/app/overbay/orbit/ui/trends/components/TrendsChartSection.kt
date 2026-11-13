package app.overbay.orbit.ui.trends.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.components.charts.ChartScrubberHeader
import app.overbay.orbit.ui.components.charts.DeltaTone
import app.overbay.orbit.ui.components.charts.SpendingBarItem
import app.overbay.orbit.ui.components.charts.SpendingTrendsBarChart
import app.overbay.orbit.ui.trends.SpendingTrendsUiState
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun TrendsChartSection(
    state: SpendingTrendsUiState,
    onScrubBar: (Int?, SpendingBarItem?) -> Unit,
    modifier: Modifier = Modifier,
    onInspectMonth: (String) -> Unit = {}
) {
    val haptics = LocalOrbitHaptics.current
    val scrubItem = state.flowScrubItem ?: state.scrubbedBarItem
    val scrubIndex = state.flowScrubIndex ?: state.scrubbedIndex
    val isScrubbing = scrubItem != null

    val barItems = remember(state.overview.flowSeries) {
        state.overview.flowSeries.map { flow ->
            SpendingBarItem(
                label = formatMonthLabel(flow.month),
                expenseCents = flow.expensesCents,
                incomeCents = flow.incomeCents
            )
        }
    }

    val flow = state.overview.flowSeries
    val dateRangeText = remember(flow) {
        if (flow.isNotEmpty()) {
            val first = LocalDateUtils.formatMonthShortLabel(flow.first().month)
            val last = LocalDateUtils.formatMonthShortLabel(flow.last().month)
            "$first – $last"
        } else ""
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
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Income Vs Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (dateRangeText.isNotEmpty()) {
                        Text(
                            text = dateRangeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val netCents = state.totalPeriodNetCents
                val netColor = if (netCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
                Text(
                    text = (if (netCents > 0L) "+" else "") + MoneyUtils.formatCents(netCents),
                    style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
                    color = netColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrubber Header
            val headerValue = if (scrubItem != null) {
                val net = (scrubItem.incomeCents ?: 0L) - scrubItem.expenseCents
                (if (net > 0L) "+" else "") + MoneyUtils.formatCents(net)
            } else {
                (if (state.totalPeriodNetCents > 0L) "+" else "") + MoneyUtils.formatCents(state.totalPeriodNetCents)
            }

            val deltaText = if (scrubItem != null && scrubItem.incomeCents != null) {
                "Inc ${MoneyUtils.formatCents(scrubItem.incomeCents)} • Exp ${MoneyUtils.formatCents(scrubItem.expenseCents)}"
            } else {
                "${state.overallSavingsRatePercent.toInt()}% savings rate"
            }

            val deltaTone = if (scrubItem != null) {
                val net = (scrubItem.incomeCents ?: 0L) - scrubItem.expenseCents
                if (net >= 0L) DeltaTone.POSITIVE else DeltaTone.NEGATIVE
            } else {
                if (state.totalPeriodNetCents >= 0L) DeltaTone.POSITIVE else DeltaTone.NEGATIVE
            }

            ChartScrubberHeader(
                title = "Monthly Cash Flow",
                formattedValue = headerValue,
                deltaText = deltaText,
                deltaTone = deltaTone,
                isScrubbing = isScrubbing,
                scrubDateText = scrubItem?.label
            )

            val scrubbedMonth = scrubIndex?.let { idx ->
                state.overview.flowSeries.getOrNull(idx)?.month
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bar Chart Canvas (Dual-bar mode)
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
