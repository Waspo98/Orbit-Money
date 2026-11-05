package app.overbay.orbit.ui.trends.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.CategorySpendingTrend
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun CategoryTrendStatsCard(
    trend: CategorySpendingTrend,
    onOpenBudgetModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highestMonth = trend.monthlySeries.maxByOrNull { it.spentCents }
    val hasBudget = trend.budgetAmountCents != null && trend.budgetAmountCents > 0L

    OrbitSectionCard(
        title = "${trend.category.name.uppercase()} OVERVIEW",
        actionText = if (hasBudget) "Edit Budget" else "Add Budget",
        onActionClick = onOpenBudgetModal,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tile 1: Monthly Budget
                StatTile(
                    label = "Monthly Budget",
                    value = if (hasBudget) MoneyUtils.formatCents(trend.budgetAmountCents!!) else "Not Set",
                    valueColor = if (hasBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Tile 2: Period Average
                StatTile(
                    label = "Period Average",
                    value = MoneyUtils.formatCents(trend.averageSpentCents),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tile 3: Highest Month
                val highestLabel = highestMonth?.let {
                    "${it.month} (${MoneyUtils.formatCents(it.spentCents)})"
                } ?: "—"
                StatTile(
                    label = "Highest Month",
                    value = highestLabel,
                    valueColor = OrbitColorTokens.Red,
                    modifier = Modifier.weight(1f)
                )

                // Tile 4: Months Over Budget
                val overBudgetLabel = if (hasBudget) {
                    "${trend.overBudgetMonthCount} of ${trend.monthlySeries.size}"
                } else {
                    "None"
                }
                StatTile(
                    label = "Months Over Budget",
                    value = overBudgetLabel,
                    valueColor = if (trend.overBudgetMonthCount > 0) OrbitColorTokens.Amber else OrbitColorTokens.Green,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = TabularNumStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1
            )
        }
    }
}
