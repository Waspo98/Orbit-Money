package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.MonthlyBudgetOverview
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.RollingTickerText
import kotlin.math.abs

/**
 * Budgeted-first Snapshot panel showing total budgeted spending against the budgeted cap,
 * a progress bar with optional today-pace marker, and a 3-column inline metric strip
 * (Daily Pace, Unbudgeted, Cash Flow).
 */
@Composable
fun BudgetSummaryHeroCard(
    overview: MonthlyBudgetOverview?,
    monthLabel: String,
    daysRemaining: Int,
    monthProgressFraction: Float? = null,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    val totalBudgeted = overview?.totalBudgetedCents ?: 0L
    val spentInBudget = overview?.totalSpentInBudgetCents ?: 0L
    val spentUnbudgeted = overview?.totalSpentUnbudgetedCents ?: 0L
    val unbudgetedCount = overview?.unbudgeted?.size ?: 0
    val totalIncome = overview?.totalIncomeCents ?: 0L
    val netCashflow = overview?.netSavingsCents ?: 0L
    val budgetedPace = overview?.budgetedDailySafeSpend

    val isOverBudget = totalBudgeted > 0L && spentInBudget > totalBudgeted
    val utilization = if (totalBudgeted > 0L) spentInBudget.toFloat() / totalBudgeted.toFloat() else 0f
    val budgetedRemaining = totalBudgeted - spentInBudget

    OrbitSectionCard(
        title = "${monthLabel.uppercase()} BUDGETS",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero Budgeted Spent Figure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    RollingTickerCents(
                        cents = spentInBudget,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (totalBudgeted > 0L) {
                            "of ${MoneyUtils.formatCents(totalBudgeted)} budgeted"
                        } else {
                            "No category budgets set"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (totalBudgeted > 0L) {
                    val remainingText = if (isOverBudget) {
                        "${MoneyUtils.formatCents(abs(budgetedRemaining))} over"
                    } else {
                        "${MoneyUtils.formatCents(budgetedRemaining)} left"
                    }
                    val remainingColor by animateColorAsState(
                        targetValue = if (isOverBudget) colors.danger else colors.income,
                        animationSpec = OrbitSprings.ColorEffectsFast,
                        label = "budgetRemainingColor"
                    )
                    RollingTickerText(
                        text = remainingText,
                        style = TabularNumStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = remainingColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OrbitPillProgressBar(
                percent = utilization,
                overBudget = isOverBudget,
                height = 12.dp,
                paceMarkerFraction = monthProgressFraction
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // 3-Column Inline Metrics Strip (no nested boxes)
            val paceValue = when {
                totalBudgeted <= 0L -> "—"
                budgetedPace?.tone == "over" -> "$0/d"
                budgetedPace?.dailyCents != null -> "${MoneyUtils.formatCents(budgetedPace.dailyCents, showCents = false)}/d"
                else -> "—"
            }
            val paceSub = when {
                totalBudgeted <= 0L -> "Add budgets"
                budgetedPace?.detail?.isNotBlank() == true && budgetedPace.daysRemaining == 0 -> budgetedPace.detail
                else -> "$daysRemaining days left"
            }

            val unbudgetedValue = MoneyUtils.formatCents(spentUnbudgeted, signed = spentUnbudgeted < 0L)
            val unbudgetedSub = if (unbudgetedCount == 1) "1 category" else "$unbudgetedCount categories"

            val cashflowValue = MoneyUtils.formatCents(netCashflow, signed = true)
            val cashflowSub = "${MoneyUtils.formatCents(totalIncome, showCents = false)} income"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InlineHeroMetric(
                    label = "DAILY PACE",
                    value = paceValue,
                    subText = paceSub,
                    valueColor = if (budgetedPace?.tone == "over") colors.danger else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    color = colors.cardBorder,
                    modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp)
                )
                InlineHeroMetric(
                    label = "UNBUDGETED",
                    value = unbudgetedValue,
                    subText = unbudgetedSub,
                    valueColor = if (spentUnbudgeted < 0L) colors.income else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    color = colors.cardBorder,
                    modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp)
                )
                InlineHeroMetric(
                    label = "CASH FLOW",
                    value = cashflowValue,
                    subText = cashflowSub,
                    valueColor = if (netCashflow >= 0L) colors.income else colors.danger,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineHeroMetric(
    label: String,
    value: String,
    subText: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = TabularNumStyle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subText,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
