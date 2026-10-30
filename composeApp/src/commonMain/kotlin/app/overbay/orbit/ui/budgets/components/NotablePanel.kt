package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.BudgetNotables
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial Notable Insights panel highlighting Over Budget categories, Near Limit (>= 80%) categories,
 * and Unbudgeted spend / notable refund variances with 1-tap category inspection and "+ Add Budget".
 * Strictly <= 390 lines.
 */
@Composable
fun NotablePanel(
    notables: BudgetNotables,
    onInspectCategory: (categoryId: Long) -> Unit,
    onAddBudget: (item: CategoryBudgetOverviewItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!notables.hasNotables) return

    val colors = OrbitTheme.colors

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 16.sp
                    )
                    Text(
                        text = "NOTABLE THIS MONTH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "${notables.totalCount} Alert${if (notables.totalCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Bucket 1: Over Budget
            if (notables.overBudget.isNotEmpty()) {
                NotableBucketSection(
                    title = "OVER BUDGET",
                    badgeText = "${notables.overBudget.size}",
                    badgeColor = colors.danger,
                    items = notables.overBudget,
                    onInspectCategory = onInspectCategory,
                    onAddBudget = onAddBudget,
                    isOverBudget = true
                )
            }

            // Bucket 2: Near Limit (>= 80%)
            if (notables.nearLimit.isNotEmpty()) {
                NotableBucketSection(
                    title = "APPROACHING LIMIT (80%+)",
                    badgeText = "${notables.nearLimit.size}",
                    badgeColor = colors.warning,
                    items = notables.nearLimit,
                    onInspectCategory = onInspectCategory,
                    onAddBudget = onAddBudget,
                    isNearLimit = true
                )
            }

            // Bucket 3: Variances (Unbudgeted & Material Spend or Refunds)
            if (notables.variances.isNotEmpty()) {
                NotableBucketSection(
                    title = "UNBUDGETED & REFUND VARIANCES",
                    badgeText = "${notables.variances.size}",
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    items = notables.variances,
                    onInspectCategory = onInspectCategory,
                    onAddBudget = onAddBudget,
                    isVariance = true
                )
            }
        }
    }
}

@Composable
private fun NotableBucketSection(
    title: String,
    badgeText: String,
    badgeColor: Color,
    items: List<CategoryBudgetOverviewItem>,
    onInspectCategory: (categoryId: Long) -> Unit,
    onAddBudget: (item: CategoryBudgetOverviewItem) -> Unit,
    isOverBudget: Boolean = false,
    isNearLimit: Boolean = false,
    isVariance: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
            ) {
                Surface(color = badgeColor, modifier = Modifier.fillMaxWidth().height(8.dp)) {}
            }
            Text(
                text = "$title ($badgeText)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        items.forEach { item ->
            NotableItemRow(
                item = item,
                onInspect = { onInspectCategory(item.category.id) },
                onAddBudget = { onAddBudget(item) },
                isOverBudget = isOverBudget,
                isNearLimit = isNearLimit,
                isVariance = isVariance
            )
        }
    }
}

@Composable
private fun NotableItemRow(
    item: CategoryBudgetOverviewItem,
    onInspect: () -> Unit,
    onAddBudget: () -> Unit,
    isOverBudget: Boolean,
    isNearLimit: Boolean,
    isVariance: Boolean
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val catColor = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                onInspect()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = catColor.copy(alpha = 0.18f),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {}
                    Text(
                        text = item.category.icon ?: "📁",
                        fontSize = 15.sp
                    )
                }

                Column {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val subtitle = when {
                        isOverBudget -> {
                            val overCents = item.spentCents - item.amountCents
                            "${MoneyUtils.formatCents(overCents)} over (${MoneyUtils.formatCents(item.spentCents)} of ${MoneyUtils.formatCents(item.amountCents)})"
                        }
                        isNearLimit -> {
                            val pct = (item.utilizationRatio * 100).toInt()
                            "$pct% used · ${MoneyUtils.formatCents(item.remainingCents)} left"
                        }
                        item.spentCents < 0L -> {
                            "Net refund: +${MoneyUtils.formatCents(-item.spentCents)}"
                        }
                        item.amountCents <= 0L -> {
                            "${MoneyUtils.formatCents(item.spentCents)} unbudgeted spend"
                        }
                        else -> {
                            "${MoneyUtils.formatCents(item.spentCents)} spent"
                        }
                    }

                    Text(
                        text = subtitle,
                        style = TabularNumStyle.copy(fontSize = 11.sp),
                        color = when {
                            isOverBudget -> colors.danger
                            isNearLimit -> colors.warning
                            item.spentCents < 0L -> colors.income
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isVariance && item.amountCents <= 0L) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onAddBudget()
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "+ Set Budget",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = MoneyUtils.formatCents(item.spentCents),
                    style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = if (isOverBudget) colors.danger else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
