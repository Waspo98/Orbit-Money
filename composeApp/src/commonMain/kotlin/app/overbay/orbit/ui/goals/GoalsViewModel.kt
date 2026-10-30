package app.overbay.orbit.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.reorderGoals
import app.overbay.orbit.data.saveGoalWithAllocations
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.GoalAllocationEngine
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
    private val _isReorderMode = MutableStateFlow(false)
    private val _showAccountsOverview = MutableStateFlow(true)
    private val _goalSortOverrides = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val dataFlow = combine(
        repository.goals,
        repository.accounts,
        repository.transactions,
        repository.snapshot
    ) { goals, accounts, transactions, snapshot ->
        GoalEngineInput(goals, accounts, transactions, snapshot.balanceRecords)
    }

    private val dialogFlow = combine(
        _editingGoal,
        _showEditorSheet,
        _allocatingGoal,
        _showAllocationDialog
    ) { editing, showSheet, allocating, showAlloc ->
        GoalsDialogState(editing, showSheet, allocating, showAlloc)
    }

    private val controlFlow = combine(
        _inspectingGoalId,
        _isReorderMode,
        _showAccountsOverview,
        _goalSortOverrides
    ) { inspectingId, isReorder, showOverview, sortOverrides ->
        GoalsControlState(inspectingId, isReorder, showOverview, sortOverrides)
    }

    val uiState: StateFlow<GoalsUiState> = combine(
        dataFlow,
        dialogFlow,
        controlFlow
    ) { input, dialog, control ->
        val inspectingId = control.inspectingId
        val isReorder = control.isReorder
        val showOverview = control.showOverview
        val sortOverrides = control.sortOverrides
        // Enrich goals with 12-month historical reconstruction, trailing pace, and ETA projections
        val enrichedGoals = input.goals.map { goal ->
            val history = GoalAllocationEngine.reconstructGoalHistory(
                goal = goal,
                accounts = input.accounts,
                balanceRecords = input.balanceRecords,
                transactions = input.transactions,
                monthsCount = 12
            )
            val (pace, eta) = GoalAllocationEngine.computeMonthlyPaceAndEta(goal, history)
            goal.copy(
                history = history,
                monthlyPaceCents = pace,
                eta = eta
            )
        }

        val sortedGoals = enrichedGoals.sortedWith(
            compareBy<Goal> { it.isCompleted }
                .thenBy { sortOverrides[it.id] ?: it.sortOrder }
                .thenBy { it.id }
        )

        val accountSummaries = GoalAllocationEngine.computeAccountSummaries(
            accounts = input.accounts,
            goals = enrichedGoals
        )

        val totalSaved = enrichedGoals.sumOf { it.currentAmountCents }
        val totalTarget = enrichedGoals.sumOf { it.targetAmountCents }

        GoalsUiState(
            goals = sortedGoals,
            accounts = input.accounts,
            accountMap = input.accounts.associateBy { it.id },
            accountSummaries = accountSummaries,
            totalSavedCents = totalSaved,
            totalTargetCents = totalTarget,
            inspectingGoalId = inspectingId,
            editingGoal = dialog.editing,
            showEditorSheet = dialog.showSheet,
            allocatingGoal = dialog.allocating,
            showAllocationDialog = dialog.showAlloc,
            isReorderMode = isReorder,
            showAccountsOverview = showOverview,
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

    fun toggleReorderMode() {
        _isReorderMode.value = !_isReorderMode.value
    }

    fun toggleAccountsOverview() {
        _showAccountsOverview.value = !_showAccountsOverview.value
    }

    fun moveGoal(completedGroup: Boolean, fromIndex: Int, toIndex: Int) {
        val list = (if (completedGroup) uiState.value.completedGoals else uiState.value.activeGoals).toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        val newOverrides = _goalSortOverrides.value.toMutableMap()
        list.forEachIndexed { idx, goal -> newOverrides[goal.id] = idx }
        _goalSortOverrides.value = newOverrides
        viewModelScope.launch {
            repository.reorderGoals(list.map { it.id })
        }
    }

    fun moveGoalUp(goalId: Long) {
        val currentGoals = uiState.value.activeGoals
        val index = currentGoals.indexOfFirst { it.id == goalId }
        if (index > 0) {
            moveGoal(completedGroup = false, fromIndex = index, toIndex = index - 1)
        }
    }

    fun moveGoalDown(goalId: Long) {
        val currentGoals = uiState.value.activeGoals
        val index = currentGoals.indexOfFirst { it.id == goalId }
        if (index in 0 until currentGoals.size - 1) {
            moveGoal(completedGroup = false, fromIndex = index, toIndex = index + 1)
        }
    }

    fun saveGoalWithAllocations(goal: Goal, stealFromOthers: Boolean = false) {
        viewModelScope.launch {
            repository.saveGoalWithAllocations(goal, stealFromOthers)
            closeEditorSheet()
        }
    }

    fun saveGoal(goal: Goal) {
        saveGoalWithAllocations(goal, stealFromOthers = false)
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
            repository.saveGoalWithAllocations(updated, stealFromOthers = false)
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

    private data class GoalEngineInput(
        val goals: List<Goal>,
        val accounts: List<app.overbay.orbit.data.model.Account>,
        val transactions: List<app.overbay.orbit.data.model.Transaction>,
        val balanceRecords: List<app.overbay.orbit.data.model.BalanceRecord>
    )

    private data class GoalsDialogState(
        val editing: Goal?,
        val showSheet: Boolean,
        val allocating: Goal?,
        val showAlloc: Boolean
    )

    private data class GoalsControlState(
        val inspectingId: Long?,
        val isReorder: Boolean,
        val showOverview: Boolean,
        val sortOverrides: Map<Long, Int>
    )
}
