package app.overbay.orbit.ui.goals.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.ui.goals.GoalsUiState
import app.overbay.orbit.ui.goals.GoalsViewModel
import app.overbay.orbit.ui.goals.dialogs.GoalEditorContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

private enum class GoalInspectorSubMode { DETAIL, EDIT }

/**
 * Unified goal detail & edit inspector used both inside the two-pane split layout
 * and inside a mobile modal bottom sheet.
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
    val inspecting = state.inspectingGoal
    val isInsideSheet = LocalOrbitSheetDismiss.current != null
    val subMode = if (state.showEditorSheet) GoalInspectorSubMode.EDIT else GoalInspectorSubMode.DETAIL

    val canBackToDetail = subMode == GoalInspectorSubMode.EDIT &&
        state.inspectingGoalId != null &&
        state.editingGoal?.id == state.inspectingGoalId

    BackHandler(enabled = canBackToDetail) {
        haptics.tick()
        viewModel.closeEditorSheet()
    }

    AnimatedContent(
        targetState = Pair(subMode, state.inspectingGoalId ?: state.editingGoal?.id),
        transitionSpec = { orbitMorphContentTransform() },
        label = "goalInspectorSubModeTransition",
        modifier = if (isInsideSheet) modifier.fillMaxWidth() else modifier.fillMaxSize()
    ) { (mode, _) ->
        when (mode) {
            GoalInspectorSubMode.EDIT -> {
                GoalEditorContent(
                    goal = state.editingGoal,
                    accounts = state.accounts,
                    existingGoals = state.goals,
                    onSave = { updated, steal -> viewModel.saveGoalWithAllocations(updated, steal) },
                    onDelete = { id ->
                        viewModel.closeEditorSheet()
                        viewModel.requestDeleteGoal(id)
                    },
                    onDismiss = onClosePane,
                    onNavigateBack = if (canBackToDetail) {
                        {
                            haptics.tick()
                            viewModel.closeEditorSheet()
                        }
                    } else {
                        null
                    },
                    onOpenLinkedAccount = { accountId ->
                        onNavigate(OrbitNavKey.Accounts(accountId))
                    },
                    modifier = if (isInsideSheet) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
                )
            }
            GoalInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    GoalReadOnlyDetailContent(
                        goal = inspecting,
                        accountMap = state.accountMap,
                        monthlyNeededCents = viewModel.calculateMonthlyRequiredCents(inspecting),
                        historyMonths = state.historyMonths,
                        whatIfBoostCents = state.whatIfBoostCents,
                        onTimeframeChanged = viewModel::setHistoryMonths,
                        onWhatIfBoostChanged = viewModel::setWhatIfBoostCents,
                        onOpenEdit = { viewModel.openEditGoal(inspecting) },
                        onOpenAccount = { accountId -> onNavigate(OrbitNavKey.Accounts(accountId)) },
                        onRequestDelete = { viewModel.requestDeleteGoal(inspecting.id) },
                        onDismiss = onClosePane,
                        isInsideSheet = isInsideSheet
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Select a goal to inspect",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalReadOnlyDetailContent(
    goal: Goal,
    accountMap: Map<Long, Account>,
    monthlyNeededCents: Cents?,
    historyMonths: Int,
    whatIfBoostCents: Cents,
    onTimeframeChanged: (Int) -> Unit,
    onWhatIfBoostChanged: (Cents) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
    isInsideSheet: Boolean
) {
    val colors = OrbitTheme.colors
    val haptics = LocalOrbitHaptics.current
    val pct = (goal.progressRatio * 100.0).roundToInt()
    val progressColor = if (goal.isCompleted) colors.income else MaterialTheme.colorScheme.primary
    val projectedLabel = goal.projectedLandingLabel()
    val isStalled = !goal.isCompleted && projectedLabel == "Stalled"
    val isPastTarget = !goal.isCompleted && !isStalled && goal.isProjectedPastTargetDate()

    Column(
        modifier = (if (isInsideSheet) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OrbitSheetHeader(
            title = goal.labelWithEmoji,
            subtitle = if (goal.isCompleted) "Completed Goal" else goal.sourceSummaryLabel(accountMap),
            onDismiss = onDismiss,
            trailingActions = {
                OrbitActionChip(
                    label = "Edit",
                    leadingIcon = Icons.Default.Edit,
                    onClick = {
                        haptics.tick()
                        onOpenEdit()
                    }
                )
            }
        )

        // Hero Progress Summary
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formatCurrencySmart(goal.currentAmountCents),
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumStyle),
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
                OrbitBadge(
                    text = "$pct% of ${formatCurrencySmart(goal.targetAmountCents)}",
                    containerColor = progressColor.copy(alpha = 0.14f),
                    contentColor = progressColor
                )
            }

            OrbitPillProgressBar(
                percent = goal.progressRatio.toFloat(),
                customFillColor = progressColor,
                height = 10.dp
            )

            Text(
                text = if (goal.isCompleted) {
                    "Goal target reached"
                } else {
                    "${formatCurrencySmart(goal.remainingAmountCents)} remaining to target"
                },
                style = MaterialTheme.typography.labelMedium.merge(TabularNumStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2x2 Key Metrics Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitStatTile(
                    label = "Monthly Pace",
                    value = formatMonthlyPace(goal.monthlyPaceCents),
                    valueColor = when {
                        goal.monthlyPaceCents > 0L -> colors.income
                        isStalled -> colors.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Monthly Needed",
                    value = monthlyNeededCents?.let {
                        "${MoneyUtils.formatCents(it, showCents = false)}/mo"
                    } ?: "Flexible",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitStatTile(
                    label = "Desired Date",
                    value = goal.targetDate?.let { LocalDateUtils.formatMonthShortLabel(it.take(7)) } ?: "ASAP",
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Projected",
                    value = projectedLabel,
                    valueColor = when {
                        goal.isCompleted -> colors.income
                        isStalled || isPastTarget -> colors.warning
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Linked Funding Accounts (supports multi-account allocations + legacy single account)
        val sourceChips = goal.allocations.mapNotNull { alloc ->
            val acct = accountMap[alloc.accountId]
            val acctName = acct?.name ?: alloc.accountName.takeIf { it.isNotBlank() }
            val fundedCents = alloc.resolvedFundedCents(acct?.currentBalanceCents ?: 0L)
            acctName?.let { Triple(alloc.accountId, it, formatCurrencySmart(fundedCents)) }
        }.ifEmpty {
            listOfNotNull(
                goal.linkedAccountId?.let { id ->
                    accountMap[id]?.let { acct -> Triple(id, acct.name, formatCurrencySmart(acct.currentBalanceCents)) }
                }
            )
        }

        if (sourceChips.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Funding Sources",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sourceChips.forEach { (accountId, accountName, amountLabel) ->
                        Surface(
                            onClick = {
                                haptics.tick()
                                onOpenAccount(accountId)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    OrbitIcons.Accounts,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$accountName · $amountLabel",
                                    style = MaterialTheme.typography.labelMedium.merge(TabularNumStyle),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open account",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        goal.notes?.takeIf { it.isNotBlank() }?.let { note ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        GoalHistoryAndImagineCard(
            goal = goal,
            selectedMonths = historyMonths,
            onTimeframeChanged = onTimeframeChanged,
            whatIfBoostCents = whatIfBoostCents,
            onWhatIfBoostChanged = onWhatIfBoostChanged
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OrbitActionChip(
                label = "Delete Goal",
                leadingIcon = Icons.Default.Delete,
                isDestructive = true,
                onClick = {
                    haptics.reject()
                    onRequestDelete()
                }
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
