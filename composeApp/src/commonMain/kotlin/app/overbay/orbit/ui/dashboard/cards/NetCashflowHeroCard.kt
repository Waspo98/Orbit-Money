package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.dashboard.CashflowHeroState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Net cashflow hero banner card with RollingTickerCents readout,
 * monthly income vs expense delta pill, and savings rate percentage badge.
 */
@Composable
fun NetCashflowHeroCard(
    cashflow: CashflowHeroState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val isPositive = cashflow.netCents >= 0L
    val heroColor = if (isPositive) colors.income else colors.expense
    val rateText = if (isPositive) {
        "+${(cashflow.savingsRatePercent * 10).toInt() / 10.0}% Saved"
    } else {
        "${(cashflow.savingsRatePercent * 10).toInt() / 10.0}% Deficit"
    }

    OrbitSectionCard(
        title = "Net Cashflow",
        subtitle = "Monthly Income vs Spending",
        action = {
            TextButton(
                onClick = {
                    haptics.tick()
                    onClick()
                }
            ) {
                Text(
                    text = "Trends",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isPositive) "SURPLUS" else "DEFICIT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    RollingTickerCents(
                        cents = cashflow.netCents,
                        showSign = true,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = heroColor
                    )
                }

                // Savings Rate Delta Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isPositive) colors.incomeContainer else colors.expenseContainer
                ) {
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isPositive) colors.onIncomeContainer else colors.onExpenseContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Income & Expense Breakdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricSubPill(
                    label = "Income",
                    formattedAmount = MoneyUtils.formatCents(cashflow.incomeCents, showCents = false),
                    color = colors.income,
                    modifier = Modifier.weight(1f)
                )

                MetricSubPill(
                    label = "Expenses",
                    formattedAmount = MoneyUtils.formatCents(cashflow.expenseCents, showCents = false),
                    color = colors.expense,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricSubPill(
    label: String,
    formattedAmount: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = color
            )
        }
    }
}
