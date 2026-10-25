package app.overbay.orbit.ui.rules

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.TransactionRule

data class RulesUiState(
    val rules: List<TransactionRule> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categoryMap: Map<Long, Category> = emptyMap(),
    val editingRule: TransactionRule? = null,
    val showEditorSheet: Boolean = false,
    val deleteConfirmRuleId: Long? = null,
    val isApplyingRules: Boolean = false,
    val isLoading: Boolean = false
) {
    val enabledRulesCount: Int get() = rules.count { it.enabled }
}
