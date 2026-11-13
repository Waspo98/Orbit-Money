package app.overbay.orbit.ui.rules.sheets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.RulePreviewResult
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactTextField
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.rules.RuleDraftDefaults
import app.overbay.orbit.ui.rules.components.RuleEditorEntry
import app.overbay.orbit.ui.rules.components.RuleLogicCard
import app.overbay.orbit.ui.rules.components.RuleMatchPreviewWindow
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun RuleEditorSheet(
    rule: TransactionRule?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (TransactionRule) -> Unit,
    onPreviewCount: (List<RuleCondition>) -> Int,
    onDismiss: () -> Unit,
    sourceTransaction: Transaction? = null,
    onPreviewMatches: (List<RuleCondition>) -> List<Transaction> = { emptyList() },
    onPreviewRule: ((TransactionRule) -> RulePreviewResult)? = null,
    onOpenRule: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        RuleEditorContent(
            rule = rule,
            categories = categories,
            accounts = accounts,
            onSave = onSave,
            onPreviewCount = onPreviewCount,
            onDismiss = onDismiss,
            sourceTransaction = sourceTransaction,
            onPreviewMatches = onPreviewMatches,
            onPreviewRule = onPreviewRule,
            onOpenRule = onOpenRule
        )
    }
}

@Composable
fun RuleEditorContent(
    rule: TransactionRule?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (TransactionRule) -> Unit,
    onPreviewCount: (List<RuleCondition>) -> Int,
    onDismiss: () -> Unit,
    sourceTransaction: Transaction? = null,
    onPreviewMatches: (List<RuleCondition>) -> List<Transaction> = { emptyList() },
    onPreviewRule: ((TransactionRule) -> RulePreviewResult)? = null,
    onOpenRule: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditingExisting = rule != null && rule.id > 0L
    val sortedCategories = remember(categories) { categories.sortedBy { it.name.lowercase() } }

    val entryKeyCounter = remember(rule, sourceTransaction?.id) { longArrayOf(1L) }

    val conditionEntries = remember(rule, sourceTransaction?.id) {
        mutableStateListOf<RuleEditorEntry<RuleCondition>>().apply {
            val initialConditions = if (rule != null && rule.conditions.isNotEmpty()) {
                rule.conditions
            } else {
                listOf(
                    RuleDraftDefaults.conditionFromTransaction(
                        RuleConditionField.MERCHANT,
                        RuleOperator.CONTAINS,
                        sourceTransaction,
                        accounts,
                        sortedCategories
                    )
                )
            }
            initialConditions.forEach { condition ->
                add(RuleEditorEntry(key = entryKeyCounter[0]++, value = condition, animateEnter = false))
            }
        }
    }

    val actionEntries = remember(rule, sourceTransaction?.id) {
        mutableStateListOf<RuleEditorEntry<RuleAction>>().apply {
            val initialActions = if (rule != null && rule.actions.isNotEmpty()) {
                rule.actions
            } else {
                val defaultType = if (sourceTransaction != null) RuleActionType.RENAME else RuleActionType.CATEGORIZE
                listOf(RuleDraftDefaults.actionForType(defaultType, sourceTransaction, sortedCategories))
            }
            initialActions.forEach { action ->
                add(RuleEditorEntry(key = entryKeyCounter[0]++, value = action, animateEnter = false))
            }
        }
    }

    val currentConditionEntries = conditionEntries.toList()
    val currentActionEntries = actionEntries.toList()
    val currentConditions = currentConditionEntries.filterNot { it.isRemoving }.map { it.value }
    val currentActions = currentActionEntries.filterNot { it.isRemoving }.map { it.value }
    val computedDefaultName = remember(currentConditions, currentActions, sourceTransaction) {
        RuleDraftDefaults.defaultRuleName(currentConditions, currentActions, sourceTransaction)
    }

    var hasCustomName by remember(rule, sourceTransaction?.id) {
        mutableStateOf(isEditingExisting && !rule?.name.isNullOrBlank())
    }
    var name by remember(rule, sourceTransaction?.id) {
        mutableStateOf(
            if (isEditingExisting) rule?.name.orEmpty()
            else computedDefaultName.ifBlank { rule?.name.orEmpty() }
        )
    }

    LaunchedEffect(computedDefaultName, hasCustomName) {
        if (!hasCustomName) {
            name = computedDefaultName
        }
    }

    val allConditionsValid = currentConditions.isNotEmpty() && currentConditions.all(RuleDraftDefaults::isConditionValid)
    val canSave = allConditionsValid && currentActions.isNotEmpty() && currentActions.all(RuleDraftDefaults::isActionValid)
    var matchingTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var matchCount by remember { mutableStateOf(0) }
    LaunchedEffect(currentConditions, allConditionsValid) {
        if (!allConditionsValid) {
            matchingTransactions = emptyList()
            matchCount = 0
            return@LaunchedEffect
        }
        delay(250)
        val (matches, count) = withContext(Dispatchers.Default) {
            val list = onPreviewMatches(currentConditions)
            val c = if (list.isNotEmpty()) list.size else onPreviewCount(currentConditions)
            list to c
        }
        matchingTransactions = matches
        matchCount = count
    }

    val previewResult = remember(currentConditions, currentActions, canSave) {
        if (allConditionsValid && canSave && onPreviewRule != null) {
            val draftRule = TransactionRule(
                id = rule?.id ?: 0L,
                name = name.ifBlank { "Preview Rule" },
                priority = rule?.priority ?: 10,
                conditions = currentConditions,
                actions = currentActions,
                enabled = rule?.enabled ?: true
            )
            onPreviewRule.invoke(draftRule)
        } else null
    }

    OrbitModalScaffold(
        title = if (isEditingExisting) "Edit Rule" else "New Rule",
        onDismiss = onDismiss,
        confirmText = if (isEditingExisting) "Save Rule" else "Create Rule",
        confirmEnabled = canSave,
        onConfirm = {
            val finalName = RuleDraftDefaults.resolveRuleName(
                rawName = name,
                conditions = currentConditions,
                actions = currentActions,
                accounts = accounts,
                categories = sortedCategories,
                sourceTransaction = sourceTransaction
            )
            onSave(
                TransactionRule(
                    id = rule?.id ?: 0L,
                    name = finalName,
                    priority = rule?.priority ?: 10,
                    conditions = currentConditions,
                    actions = currentActions,
                    enabled = rule?.enabled ?: true
                )
            )
        },
        bodyPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        bodySpacing = 10.dp,
        modifier = modifier
    ) {
        OrbitCompactTextField(
            value = name,
            onValueChange = {
                name = it
                hasCustomName = true
            },
            placeholder = "Rule name (auto-filled)",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rule Name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingContent = {
                if (!hasCustomName && name.isNotBlank()) {
                    OrbitBadge(
                        text = "Auto",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                if (name.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            name = ""
                            hasCustomName = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Rule Name",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f),
            modifier = Modifier.fillMaxWidth()
        )

        RuleLogicCard(
            conditions = currentConditionEntries,
            actions = currentActionEntries,
            accounts = accounts,
            categories = sortedCategories,
            onAddCondition = {
                conditionEntries.add(
                    RuleEditorEntry(
                        key = entryKeyCounter[0]++,
                        value = RuleDraftDefaults.conditionFromTransaction(
                            RuleConditionField.MERCHANT,
                            RuleOperator.CONTAINS,
                            sourceTransaction,
                            accounts,
                            sortedCategories
                        ),
                        animateEnter = true
                    )
                )
            },
            onConditionFieldChanged = { key, nextField ->
                conditionEntries.updateActiveEntry(key) { current ->
                    RuleDraftDefaults.conditionFromTransaction(
                        nextField,
                        current.operator,
                        sourceTransaction,
                        accounts,
                        sortedCategories
                    )
                }
            },
            onConditionChanged = { key, updated ->
                conditionEntries.updateActiveEntry(key) { updated }
            },
            onDeleteCondition = { key ->
                conditionEntries.markEntryRemoving(key)
            },
            onRemoveConditionFinished = { key ->
                conditionEntries.removeAll { it.key == key }
            },
            onAddAction = {
                val hasRename = currentActions.any {
                    RuleDraftDefaults.canonicalActionType(it.type) == RuleActionType.RENAME
                }
                val nextType = if (hasRename && sortedCategories.isNotEmpty()) {
                    RuleActionType.CATEGORIZE
                } else {
                    RuleActionType.RENAME
                }
                actionEntries.add(
                    RuleEditorEntry(
                        key = entryKeyCounter[0]++,
                        value = RuleDraftDefaults.actionForType(nextType, sourceTransaction, sortedCategories),
                        animateEnter = true
                    )
                )
            },
            onActionTypeChanged = { key, nextType ->
                actionEntries.updateActiveEntry(key) {
                    RuleDraftDefaults.actionForType(nextType, sourceTransaction, sortedCategories)
                }
            },
            onActionChanged = { key, updated ->
                actionEntries.updateActiveEntry(key) { updated }
            },
            onDeleteAction = { key ->
                actionEntries.markEntryRemoving(key)
            },
            onRemoveActionFinished = { key ->
                actionEntries.removeAll { it.key == key }
            }
        )

        RuleMatchPreviewWindow(
            allConditionsValid = allConditionsValid,
            matchCount = matchCount,
            matchingTransactions = matchingTransactions,
            accounts = accounts,
            categories = sortedCategories,
            sourceTransactionId = sourceTransaction?.id,
            previewResult = previewResult,
            onOpenRule = onOpenRule
        )
    }
}

private fun <T> MutableList<RuleEditorEntry<T>>.updateActiveEntry(
    key: Long,
    transform: (T) -> T
) {
    val index = indexOfFirst { it.key == key && !it.isRemoving }
    if (index >= 0) {
        val current = this[index]
        this[index] = current.copy(value = transform(current.value))
    }
}

private fun <T> MutableList<RuleEditorEntry<T>>.markEntryRemoving(key: Long) {
    if (count { !it.isRemoving } <= 1) return
    val index = indexOfFirst { it.key == key && !it.isRemoving }
    if (index >= 0) {
        this[index] = this[index].copy(isRemoving = true)
    }
}
