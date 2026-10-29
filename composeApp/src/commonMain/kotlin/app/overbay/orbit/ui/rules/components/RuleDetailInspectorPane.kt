package app.overbay.orbit.ui.rules.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.ui.rules.RulesUiState
import app.overbay.orbit.ui.rules.sheets.RuleEditorContent
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun RuleDetailInspectorPane(
    state: RulesUiState,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onOpenEdit: (TransactionRule) -> Unit,
    onSaveRule: (TransactionRule) -> Unit,
    onCloseEdit: () -> Unit,
    onDeleteRule: (Long) -> Unit,
    onRunRulesNow: () -> Unit,
    onPreviewCount: (List<RuleCondition>) -> Int,
    onDismissPane: () -> Unit,
    onPreviewMatches: (List<RuleCondition>) -> List<Transaction> = { emptyList() },
    modifier: Modifier = Modifier
) {
    val activeMode = when {
        state.showEditorSheet -> "EDIT"
        state.inspectingRule != null -> "DETAIL"
        else -> "NONE"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = activeMode,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessLow))
            },
            label = "RuleInspectorModeTransition"
        ) { mode ->
            when (mode) {
                "EDIT" -> {
                    RuleEditorContent(
                        rule = state.editingRule,
                        categories = state.categories,
                        accounts = state.accounts,
                        onSave = onSaveRule,
                        onPreviewCount = onPreviewCount,
                        onPreviewMatches = onPreviewMatches,
                        onDismiss = {
                            if (state.inspectingRule != null) {
                                onCloseEdit()
                            } else {
                                onDismissPane()
                            }
                        }
                    )
                }
                "DETAIL" -> {
                    val rule = state.inspectingRule
                    if (rule != null) {
                        RuleInspectorDetailView(
                            rule = rule,
                            categoryMap = state.categoryMap,
                            isApplyingRules = state.isApplyingRules,
                            onToggleEnabled = { enabled -> onToggleEnabled(rule.id, enabled) },
                            onOpenEdit = { onOpenEdit(rule) },
                            onDeleteRule = { onDeleteRule(rule.id) },
                            onRunRulesNow = onRunRulesNow,
                            onPreviewCount = onPreviewCount,
                            onDismissPane = onDismissPane
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RuleInspectorDetailView(
    rule: TransactionRule,
    categoryMap: Map<Long, Category>,
    isApplyingRules: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenEdit: () -> Unit,
    onDeleteRule: () -> Unit,
    onRunRulesNow: () -> Unit,
    onPreviewCount: (List<RuleCondition>) -> Int,
    onDismissPane: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val matchCount = remember(rule.conditions) {
        onPreviewCount(rule.conditions)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "P${rule.priority}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (rule.enabled) "Active Rule" else "Paused Rule",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }

            IconButton(onClick = onDismissPane) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Inspector",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Enabled Toggle Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rule Status",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Evaluate automatically when importing or running rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = {
                        haptics.tick()
                        onToggleEnabled(it)
                    }
                )
            }
        }

        // Conditions Breakdown Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "WHEN ALL CONDITIONS MATCH (${rule.conditions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (rule.conditions.isEmpty()) {
                    Text(
                        text = "No conditions defined",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    rule.conditions.forEach { cond ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${cond.field.name} ${cond.operator.name} '${cond.value}'",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Actions Breakdown Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "THEN PERFORM ACTIONS (${rule.actions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                if (rule.actions.isEmpty()) {
                    Text(
                        text = "No actions defined",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    rule.actions.forEach { act ->
                        val targetCatName = act.targetCategoryId?.let { categoryMap[it]?.labelWithEmoji }
                        val label = when (act.type) {
                            RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                                "Set Category -> ${targetCatName ?: act.value}"
                            }
                            RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                                "Rename -> '${act.textValue ?: act.value}'"
                            }
                            RuleActionType.MARK_TRANSFER -> "Mark as Transfer"
                            RuleActionType.MARK_IGNORED -> "Mark as Ignored"
                            RuleActionType.SET_MHA_ELIGIBLE -> "Set MHA Eligible"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Match Count Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Matches $matchCount existing transaction${if (matchCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Action Buttons Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { haptics.tick(); onOpenEdit() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Rule")
            }

            OutlinedButton(
                onClick = { haptics.tick(); onRunRulesNow() },
                enabled = !isApplyingRules,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isApplyingRules) "Running..." else "Run Rules")
            }
        }

        OutlinedButton(
            onClick = { haptics.reject(); onDeleteRule() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete Rule")
        }
    }
}
