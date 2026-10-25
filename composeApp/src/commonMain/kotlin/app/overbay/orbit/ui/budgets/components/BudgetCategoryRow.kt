package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme

@Composable
fun BudgetCategoryRow(
    item: CategoryBudgetOverviewItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = try {
        val hex = item.category.color.trim().removePrefix("#")
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val isOverBudget = item.paceStatus == BudgetPaceStatus.OVER_BUDGET
    val isWatch = item.paceStatus == BudgetPaceStatus.WATCH
    val progressColor = when {
        isOverBudget -> OrbitTheme.colors.expense
        isWatch -> OrbitTheme.colors.warning
        else -> OrbitTheme.colors.income
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Category Info & Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (item.rollover) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "ROLLOVER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Remaining Badge
                val remainingText = if (isOverBudget) {
                    "${MoneyUtils.formatCents(-item.remainingCents, showCents = false)} over"
                } else if (item.amountCents > 0L) {
                    "${MoneyUtils.formatCents(item.remainingCents, showCents = false)} left"
                } else {
                    "${MoneyUtils.formatCents(item.spentCents, showCents = false)} spent"
                }

                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            val progress = if (item.amountCents > 0L) {
                (item.spentCents.toFloat() / item.amountCents.toFloat()).coerceIn(0f, 1f)
            } else 1f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Subtext: Spent vs Budgeted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.amountCents > 0L) {
                        "${MoneyUtils.formatCents(item.spentCents, showCents = false)} of ${MoneyUtils.formatCents(item.amountCents, showCents = false)}"
                    } else {
                        "No budget set"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${item.transactionCount} transaction${if (item.transactionCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
