package app.overbay.orbit.ui.rules.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun RuleItemCard(
    rule: TransactionRule,
    categoryMap: Map<Long, Category>,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isInspected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = if (isInspected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isInspected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                rule.enabled -> MaterialTheme.colorScheme.surfaceContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    OrbitBadge(
                        text = "#${rule.priority}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = {
                            haptics.tick()
                            onToggleEnabled(it)
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = {
                        haptics.tick()
                        onEdit()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Rule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        haptics.reject()
                        onDelete()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Rule",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "When matching",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (rule.conditions.isEmpty()) {
                    Text(
                        text = "No conditions defined",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    rule.conditions.forEach { cond ->
                        OrbitBadge(
                            text = formatRuleCondition(cond, categoryMap),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Then",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                                "Categorize as ${targetCatName ?: act.value}"
                            }
                            RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                                "Rename to \"${act.textValue ?: act.value}\""
                            }
                            RuleActionType.MARK_TRANSFER -> "Mark as Transfer"
                            RuleActionType.MARK_IGNORED -> "Ignore Transaction"
                            RuleActionType.SET_MHA_ELIGIBLE -> "Mark MHA Eligible"
                        }
                        OrbitBadge(
                            text = label,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatRuleCondition(cond: RuleCondition, categoryMap: Map<Long, Category>): String {
    val fieldName = when (cond.field) {
        RuleConditionField.MERCHANT -> "Merchant"
        RuleConditionField.ORIGINAL_DESCRIPTION -> "Description"
        RuleConditionField.NOTES -> "Notes"
        RuleConditionField.AMOUNT -> "Amount"
        RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID -> "Account"
        RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID -> "Category"
    }
    val opName = when (cond.operator) {
        RuleOperator.CONTAINS -> "contains"
        RuleOperator.EXACT, RuleOperator.EQUALS, RuleOperator.IS -> "is"
        RuleOperator.IS_NOT -> "is not"
        RuleOperator.STARTS_WITH -> "starts with"
        RuleOperator.ENDS_WITH -> "ends with"
        RuleOperator.GREATER_THAN -> ">"
        RuleOperator.LESS_THAN -> "<"
        RuleOperator.BETWEEN -> "between"
        RuleOperator.REGEX -> "matches"
    }
    val valueText = if (cond.field == RuleConditionField.CATEGORY || cond.field == RuleConditionField.CATEGORY_ID) {
        val catId = cond.targetId ?: cond.value.toLongOrNull()
        catId?.let { categoryMap[it]?.labelWithEmoji } ?: "\"${cond.value}\""
    } else {
        "\"${cond.value}\""
    }
    return "$fieldName $opName $valueText"
}
