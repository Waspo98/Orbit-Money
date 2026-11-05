package app.overbay.orbit.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RulePreviewResult
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.RuleMatcherEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(
    private val repository: OrbitRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _inspectingRuleId = MutableStateFlow<Long?>(null)
    private val _editingRule = MutableStateFlow<TransactionRule?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _deleteConfirmId = MutableStateFlow<Long?>(null)
    private val _isApplying = MutableStateFlow(false)
    private val _showWipeAllRulesDialog = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.pendingRuleIdToEdit.collect { pendingId ->
                if (pendingId != null) {
                    val rule = repository.rules.value.find { it.id == pendingId }
                    if (rule != null) {
                        openEditRule(rule)
                    }
                    repository.requestOpenRuleEditor(null)
                }
            }
        }
    }

    private val dataFlow = combine(
        repository.rules,
        repository.categories,
        repository.accounts
    ) { rules, categories, accounts ->
        val sorted = rules.sortedWith(
            compareByDescending<TransactionRule> { it.priority }.thenBy { it.id }
        )
        val categoryMap = categories.associateBy { it.id }
        Triple(sorted, categories, Pair(accounts, categoryMap))
    }

    private val editorFlow = combine(
        _inspectingRuleId,
        _editingRule,
        _showEditorSheet,
        _deleteConfirmId,
        _isApplying
    ) { inspectingId, editing, showSheet, deleteId, isApplying ->
        RulesUiState(
            inspectingRuleId = inspectingId,
            editingRule = editing,
            showEditorSheet = showSheet,
            deleteConfirmRuleId = deleteId,
            isApplyingRules = isApplying
        )
    }.combine(_showWipeAllRulesDialog) { state, showWipe ->
        state.copy(showWipeAllRulesDialog = showWipe)
    }

    val uiState: StateFlow<RulesUiState> = combine(
        dataFlow,
        editorFlow
    ) { (sorted, categories, acctsAndMap), editorState ->
        editorState.copy(
            rules = sorted,
            categories = categories,
            accounts = acctsAndMap.first,
            categoryMap = acctsAndMap.second
        )
    }.flowOn(computeDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RulesUiState(isLoading = true)
    )

    fun selectRuleForInspect(id: Long?) {
        val nextId = if (_inspectingRuleId.value == id) null else id
        _inspectingRuleId.value = nextId
        if (nextId != null && _showEditorSheet.value && _editingRule.value?.id != nextId) {
            _showEditorSheet.value = false
            _editingRule.value = null
        }
    }

    fun dismissInspectorPane() {
        _inspectingRuleId.value = null
        _showEditorSheet.value = false
        _editingRule.value = null
    }

    fun openAddRule() {
        val nextPriority = (uiState.value.rules.maxOfOrNull { it.priority } ?: 0) + 10
        _editingRule.value = TransactionRule(
            id = 0L,
            name = "",
            priority = nextPriority,
            enabled = true
        )
        _showEditorSheet.value = true
    }

    fun openEditRule(rule: TransactionRule) {
        _inspectingRuleId.value = rule.id
        _editingRule.value = rule
        _showEditorSheet.value = true
    }

    fun closeEditorSheet() {
        _showEditorSheet.value = false
        _editingRule.value = null
    }

    fun saveRule(rule: TransactionRule) {
        closeEditorSheet()
        viewModelScope.launch {
            repository.saveRule(rule)
        }
    }

    fun toggleRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setRuleEnabled(id, enabled)
        }
    }

    fun requestDeleteRule(id: Long) {
        _deleteConfirmId.value = id
    }

    fun dismissDeleteRule() {
        _deleteConfirmId.value = null
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
            if (_inspectingRuleId.value == id) {
                _inspectingRuleId.value = null
            }
            _deleteConfirmId.value = null
        }
    }

    fun openWipeAllRulesDialog() {
        _showWipeAllRulesDialog.value = true
    }

    fun dismissWipeAllRulesDialog() {
        _showWipeAllRulesDialog.value = false
    }

    fun wipeAllRules() {
        viewModelScope.launch {
            repository.wipeAllRules()
            _showWipeAllRulesDialog.value = false
        }
    }

    fun runRulesNow() {
        viewModelScope.launch {
            _isApplying.value = true
            try {
                repository.runRulesOnAllTransactions()
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun previewRule(rule: TransactionRule): RulePreviewResult =
        repository.previewRuleLocal(rule)

    fun previewMatchCount(conditions: List<RuleCondition>): Int {
        if (conditions.isEmpty()) return 0
        return RuleMatcherEngine.countMatches(repository.transactions.value, conditions)
    }

    fun previewMatchingTransactions(conditions: List<RuleCondition>): List<Transaction> =
        RuleMatcherEngine.matchingTransactions(repository.transactions.value, conditions)
}
