package app.overbay.orbit.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.RuleMatcherEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _inspectingRuleId = MutableStateFlow<Long?>(null)
    private val _editingRule = MutableStateFlow<TransactionRule?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _deleteConfirmId = MutableStateFlow<Long?>(null)
    private val _isApplying = MutableStateFlow(false)

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
    }.stateIn(
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
        viewModelScope.launch {
            repository.saveRule(rule)
            closeEditorSheet()
        }
    }

    fun toggleRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            val rule = uiState.value.rules.find { it.id == id } ?: return@launch
            repository.saveRule(rule.copy(enabled = enabled))
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

    fun previewMatchCount(conditions: List<RuleCondition>): Int {
        if (conditions.isEmpty()) return 0
        return RuleMatcherEngine.countMatches(repository.transactions.value, conditions)
    }
}
