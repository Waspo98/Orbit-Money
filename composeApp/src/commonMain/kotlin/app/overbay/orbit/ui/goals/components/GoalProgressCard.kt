package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.orbitTactilePress
import kotlin.math.roundToInt

/**
 * Scannable goal progress card matching Orbit's 16dp card language.
 * Displays emoji badge, multi-account source summary, funding progress, and target/projected/pace footer.
 */
@Composable
fun GoalProgressCard(
    goal: Goal,
    accountMap: Map<Long, Account>,
    monthlyNeededCents: Cents?,
    onClick: () -> Unit,
    isInspected: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val progress = goal.progressRatio.toFloat()
    val isDone = goal.isCompleted
    val progressColor = if (isDone) colors.income else MaterialTheme.colorScheme.primary

    val desiredEta = goal.targetDate?.let { LocalDateUtils.formatMonthShortLabel(it.take(7)) } ?: "ASAP"
    val projectedEta = goal.projectedLandingLabel()
    val isStalled = !isDone && projectedEta == "Stalled"
    val isBehindTarget = !isDone && !isStalled && goal.isProjectedPastTargetDate()

    val projectedColor = when {
        isDone -> colors.income
        isStalled || isBehindTarget -> colors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    val pace = goal.monthlyPaceCents
    val paceFormatted = formatMonthlyPace(pace)
    val paceColor = when {
        pace > 0L -> colors.income
        isStalled -> colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val hasLinkedSources = goal.linkedAccountIds().isNotEmpty()
    val sourceLabel = goal.sourceSummaryLabel(accountMap)

    val cardBgColor = if (isInspected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorderColor = if (isInspected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        colors.cardBorder
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor,
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .orbitTactilePress(pressedScale = 0.985f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (dragHandle != null) {
                    dragHandle()
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = goal.resolvedEmoji, fontSize = 19.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasLinkedSources || isDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            colors.warning
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isDone) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.income.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = colors.income,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Completed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.income
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${(goal.progressRatio * 100).roundToInt()}%",
                        style = TabularNumStyle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OrbitPillProgressBar(
                percent = progress,
                customFillColor = progressColor,
                height = 8.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = MoneyUtils.formatCents(goal.currentAmountCents, showCents = false),
                        style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of ${MoneyUtils.formatCents(goal.targetAmountCents, showCents = false)}",
                        style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val rightReadout = when {
                    isDone -> "Target reached"
                    monthlyNeededCents != null && monthlyNeededCents > 0L ->
                        "${MoneyUtils.formatCents(monthlyNeededCents, showCents = false)}/mo needed"
                    else -> "${MoneyUtils.formatCents(goal.remainingAmountCents, showCents = false)} left"
                }
                Text(
                    text = rightReadout,
                    style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (isDone) colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoalFooterMetric(
                    label = "Target",
                    value = desiredEta,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    alignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                )
                GoalFooterMetric(
                    label = "Projected",
                    value = projectedEta,
                    valueColor = projectedColor,
                    alignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                )
                GoalFooterMetric(
                    label = "Monthly Pace",
                    value = paceFormatted,
                    valueColor = paceColor,
                    alignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GoalFooterMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
