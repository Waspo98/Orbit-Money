package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.dashboard.CashflowHeroState
import kotlin.math.roundToInt

/**
 * Editorial "This Month" cashflow hero card with 3 rounded stat tiles (Income, Expenses, Net)
 * and a contextual savings rate + prior-month delta summary strip.
 */
@Composable
fun NetCashflowHeroCard(
    cashflow: CashflowHeroState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    val subtitleText = if (cashflow.isCurrentMonth) {
        "Day ${cashflow.dayOfMonth} of ${cashflow.totalDaysInMonth}"
    } else {
        cashflow.monthLabel.ifBlank { "Completed month" }
    }

    OrbitSectionCard(
        title = "This Month",
        subtitle = subtitleText,
        headerBadge = if (cashflow.incomeCents > 0L) {
            {
                val rate = cashflow.savingsRatePercent.roundToInt()
                val positiveRate = rate >= 0
                OrbitBadge(
                    text = if (positiveRate) "$rate% saved" else "${rate}% net",
                    containerColor = (if (positiveRate) colors.income else colors.danger).copy(alpha = 0.14f),
                    contentColor = if (positiveRate) colors.income else colors.danger
                )
            }
        } else null,
        actionText = "Details",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        val netColor by animateColorAsState(
            targetValue = if (cashflow.isNetPositive) colors.income else colors.danger,
            animationSpec = OrbitSprings.ColorEffectsFast,
            label = "cashflowNetColor"
        )
        val netTileColor by animateColorAsState(
            targetValue = (if (cashflow.isNetPositive) colors.income else colors.danger).copy(alpha = 0.08f),
            animationSpec = OrbitSprings.ColorEffectsFast,
            label = "cashflowNetTileColor"
        )
        val netBorderColor by animateColorAsState(
            targetValue = (if (cashflow.isNetPositive) colors.income else colors.danger).copy(alpha = 0.24f),
            animationSpec = OrbitSprings.ColorEffectsFast,
            label = "cashflowNetBorderColor"
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthStatColumn(
                    label = "Income",
                    cents = cashflow.incomeCents,
                    color = colors.income,
                    modifier = Modifier.weight(1f)
                )
                MonthStatColumn(
                    label = "Expenses",
                    cents = cashflow.expenseCents,
                    color = colors.danger,
                    modifier = Modifier.weight(1f)
                )
                MonthStatColumn(
                    label = "Net",
                    cents = cashflow.netCents,
                    showSign = true,
                    color = netColor,
                    containerColor = netTileColor,
                    borderColor = netBorderColor,
                    modifier = Modifier.weight(1f)
                )
            }

            val priorNet = cashflow.priorMonthNetCents
            if (priorNet != null && (cashflow.incomeCents > 0L || cashflow.expenseCents > 0L || priorNet != 0L)) {
                val netDiff = cashflow.netCents - priorNet
                val isImproved = netDiff >= 0L
                val diffColor = if (isImproved) colors.income else colors.danger
                val pctSuffix = cashflow.netDeltaPercent?.let { pct ->
                    val rounded = pct.roundToInt()
                    val sign = if (rounded >= 0) "+" else ""
                    " ($sign$rounded%)"
                }.orEmpty()
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net vs. prior month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MoneyUtils.formatCents(netDiff, signed = true)}$pctSuffix",
                        style = TabularNumStyle.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = diffColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthStatColumn(
    label: String,
    cents: Cents,
    color: Color,
    modifier: Modifier = Modifier,
    showSign: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = OrbitTheme.colors.cardBorder
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.45.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            RollingTickerCents(
                cents = cents,
                showSign = showSign,
                style = TabularNumStyle.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
        }
    }
}
