package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.BudgetPulseState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial Budget Pulse card matching original Orbit Money design.
 * Features usage summary ($X of $Y used · Z%), OrbitPillProgressBar(10.dp),
 * Daily Spend Remaining / over budget pace row, and category alert notifications.
 */
@Composable
fun BudgetPulseCard(
    summary: BudgetPulseState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "BUDGET PULSE",
        actionText = "Manage",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!summary.hasBudgets) {
                Text(
                    text = "No category budgets configured for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Usage callout + percent
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${MoneyUtils.formatCents(summary.totalSpentCents)} of ${MoneyUtils.formatCents(summary.totalBudgetedCents)} used",
                            style = TabularNumStyle.copy(
                                fontSize = 14.25.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${summary.percentUsed}%",
                        style = TabularNumStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Bar
                OrbitPillProgressBar(
                    percent = summary.utilizationRatio.toFloat(),
                    overBudget = summary.isOverBudget,
                    height = 10.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Daily Spend Pace Row
                if (summary.isOverBudget) {
                    OrbitMiniStatRow(
                        label = "Daily Spend Remaining",
                        detail = "${summary.daysRemaining} days left",
                        value = "${MoneyUtils.formatCents(summary.overBudgetCents)} over",
                        valueColor = colors.expense
                    )
                } else {
                    OrbitMiniStatRow(
                        label = "Daily Spend Remaining",
                        detail = "${summary.daysRemaining} days left",
                        value = "${MoneyUtils.formatCents(summary.dailySafeSpendCents)} / day",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Alert Categories (if any over budget or in watch zone)
                if (summary.attentionCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "CATEGORIES TO WATCH",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    summary.attentionCategories.take(2).forEachIndexed { index, item ->
                        val isOver = item.spentCents > item.amountCents
                        val alertText = if (isOver) {
                            "${MoneyUtils.formatCents(item.spentCents - item.amountCents)} over"
                        } else {
                            "${((item.spentCents.toDouble() / item.amountCents.toDouble()) * 100).toInt()}% used"
                        }
                        OrbitMiniStatRow(
                            label = item.category.labelWithEmoji,
                            value = alertText,
                            valueColor = if (isOver) colors.expense else colors.warning,
                            showTopDivider = index > 0
                        )
                    }
                }
            }
        }
    }
}
