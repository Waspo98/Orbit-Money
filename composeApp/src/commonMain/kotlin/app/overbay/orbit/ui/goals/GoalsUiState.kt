package app.overbay.orbit.ui.goals

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.Cents

data class GoalsUiState(
    val goals: List<Goal> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val accountMap: Map<Long, Account> = emptyMap(),
    val totalSavedCents: Cents = 0L,
    val totalTargetCents: Cents = 0L,
    val inspectingGoalId: Long? = null,
    val editingGoal: Goal? = null,
    val showEditorSheet: Boolean = false,
    val allocatingGoal: Goal? = null,
    val showAllocationDialog: Boolean = false,
    val isReorderMode: Boolean = false,
    val isLoading: Boolean = false
) {
    val activeGoals get() = goals.filter { !it.isCompleted }
    val completedGoals get() = goals.filter { it.isCompleted }
    val overallProgressRatio get() = if (totalTargetCents > 0L) totalSavedCents.toDouble() / totalTargetCents.toDouble() else 0.0
    val inspectingGoal: Goal?
        get() = goals.find { it.id == inspectingGoalId } ?: editingGoal ?: allocatingGoal
}
