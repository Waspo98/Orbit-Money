package app.overbay.orbit.ui.rules.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.rules.RuleDraftDefaults
import kotlin.math.abs

@Composable
fun ConditionEditorRow(
    condition: RuleCondition,
    accounts: List<Account>,
    categories: List<Category>,
    canDelete: Boolean,
    onFieldChanged: (RuleConditionField) -> Unit,
    onConditionChanged: (RuleCondition) -> Unit,
    onDelete: () -> Unit
) {
    val canonicalField = RuleDraftDefaults.canonicalField(condition.field)
    val availableOperators = RuleDraftDefaults.operatorsForField(canonicalField)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(0.54f)) {
                    OrbitDropdownField(
                        label = "Field",
                        selectedText = RuleDraftDefaults.fieldLabel(canonicalField),
                        options = RuleDraftDefaults.EDITOR_FIELDS,
                        optionLabel = RuleDraftDefaults::fieldLabel,
                        onOptionSelected = { nextField ->
                            if (RuleDraftDefaults.canonicalField(nextField) != canonicalField) {
                                onFieldChanged(nextField)
                            }
                        }
                    )
                }

                Box(modifier = Modifier.weight(0.46f)) {
                    OrbitDropdownField(
                        label = "Match",
                        selectedText = RuleDraftDefaults.operatorLabel(condition.operator),
                        options = availableOperators,
                        optionLabel = RuleDraftDefaults::operatorLabel,
                        onOptionSelected = { nextOp ->
                            onConditionChanged(RuleDraftDefaults.withOperator(condition, nextOp))
                        }
                    )
                }

                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Condition",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (canonicalField) {
                RuleConditionField.ACCOUNT_ID -> {
                    val selectedId = condition.targetId ?: condition.value.toLongOrNull()
                    val selectedAccount = accounts.find { it.id == selectedId }
                    OrbitDropdownField(
                        label = "Account",
                        selectedText = selectedAccount?.name ?: "Select Account",
                        options = accounts,
                        optionLabel = { it.name },
                        onOptionSelected = { acct ->
                            onConditionChanged(
                                condition.copy(
                                    value = acct.id.toString(),
                                    targetId = acct.id
                                )
                            )
                        }
                    )
                }

                RuleConditionField.CATEGORY_ID -> {
                    val selectedId = condition.targetId ?: condition.value.toLongOrNull()
                    val selectedCategory = categories.find { it.id == selectedId }
                    OrbitDropdownField(
                        label = "Category",
                        selectedText = selectedCategory?.labelWithEmoji ?: "Select Category",
                        options = categories,
                        optionLabel = { it.labelWithEmoji },
                        onOptionSelected = { cat ->
                            onConditionChanged(
                                condition.copy(
                                    value = cat.id.toString(),
                                    targetId = cat.id
                                )
                            )
                        }
                    )
                }

                RuleConditionField.AMOUNT -> {
                    if (condition.operator == RuleOperator.BETWEEN) {
                        val parts = condition.value.split(",").map { it.trim() }
                        val lowCents = condition.minAmountCentsValue
                            ?: parts.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let { abs(MoneyUtils.dollarsToCents(it)) }
                            ?: 0L
                        val highCents = condition.maxAmountCentsValue
                            ?: parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { abs(MoneyUtils.dollarsToCents(it)) }
                            ?: 0L

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CurrencyTextField(
                                    cents = lowCents,
                                    label = "Low",
                                    onCentsChange = { nextLow ->
                                        val absLow = abs(nextLow)
                                        val lowStr = if (absLow == 0L) "" else MoneyUtils.centsToDollarString(absLow)
                                        val highStr = if (highCents == 0L && condition.maxAmountCentsValue == null) "" else MoneyUtils.centsToDollarString(highCents)
                                        onConditionChanged(
                                            condition.copy(
                                                minAmountCentsValue = absLow.takeIf { it > 0L },
                                                maxAmountCentsValue = condition.maxAmountCentsValue ?: highCents.takeIf { it > 0L },
                                                value = if (lowStr.isEmpty() && highStr.isEmpty()) "" else "$lowStr,$highStr"
                                            )
                                        )
                                    }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CurrencyTextField(
                                    cents = highCents,
                                    label = "High",
                                    onCentsChange = { nextHigh ->
                                        val absHigh = abs(nextHigh)
                                        val lowStr = if (lowCents == 0L && condition.minAmountCentsValue == null) "" else MoneyUtils.centsToDollarString(lowCents)
                                        val highStr = if (absHigh == 0L) "" else MoneyUtils.centsToDollarString(absHigh)
                                        onConditionChanged(
                                            condition.copy(
                                                minAmountCentsValue = condition.minAmountCentsValue ?: lowCents.takeIf { it > 0L },
                                                maxAmountCentsValue = absHigh.takeIf { it > 0L },
                                                value = if (lowStr.isEmpty() && highStr.isEmpty()) "" else "$lowStr,$highStr"
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        val currentCents = condition.amountCentsValue
                            ?: condition.value.takeIf { it.isNotBlank() }?.let { abs(MoneyUtils.dollarsToCents(it)) }
                            ?: 0L
                        CurrencyTextField(
                            cents = currentCents,
                            label = "Amount",
                            onCentsChange = { nextCents ->
                                val absVal = abs(nextCents)
                                onConditionChanged(
                                    condition.copy(
                                        amountCentsValue = absVal.takeIf { it > 0L },
                                        value = if (absVal == 0L) "" else MoneyUtils.centsToDollarString(absVal)
                                    )
                                )
                            }
                        )
                    }
                }

                else -> {
                    val placeholderText = when (canonicalField) {
                        RuleConditionField.ORIGINAL_DESCRIPTION -> "Match raw description text"
                        RuleConditionField.NOTES -> "Match notes text"
                        else -> "Match text (e.g. Amazon)"
                    }
                    OrbitTextField(
                        value = condition.value,
                        onValueChange = { onConditionChanged(condition.copy(value = it)) },
                        placeholder = { Text(placeholderText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ActionEditorRow(
    action: RuleAction,
    categories: List<Category>,
    canDelete: Boolean,
    onTypeChanged: (RuleActionType) -> Unit,
    onActionChanged: (RuleAction) -> Unit,
    onDelete: () -> Unit
) {
    val canonicalType = RuleDraftDefaults.canonicalActionType(action.type)
    val resolvedCategoryId = action.targetCategoryId ?: action.value.toLongOrNull()
    val selectedCategory = categories.find { it.id == resolvedCategoryId }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OrbitDropdownField(
                        label = "Action",
                        selectedText = RuleDraftDefaults.actionTypeLabel(canonicalType),
                        options = RuleDraftDefaults.EDITOR_ACTION_TYPES,
                        optionLabel = RuleDraftDefaults::actionTypeLabel,
                        onOptionSelected = { nextType ->
                            if (RuleDraftDefaults.canonicalActionType(nextType) != canonicalType) {
                                onTypeChanged(nextType)
                            }
                        }
                    )
                }

                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Action",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            when (canonicalType) {
                RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OrbitDropdownField(
                        label = "Target Category",
                        selectedText = selectedCategory?.labelWithEmoji ?: "Select Category",
                        options = categories,
                        optionLabel = { it.labelWithEmoji },
                        onOptionSelected = { cat ->
                            onActionChanged(
                                action.copy(
                                    type = RuleActionType.CATEGORIZE,
                                    value = cat.id.toString(),
                                    targetCategoryId = cat.id
                                )
                            )
                        }
                    )
                }
                RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OrbitTextField(
                        value = action.textValue ?: action.value,
                        onValueChange = {
                            onActionChanged(
                                action.copy(
                                    type = RuleActionType.RENAME,
                                    value = it,
                                    textValue = it
                                )
                            )
                        },
                        placeholder = { Text("New merchant name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Applies automatically without extra value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
