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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(
    private val repository: OrbitRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _inspectingGoalId = MutableStateFlow<Long?>(null)
    private val _editingGoal = MutableStateFlow<Goal?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _deleteConfirmGoalId = MutableStateFlow<Long?>(null)
    private val _isReorderMode = MutableStateFlow(false)
    private val _activeTab = MutableStateFlow(GoalsTab.GOALS)
    private val _showCompletedGoals = MutableStateFlow(true)
    private val _showUnallocatedAccounts = MutableStateFlow(false)
    private val _showFundingDetails = MutableStateFlow(false)
    private val _historyMonths = MutableStateFlow(12)
    private val _whatIfBoostCents = MutableStateFlow(0L)
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
        _deleteConfirmGoalId
    ) { editing, showSheet, deleteConfirmId ->
        GoalsDialogState(editing, showSheet, deleteConfirmId)
    }

    private val displayPrefsFlow = combine(
        _activeTab,
        _showCompletedGoals,
        _showUnallocatedAccounts,
        _showFundingDetails,
        _historyMonths
    ) { activeTab, showCompleted, showUnallocated, showDetails, historyMonths ->
        GoalsDisplayPrefs(activeTab, showCompleted, showUnallocated, showDetails, historyMonths)
    }

    private val controlFlow = combine(
        _inspectingGoalId,
        _isReorderMode,
        _whatIfBoostCents,
        _goalSortOverrides,
        displayPrefsFlow
    ) { inspectingId, isReorder, whatIfBoost, sortOverrides, prefs ->
        GoalsControlState(inspectingId, isReorder, whatIfBoost, sortOverrides, prefs)
    }

    val uiState: StateFlow<GoalsUiState> = combine(
        dataFlow,
        dialogFlow,
        controlFlow
    ) { input, dialog, control ->
        val enrichedGoals = input.goals.map { goal ->
            val history = GoalAllocationEngine.reconstructGoalHistory(
                goal = goal,
                accounts = input.accounts,
                balanceRecords = input.balanceRecords,
                transactions = input.transactions,
                monthsCount = 24
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
                .thenBy { control.sortOverrides[it.id] ?: it.sortOrder }
                .thenBy { it.id }
        )

        val accountSummaries = GoalAllocationEngine.computeAccountSummaries(
            accounts = input.accounts,
            goals = enrichedGoals
        )

        val activeGoals = sortedGoals.filter { !it.isCompleted }
        val totalSaved = sortedGoals.sumOf { it.currentAmountCents }
        val totalTarget = sortedGoals.sumOf { it.targetAmountCents }
        val activeSaved = activeGoals.sumOf { it.currentAmountCents }
        val activeTarget = activeGoals.sumOf { it.targetAmountCents }
        val combinedPace = activeGoals.sumOf { it.monthlyPaceCents.coerceAtLeast(0L) }
        val combinedRequired = activeGoals.sumOf { calculateMonthlyRequiredCents(it) ?: 0L }
        val totalUnallocated = accountSummaries.sumOf { it.remainingAmountCents }
        val onTrackCount = activeGoals.count { goal ->
            val pace = goal.monthlyPaceCents
            if (pace <= 0L) false
            else {
                val req = calculateMonthlyRequiredCents(goal)
                req == null || pace >= req
            }
        }

        GoalsUiState(
            goals = sortedGoals,
            accounts = input.accounts,
            accountMap = input.accounts.associateBy { it.id },
            accountSummaries = accountSummaries,
            totalSavedCents = totalSaved,
            totalTargetCents = totalTarget,
            activeSavedCents = activeSaved,
            activeTargetCents = activeTarget,
            combinedMonthlyPaceCents = combinedPace,
            combinedMonthlyRequiredCents = combinedRequired,
            totalUnallocatedCashCents = totalUnallocated,
            onTrackActiveCount = onTrackCount,
            activeTab = control.prefs.activeTab,
            showCompletedGoals = control.prefs.showCompletedGoals,
            showUnallocatedAccounts = control.prefs.showUnallocatedAccounts,
            showFundingDetails = control.prefs.showFundingDetails,
            historyMonths = control.prefs.historyMonths,
            whatIfBoostCents = control.whatIfBoostCents,
            inspectingGoalId = control.inspectingId,
            editingGoal = dialog.editing,
            showEditorSheet = dialog.showSheet,
            deleteConfirmGoalId = dialog.deleteConfirmId,
            isReorderMode = control.isReorder,
            isLoading = false
        )
    }.flowOn(computeDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState(isLoading = true)
    )

    fun selectGoalForInspect(id: Long?) {
        if (id != null && _inspectingGoalId.value == id && !_showEditorSheet.value) {
            _inspectingGoalId.value = null
            _whatIfBoostCents.value = 0L
        } else {
            val targetGoal = id?.let { targetId -> uiState.value.goals.find { it.id == targetId } }
            _inspectingGoalId.value = id
            _showEditorSheet.value = false
            _editingGoal.value = null
            _whatIfBoostCents.value = targetGoal?.let { computeSmartDefaultBoostCents(it) } ?: 0L
        }
    }

    fun selectGoal(id: Long?) = selectGoalForInspect(id)

    fun dismissInspectorPane() {
        _inspectingGoalId.value = null
        _showEditorSheet.value = false
        _editingGoal.value = null
        _whatIfBoostCents.value = 0L
    }

    fun openAddGoal() {
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
        _editingGoal.value = goal
        _showEditorSheet.value = true
    }

    fun closeEditorSheet() {
        _showEditorSheet.value = false
        _editingGoal.value = null
    }

    fun selectTab(tab: GoalsTab) {
        _activeTab.value = tab
    }

    fun toggleReorderMode() {
        _isReorderMode.value = !_isReorderMode.value
    }

    fun toggleCompletedGoals() {
        _showCompletedGoals.value = !_showCompletedGoals.value
    }

    fun toggleUnallocatedAccounts() {
        _showUnallocatedAccounts.value = !_showUnallocatedAccounts.value
    }

    fun toggleFundingDetails() {
        _showFundingDetails.value = !_showFundingDetails.value
    }

    fun setHistoryMonths(months: Int) {
        if (months in listOf(6, 12, 24)) {
            _historyMonths.value = months
        }
    }

    fun setWhatIfBoostCents(cents: Cents) {
        _whatIfBoostCents.value = cents.coerceAtLeast(0L)
    }

    fun moveGoal(completedGroup: Boolean, fromIndex: Int, toIndex: Int) {
        val currentActive = uiState.value.activeGoals.toMutableList()
        val currentCompleted = uiState.value.completedGoals.toMutableList()
        val list = if (completedGroup) currentCompleted else currentActive
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)

        val updatedActive = if (completedGroup) currentActive else list
        val updatedCompleted = if (completedGroup) list else currentCompleted
        val allReordered = updatedActive + updatedCompleted

        val newOverrides = _goalSortOverrides.value.toMutableMap()
        allReordered.forEachIndexed { idx, goal -> newOverrides[goal.id] = idx }
        _goalSortOverrides.value = newOverrides
        viewModelScope.launch {
            repository.reorderGoals(allReordered.map { it.id })
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

    fun requestDeleteGoal(id: Long) {
        _deleteConfirmGoalId.value = id
    }

    fun dismissDeleteGoal() {
        _deleteConfirmGoalId.value = null
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch {
            repository.deleteGoal(id)
            _deleteConfirmGoalId.value = null
            if (_inspectingGoalId.value == id) {
                _inspectingGoalId.value = null
            }
            closeEditorSheet()
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

    fun calculateNeededExtraMonthlyCents(goal: Goal): Cents? {
        val requiredMonthly = calculateMonthlyRequiredCents(goal) ?: return null
        val pace = (goal.monthlyPaceCents).coerceAtLeast(0L)
        return (requiredMonthly - pace).coerceAtLeast(0L)
    }

    private fun computeSmartDefaultBoostCents(goal: Goal): Cents {
        if (goal.isCompleted) return 0L
        val neededExtra = calculateNeededExtraMonthlyCents(goal)
        if (neededExtra != null && neededExtra > 0L) return neededExtra
        return if (goal.monthlyPaceCents <= 0L) 100_00L else 0L
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
        val deleteConfirmId: Long?
    )

    private data class GoalsDisplayPrefs(
        val activeTab: GoalsTab,
        val showCompletedGoals: Boolean,
        val showUnallocatedAccounts: Boolean,
        val showFundingDetails: Boolean,
        val historyMonths: Int
    )

    private data class GoalsControlState(
        val inspectingId: Long?,
        val isReorder: Boolean,
        val whatIfBoostCents: Cents,
        val sortOverrides: Map<Long, Int>,
        val prefs: GoalsDisplayPrefs
    )
}
