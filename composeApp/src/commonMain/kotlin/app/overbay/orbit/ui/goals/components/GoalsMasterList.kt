package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitDragReorderState
import app.overbay.orbit.ui.components.OrbitExpandableChevron
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.ui.components.rememberOrbitLazyListState
import app.overbay.orbit.ui.goals.GoalsTab
import app.overbay.orbit.ui.goals.GoalsUiState

/**
 * Scrollable master list for Savings Goals featuring the active-target summary hero,
 * Goals vs Funding tab switcher, and reorderable goal cards.
 */
@Composable
fun GoalsMasterList(
    state: GoalsUiState,
    contentPadding: PaddingValues,
    calculateMonthlyNeeded: (Goal) -> Cents?,
    onSelectTab: (GoalsTab) -> Unit,
    onGoalClick: (Goal) -> Unit,
    onAddGoal: () -> Unit,
    onMoveGoal: (Boolean, Int, Int) -> Unit,
    onToggleReorder: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleFundingDetails: () -> Unit,
    onToggleUnallocated: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeReorderState = rememberDragReorderState(
        items = state.activeGoals,
        keySelector = { it.id },
        onMove = { from, to -> onMoveGoal(false, from, to) },
        itemSpacingDp = 12.dp
    )
    val completedReorderState = rememberDragReorderState(
        items = state.completedGoals,
        keySelector = { it.id },
        onMove = { from, to -> onMoveGoal(true, from, to) },
        itemSpacingDp = 12.dp
    )
    val listState = rememberOrbitLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.totalTargetCents > 0L) {
            item(key = "goals_summary_hero") {
                GoalsSummaryHeroCard(
                    activeSavedCents = state.activeSavedCents,
                    activeTargetCents = state.activeTargetCents,
                    activeProgressRatio = state.activeProgressRatio,
                    totalSavedCents = state.totalSavedCents,
                    totalTargetCents = state.totalTargetCents,
                    completedCount = state.completedGoals.size,
                    completedSavedCents = state.completedSavedCents,
                    combinedMonthlyPaceCents = state.combinedMonthlyPaceCents,
                    combinedMonthlyRequiredCents = state.combinedMonthlyRequiredCents,
                    totalUnallocatedCashCents = state.totalUnallocatedCashCents,
                    onOpenFundingAccounts = { onSelectTab(GoalsTab.FUNDING) }
                )
            }
        }

        item(key = "goals_tab_switcher") {
            OrbitSegmentedTabRow(
                items = GoalsTab.entries,
                selected = state.activeTab,
                onSelect = onSelectTab,
                label = { tab ->
                    when (tab) {
                        GoalsTab.GOALS -> "Goals (${state.activeGoals.size})"
                        GoalsTab.FUNDING -> "Funding (${state.allocatedAccountSummaries.size})"
                    }
                }
            )
        }

        when (state.activeTab) {
            GoalsTab.GOALS -> {
                if (state.goals.isEmpty() && !state.isLoading) {
                    item(key = "goals_empty_state") {
                        EmptyStateCard(
                            icon = OrbitIcons.Goals,
                            title = "No Savings Goals Yet",
                            description = "Connect savings accounts, allocate balances, and forecast when you'll hit each milestone.",
                            actionButtonText = "Add First Goal",
                            onActionClick = onAddGoal
                        )
                    }
                } else {
                    if (state.activeGoals.isNotEmpty()) {
                        item(key = "active_goals_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Goals (${state.activeGoals.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.isReorderMode) {
                                    OrbitActionChip(label = "Done", onClick = onToggleReorder)
                                }
                            }
                        }

                        goalCardItems(
                            goals = state.activeGoals,
                            accountMap = state.accountMap,
                            inspectingGoalId = state.inspectingGoalId,
                            isReorderMode = state.isReorderMode,
                            reorderState = activeReorderState,
                            calculateMonthlyNeeded = calculateMonthlyNeeded,
                            onGoalClick = onGoalClick
                        )
                    }

                    if (state.completedGoals.isNotEmpty()) {
                        item(key = "completed_goals_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onToggleCompleted() }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Completed Goals (${state.completedGoals.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OrbitExpandableChevron(expanded = state.showCompletedGoals)
                            }
                        }

                        if (state.showCompletedGoals) {
                            goalCardItems(
                                goals = state.completedGoals,
                                accountMap = state.accountMap,
                                inspectingGoalId = state.inspectingGoalId,
                                isReorderMode = state.isReorderMode,
                                reorderState = completedReorderState,
                                calculateMonthlyNeeded = { 0L },
                                onGoalClick = onGoalClick
                            )
                        }
                    }
                }
            }

            GoalsTab.FUNDING -> {
                item(key = "funding_accounts_section") {
                    GoalFundingAccountsSection(
                        allocatedSummaries = state.allocatedAccountSummaries,
                        unallocatedSummaries = state.unallocatedAccountSummaries,
                        goals = state.goals,
                        showDetails = state.showFundingDetails,
                        showUnallocated = state.showUnallocatedAccounts,
                        onToggleDetails = onToggleFundingDetails,
                        onToggleUnallocated = onToggleUnallocated,
                        onOpenAccount = onOpenAccount
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun LazyListScope.goalCardItems(
    goals: List<Goal>,
    accountMap: Map<Long, Account>,
    inspectingGoalId: Long?,
    isReorderMode: Boolean,
    reorderState: OrbitDragReorderState<Long>,
    calculateMonthlyNeeded: (Goal) -> Cents?,
    onGoalClick: (Goal) -> Unit
) {
    items(goals, key = { it.id }) { goal ->
        GoalProgressCard(
            goal = goal,
            accountMap = accountMap,
            monthlyNeededCents = calculateMonthlyNeeded(goal),
            isInspected = inspectingGoalId == goal.id,
            onClick = { onGoalClick(goal) },
            dragHandle = if (isReorderMode) {
                { OrbitDragHandle(state = reorderState, key = goal.id) }
            } else null,
            modifier = Modifier
                .animateItem(
                    fadeInSpec = OrbitSprings.FloatEffectsDefault,
                    fadeOutSpec = OrbitSprings.FloatEffectsDefault,
                    placementSpec = OrbitSprings.IntOffsetSpatialDefault
                )
                .dragReorderItem(
                    state = if (isReorderMode) reorderState else null,
                    key = goal.id
                )
        )
    }
}
