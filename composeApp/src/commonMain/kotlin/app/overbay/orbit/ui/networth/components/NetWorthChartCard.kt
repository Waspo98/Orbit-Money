package app.overbay.orbit.ui.networth.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.charts.NetWorthAreaChart
import app.overbay.orbit.ui.networth.NetWorthPeriod
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun NetWorthChartCard(
    points: List<MonthlyNetWorthPoint>,
    selectedPeriod: NetWorthPeriod,
    scrubbedPoint: MonthlyNetWorthPoint?,
    onSelectPeriod: (NetWorthPeriod) -> Unit,
    onPointScrubbed: (MonthlyNetWorthPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val activePoint = scrubbedPoint ?: points.lastOrNull()

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
            // Header: Scrub Info or Current Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (scrubbedPoint != null) "Month: ${scrubbedPoint.month}" else "Net Worth History",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val amount = activePoint?.snapshot?.netWorthCents ?: 0L
                    Text(
                        text = MoneyUtils.formatCents(amount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (amount >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
                    )
                }

                activePoint?.let { pt ->
                    if (pt.deltaFromPreviousCents != 0L) {
                        val isPositive = pt.deltaFromPreviousCents > 0L
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPositive) OrbitColorTokens.Green.copy(alpha = 0.15f)
                            else OrbitColorTokens.Red.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = (if (isPositive) "+" else "") + MoneyUtils.formatCents(pt.deltaFromPreviousCents),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPositive) OrbitColorTokens.Green else OrbitColorTokens.Red,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizon Tabs (1M, 3M, 6M, 1Y, ALL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NetWorthPeriod.entries.forEach { period ->
                    val isSelected = period == selectedPeriod
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) null
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptics.tick()
                                onSelectPeriod(period)
                            }
                    ) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Area Chart Canvas
            if (points.isNotEmpty()) {
                NetWorthAreaChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth(),
                    height = 200.dp,
                    onPointSelected = onPointScrubbed
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Not enough historical data to chart",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
