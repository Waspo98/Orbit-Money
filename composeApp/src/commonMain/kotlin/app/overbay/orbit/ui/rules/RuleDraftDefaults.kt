package app.overbay.orbit.ui.rules

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.MoneyUtils
import kotlin.math.abs

object RuleDraftDefaults {

    val EDITOR_FIELDS: List<RuleConditionField> = listOf(
        RuleConditionField.MERCHANT,
        RuleConditionField.ORIGINAL_DESCRIPTION,
        RuleConditionField.AMOUNT,
        RuleConditionField.ACCOUNT_ID,
        RuleConditionField.CATEGORY_ID
    )

    val EDITOR_ACTION_TYPES: List<RuleActionType> = listOf(
        RuleActionType.RENAME,
        RuleActionType.CATEGORIZE,
        RuleActionType.MARK_TRANSFER,
        RuleActionType.MARK_IGNORED,
        RuleActionType.SET_MHA_ELIGIBLE
    )

    fun canonicalField(field: RuleConditionField): RuleConditionField = when (field) {
        RuleConditionField.ACCOUNT -> RuleConditionField.ACCOUNT_ID
        RuleConditionField.CATEGORY -> RuleConditionField.CATEGORY_ID
        else -> field
    }

    fun canonicalOperator(operator: RuleOperator): RuleOperator = when (operator) {
        RuleOperator.EXACT -> RuleOperator.EQUALS
        else -> operator
    }

    fun canonicalActionType(type: RuleActionType): RuleActionType = when (type) {
        RuleActionType.RENAME_MERCHANT -> RuleActionType.RENAME
        RuleActionType.SET_CATEGORY -> RuleActionType.CATEGORIZE
        else -> type
    }

    fun fieldLabel(field: RuleConditionField): String = when (field) {
        RuleConditionField.MERCHANT -> "Merchant Name"
        RuleConditionField.ORIGINAL_DESCRIPTION -> "Original Description"
        RuleConditionField.AMOUNT -> "Amount"
        RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID -> "Account"
        RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID -> "Category"
        RuleConditionField.NOTES -> "Notes"
    }

    fun operatorsForField(field: RuleConditionField): List<RuleOperator> = when (canonicalField(field)) {
        RuleConditionField.AMOUNT -> listOf(
            RuleOperator.EQUALS,
            RuleOperator.GREATER_THAN,
            RuleOperator.LESS_THAN,
            RuleOperator.BETWEEN
        )
        RuleConditionField.ACCOUNT_ID, RuleConditionField.CATEGORY_ID -> listOf(
            RuleOperator.IS,
            RuleOperator.IS_NOT
        )
        else -> listOf(
            RuleOperator.CONTAINS,
            RuleOperator.EQUALS,
            RuleOperator.STARTS_WITH,
            RuleOperator.REGEX
        )
    }

    fun operatorLabel(operator: RuleOperator): String = when (operator) {
        RuleOperator.CONTAINS -> "contains"
        RuleOperator.EQUALS, RuleOperator.EXACT -> "equals"
        RuleOperator.STARTS_WITH -> "starts with"
        RuleOperator.ENDS_WITH -> "ends with"
        RuleOperator.REGEX -> "matches regex"
        RuleOperator.GREATER_THAN -> "is greater than"
        RuleOperator.LESS_THAN -> "is less than"
        RuleOperator.BETWEEN -> "is between"
        RuleOperator.IS -> "is"
        RuleOperator.IS_NOT -> "is not"
    }

    fun actionTypeLabel(type: RuleActionType): String = when (type) {
        RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> "Rename merchant to"
        RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> "Set category to"
        RuleActionType.MARK_TRANSFER -> "Mark as transfer"
        RuleActionType.MARK_IGNORED -> "Ignore from budget"
        RuleActionType.SET_MHA_ELIGIBLE -> "Set MHA eligible"
    }

    /**
     * Seeds a new rule draft from a transaction:
     * - Condition: merchant contains [originalMerchant (fallback effectiveMerchant)]
     * - Action: rename merchant to [effectiveMerchant]
     * - Name: populated by default with the final output merchant name (or original merchant)
     */
    fun makeRuleDraftFromTransaction(
        transaction: Transaction,
        nextPriority: Int = 10
    ): TransactionRule {
        val matchMerchant = transaction.originalMerchant.ifBlank { transaction.effectiveMerchant }.trim()
        val targetMerchant = transaction.effectiveMerchant
        val conditions = listOf(
            RuleCondition(
                field = RuleConditionField.MERCHANT,
                operator = RuleOperator.CONTAINS,
                value = matchMerchant
            )
        )
        val actions = listOf(
            RuleAction(
                type = RuleActionType.RENAME,
                value = targetMerchant,
                textValue = targetMerchant
            )
        )
        return TransactionRule(
            id = 0L,
            name = defaultRuleName(conditions, actions, transaction),
            priority = nextPriority,
            conditions = conditions,
            actions = actions,
            enabled = true
        )
    }

