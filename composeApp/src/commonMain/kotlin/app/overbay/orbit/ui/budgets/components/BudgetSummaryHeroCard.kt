package app.overbay.orbit.ui.budgets.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.MonthlyBudgetOverview
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.RollingTickerText
import kotlin.math.roundToInt

/**
 * Editorial Budget Snapshot panel matching original Orbit Money design.
 * Features 36sp hero spent figure, 12dp pill progress bar, remaining pace,
 * and a 2x2 grid of tiles (BUDGETED, UNBUDGETED, CASH FLOW, SPEND PACE).
 *
 */
@Composable
fun BudgetSummaryHeroCard(
    overview: MonthlyBudgetOverview?,
    monthLabel: String,
    daysRemaining: Int,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    val totalBudgeted = overview?.totalBudgetedCents ?: 0L
    val totalSpent = overview?.totalSpentCents ?: 0L
    val spentInBudget = overview?.totalSpentInBudgetCents ?: 0L
    val spentUnbudgeted = overview?.totalSpentUnbudgetedCents ?: 0L
    val totalIncome = overview?.totalIncomeCents ?: 0L
    val netCashflow = overview?.netSavingsCents ?: 0L
    val safeDailySpend = overview?.dailySafeSpendCents ?: 0L

    val isOverBudget = totalSpent > totalBudgeted && totalBudgeted > 0L
    val utilization = if (totalBudgeted > 0L) totalSpent.toFloat() / totalBudgeted.toFloat() else 0f
    val budgetPercent = if (totalBudgeted > 0L) ((spentInBudget.toDouble() / totalBudgeted.toDouble()) * 100.0).roundToInt() else 0
    val unbudgetedPercent = if (totalSpent > 0L) ((spentUnbudgeted.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt() else 0

    OrbitSectionCard(
        title = "${monthLabel.uppercase()} SNAPSHOT",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero Spent Figure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
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
                        text = "spent of ${MoneyUtils.formatCents(totalBudgeted)} budgeted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Remaining / Over budget label
                val remainingText = if (isOverBudget) {
                    "${MoneyUtils.formatCents(totalSpent - totalBudgeted)} over"
                } else {
                    "${MoneyUtils.formatCents(totalBudgeted - totalSpent)} remaining"
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

            Spacer(modifier = Modifier.height(12.dp))

            // 12dp Pill Progress Bar
            OrbitPillProgressBar(
                percent = utilization,
                overBudget = isOverBudget,
                height = 12.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2x2 Grid of 4 Snapshot Stat Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tile 1: Budgeted
                OrbitStatTile(
                    label = "BUDGETED",
                    value = "${MoneyUtils.formatCents(spentInBudget)} / ${MoneyUtils.formatCents(totalBudgeted)}",
                    subText = "$budgetPercent% used",
                    subTextColor = if (spentInBudget > totalBudgeted && totalBudgeted > 0L) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Tile 2: Unbudgeted
                OrbitStatTile(
                    label = "UNBUDGETED",
                    value = MoneyUtils.formatCents(spentUnbudgeted),
                    subText = "$unbudgetedPercent% of expenses",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tile 3: Cash Flow
                OrbitStatTile(
                    label = "CASH FLOW",
                    value = MoneyUtils.formatCents(netCashflow, signed = true),
                    valueColor = if (netCashflow >= 0L) colors.income else colors.danger,
                    subText = "${MoneyUtils.formatCents(totalIncome)} income",
                    modifier = Modifier.weight(1f)
                )

                // Tile 4: Spend Pace
                OrbitStatTile(
                    label = "SPEND PACE",
                    value = "${MoneyUtils.formatCents(safeDailySpend)} / day",
                    subText = "$daysRemaining days left",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
