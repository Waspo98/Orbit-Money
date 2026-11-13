package app.overbay.orbit.ui.networth.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.charts.NetWorthAreaChart
import app.overbay.orbit.ui.networth.NetWorthPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthChartCard(
    points: List<MonthlyNetWorthPoint>,
    selectedPeriod: NetWorthPeriod,
    scrubbedPoint: MonthlyNetWorthPoint?,
    onSelectPeriod: (NetWorthPeriod) -> Unit,
    onPointScrubbed: (MonthlyNetWorthPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
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
                        text = if (scrubbedPoint != null) "Month: ${LocalDateUtils.formatMonthShortLabel(scrubbedPoint.month)}" else "Net Worth History",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val amount = activePoint?.snapshot?.netWorthCents ?: 0L
                    val amountColor by animateColorAsState(
                        targetValue = if (amount >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red,
                        animationSpec = OrbitSprings.ColorEffectsFast,
                        label = "netWorthAmountColor"
                    )
                    RollingTickerCents(
                        cents = amount,
                        animate = scrubbedPoint == null,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = amountColor
                    )
                }

                val currentDelta = activePoint?.deltaFromPreviousCents ?: 0L
                var lastNonZeroDelta by remember { mutableLongStateOf(0L) }
                if (currentDelta != 0L) lastNonZeroDelta = currentDelta
                val displayDelta = if (currentDelta != 0L) currentDelta else lastNonZeroDelta
                val isPositive = displayDelta > 0L
                val deltaBg by animateColorAsState(
                    targetValue = if (isPositive) OrbitColorTokens.Green.copy(alpha = 0.15f)
                    else OrbitColorTokens.Red.copy(alpha = 0.15f),
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "netWorthDeltaBg"
                )
                val deltaTextColor by animateColorAsState(
                    targetValue = if (isPositive) OrbitColorTokens.Green else OrbitColorTokens.Red,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "netWorthDeltaText"
                )
                OrbitScaleBadgeVisibility(visible = currentDelta != 0L) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = deltaBg
                    ) {
                        RollingTickerCents(
                            cents = displayDelta,
                            showSign = true,
                            animate = scrubbedPoint == null,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = deltaTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