    fun conditionFromTransaction(
        field: RuleConditionField,
        currentOperator: RuleOperator? = null,
        sourceTransaction: Transaction? = null,
        accounts: List<Account> = emptyList(),
        categories: List<Category> = emptyList()
    ): RuleCondition {
        val resolvedField = canonicalField(field)
        val validOps = operatorsForField(resolvedField)
        val normalizedCurrent = currentOperator?.let(::canonicalOperator)
        val resolvedOp = if (normalizedCurrent != null && normalizedCurrent in validOps) {
            normalizedCurrent
        } else {
            validOps.first()
        }

        return when (resolvedField) {
            RuleConditionField.MERCHANT -> {
                val text = sourceTransaction?.let {
                    it.originalMerchant.ifBlank { it.effectiveMerchant }.trim()
                }.orEmpty()
                RuleCondition(field = resolvedField, operator = resolvedOp, value = text)
            }
            RuleConditionField.ORIGINAL_DESCRIPTION -> {
                val text = sourceTransaction?.originalDescription?.trim().orEmpty()
                RuleCondition(field = resolvedField, operator = resolvedOp, value = text)
            }
            RuleConditionField.NOTES -> {
                val text = sourceTransaction?.notes?.trim().orEmpty()
                RuleCondition(field = resolvedField, operator = resolvedOp, value = text)
            }
            RuleConditionField.AMOUNT -> {
                if (sourceTransaction != null) {
                    val absCents = abs(sourceTransaction.effectiveAmountCents)
                    val dollarStr = MoneyUtils.centsToDollarString(absCents)
                    if (resolvedOp == RuleOperator.BETWEEN) {
                        RuleCondition(
                            field = resolvedField,
                            operator = resolvedOp,
                            value = "$dollarStr,$dollarStr",
                            minAmountCentsValue = absCents,
                            maxAmountCentsValue = absCents
                        )
                    } else {
                        RuleCondition(
                            field = resolvedField,
                            operator = resolvedOp,
                            value = dollarStr,
                            amountCentsValue = absCents
                        )
                    }
                } else {
                    RuleCondition(field = resolvedField, operator = resolvedOp, value = "")
                }
            }
            RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID -> {
                val acctId = sourceTransaction?.accountId
                    ?.takeIf { id -> accounts.any { it.id == id } }
                    ?: accounts.firstOrNull()?.id
                RuleCondition(
                    field = resolvedField,
                    operator = resolvedOp,
                    value = acctId?.toString().orEmpty(),
                    targetId = acctId
                )
            }
            RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID -> {
                val candidateId = sourceTransaction?.originalCategoryId ?: sourceTransaction?.effectiveCategoryId
                val catId = candidateId
                    ?.takeIf { id -> categories.any { it.id == id } }
                    ?: categories.firstOrNull()?.id
                RuleCondition(
                    field = resolvedField,
                    operator = resolvedOp,
                    value = catId?.toString().orEmpty(),
                    targetId = catId
                )
            }
        }
    }

    fun withOperator(condition: RuleCondition, nextOperator: RuleOperator): RuleCondition {
        val resolvedOp = canonicalOperator(nextOperator)
        if (canonicalField(condition.field) != RuleConditionField.AMOUNT) {
            return condition.copy(operator = resolvedOp)
        }
        return if (resolvedOp == RuleOperator.BETWEEN) {
            val baseCents = condition.minAmountCentsValue
                ?: condition.amountCentsValue
                ?: condition.value.takeIf { it.isNotBlank() }?.let { abs(MoneyUtils.dollarsToCents(it)) }
            if (baseCents != null && baseCents > 0L) {
                val s = MoneyUtils.centsToDollarString(baseCents)
                condition.copy(operator = resolvedOp, value = "$s,$s", amountCentsValue = null, minAmountCentsValue = baseCents, maxAmountCentsValue = baseCents)
            } else {
                condition.copy(operator = resolvedOp, value = "", amountCentsValue = null, minAmountCentsValue = null, maxAmountCentsValue = null)
            }
        } else {
            val baseCents = condition.amountCentsValue
                ?: condition.minAmountCentsValue
                ?: condition.value.split(",").firstOrNull()?.takeIf { it.isNotBlank() }?.let { abs(MoneyUtils.dollarsToCents(it)) }
            if (baseCents != null && baseCents > 0L) {
                val s = MoneyUtils.centsToDollarString(baseCents)
                condition.copy(operator = resolvedOp, value = s, amountCentsValue = baseCents, minAmountCentsValue = null, maxAmountCentsValue = null)
            } else {
                condition.copy(operator = resolvedOp, value = "", amountCentsValue = null, minAmountCentsValue = null, maxAmountCentsValue = null)
            }
        }
    }

