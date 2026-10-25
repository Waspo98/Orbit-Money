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
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.charts.BudgetRingGauge
import app.overbay.orbit.ui.dashboard.BudgetPulseState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Compact card embedding the BudgetRingGauge horseshoe gauge,
 * daily safe-to-spend ticker, and budgeted vs spent summary footer.
 */
@Composable
fun BudgetPulseCard(
    summary: BudgetPulseState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "Budget Pulse",
        subtitle = "Monthly Spending Pace",
        action = {
            TextButton(
                onClick = {
                    haptics.tick()
                    onClick()
                }
            ) {
                Text(
                    text = "View Budgets",
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Open Horseshoe Gauge
            BudgetRingGauge(
                spentCents = summary.totalSpentCents,
                budgetCents = summary.totalBudgetedCents,
                safeSpendDailyCents = summary.dailySafeSpendCents,
                paceStatus = summary.paceStatus,
                gaugeSize = 200.dp,
                ringThickness = 14.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Summary Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BudgetStatItem(
                        label = "Budgeted",
                        amount = MoneyUtils.formatCents(summary.totalBudgetedCents, showCents = false)
                    )
                    BudgetStatItem(
                        label = "Spent",
                        amount = MoneyUtils.formatCents(summary.totalSpentCents, showCents = false)
                    )
                    BudgetStatItem(
                        label = "Remaining",
                        amount = MoneyUtils.formatCents(summary.remainingCents, showCents = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetStatItem(
    label: String,
    amount: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
