package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.RollingTickerText
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.ui.dashboard.GoalFocusState
import app.overbay.orbit.ui.dashboard.GoalsProgressState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Overall goals progress card with active count badge, animated overall funding bar,
 * and individual slim progress bars on up to 4 top goals.
 */
@Composable
fun GoalsProgressCard(
    state: GoalsProgressState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Goals Progress",
        headerBadge = if (state.activeGoalsCount > 0) {
            {
                OrbitBadge(
                    text = "${state.activeGoalsCount} active",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else null,
        actionText = "Goals",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.goals.isEmpty()) {
                Text(
                    text = "No active savings goals found. Tap Goals to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${MoneyUtils.formatCents(state.totalSavedCents)} of ${MoneyUtils.formatCents(state.totalTargetCents)} saved",
                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    RollingTickerText(
                        text = "${(state.overallProgressRatio * 100).toInt()}%",
                        style = TabularNumStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OrbitPillProgressBar(
                    percent = state.overallProgressRatio.toFloat(),
                    height = 8.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                state.goals.take(4).forEachIndexed { index, goal ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = goal.labelWithEmoji,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val dateLabel = goal.targetDateDisplay ?: goal.targetDate
                                if (dateLabel != null) {
                                    Text(
                                        text = "Target $dateLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "${MoneyUtils.formatCents(goal.currentAmountCents)} (${(goal.progressRatio * 100).toInt()}%)",
                                style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = if (goal.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        OrbitPillProgressBar(
                            percent = goal.progressRatio.toFloat(),
                            height = 4.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Goal Focus card concentrating on a single priority savings goal with monthly pace insight
 * and an M3E bottom-sheet goal selector.
 */
@Composable
fun GoalFocusCard(
    state: GoalFocusState,
    onSelectGoal: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showGoalPicker by remember { mutableStateOf(false) }

    val goal = state.focusedGoal
    val canPickGoal = state.availableGoals.isNotEmpty()
    LaunchedEffect(canPickGoal) {
        if (!canPickGoal) showGoalPicker = false
    }

    OrbitSectionCard(
        title = "Goal Focus",
        actionText = if (canPickGoal) "Pick Goal" else null,
        onActionClick = if (canPickGoal) { { showGoalPicker = true } } else null,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (goal == null) {
                Text(
                    text = "No goal selected for focus. Tap Goals to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.labelWithEmoji,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${MoneyUtils.formatCents(goal.currentAmountCents)} saved of ${MoneyUtils.formatCents(goal.targetAmountCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RollingTickerText(
                        text = "${(goal.progressRatio * 100).toInt()}%",
                        style = TabularNumStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OrbitPillProgressBar(
                    percent = goal.progressRatio.toFloat(),
                    height = 10.dp
                )

                if (goal.targetDate != null || goal.monthlyPaceCents != null || goal.remainingAmountCents > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateText = goal.targetDateDisplay ?: goal.targetDate
                        val leftCaption = buildString {
                            if (dateText != null) append("Target $dateText")
                            if (goal.remainingAmountCents > 0L) {
                                if (isNotEmpty()) append(" · ")
                                append("${MoneyUtils.formatCents(goal.remainingAmountCents)} left")
                            }
                        }
                        Text(
                            text = leftCaption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (goal.monthlyPaceCents != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "~${MoneyUtils.formatCents(goal.monthlyPaceCents)}/mo needed",
                                    style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGoalPicker && state.availableGoals.isNotEmpty()) {
        OrbitModalSheet(onDismissRequest = { showGoalPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 20.dp, vertical = 8.dp))
            ) {
                OrbitSheetHeader(
                    title = "Select Goal to Focus",
                    subtitle = "Choose which savings goal stays pinned to your dashboard",
                    onDismiss = { showGoalPicker = false }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.availableGoals.forEach { item ->
                        SelectableListRow(
                            title = item.labelWithEmoji,
                            subtitle = "${MoneyUtils.formatCents(item.currentAmountCents)} of ${MoneyUtils.formatCents(item.targetAmountCents)}",
                            sidePrimary = "${(item.progressRatio * 100).toInt()}%",
                            isSelected = goal?.id == item.id,
                            onClick = {
                                haptics.tick()
                                onSelectGoal(item.id)
                                showGoalPicker = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