    fun actionForType(
        type: RuleActionType,
        sourceTransaction: Transaction? = null,
        categories: List<Category> = emptyList()
    ): RuleAction {
        val resolvedType = canonicalActionType(type)
        return when (resolvedType) {
            RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                val text = sourceTransaction?.effectiveMerchant.orEmpty()
                RuleAction(type = RuleActionType.RENAME, value = text, textValue = text)
            }
            RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                val catId = sourceTransaction?.effectiveCategoryId
                    ?.takeIf { id -> categories.any { it.id == id } }
                    ?: categories.firstOrNull()?.id
                RuleAction(
                    type = RuleActionType.CATEGORIZE,
                    value = catId?.toString().orEmpty(),
                    targetCategoryId = catId
                )
            }
            RuleActionType.MARK_TRANSFER -> RuleAction(
                type = RuleActionType.MARK_TRANSFER,
                value = "true",
                booleanValue = true
            )
            RuleActionType.MARK_IGNORED -> RuleAction(
                type = RuleActionType.MARK_IGNORED,
                value = "true",
                booleanValue = true
            )
            RuleActionType.SET_MHA_ELIGIBLE -> RuleAction(
                type = RuleActionType.SET_MHA_ELIGIBLE,
                value = "true",
                booleanValue = true
            )
        }
    }

    fun isConditionValid(condition: RuleCondition): Boolean = when (canonicalField(condition.field)) {
        RuleConditionField.ACCOUNT_ID, RuleConditionField.CATEGORY_ID -> {
            (condition.targetId ?: condition.value.toLongOrNull()) != null
        }
        RuleConditionField.AMOUNT -> {
            if (condition.operator == RuleOperator.BETWEEN) {
                val hasTypedRange = condition.minAmountCentsValue != null && condition.maxAmountCentsValue != null
                val parts = condition.value.split(",").map { it.trim() }
                hasTypedRange || (parts.size == 2 && parts.all { it.isNotEmpty() })
            } else {
                condition.amountCentsValue != null || condition.value.isNotBlank()
            }
        }
        else -> condition.value.isNotBlank()
    }

    fun isActionValid(action: RuleAction): Boolean = when (canonicalActionType(action.type)) {
        RuleActionType.CATEGORIZE -> (action.targetCategoryId ?: action.value.toLongOrNull()) != null
        RuleActionType.RENAME -> (action.textValue ?: action.value).isNotBlank()
        else -> true
    }

    fun summarizeCondition(
        condition: RuleCondition,
        accounts: List<Account> = emptyList(),
        categories: List<Category> = emptyList()
    ): String {
        val fLabel = fieldLabel(condition.field)
        val opLabel = operatorLabel(condition.operator)
        val valLabel = when (canonicalField(condition.field)) {
            RuleConditionField.ACCOUNT_ID -> {
                val id = condition.targetId ?: condition.value.toLongOrNull()
                accounts.find { it.id == id }?.name ?: "account #${condition.value}"
            }
            RuleConditionField.CATEGORY_ID -> {
                val id = condition.targetId ?: condition.value.toLongOrNull()
                categories.find { it.id == id }?.name ?: "category #${condition.value}"
            }
            RuleConditionField.AMOUNT -> {
                if (condition.operator == RuleOperator.BETWEEN) {
                    condition.value.split(",").joinToString(" and ") { it.trim() }
                } else {
                    condition.value
                }
            }
            else -> condition.value
        }
        return "$fLabel $opLabel \"$valLabel\""
    }

    /**
     * Computes the default rule name from the rule's final output merchant name:
     * 1. If a RENAME action with non-blank text is present, use that new merchant name.
     * 2. Else if [sourceTransaction] is provided, use its original merchant name (falling back to effective merchant).
     * 3. Else if a MERCHANT condition with non-blank text is present, use that condition value.
     */
    fun defaultRuleName(
        conditions: List<RuleCondition>,
        actions: List<RuleAction>,
        sourceTransaction: Transaction? = null
    ): String {
        val renameValue = actions
            .firstOrNull { canonicalActionType(it.type) == RuleActionType.RENAME }
            ?.let { (it.textValue ?: it.value).trim() }
            .orEmpty()
        if (renameValue.isNotEmpty()) return renameValue.take(80)

        val originalMerchant = sourceTransaction
            ?.let { it.originalMerchant.trim().ifEmpty { it.effectiveMerchant.trim() } }
            .orEmpty()
        if (originalMerchant.isNotEmpty()) return originalMerchant.take(80)

        val conditionMerchant = conditions
            .firstOrNull { canonicalField(it.field) == RuleConditionField.MERCHANT }
            ?.value
            ?.trim()
            .orEmpty()
        if (conditionMerchant.isNotEmpty()) return conditionMerchant.take(80)

        return ""
    }

    fun resolveRuleName(
        rawName: String,
        conditions: List<RuleCondition>,
        actions: List<RuleAction>,
        accounts: List<Account> = emptyList(),
        categories: List<Category> = emptyList(),
        sourceTransaction: Transaction? = null
    ): String {
        val trimmed = rawName.trim()
        if (trimmed.isNotEmpty()) return trimmed

        val merchantDefault = defaultRuleName(conditions, actions, sourceTransaction)
        if (merchantDefault.isNotEmpty()) return merchantDefault

        val firstCond = conditions.firstOrNull()
        if (firstCond != null) {
            return summarizeCondition(firstCond, accounts, categories).trim().take(80)
        }
        return "New Rule"
    }
}
