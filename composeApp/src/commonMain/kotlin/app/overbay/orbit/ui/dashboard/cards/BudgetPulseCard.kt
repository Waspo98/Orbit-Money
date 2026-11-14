package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerText
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.ui.dashboard.BudgetPulseState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial Budget Pulse card with semantic pace badge, day-of-month pace marker on
 * OrbitPillProgressBar, daily spend pace row, and tappable attention category alerts.
 */
@Composable
fun BudgetPulseCard(
    summary: BudgetPulseState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dayOfMonth: Int = 1,
    totalDaysInMonth: Int = 30,
    isCurrentMonth: Boolean = true,
    onCategoryClick: (Long) -> Unit = {}
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val paceBadge: (@Composable () -> Unit)? = if (summary.hasBudgets) {
        {
            val (label, badgeColor) = when (summary.paceStatus) {
                BudgetPaceStatus.OVER_BUDGET -> "Over" to colors.expense
                BudgetPaceStatus.WATCH -> "Watch" to colors.warning
                BudgetPaceStatus.ON_TRACK -> "On Track" to colors.income
                BudgetPaceStatus.NO_BUDGET -> "No Budget" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            OrbitBadge(
                text = label,
                containerColor = badgeColor.copy(alpha = 0.14f),
                contentColor = badgeColor
            )
        }
    } else null

    OrbitSectionCard(
        title = "Monthly Budget",
        headerBadge = paceBadge,
        actionText = "Manage",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = summary.hasBudgets,
            transitionSpec = { orbitMorphContentTransform(clip = false) },
            label = "budgetPulseHasBudgets"
        ) { hasBudgets ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!hasBudgets) {
                    Text(
                        text = "No category budgets set yet. Tap Manage to create monthly targets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val percentColor by animateColorAsState(
                        targetValue = when {
                            summary.isOverBudget -> colors.expense
                            summary.paceStatus == BudgetPaceStatus.WATCH -> colors.warning
                            else -> MaterialTheme.colorScheme.primary
                        },
                        animationSpec = OrbitSprings.ColorEffectsFast,
                        label = "budgetPulsePercentColor"
                    )
                    val paceMarker = if (isCurrentMonth && totalDaysInMonth > 0) {
                        (dayOfMonth.toFloat() / totalDaysInMonth.toFloat()).coerceIn(0.02f, 0.98f)
                    } else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        RollingTickerText(
                            text = "${MoneyUtils.formatCents(summary.totalSpentCents)} of ${MoneyUtils.formatCents(summary.totalBudgetedCents)} used",
                            style = TabularNumStyle.copy(
                                fontSize = 14.25.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        RollingTickerText(
                            text = "${summary.percentUsed}%",
                            style = TabularNumStyle.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = percentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OrbitPillProgressBar(
                        percent = summary.utilizationRatio.toFloat(),
                        overBudget = summary.isOverBudget,
                        height = 10.dp,
                        paceMarkerFraction = paceMarker
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedContent(
                        targetState = summary.isOverBudget,
                        transitionSpec = { orbitFadeContentTransform() },
                        label = "budgetPulseOverPace"
                    ) { isOverBudget ->
                        if (isOverBudget) {
                            OrbitMiniStatRow(
                                label = "Daily Spend Remaining",
                                detail = "${summary.daysRemaining} days left",
                                value = "${MoneyUtils.formatCents(summary.overBudgetCents)} over",
                                valueColor = colors.expense
                            )
                        } else {
                            OrbitMiniStatRow(
                                label = "Daily Spend Remaining",
                                detail = if (isCurrentMonth) "${summary.daysRemaining} days left" else "Month complete",
                                value = "${MoneyUtils.formatCents(summary.dailySafeSpendCents)} / day",
                                valueColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    var lastAttention by remember { mutableStateOf(summary.attentionCategories) }
                    if (summary.attentionCategories.isNotEmpty()) {
                        lastAttention = summary.attentionCategories
                    }
                    OrbitAccordionVisibility(visible = summary.attentionCategories.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "CATEGORIES TO WATCH",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            lastAttention.take(2).forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                                }
                                val isOver = item.spentCents > item.amountCents
                                val alertText = if (isOver) {
                                    "${MoneyUtils.formatCents(item.spentCents - item.amountCents)} over"
                                } else {
                                    "${((item.spentCents.toDouble() / item.amountCents.toDouble()) * 100).toInt()}% used"
                                }
                                val catColor = item.category.color.toComposeColor(MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            haptics.tick()
                                            onCategoryClick(item.category.id)
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(catColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.category.labelWithEmoji,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = alertText,
                                        style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (isOver) colors.expense else colors.warning
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
