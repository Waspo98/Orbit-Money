package app.overbay.orbit.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CanopyMenuAction
import app.overbay.orbit.ui.components.CanopyMoreMenuButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.goals.components.GoalAccountsOverviewCard
import app.overbay.orbit.ui.goals.components.GoalDetailInspectorPane
import app.overbay.orbit.ui.goals.components.GoalProgressCard
import app.overbay.orbit.ui.goals.components.GoalTotalSavedBanner
import app.overbay.orbit.ui.goals.dialogs.GoalAllocationDialog
import app.overbay.orbit.ui.goals.dialogs.GoalEditorSheet
import app.overbay.orbit.util.LocalDevicePosture
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (
            state.inspectingGoal != null ||
                state.showEditorSheet ||
                state.showAllocationDialog
            )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (!state.isLoading) {
                    OrbitFloatingActionButton(
                        visible = heroScrollState.isFabVisible,
                        onClick = { viewModel.openAddGoal() },
                        contentDescription = "New Goal"
                    )
                }
            }
        ) { paddingValues ->
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .nestedScroll(heroScrollState.nestedScrollConnection)
                ) {
                    MorphingPageHero(
                        kicker = "SAVING TARGETS",
                        title = "Savings Goals",
                        subtitle = "Connect asset accounts, allocate savings, and project when each target lands.",
                        canNavigateBack = true,
                        onBack = onBack,
                        scrollState = heroScrollState,
                        canopyBar = { _, isCompact, barHeight ->
                            val pct = (state.overallProgressRatio * 100).roundToInt()
                            val savedFormatted = MoneyUtils.formatCents(state.totalSavedCents, showCents = false)
                            val targetFormatted = MoneyUtils.formatCents(state.totalTargetCents, showCents = false)
                            CanopySurfacePill(
                                expandedText = if (state.totalTargetCents > 0L) {
                                    "Saved $savedFormatted of $targetFormatted · $pct%"
                                } else {
                                    "${state.goals.size} Savings Goals"
                                },
                                compactText = if (state.totalTargetCents > 0L) {
                                    "$savedFormatted · $pct%"
                                } else {
                                    "${state.goals.size} Goals"
                                },
                                isCompact = isCompact,
                                leadingIcon = OrbitIcons.Goals,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            CanopyMoreMenuButton(
                                buttonSize = barHeight,
                                contentDescription = "Goal Options",
                                actions = listOf(
                                    CanopyMenuAction(
                                        label = if (state.isReorderMode) "Done Reordering" else "Reorder Goals",
                                        isChecked = state.isReorderMode,
                                        onClick = { viewModel.toggleReorderMode() }
                                    ),
                                    CanopyMenuAction(
                                        label = if (state.showAccountsOverview) "Hide Accounts Overview" else "Show Accounts Overview",
                                        isChecked = state.showAccountsOverview,
                                        onClick = { viewModel.toggleAccountsOverview() }
                                    ),
                                    CanopyMenuAction(
                                        label = "New Goal",
                                        onClick = { viewModel.openAddGoal() }
                                    )
                                )
                            )

                            CanopySettingsButton(
                                buttonSize = barHeight,
                                onClick = { onNavigate(OrbitNavKey.Settings) }
                            )
                        }
                    )

                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        val activeReorderState = rememberDragReorderState(
                            items = state.activeGoals,
                            keySelector = { it.id },
                            onMove = { from, to -> viewModel.moveGoal(completedGroup = false, fromIndex = from, toIndex = to) },
                            itemSpacingDp = 16.dp
                        )
                        val completedReorderState = rememberDragReorderState(
                            items = state.completedGoals,
                            keySelector = { it.id },
                            onMove = { from, to -> viewModel.moveGoal(completedGroup = true, fromIndex = from, toIndex = to) },
                            itemSpacingDp = 16.dp
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = startPad, end = endPad, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (state.totalTargetCents > 0L) {
                                item {
                                    GoalTotalSavedBanner(
                                        totalSavedCents = state.totalSavedCents,
                                        totalTargetCents = state.totalTargetCents,
                                        progressRatio = state.overallProgressRatio
                                    )
                                }
                            }

                            if (state.showAccountsOverview && state.accountSummaries.isNotEmpty()) {
                                item {
                                    GoalAccountsOverviewCard(
                                        accountSummaries = state.accountSummaries,
                                        goals = state.goals,
                                        onOpenAccount = { acctId ->
                                            onNavigate(OrbitNavKey.Accounts(accountId = acctId))
                                        }
                                    )
                                }
                            }

                            if (state.activeGoals.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Active Goals (${state.activeGoals.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                items(state.activeGoals, key = { it.id }) { goal ->
                                    val acct = goal.linkedAccountId?.let { state.accountMap[it] }
                                    val monthlyNeeded = viewModel.calculateMonthlyRequiredCents(goal)
                                    GoalProgressCard(
                                        goal = goal,
                                        accountName = acct?.name,
                                        monthlyNeededCents = monthlyNeeded,
                                        isInspected = isTwoPane && state.inspectingGoalId == goal.id,
                                        onClick = {
                                            if (isTwoPane) {
                                                viewModel.selectGoalForInspect(goal.id)
                                            } else {
                                                viewModel.openEditGoal(goal)
                                            }
                                        },
                                        onDepositClick = { viewModel.openAllocationDialog(goal) },
                                        dragHandle = if (state.isReorderMode) {
                                            { OrbitDragHandle(state = activeReorderState, key = goal.id) }
                                        } else null,
                                        modifier = Modifier.dragReorderItem(
                                            state = if (state.isReorderMode) activeReorderState else null,
                                            key = goal.id
                                        )
                                    )
                                }
                            }

                            if (state.completedGoals.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Completed Goals (${state.completedGoals.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                items(state.completedGoals, key = { it.id }) { goal ->
                                    val acct = goal.linkedAccountId?.let { state.accountMap[it] }
                                    GoalProgressCard(
                                        goal = goal,
                                        accountName = acct?.name,
                                        monthlyNeededCents = null,
                                        isInspected = isTwoPane && state.inspectingGoalId == goal.id,
                                        onClick = {
                                            if (isTwoPane) {
                                                viewModel.selectGoalForInspect(goal.id)
                                            } else {
                                                viewModel.openEditGoal(goal)
                                            }
                                        },
                                        onDepositClick = {},
                                        dragHandle = if (state.isReorderMode) {
                                            { OrbitDragHandle(state = completedReorderState, key = goal.id) }
                                        } else null,
                                        modifier = Modifier.dragReorderItem(
                                            state = if (state.isReorderMode) completedReorderState else null,
                                            key = goal.id
                                        )
                                    )
                                }
                            }

                            if (state.goals.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        title = "No Goals Yet",
                                        description = "Set targets for emergency funds, major purchases, or future vacations.",
                                        actionButtonText = "+ Add First Goal",
                                        onActionClick = { viewModel.openAddGoal() }
                                    )
                                }
                            }
                        }
                    }

                    if (isTwoPane) {
                        OrbitMorphingSplitLayout(
                            isDetailOpen = isDetailOpen,
                            onDismissDetail = { viewModel.dismissInspectorPane() },
                            masterContent = masterList,
                            detailContent = {
                                GoalDetailInspectorPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onNavigate = onNavigate,
                                    onClosePane = { viewModel.dismissInspectorPane() }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        masterList()
                    }
                }
            }
        }

        // Compact Phone Modal Editor Sheet & Allocation Dialog
        if (!isTwoPane && state.showEditorSheet) {
            GoalEditorSheet(
                goal = state.editingGoal,
                accounts = state.accounts,
                existingGoals = state.goals,
                onSave = { goal, steal -> viewModel.saveGoalWithAllocations(goal, steal) },
                onDelete = { viewModel.deleteGoal(it) },
                onDismiss = { viewModel.closeEditorSheet() },
                onOpenLinkedAccount = { acctId ->
                    viewModel.closeEditorSheet()
                    onNavigate(OrbitNavKey.Accounts(accountId = acctId))
                }
            )
        }

        val allocatingGoal = state.allocatingGoal
        if (!isTwoPane && state.showAllocationDialog && allocatingGoal != null) {
            GoalAllocationDialog(
                goal = allocatingGoal,
                onConfirmDeposit = { goalId, amount ->
                    viewModel.depositToGoal(goalId, amount)
                },
                onDismiss = { viewModel.closeAllocationDialog() }
            )
        }
    }
}
