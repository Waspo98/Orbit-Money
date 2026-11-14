package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.MonthComparisonState

/**
 * 4-column Month vs Last Month comparison table card with right-aligned numeric columns
 * and semantic tinted delta pills.
 */
@Composable
fun MonthComparisonCard(
    state: MonthComparisonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Month vs. Last Month",
        subtitle = "${state.currentMonthLabel.ifBlank { "Current" }} vs. ${state.previousMonthLabel.ifBlank { "Prior" }}",
        actionText = "Details",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Metric | Current | Previous | Change
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "METRIC",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = state.currentMonthLabel.take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = state.previousMonthLabel.take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "CHANGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.15f)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)

            state.rows.forEachIndexed { index, row ->
                val isNetRow = row.metric.equals("Net", ignoreCase = true)
                if (index > 0) {
                    HorizontalDivider(
                        thickness = if (isNetRow) 1.5.dp else 1.dp,
                        color = colors.cardBorder
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.metric,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isNetRow) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = MoneyUtils.formatCents(row.currentCents),
                        style = TabularNumStyle.copy(
                            fontSize = 12.5.sp,
                            fontWeight = if (isNetRow) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = MoneyUtils.formatCents(row.previousCents),
                        style = TabularNumStyle.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val deltaColor = if (row.isPositiveImprovement) colors.income else colors.danger
                    val deltaText = MoneyUtils.formatCents(row.deltaCents, signed = true)
                    Row(
                        modifier = Modifier.weight(1.15f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = deltaColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = deltaText,
                                style = TabularNumStyle.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = deltaColor,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
