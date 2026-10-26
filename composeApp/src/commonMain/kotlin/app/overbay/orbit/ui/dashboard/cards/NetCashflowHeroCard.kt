package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.CashflowHeroState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial "This Month" cashflow hero card matching original Orbit Money design.
 * Features "Day X of Y" caption and a 3-column stat row (Income, Expenses, Net).
 */
@Composable
fun NetCashflowHeroCard(
    cashflow: CashflowHeroState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "THIS MONTH",
        subtitle = "Day ${cashflow.dayOfMonth} of ${cashflow.totalDaysInMonth}",
        actionText = "Details",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthStatColumn(
                label = "Income",
                amount = MoneyUtils.formatCents(cashflow.incomeCents),
                color = colors.income,
                modifier = Modifier.weight(1f)
            )
            MonthStatColumn(
                label = "Expenses",
                amount = MoneyUtils.formatCents(cashflow.expenseCents),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            MonthStatColumn(
                label = "Net",
                amount = MoneyUtils.formatCents(cashflow.netCents, signed = true),
                color = if (cashflow.isNetPositive) colors.income else colors.expense,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthStatColumn(
    label: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = TabularNumStyle.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}
