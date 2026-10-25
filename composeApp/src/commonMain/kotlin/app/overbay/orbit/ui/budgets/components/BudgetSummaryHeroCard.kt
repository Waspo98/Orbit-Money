package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.MonthlyBudgetOverview
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.PeriodNavigatorPill
import app.overbay.orbit.ui.components.charts.BudgetRingGauge

@Composable
fun BudgetSummaryHeroCard(
    overview: MonthlyBudgetOverview?,
    monthLabel: String,
    canGoNextMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetToCurrentMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Capsule Month Navigator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Envelope Budget",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Monthly Spending Pacing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PeriodNavigatorPill(
                    label = monthLabel,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    canGoNext = canGoNextMonth,
                    onClickLabel = onResetToCurrentMonth
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center Ring Gauge
            val totalBudgeted = overview?.totalBudgetedCents ?: 0L
            val totalSpent = overview?.totalSpentCents ?: 0L
            val dailySafeSpend = overview?.dailySafeSpendCents
            val paceStatus = overview?.budgeted?.firstOrNull()?.paceStatus ?: app.overbay.orbit.data.model.BudgetPaceStatus.ON_TRACK

            BudgetRingGauge(
                spentCents = totalSpent,
                budgetCents = totalBudgeted,
                safeSpendDailyCents = dailySafeSpend,
                paceStatus = paceStatus,
                gaugeSize = 220.dp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Metrics Strip: Budgeted | Spent | Savings Rate
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricColumn(
                        label = "BUDGETED",
                        value = MoneyUtils.formatCents(totalBudgeted, showCents = false)
                    )

                    MetricColumn(
                        label = "SPENT",
                        value = MoneyUtils.formatCents(totalSpent, showCents = false),
                        valueColor = OrbitTheme.colors.expense
                    )

                    val savingsRate = overview?.savingsRatePercent ?: 0.0
                    val savingsColor = if (savingsRate >= 0.0) OrbitTheme.colors.income else OrbitTheme.colors.expense
                    MetricColumn(
                        label = "SAVINGS",
                        value = "${savingsRate.toInt()}%",
                        valueColor = savingsColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor
        )
    }
}
