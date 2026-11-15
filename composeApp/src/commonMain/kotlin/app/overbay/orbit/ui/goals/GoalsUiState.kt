package app.overbay.orbit.ui.goals

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountAllocationSummary
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.Cents

enum class GoalsTab(val label: String) {
    GOALS("Goals"),
    FUNDING("Funding")
}

data class GoalsUiState(
    val goals: List<Goal> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val accountMap: Map<Long, Account> = emptyMap(),
    val accountSummaries: List<AccountAllocationSummary> = emptyList(),
    val totalSavedCents: Cents = 0L,
    val totalTargetCents: Cents = 0L,
    val activeSavedCents: Cents = 0L,
    val activeTargetCents: Cents = 0L,
    val combinedMonthlyPaceCents: Cents = 0L,
    val combinedMonthlyRequiredCents: Cents = 0L,
    val totalUnallocatedCashCents: Cents = 0L,
    val onTrackActiveCount: Int = 0,
    val activeTab: GoalsTab = GoalsTab.GOALS,
    val showCompletedGoals: Boolean = true,
    val showUnallocatedAccounts: Boolean = false,
    val showFundingDetails: Boolean = false,
    val historyMonths: Int = 12,
    val whatIfBoostCents: Cents = 0L,
    val inspectingGoalId: Long? = null,
    val editingGoal: Goal? = null,
    val showEditorSheet: Boolean = false,
    val deleteConfirmGoalId: Long? = null,
    val isReorderMode: Boolean = false,
    val isLoading: Boolean = false
) {
    val activeGoals get() = goals.filter { !it.isCompleted }
    val completedGoals get() = goals.filter { it.isCompleted }
    val completedSavedCents: Cents get() = completedGoals.sumOf { it.currentAmountCents }
    val allocatedAccountSummaries: List<AccountAllocationSummary>
        get() = accountSummaries
            .filter { it.allocatedAmountCents > 0L || it.reserveAmountCents > 0L }
            .sortedByDescending { it.allocatedAmountCents }
    val unallocatedAccountSummaries: List<AccountAllocationSummary>
        get() = accountSummaries.filter { it.allocatedAmountCents == 0L && it.reserveAmountCents == 0L }
    val overallProgressRatio: Double
        get() = if (totalTargetCents > 0L) (totalSavedCents.toDouble() / totalTargetCents.toDouble()).coerceIn(0.0, 1.0) else 0.0
    val activeProgressRatio: Double
        get() = if (activeTargetCents > 0L) (activeSavedCents.toDouble() / activeTargetCents.toDouble()).coerceIn(0.0, 1.0) else 0.0
    val inspectingGoal: Goal?
        get() = goals.find { it.id == inspectingGoalId } ?: editingGoal
    val deleteConfirmGoal: Goal?
        get() = goals.find { it.id == deleteConfirmGoalId }
}
