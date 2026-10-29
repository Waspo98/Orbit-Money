package app.overbay.orbit.ui.rules.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.rules.RuleDraftDefaults
import app.overbay.orbit.ui.rules.components.ActionEditorRow
import app.overbay.orbit.ui.rules.components.ConditionEditorRow
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun RuleEditorSheet(
    rule: TransactionRule?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (TransactionRule) -> Unit,
    onPreviewCount: (List<RuleCondition>) -> Int,
    onDismiss: () -> Unit,
    sourceTransaction: Transaction? = null,
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
            sourceTransaction = sourceTransaction
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
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditingExisting = rule != null && rule.id > 0L
    val sortedCategories = remember(categories) { categories.sortedBy { it.name.lowercase() } }

    var name by remember(rule, sourceTransaction?.id) { mutableStateOf(rule?.name ?: "") }
    var priorityText by remember(rule, sourceTransaction?.id) {
        mutableStateOf((rule?.priority ?: 10).toString())
    }

    val conditions = remember(rule, sourceTransaction?.id) {
        mutableStateListOf<RuleCondition>().apply {
            if (rule != null && rule.conditions.isNotEmpty()) {
                addAll(rule.conditions)
            } else {
                add(RuleDraftDefaults.conditionFromTransaction(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, sourceTransaction, accounts, sortedCategories))
            }
        }
    }

    val actions = remember(rule, sourceTransaction?.id) {
        mutableStateListOf<RuleAction>().apply {
            if (rule != null && rule.actions.isNotEmpty()) {
                addAll(rule.actions)
            } else {
                val defaultType = if (sourceTransaction != null) RuleActionType.RENAME else RuleActionType.CATEGORIZE
                add(RuleDraftDefaults.actionForType(defaultType, sourceTransaction, sortedCategories))
            }
        }
    }

    val currentConditions = conditions.toList()
    val currentActions = actions.toList()
    val allConditionsValid = currentConditions.isNotEmpty() && currentConditions.all(RuleDraftDefaults::isConditionValid)
    val canSave = allConditionsValid && currentActions.isNotEmpty() && currentActions.all(RuleDraftDefaults::isActionValid)
    val matchCount = remember(currentConditions) { if (allConditionsValid) onPreviewCount(currentConditions) else 0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isEditingExisting) "Edit Rule" else "New Rule",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (Optional)") },
                placeholder = { Text("Auto-generated if empty") },
                singleLine = true,
                modifier = Modifier.weight(0.68f)
            )

            OrbitTextField(
                value = priorityText,
                onValueChange = { priorityText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Priority") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.32f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "IF THE TRANSACTION…",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
                onClick = {
                    haptics.tick()
                    conditions.add(
                        RuleDraftDefaults.conditionFromTransaction(
                            RuleConditionField.MERCHANT,
                            RuleOperator.CONTAINS,
                            sourceTransaction,
                            accounts,
                            sortedCategories
                        )
                    )
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Condition", modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text("Add Condition")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        conditions.forEachIndexed { index, condition ->
            ConditionEditorRow(
                condition = condition,
                accounts = accounts,
                categories = sortedCategories,
                canDelete = conditions.size > 1,
                onFieldChanged = { nextField ->
                    conditions[index] = RuleDraftDefaults.conditionFromTransaction(
                        nextField,
                        condition.operator,
                        sourceTransaction,
                        accounts,
                        sortedCategories
                    )
                },
                onConditionChanged = { conditions[index] = it },
                onDelete = { haptics.tick(); conditions.removeAt(index) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "THEN…",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            OutlinedButton(
                onClick = {
                    haptics.tick()
                    val hasRename = actions.any { RuleDraftDefaults.canonicalActionType(it.type) == RuleActionType.RENAME }
                    val nextType = if (hasRename && sortedCategories.isNotEmpty()) RuleActionType.CATEGORIZE else RuleActionType.RENAME
                    actions.add(RuleDraftDefaults.actionForType(nextType, sourceTransaction, sortedCategories))
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Action", modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text("Add Action")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        actions.forEachIndexed { index, action ->
            ActionEditorRow(
                action = action,
                categories = sortedCategories,
                canDelete = actions.size > 1,
                onTypeChanged = { nextType ->
                    actions[index] = RuleDraftDefaults.actionForType(nextType, sourceTransaction, sortedCategories)
                },
                onActionChanged = { actions[index] = it },
                onDelete = { haptics.tick(); actions.removeAt(index) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Match Preview",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (allConditionsValid) {
                        "Matches $matchCount existing transaction${if (matchCount == 1) "" else "s"}"
                    } else {
                        "Fill in conditions to see a match preview"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    haptics.confirm()
                    val priority = priorityText.toIntOrNull() ?: 10
                    val finalName = RuleDraftDefaults.resolveRuleName(name, currentConditions, currentActions, accounts, sortedCategories)
                    onSave(
                        TransactionRule(
                            id = rule?.id ?: 0L,
                            name = finalName,
                            priority = priority,
                            conditions = currentConditions,
                            actions = currentActions,
                            enabled = rule?.enabled ?: true
                        )
                    )
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(if (isEditingExisting) "Save Rule" else "Create Rule", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
