package app.overbay.orbit.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

class GoalsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _inspectingGoalId = MutableStateFlow<Long?>(null)
    private val _editingGoal = MutableStateFlow<Goal?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _allocatingGoal = MutableStateFlow<Goal?>(null)
    private val _showAllocationDialog = MutableStateFlow(false)

    private val dataFlow = combine(
        repository.goals,
        repository.accounts
    ) { goals, accounts ->
        Pair(goals, accounts)
    }

    private val dialogFlow = combine(
        _editingGoal,
        _showEditorSheet,
        _allocatingGoal,
        _showAllocationDialog
    ) { editing, showSheet, allocating, showAlloc ->
        GoalsDialogState(editing, showSheet, allocating, showAlloc)
    }

    val uiState: StateFlow<GoalsUiState> = combine(
        dataFlow,
        dialogFlow,
        _inspectingGoalId
    ) { (goals, accounts), dialog, inspectingId ->
        val sortedGoals = goals.sortedWith(
            compareBy<Goal> { it.isCompleted }.thenBy { it.sortOrder }.thenBy { it.id }
        )
        val totalSaved = goals.sumOf { it.currentAmountCents }
        val totalTarget = goals.sumOf { it.targetAmountCents }

        GoalsUiState(
            goals = sortedGoals,
            accounts = accounts,
            accountMap = accounts.associateBy { it.id },
            totalSavedCents = totalSaved,
            totalTargetCents = totalTarget,
            inspectingGoalId = inspectingId,
            editingGoal = dialog.editing,
            showEditorSheet = dialog.showSheet,
            allocatingGoal = dialog.allocating,
            showAllocationDialog = dialog.showAlloc,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState(isLoading = true)
    )

    fun selectGoalForInspect(id: Long?) {
        if (id != null && _inspectingGoalId.value == id && !_showEditorSheet.value && !_showAllocationDialog.value) {
            _inspectingGoalId.value = null
        } else {
            _inspectingGoalId.value = id
            _showEditorSheet.value = false
            _editingGoal.value = null
            _showAllocationDialog.value = false
            _allocatingGoal.value = null
        }
    }

    fun dismissInspectorPane() {
        _inspectingGoalId.value = null
        _showEditorSheet.value = false
        _editingGoal.value = null
        _showAllocationDialog.value = false
        _allocatingGoal.value = null
    }

    fun openAddGoal() {
        _showAllocationDialog.value = false
        _allocatingGoal.value = null
        _editingGoal.value = Goal(
            id = 0L,
            name = "",
            targetAmountCents = 0L,
            currentAmountCents = 0L
        )
        _showEditorSheet.value = true
    }

    fun openEditGoal(goal: Goal) {
        _inspectingGoalId.value = goal.id
        _showAllocationDialog.value = false
        _allocatingGoal.value = null
        _editingGoal.value = goal
        _showEditorSheet.value = true
    }

    fun closeEditorSheet() {
        _showEditorSheet.value = false
        _editingGoal.value = null
    }

    fun saveGoal(goal: Goal) {
        viewModelScope.launch {
            repository.saveGoal(goal)
            closeEditorSheet()
        }
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch {
            repository.deleteGoal(id)
            if (_inspectingGoalId.value == id) {
                _inspectingGoalId.value = null
            }
            closeEditorSheet()
        }
    }

    fun openAllocationDialog(goal: Goal) {
        _inspectingGoalId.value = goal.id
        _showEditorSheet.value = false
        _editingGoal.value = null
        _allocatingGoal.value = goal
        _showAllocationDialog.value = true
    }

    fun closeAllocationDialog() {
        _showAllocationDialog.value = false
        _allocatingGoal.value = null
    }

    fun depositToGoal(goalId: Long, depositAmountCents: Cents) {
        viewModelScope.launch {
            val goal = repository.goals.value.find { it.id == goalId } ?: return@launch
            val updated = goal.copy(
                currentAmountCents = max(0L, goal.currentAmountCents + depositAmountCents)
            )
            repository.saveGoal(updated)
            closeAllocationDialog()
        }
    }

    fun calculateMonthlyRequiredCents(goal: Goal): Cents? {
        val targetDateIso = goal.targetDate ?: return null
        if (goal.isCompleted) return 0L

        val todayIso = LocalDateUtils.todayIsoDate()
        val today = LocalDateUtils.parseIsoDate(todayIso) ?: return null
        val target = LocalDateUtils.parseIsoDate(targetDateIso) ?: return null

        val months = (target.year - today.year) * 12 + (target.monthNumber - today.monthNumber)
        if (months <= 0) return goal.remainingAmountCents

        return goal.remainingAmountCents / months
    }
}

private data class GoalsDialogState(
    val editing: Goal?,
    val showSheet: Boolean,
    val allocating: Goal?,
    val showAlloc: Boolean
)
