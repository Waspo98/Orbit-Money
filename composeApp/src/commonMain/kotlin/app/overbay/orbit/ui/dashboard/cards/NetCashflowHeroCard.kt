package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.CashflowHeroState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial "This Month" cashflow hero card matching main branch `.dash-month-stats`.
 * Features "Day X of Y" caption and 3 tinted `.dash-month-stat` tiles (Income, Expenses, Net).
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                color = colors.danger,
                modifier = Modifier.weight(1f)
            )
            MonthStatColumn(
                label = "Net",
                amount = MoneyUtils.formatCents(cashflow.netCents, signed = true),
                color = if (cashflow.isNetPositive) colors.income else colors.danger,
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
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp),
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = TabularNumStyle.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
