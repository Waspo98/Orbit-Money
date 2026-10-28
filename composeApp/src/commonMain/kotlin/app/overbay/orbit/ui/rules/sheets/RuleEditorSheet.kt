package app.overbay.orbit.ui.rules.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.ui.components.OrbitModalSheet
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
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        RuleEditorContent(
            rule = rule,
            categories = categories,
            accounts = accounts,
            onSave = onSave,
            onPreviewCount = onPreviewCount,
            onDismiss = onDismiss
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
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var name by remember(rule) { mutableStateOf(rule?.name ?: "") }
    var priorityText by remember(rule) { mutableStateOf((rule?.priority ?: 10).toString()) }

    val conditions = remember(rule) {
        mutableStateListOf<RuleCondition>().apply {
            if (rule != null && rule.conditions.isNotEmpty()) {
                addAll(rule.conditions)
            } else {
                add(RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, ""))
            }
        }
    }

    val actions = remember(rule) {
        mutableStateListOf<RuleAction>().apply {
            if (rule != null && rule.actions.isNotEmpty()) {
                addAll(rule.actions)
            } else {
                add(RuleAction(RuleActionType.CATEGORIZE, targetCategoryId = categories.firstOrNull()?.id))
            }
        }
    }

    val matchCount = remember(conditions.toList()) {
        onPreviewCount(conditions)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (rule != null && rule.id > 0L) "Edit Rule" else "New Rule",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Rule Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Rule Name") },
            placeholder = { Text("e.g. Categorize Grocery Trips") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Priority
        OutlinedTextField(
            value = priorityText,
            onValueChange = { priorityText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Priority (Higher evaluated first)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Conditions Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CONDITIONS (ALL MUST MATCH)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
                onClick = {
                    haptics.tick()
                    conditions.add(RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, ""))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Condition",
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
                Text("Add Condition")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        conditions.forEachIndexed { index, condition ->
            ConditionEditorRow(
                condition = condition,
                canDelete = conditions.size > 1,
                onConditionChanged = { conditions[index] = it },
                onDelete = {
                    haptics.tick()
                    conditions.removeAt(index)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            OutlinedButton(
                onClick = {
                    haptics.tick()
                    actions.add(RuleAction(RuleActionType.CATEGORIZE, targetCategoryId = categories.firstOrNull()?.id))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Action",
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
                Text("Add Action")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        actions.forEachIndexed { index, action ->
            ActionEditorRow(
                action = action,
                categories = categories,
                canDelete = actions.size > 1,
                onActionChanged = { actions[index] = it },
                onDelete = {
                    haptics.tick()
                    actions.removeAt(index)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Match Count Preview Badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Match Preview",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Matches $matchCount existing transaction${if (matchCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Actions (Title Case)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    haptics.confirm()
                    val priority = priorityText.toIntOrNull() ?: 10
                    val newRule = TransactionRule(
                        id = rule?.id ?: 0L,
                        name = name.trim(),
                        priority = priority,
                        conditions = conditions.toList(),
                        actions = actions.toList(),
                        enabled = rule?.enabled ?: true
                    )
                    onSave(newRule)
                },
                enabled = name.isNotBlank() && conditions.isNotEmpty() && actions.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Rule")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
