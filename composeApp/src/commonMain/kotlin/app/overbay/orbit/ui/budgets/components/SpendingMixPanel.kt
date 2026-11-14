package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import kotlin.math.roundToInt

/**
 * Multi-segment colored horizontal strip showing each category's proportion
 * of positive spending along with dollar amount + percentage legend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpendingMixPanel(
    items: List<CategoryBudgetOverviewItem>,
    modifier: Modifier = Modifier
) {
    val sorted = items.sortedByDescending { it.spentCents }
    val totalSpent = sorted.sumOf { it.spentCents }
    if (totalSpent <= 0L) return

    OrbitSectionCard(
        title = "SPENDING MIX",
        subtitle = "${MoneyUtils.formatCents(totalSpent, showCents = false)} gross outflows",
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitStackedSegmentBar(
                segments = sorted.map { item ->
                    OrbitBarSegment(
                        key = item.category.id,
                        fraction = (item.spentCents.toFloat() / totalSpent.toFloat()).coerceAtLeast(0.01f),
                        color = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)
                    )
                },
                height = 10.dp,
                normalizeToFullWidth = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sorted.take(5).forEach { item ->
                    val pct = ((item.spentCents.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
                    val color = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.category.labelWithEmoji,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${MoneyUtils.formatCents(item.spentCents, showCents = false)} ($pct%)",
                            style = TabularNumStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
