package app.overbay.orbit.ui.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CanopyMenuAction
import app.overbay.orbit.ui.components.CanopyMoreMenuButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.HeroScrollState
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.goals.components.GoalDetailInspectorPane
import app.overbay.orbit.ui.goals.components.GoalsMasterList
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Primary Savings Goals screen with active-target summary, segmented Goals/Funding view,
 * adaptive two-pane inspector on wide screens, and a bottom modal sheet on compact phones.
 */
@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posture = LocalDevicePosture.current
    val haptics = LocalOrbitHaptics.current
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (state.inspectingGoal != null || state.showEditorSheet)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                OrbitFloatingActionButton(
                    visible = heroScrollState.isFabVisible,
                    onClick = { viewModel.openAddGoal() },
                    contentDescription = "Add Goal"
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    GoalsScreenCanopy(
                        state = state,
                        heroScrollState = heroScrollState,
                        onToggleReorder = viewModel::toggleReorderMode,
                        onToggleCompleted = viewModel::toggleCompletedGoals,
                        onOpenSettings = { onNavigate(OrbitNavKey.Settings()) }
                    )

                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        GoalsMasterList(
                            state = state,
                            contentPadding = PaddingValues(start = startPad, end = endPad, top = 16.dp, bottom = 88.dp),
                            calculateMonthlyNeeded = viewModel::calculateMonthlyRequiredCents,
                            onSelectTab = { tab ->
                                haptics.tick()
                                viewModel.selectTab(tab)
                            },
                            onGoalClick = { goal ->
                                haptics.tick()
                                viewModel.selectGoalForInspect(goal.id)
                            },
                            onAddGoal = { viewModel.openAddGoal() },
                            onMoveGoal = viewModel::moveGoal,
                            onToggleReorder = viewModel::toggleReorderMode,
                            onToggleCompleted = viewModel::toggleCompletedGoals,
                            onToggleFundingDetails = viewModel::toggleFundingDetails,
                            onToggleUnallocated = viewModel::toggleUnallocatedAccounts,
                            onOpenAccount = { accountId -> onNavigate(OrbitNavKey.Accounts(accountId)) }
                        )
                    }

                    if (isTwoPane) {
                        OrbitMorphingSplitLayout(
                            isDetailOpen = isDetailOpen,
                            onDismissDetail = { viewModel.dismissInspectorPane() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            masterContent = masterList,
                            detailContent = {
                                GoalDetailInspectorPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onNavigate = onNavigate,
                                    onClosePane = { viewModel.dismissInspectorPane() }
                                )
                            }
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            masterList()
                        }
                    }
                }
            }
        }

        if (!isTwoPane && (state.inspectingGoal != null || state.showEditorSheet)) {
            OrbitModalSheet(
                onDismissRequest = { viewModel.dismissInspectorPane() }
            ) {
                GoalDetailInspectorPane(
                    state = state,
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onClosePane = { viewModel.dismissInspectorPane() }
                )
            }
        }

        state.deleteConfirmGoal?.let { target ->
            OrbitConfirmationDialog(
                title = "Delete Savings Goal?",
                message = "Remove ${target.labelWithEmoji}? Your connected accounts and balances will not be affected.",
                confirmText = "Delete Goal",
                isDestructive = true,
                onConfirm = { viewModel.deleteGoal(target.id) },
                onDismiss = { viewModel.dismissDeleteGoal() }
            )
        }
    }
}

@Composable
private fun GoalsScreenCanopy(
    state: GoalsUiState,
    heroScrollState: HeroScrollState,
    onToggleReorder: () -> Unit,
    onToggleCompleted: () -> Unit,
    onOpenSettings: () -> Unit
) {
    MorphingPageHero(
        kicker = "SAVING TARGETS",
        title = "Savings Goals",
        subtitle = "Link accounts, track contributions, and model goal ETAs.",
        scrollState = heroScrollState,
        canopyBar = { _, isCompact, barHeight ->
            val hasActive = state.activeGoals.isNotEmpty()
            val savedCents = if (hasActive) state.activeSavedCents else state.totalSavedCents
            val targetCents = if (hasActive) state.activeTargetCents else state.totalTargetCents
            val ratio = if (hasActive) state.activeProgressRatio else state.overallProgressRatio
            val pct = (ratio * 100.0).roundToInt()

            val savedShort = MoneyUtils.formatCents(savedCents, showCents = false)
            val targetShort = MoneyUtils.formatCents(targetCents, showCents = false)
            val compactSummary = "$savedShort · $pct%"
            val expandedSummary = if (targetCents > 0L) {
                "Saved $savedShort of $targetShort ($pct%)"
            } else {
                "Plan savings milestones"
            }

            CanopySurfacePill(
                expandedText = expandedSummary,
                compactText = compactSummary,
                isCompact = isCompact,
                leadingIcon = OrbitIcons.Goals,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            CanopyMoreMenuButton(
                buttonSize = barHeight,
                actions = listOf(
                    CanopyMenuAction(
                        label = if (state.isReorderMode) "Done Reordering" else "Reorder Goals",
                        isChecked = state.isReorderMode,
                        onClick = onToggleReorder
                    ),
                    CanopyMenuAction(
                        label = if (state.showCompletedGoals) "Hide Completed Goals" else "Show Completed Goals",
                        isChecked = !state.showCompletedGoals,
                        onClick = onToggleCompleted
                    )
                )
            )

            CanopySettingsButton(
                buttonSize = barHeight,
                onClick = onOpenSettings
            )
        }
    )
}
