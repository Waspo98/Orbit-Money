package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.RollingTickerText
import kotlin.math.abs

/**
 * Total-spent-first Snapshot panel showing total monthly spending against the budgeted cap,
 * a two-segment Budgeted + Everything Else progress bar with optional today-pace marker,
 * and a 3-column inline metric strip (Daily Pace, This Time Last Month, Cash Flow).
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val totalSpent = overview?.totalSpentCents ?: 0L
    val totalIncome = overview?.totalIncomeCents ?: 0L
    val netCashflow = overview?.netSavingsCents ?: 0L
    val dailyPace = overview?.dailySafeSpend
    val lastMonthSpent = overview?.lastMonthMtdSpentCents ?: 0L
    val lastMonthLabel = overview?.lastMonthLabel.orEmpty()

    val isOverBudget = totalBudgeted > 0L && totalSpent > totalBudgeted
    val totalRemaining = totalBudgeted - totalSpent

    val budgetedSegmentColor by animateColorAsState(
        targetValue = if (isOverBudget) colors.danger else MaterialTheme.colorScheme.primary,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "heroBudgetedSegmentColor"
    )
    val everythingElseSegmentColor = MaterialTheme.colorScheme.tertiary

    OrbitSectionCard(
        title = "${monthLabel.uppercase()} BUDGETS",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero Total Spent Figure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    RollingTickerCents(
                        cents = totalSpent,
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
                        "${MoneyUtils.formatCents(abs(totalRemaining))} over"
                    } else {
                        "${MoneyUtils.formatCents(totalRemaining)} left"
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

            val positiveBudgeted = spentInBudget.coerceAtLeast(0L).toFloat()
            val positiveEverythingElse = spentUnbudgeted.coerceAtLeast(0L).toFloat()
            val positiveTotal = positiveBudgeted + positiveEverythingElse
            val fitsWithinCap = totalBudgeted > 0L && positiveTotal <= totalBudgeted.toFloat()

            val segments = if (fitsWithinCap) {
                listOf(
                    OrbitBarSegment("budgeted", positiveBudgeted / totalBudgeted.toFloat(), budgetedSegmentColor),
                    OrbitBarSegment("everything_else", positiveEverythingElse / totalBudgeted.toFloat(), everythingElseSegmentColor)
                )
            } else {
                listOf(
                    OrbitBarSegment("budgeted", positiveBudgeted, budgetedSegmentColor),
                    OrbitBarSegment("everything_else", positiveEverythingElse, everythingElseSegmentColor)
                )
            }

            val scaledPaceMarker = when {
                monthProgressFraction == null -> null
                totalBudgeted <= 0L -> monthProgressFraction
                fitsWithinCap -> monthProgressFraction
                positiveTotal > 0f -> (monthProgressFraction * (totalBudgeted.toFloat() / positiveTotal)).coerceIn(0.02f, 0.98f)
                else -> monthProgressFraction
            }

            OrbitStackedSegmentBar(
                segments = segments,
                height = 12.dp,
                normalizeToFullWidth = !fitsWithinCap && positiveTotal > 0f,
                paceMarkerFraction = scaledPaceMarker
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HeroSegmentLegendItem(
                    color = budgetedSegmentColor,
                    text = "${MoneyUtils.formatCents(spentInBudget, showCents = false)} budgeted"
                )
                HeroSegmentLegendItem(
                    color = everythingElseSegmentColor,
                    text = "${MoneyUtils.formatCents(spentUnbudgeted, signed = spentUnbudgeted < 0L, showCents = false)} Everything Else"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // 3-Column Inline Metrics Strip (no nested boxes)
            val paceValue = when {
                totalBudgeted <= 0L -> "—"
                dailyPace?.tone == "over" -> "$0/d"
                dailyPace?.dailyCents != null -> "${MoneyUtils.formatCents(dailyPace.dailyCents, showCents = false)}/d"
                else -> "—"
            }
            val paceSub = when {
                totalBudgeted <= 0L -> "Add budgets"
                dailyPace?.detail?.isNotBlank() == true && dailyPace.daysRemaining == 0 -> dailyPace.detail
                else -> "$daysRemaining days left"
            }

            val lastMonthTitle = if (monthProgressFraction != null) "THIS TIME LAST MO" else "LAST MONTH"
            val lastMonthValue = MoneyUtils.formatCents(lastMonthSpent, showCents = false)
            val lastMonthDelta = totalSpent - lastMonthSpent
            val lastMonthSub = when {
                lastMonthSpent == 0L && totalSpent == 0L -> if (lastMonthLabel.isNotBlank()) "No spend $lastMonthLabel" else "No prior spend"
                lastMonthDelta == 0L -> "Same as $lastMonthLabel"
                else -> "${MoneyUtils.formatCents(lastMonthDelta, signed = true, showCents = false)} vs this mo"
            }
            val lastMonthDeltaColor = when {
                lastMonthDelta < 0L -> colors.income
                lastMonthDelta > 0L -> colors.danger
                else -> MaterialTheme.colorScheme.outline
            }

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
                    valueColor = if (dailyPace?.tone == "over") colors.danger else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    color = colors.cardBorder,
                    modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp)
                )
                InlineHeroMetric(
                    label = lastMonthTitle,
                    value = lastMonthValue,
                    subText = lastMonthSub,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    subTextColor = lastMonthDeltaColor,
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
private fun HeroSegmentLegendItem(
    color: Color,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InlineHeroMetric(
    label: String,
    value: String,
    subText: String,
    valueColor: Color,
    subTextColor: Color = MaterialTheme.colorScheme.outline,
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
            style = TabularNumStyle.copy(fontSize = 11.sp),
            color = subTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
