package app.overbay.orbit.ui.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.goals.components.GoalDetailInspectorPane
import app.overbay.orbit.ui.goals.components.GoalProgressCard
import app.overbay.orbit.ui.goals.dialogs.GoalAllocationDialog
import app.overbay.orbit.ui.goals.dialogs.GoalEditorSheet
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
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
                        onBack = { onNavigate(OrbitNavKey.Dashboard) },
                        scrollState = heroScrollState
                    )

                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = startPad, end = endPad, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (state.totalTargetCents > 0L) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "TOTAL SAVED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = MoneyUtils.formatCents(state.totalSavedCents, showCents = false),
                                                        style = MaterialTheme.typography.headlineMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "TOTAL TARGET",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = MoneyUtils.formatCents(state.totalTargetCents, showCents = false),
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            LinearProgressIndicator(
                                                progress = { state.overallProgressRatio.toFloat().coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
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
                                        onDepositClick = { viewModel.openAllocationDialog(goal) }
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
                                        onDepositClick = {}
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
                onSave = { viewModel.saveGoal(it) },
                onDelete = { viewModel.deleteGoal(it) },
                onDismiss = { viewModel.closeEditorSheet() }
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
