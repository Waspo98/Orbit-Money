package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.GoalFocusState
import app.overbay.orbit.ui.dashboard.GoalsProgressState
import app.overbay.orbit.ui.dashboard.SubscriptionsState
import app.overbay.orbit.ui.dashboard.UpcomingBillItem
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Subscriptions / Recurring items card showing up to 5 items from Upcoming.
 */
@Composable
fun SubscriptionsCard(
    state: SubscriptionsState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "SUBSCRIPTIONS / RECURRING",
        actionText = "Review",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.items.isEmpty()) {
                Text(
                    text = "No active recurring subscriptions found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.items.forEachIndexed { index, item ->
                    val catPrefix = CategoryEmojiMapper.resolveEmoji(item.categoryIcon)?.let { "$it " } ?: ""
                    val catLabel = item.categoryName?.let { "$catPrefix$it" } ?: "Subscription"
                    OrbitCompactListRow(
                        title = item.merchant ?: item.name,
                        subtitle = "$catLabel · ${item.frequencyLabel}",
                        amount = MoneyUtils.formatCents(item.amountCents),
                        amountDetail = if (item.nextDateDisplay.isNotBlank()) "Next ${item.nextDateDisplay}" else null,
                        amountColor = colors.expense,
                        showTopDivider = index > 0
                    )
                }
            }
        }
    }
}

/**
 * Upcoming bills and income card showing next 3 occurrences with quick-pay action.
 */
@Composable
fun UpcomingBillsCard(
    bills: List<UpcomingBillItem>,
    onReconcile: (Long, String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "UPCOMING: BILLS AND INCOME",
        actionText = "Upcoming",
        onActionClick = onViewAll,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onViewAll()
            }
    ) {
        if (bills.isEmpty()) {
            Text(
                text = "No bills or income scheduled for the next 14 days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                bills.take(3).forEachIndexed { index, bill ->
                    val amtColor = if (bill.isExpense) colors.expense else colors.income
                    val formattedAmt = if (bill.isExpense) {
                        "-${MoneyUtils.formatCents(bill.amountCents)}"
                    } else {
                        "+${MoneyUtils.formatCents(bill.amountCents)}"
                    }

                    val catPrefix = CategoryEmojiMapper.resolveEmoji(bill.categoryIcon)?.let { "$it " } ?: ""
                    val catLabel = bill.categoryName?.let { "$catPrefix$it" } ?: "General"
                    OrbitCompactListRow(
                        title = bill.merchant ?: bill.name,
                        subtitle = "$catLabel · ${bill.dueLabel}",
                        amount = formattedAmt,
                        amountColor = amtColor,
                        showTopDivider = index > 0,
                        trailingAction = {
                            OutlinedButton(
                                onClick = {
                                    haptics.confirm()
                                    onReconcile(bill.upcomingItemId, bill.dueDateIso)
                                },
                                shape = CircleShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Pay",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Overall goals progress card with total funding bar and top goals list.
 */
@Composable
fun GoalsProgressCard(
    state: GoalsProgressState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "GOALS PROGRESS",
        actionText = "Goals",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.goals.isEmpty()) {
                Text(
                    text = "No active savings goals found. Tap 'Goals' to create one.",
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
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
                    OrbitMiniStatRow(
                        label = goal.labelWithEmoji,
                        detail = if (goal.targetDate != null) "Target ${goal.targetDate}" else null,
                        value = "${MoneyUtils.formatCents(goal.currentAmountCents)} (${(goal.progressRatio * 100).toInt()}%)",
                        valueColor = if (goal.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        showTopDivider = index > 0
                    )
                }
            }
        }
    }
}

/**
 * Goal Focus card concentrating on a single priority savings goal.
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

    OrbitSectionCard(
        title = "GOAL FOCUS",
        actionText = "Pick Goal",
        onActionClick = { showGoalPicker = true },
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (goal == null) {
                Text(
                    text = "No goal selected for focus.",
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${MoneyUtils.formatCents(goal.currentAmountCents)} saved of ${MoneyUtils.formatCents(goal.targetAmountCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
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

                if (goal.targetDate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Target Date: ${goal.targetDate} · ${MoneyUtils.formatCents(goal.remainingAmountCents)} remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showGoalPicker && state.availableGoals.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showGoalPicker = false },
            title = { Text("Select Goal to Focus") },
            text = {
                Column {
                    state.availableGoals.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.tick()
                                    onSelectGoal(item.id)
                                    showGoalPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = goal?.id == item.id,
                                onClick = {
                                    haptics.tick()
                                    onSelectGoal(item.id)
                                    showGoalPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = item.labelWithEmoji, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoalPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}
