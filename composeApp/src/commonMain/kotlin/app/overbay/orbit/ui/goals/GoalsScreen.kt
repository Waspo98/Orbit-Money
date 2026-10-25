package app.overbay.orbit.ui.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.goals.components.GoalProgressCard
import app.overbay.orbit.ui.goals.dialogs.GoalAllocationDialog
import app.overbay.orbit.ui.goals.dialogs.GoalEditorSheet
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalOrbitHaptics.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.tick()
                    viewModel.openAddGoal()
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal")
            }
        },
        modifier = modifier.fillMaxSize()
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Canopy
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Savings Goals",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Track progress toward milestone funds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Overall Savings Summary Banner
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

                // Active Goals Section
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
                            onClick = { viewModel.openEditGoal(goal) },
                            onDepositClick = { viewModel.openAllocationDialog(goal) }
                        )
                    }
                }

                // Completed Goals Section
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
                            onClick = { viewModel.openEditGoal(goal) },
                            onDepositClick = {}
                        )
                    }
                }

                // Empty State
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
    }

    // Modal Editor Sheet
    if (state.showEditorSheet) {
        GoalEditorSheet(
            goal = state.editingGoal,
            accounts = state.accounts,
            onSave = { viewModel.saveGoal(it) },
            onDelete = { viewModel.deleteGoal(it) },
            onDismiss = { viewModel.closeEditorSheet() }
        )
    }

    // Modal Allocation / Deposit Dialog
    val allocatingGoal = state.allocatingGoal
    if (state.showAllocationDialog && allocatingGoal != null) {
        GoalAllocationDialog(
            goal = allocatingGoal,
            onConfirmDeposit = { goalId, amount ->
                viewModel.depositToGoal(goalId, amount)
            },
            onDismiss = { viewModel.closeAllocationDialog() }
        )
    }
}
