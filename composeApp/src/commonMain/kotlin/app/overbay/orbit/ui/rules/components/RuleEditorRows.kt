package app.overbay.orbit.ui.rules.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.OrbitCompactCurrencyField
import app.overbay.orbit.ui.components.OrbitCompactDropdownPill
import app.overbay.orbit.ui.components.OrbitCompactTextField
import app.overbay.orbit.ui.rules.RuleDraftDefaults
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitCompactDropdownPill(
                    selectedText = RuleDraftDefaults.fieldLabel(canonicalField),
                    options = RuleDraftDefaults.EDITOR_FIELDS,
                    optionLabel = RuleDraftDefaults::fieldLabel,
                    onOptionSelected = { nextField ->
                        if (RuleDraftDefaults.canonicalField(nextField) != canonicalField) {
                            onFieldChanged(nextField)
                        }
                    }
                )

                OrbitCompactDropdownPill(
                    selectedText = RuleDraftDefaults.operatorLabel(condition.operator),
                    options = availableOperators,
                    optionLabel = RuleDraftDefaults::operatorLabel,
                    onOptionSelected = { nextOp ->
                        onConditionChanged(RuleDraftDefaults.withOperator(condition, nextOp))
                    }
                )
            }

            RuleRowDeleteButton(
                visible = canDelete,
                contentDescription = "Delete Condition",
                leadingSpacing = 6.dp,
                onClick = onDelete
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = canonicalField to (condition.operator == RuleOperator.BETWEEN),
            transitionSpec = { ruleRowContentTransform() },
            contentAlignment = Alignment.TopStart,
            label = "ConditionValueMorph",
            modifier = Modifier.fillMaxWidth()
        ) { (targetField, isBetween) ->
            when (targetField) {
                RuleConditionField.ACCOUNT_ID -> {
                    val selectedId = condition.targetId ?: condition.value.toLongOrNull()
                    val selectedAccount = accounts.find { it.id == selectedId }
                    OrbitCompactDropdownPill(
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
                        },
                        fillWidth = true,
                        isPlaceholder = selectedAccount == null,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                }

                RuleConditionField.CATEGORY_ID -> {
                    val selectedId = condition.targetId ?: condition.value.toLongOrNull()
                    val selectedCategory = categories.find { it.id == selectedId }
                    OrbitCompactDropdownPill(
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
                        },
                        fillWidth = true,
                        isPlaceholder = selectedCategory == null,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                }

                RuleConditionField.AMOUNT -> {
                    if (isBetween) {
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
                                OrbitCompactCurrencyField(
                                    cents = lowCents,
                                    inlineLabel = "Low",
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
                                OrbitCompactCurrencyField(
                                    cents = highCents,
                                    inlineLabel = "High",
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
                        OrbitCompactCurrencyField(
                            cents = currentCents,
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
                    val placeholderText = when (targetField) {
                        RuleConditionField.ORIGINAL_DESCRIPTION -> "Match raw description text"
                        RuleConditionField.NOTES -> "Match notes text"
                        else -> "Match text (e.g. Amazon)"
                    }
                    OrbitCompactTextField(
                        value = condition.value,
                        onValueChange = { onConditionChanged(condition.copy(value = it)) },
                        placeholder = placeholderText
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OrbitCompactDropdownPill(
                selectedText = RuleDraftDefaults.actionTypeLabel(canonicalType),
                options = RuleDraftDefaults.EDITOR_ACTION_TYPES,
                optionLabel = RuleDraftDefaults::actionTypeLabel,
                onOptionSelected = { nextType ->
                    if (RuleDraftDefaults.canonicalActionType(nextType) != canonicalType) {
                        onTypeChanged(nextType)
                    }
                },
                modifier = Modifier.weight(1f, fill = false)
            )

            RuleRowDeleteButton(
                visible = canDelete,
                contentDescription = "Delete Action",
                leadingSpacing = 6.dp,
                onClick = onDelete
            )
        }

        AnimatedContent(
            targetState = canonicalType,
            transitionSpec = { ruleRowContentTransform() },
            contentAlignment = Alignment.TopStart,
            label = "ActionArgumentMorph",
            modifier = Modifier.fillMaxWidth()
        ) { targetActionType ->
            when (targetActionType) {
                RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OrbitCompactDropdownPill(
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
                            },
                            fillWidth = true,
                            isPlaceholder = selectedCategory == null,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    }
                }

                RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OrbitCompactTextField(
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
                            placeholder = "New merchant name"
                        )
                    }
                }

                else -> Spacer(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RuleRowDeleteButton(
    visible: Boolean,
    contentDescription: String,
    leadingSpacing: Dp = 0.dp,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            animationSpec = OrbitSprings.IntSizeAccordionFast,
            expandFrom = Alignment.End
        ) + fadeIn(animationSpec = OrbitSprings.FloatEffectsFast),
        exit = shrinkHorizontally(
            animationSpec = OrbitSprings.IntSizeAccordionFast,
            shrinkTowards = Alignment.End
        ) + fadeOut(animationSpec = OrbitSprings.FloatEffectsFast)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingSpacing > 0.dp) {
                Spacer(modifier = Modifier.width(leadingSpacing))
            }
            IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<*>.ruleRowContentTransform(): ContentTransform =
    ((fadeIn(animationSpec = OrbitSprings.FloatEffectsFast) +
        expandVertically(
            animationSpec = OrbitSprings.IntSizeAccordionFast,
            expandFrom = Alignment.Top
        )) togetherWith
        (fadeOut(animationSpec = OrbitSprings.FloatEffectsFast) +
            shrinkVertically(
                animationSpec = OrbitSprings.IntSizeAccordionFast,
                shrinkTowards = Alignment.Top
            ))).using(SizeTransform(clip = true) { _, _ -> OrbitSprings.IntSizeSpatialDefault })
