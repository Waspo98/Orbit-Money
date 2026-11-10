package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun GoalProgressCard(
    goal: Goal,
    accountName: String?,
    monthlyNeededCents: Cents?,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    isInspected: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val progress = goal.progressRatio.toFloat()
    val isDone = goal.isCompleted
    val progressColor = if (isDone) OrbitTheme.colors.income else MaterialTheme.colorScheme.primary

    val desiredEta = goal.targetDate?.let { LocalDateUtils.formatMonthShortLabel(it.take(7)) } ?: "ASAP"
    val projectedEta = if (isDone) {
        "Reached"
    } else {
        goal.eta?.date?.let { LocalDateUtils.formatMonthShortLabel(it.take(7)) } ?: "Stalled"
    }
    val pace = goal.monthlyPaceCents ?: 0L
    val paceFormatted = when {
        pace > 0L -> "+${MoneyUtils.formatCents(pace, showCents = false)}/mo"
        pace < 0L -> "-${MoneyUtils.formatCents(-pace, showCents = false)}/mo"
        else -> "$0/mo"
    }

    val cardBgColor = if (isInspected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorderColor = if (isInspected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = cardBgColor,
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, cardBorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Goal Name & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (dragHandle != null) {
                    dragHandle()
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = goal.labelWithEmoji,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (accountName != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Linked to $accountName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isDone) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = OrbitTheme.colors.income.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = OrbitTheme.colors.income,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Completed!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.income
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${(goal.progressRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar
            OrbitPillProgressBar(
                percent = progress,
                customFillColor = progressColor,
                height = 8.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Amount Readout: Saved vs Target & Edit Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${MoneyUtils.formatCents(goal.currentAmountCents, showCents = false)} of ${MoneyUtils.formatCents(goal.targetAmountCents, showCents = false)}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!isDone && monthlyNeededCents != null && monthlyNeededCents > 0L) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${MoneyUtils.formatCents(monthlyNeededCents, showCents = false)}/mo needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                OrbitActionChip(
                    label = "Edit",
                    onClick = {
                        haptics.tick()
                        onEditClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Metadata Row: Desired ETA, Projected ETA, Monthly Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DESIRED",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = desiredEta,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PROJECTED",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = projectedEta,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) OrbitTheme.colors.income else MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "MONTHLY PACE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = paceFormatted,
                        style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = if (pace > 0L) OrbitTheme.colors.income else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
