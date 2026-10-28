package app.overbay.orbit.ui.goals.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.goals.GoalsUiState
import app.overbay.orbit.ui.goals.GoalsViewModel
import app.overbay.orbit.ui.goals.dialogs.GoalAllocationContent
import app.overbay.orbit.ui.goals.dialogs.GoalEditorContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class GoalInspectorSubMode { DETAIL, EDIT, DEPOSIT }

/**
 * Right-hand morphing inspector pane for Savings Goals on unfolded phones, tablets, and desktops.
 * Shows the read-only goal overview by default when tapped, and morphs smoothly into the Edit or Deposit form.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun GoalDetailInspectorPane(
    state: GoalsUiState,
    viewModel: GoalsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var cachedGoal by remember { mutableStateOf<Goal?>(null) }
    val activeGoal = state.inspectingGoal
    if (activeGoal != null) {
        cachedGoal = activeGoal
    }
    val inspecting = activeGoal ?: cachedGoal

    val subMode = when {
        state.showEditorSheet -> GoalInspectorSubMode.EDIT
        state.showAllocationDialog && (state.allocatingGoal ?: inspecting) != null -> GoalInspectorSubMode.DEPOSIT
        else -> GoalInspectorSubMode.DETAIL
    }

    val canBackFromSubMode = subMode != GoalInspectorSubMode.DETAIL && state.inspectingGoalId != null
    BackHandler(enabled = canBackFromSubMode) {
        haptics.tick()
        when (subMode) {
            GoalInspectorSubMode.EDIT -> viewModel.closeEditorSheet()
            GoalInspectorSubMode.DEPOSIT -> viewModel.closeAllocationDialog()
            else -> {}
        }
    }

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxSize(),
        label = "goalInspectorSubModeTransition"
    ) { mode ->
        when (mode) {
            GoalInspectorSubMode.EDIT -> {
                GoalEditorContent(
                    goal = state.editingGoal ?: inspecting,
                    accounts = state.accounts,
                    onSave = { viewModel.saveGoal(it) },
                    onDelete = { viewModel.deleteGoal(it) },
                    onDismiss = {
                        if (state.inspectingGoalId != null) viewModel.closeEditorSheet() else onClosePane()
                    }
                )
            }
            GoalInspectorSubMode.DEPOSIT -> {
                val target = state.allocatingGoal ?: inspecting
                if (target != null) {
                    GoalAllocationContent(
                        goal = target,
                        onConfirmDeposit = { id, amount -> viewModel.depositToGoal(id, amount) },
                        onDismiss = {
                            if (state.inspectingGoalId != null) viewModel.closeAllocationDialog() else onClosePane()
                        }
                    )
                }
            }
            GoalInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    val acct = inspecting.linkedAccountId?.let { state.accountMap[it] }
                    val monthlyNeeded = viewModel.calculateMonthlyRequiredCents(inspecting)
                    GoalReadOnlyDetailContent(
                        goal = inspecting,
                        linkedAccount = acct,
                        monthlyNeededCents = monthlyNeeded,
                        onClose = onClosePane,
                        onEdit = { viewModel.openEditGoal(inspecting) },
                        onDeposit = { viewModel.openAllocationDialog(inspecting) },
                        onDelete = { viewModel.deleteGoal(inspecting.id) },
                        onOpenLinkedAccount = if (acct != null) {
                            { onNavigate(OrbitNavKey.Accounts(accountId = acct.id)) }
                        } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalReadOnlyDetailContent(
    goal: Goal,
    linkedAccount: Account?,
    monthlyNeededCents: Cents?,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDeposit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLinkedAccount: (() -> Unit)?
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val progress = goal.progressRatio.toFloat()
    val isDone = goal.isCompleted
    val progressColor = if (isDone) colors.income else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.labelWithEmoji,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${MoneyUtils.formatCents(goal.currentAmountCents, showCents = false)} of ${MoneyUtils.formatCents(goal.targetAmountCents, showCents = false)}",
                    style = TabularNumStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = progressColor
                )
            }
            IconButton(onClick = { haptics.tick(); onClose() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalMetaField(label = "PROGRESS", value = "${(goal.progressRatio * 100).toInt()}%")
                GoalMetaField(
                    label = "REMAINING",
                    value = MoneyUtils.formatCents(goal.remainingAmountCents, showCents = false)
                )
                GoalMetaField(
                    label = "MONTHLY PACE NEEDED",
                    value = if (monthlyNeededCents != null && monthlyNeededCents > 0L) {
                        "${MoneyUtils.formatCents(monthlyNeededCents, showCents = false)}/mo"
                    } else if (isDone) {
                        "Goal reached"
                    } else {
                        "No target date set"
                    }
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalMetaField(
                    label = "TARGET DATE",
                    value = goal.targetDate ?: "Not set"
                )
                GoalMetaField(
                    label = "LINKED ACCOUNT",
                    value = linkedAccount?.name ?: "None / Unlinked"
                )
                GoalMetaField(
                    label = "CATEGORY KIND",
                    value = goal.kind.replaceFirstChar { it.uppercase() }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoalPillAction(label = "Edit Goal", onClick = onEdit)
            if (!isDone) {
                GoalPillAction(label = "Deposit", onClick = onDeposit)
            }
            if (onOpenLinkedAccount != null) {
                GoalPillAction(label = "View Linked Account", onClick = onOpenLinkedAccount)
            }
            GoalPillAction(label = "Delete", isDestructive = true, onClick = onDelete)
        }
    }
}

@Composable
private fun GoalMetaField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GoalPillAction(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = { haptics.tick(); onClick() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (isDestructive) OrbitTheme.colors.danger else OrbitTheme.colors.cardBorder),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
        )
    }
}
