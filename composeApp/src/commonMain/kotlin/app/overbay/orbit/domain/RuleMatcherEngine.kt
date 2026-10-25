package app.overbay.orbit.domain

import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import kotlin.math.abs

object RuleMatcherEngine {

    /**
     * Re-evaluates all enabled rules against transactions in priority DESC, id ASC order.
     * Evaluates conditions strictly against immutable original_* fields.
     * Respects user and system edit locks.
     */
    fun applyRules(
        transactions: List<Transaction>,
        rules: List<TransactionRule>
    ): List<Transaction> {
        val sortedRules = rules
            .filter { it.enabled && it.conditions.isNotEmpty() && it.actions.isNotEmpty() }
            .sortedWith(compareByDescending<TransactionRule> { it.priority }.thenBy { it.id })

        return transactions.map { txn ->
            if (txn.editSource == EditSource.USER) {
                txn
            } else {
                computeTransactionEdits(txn, sortedRules)
            }
        }
    }

    fun evaluateAll(rules: List<TransactionRule>, transactions: List<Transaction>): List<Transaction> =
        applyRules(transactions, rules)

    fun computeTransactionEdits(
        transaction: Transaction,
        rules: List<TransactionRule>
    ): Transaction {
        // Strict User Provenance Lock: user-edited transactions are never mutated by automated rules
        if (transaction.editSource == EditSource.USER) {
            return transaction
        }

        // Start from empty edits, preserving user and system locks
        var editedMerchant: String? = null
        var editedMerchantSource: String? = null

        var editedCategoryId: Long? = null
        var editedCategoryIdSource: String? = null

        var editedIsTransfer: Boolean? = null
        var editedIsTransferSource: String? = null

        var editedIsIgnored: Boolean? = null
        var editedIsIgnoredSource: String? = null

        var editedMhaEligible: Boolean? = null
        var editedMhaEligibleSource: String? = null

        // Preserve locks
        if (isLocked(transaction.editedMerchantSource)) {
            editedMerchant = transaction.editedMerchant
            editedMerchantSource = transaction.editedMerchantSource
        }
        if (isLocked(transaction.editedCategoryIdSource)) {
            editedCategoryId = transaction.editedCategoryId
            editedCategoryIdSource = transaction.editedCategoryIdSource
        }
        if (isLocked(transaction.editedIsTransferSource)) {
            editedIsTransfer = transaction.editedIsTransfer
            editedIsTransferSource = transaction.editedIsTransferSource
        }
        if (isLocked(transaction.editedIsIgnoredSource)) {
            editedIsIgnored = transaction.editedIsIgnored
            editedIsIgnoredSource = transaction.editedIsIgnoredSource
        }
        if (isLocked(transaction.editedMhaEligibleSource)) {
            editedMhaEligible = transaction.editedMhaEligible
            editedMhaEligibleSource = transaction.editedMhaEligibleSource
        }

        // Apply rules in order. First rule to claim an unclaimed field wins.
        for (rule in rules) {
            if (!evalConditions(transaction, rule.conditions)) continue
            val ruleSourceTag = "rule:${rule.id}"

            for (action in rule.actions) {
                when (action.type) {
                    RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                        val newName = action.textValue ?: action.value.takeIf { it.isNotBlank() }
                        if (editedMerchantSource == null && newName != null) {
                            if (newName != transaction.originalMerchant) {
                                editedMerchant = newName
                                editedMerchantSource = ruleSourceTag
                            }
                        }
                    }
                    RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                        val targetCatId = action.targetCategoryId ?: action.value.toLongOrNull()
                        if (editedCategoryIdSource == null && targetCatId != null) {
                            if (targetCatId != transaction.originalCategoryId) {
                                editedCategoryId = targetCatId
                                editedCategoryIdSource = ruleSourceTag
                            }
                        }
                    }
                    RuleActionType.MARK_TRANSFER -> {
                        if (editedIsTransferSource == null) {
                            if (!transaction.originalIsTransfer) {
                                editedIsTransfer = true
                                editedIsTransferSource = ruleSourceTag
                            }
                        }
                    }
                    RuleActionType.MARK_IGNORED -> {
                        if (editedIsIgnoredSource == null) {
                            if (!transaction.originalIsIgnored) {
                                editedIsIgnored = true
                                editedIsIgnoredSource = ruleSourceTag
                            }
                        }
                    }
                    RuleActionType.SET_MHA_ELIGIBLE -> {
                        val boolVal = action.booleanValue ?: (action.value.lowercase() in setOf("1", "true"))
                        if (editedMhaEligibleSource == null) {
                            editedMhaEligible = boolVal
                            editedMhaEligibleSource = ruleSourceTag
                        }
                    }
                }
            }
        }

        val hasAnyRuleEdits = listOf(
            editedMerchantSource,
            editedCategoryIdSource,
            editedIsTransferSource,
            editedIsIgnoredSource,
            editedMhaEligibleSource
        ).any { it?.startsWith("rule:") == true }

        val newEditSource = when {
            transaction.editSource == EditSource.USER -> EditSource.USER
            hasAnyRuleEdits -> EditSource.RULE
            else -> EditSource.NONE
        }

        return transaction.copy(
            editedMerchant = editedMerchant,
            editedMerchantSource = editedMerchantSource,
            editedCategoryId = editedCategoryId,
            editedCategoryIdSource = editedCategoryIdSource,
            editedIsTransfer = editedIsTransfer,
            editedIsTransferSource = editedIsTransferSource,
            editedIsIgnored = editedIsIgnored,
            editedIsIgnoredSource = editedIsIgnoredSource,
            editedMhaEligible = editedMhaEligible,
            editedMhaEligibleSource = editedMhaEligibleSource,
            editSource = newEditSource
        )
    }

    fun evalConditions(transaction: Transaction, conditions: List<RuleCondition>): Boolean {
        if (conditions.isEmpty()) return false
        return conditions.all { matchesCondition(transaction, it) }
    }

    private fun matchesCondition(transaction: Transaction, condition: RuleCondition): Boolean {
        return when (condition.field) {
            RuleConditionField.MERCHANT -> {
                val candidates = listOfNotNull(transaction.originalMerchant, transaction.originalDescription)
                matchesAnyText(candidates, condition.operator, condition.value)
            }
            RuleConditionField.ORIGINAL_DESCRIPTION -> {
                matchesAnyText(listOfNotNull(transaction.originalDescription), condition.operator, condition.value)
            }
            RuleConditionField.AMOUNT -> {
                val amountCents = abs(transaction.originalAmountCents)
                val targetAmountCents = condition.amountCentsValue ?: MoneyUtils.dollarsToCents(condition.value)
                when (condition.operator) {
                    RuleOperator.EQUALS, RuleOperator.EXACT -> amountCents == targetAmountCents
                    RuleOperator.GREATER_THAN -> amountCents > targetAmountCents
                    RuleOperator.LESS_THAN -> amountCents < targetAmountCents
                    RuleOperator.BETWEEN -> {
                        val parsedRange = if (condition.minAmountCentsValue == null && condition.maxAmountCentsValue == null) {
                            parseAmountRange(condition.value)
                        } else null
                        val min = condition.minAmountCentsValue ?: parsedRange?.first ?: 0L
                        val max = condition.maxAmountCentsValue ?: parsedRange?.second ?: Long.MAX_VALUE
                        amountCents in min..max
                    }
                    else -> false
                }
            }
            RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID -> {
                val targetId = condition.targetId ?: condition.value.toLongOrNull()
                when (condition.operator) {
                    RuleOperator.IS, RuleOperator.EQUALS, RuleOperator.EXACT -> transaction.accountId == targetId
                    RuleOperator.IS_NOT -> transaction.accountId != targetId
                    else -> false
                }
            }
            RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID -> {
                val targetId = condition.targetId ?: condition.value.toLongOrNull()
                when (condition.operator) {
                    RuleOperator.IS, RuleOperator.EQUALS, RuleOperator.EXACT -> transaction.originalCategoryId == targetId
                    RuleOperator.IS_NOT -> transaction.originalCategoryId != targetId
                    else -> false
                }
            }
            RuleConditionField.NOTES -> {
                matchesAnyText(listOfNotNull(transaction.notes), condition.operator, condition.value)
            }
        }
    }

    private fun matchesAnyText(candidates: List<String>, operator: RuleOperator, expected: String): Boolean {
        val targetLower = expected.trim().lowercase()
        return candidates.any { candidate ->
            val actualLower = candidate.lowercase()
            when (operator) {
                RuleOperator.EQUALS, RuleOperator.EXACT -> actualLower == targetLower
                RuleOperator.CONTAINS -> actualLower.contains(targetLower)
                RuleOperator.STARTS_WITH -> actualLower.startsWith(targetLower)
                RuleOperator.ENDS_WITH -> actualLower.endsWith(targetLower)
                RuleOperator.REGEX -> {
                    try {
                        Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(candidate)
                    } catch (_: Exception) {
                        false
                    }
                }
                else -> false
            }
        }
    }

    fun countMatches(transactions: List<Transaction>, conditions: List<RuleCondition>): Int {
        return transactions.count { evalConditions(it, conditions) }
    }

    private fun parseAmountRange(raw: String): Pair<Cents, Cents>? {
        if (raw.isBlank()) return null
        val cleaned = raw.trim().removeSurrounding("[", "]").trim()
        val delimiter = when {
            cleaned.contains(" to ", ignoreCase = true) -> Regex("\\s+to\\s+", RegexOption.IGNORE_CASE)
            cleaned.contains(" and ", ignoreCase = true) -> Regex("\\s+and\\s+", RegexOption.IGNORE_CASE)
            cleaned.contains(",") -> Regex(",")
            cleaned.contains("-") && !cleaned.startsWith("-") -> Regex("-")
            cleaned.contains("..") -> Regex("\\.\\.")
            else -> return null
        }
        val parts = cleaned.split(delimiter, limit = 2).map { it.trim() }
        if (parts.size != 2) return null
        val first = abs(MoneyUtils.dollarsToCents(parts[0], fallback = Long.MIN_VALUE))
        val second = abs(MoneyUtils.dollarsToCents(parts[1], fallback = Long.MIN_VALUE))
        if (first == Long.MIN_VALUE || second == Long.MIN_VALUE) return null
        return Pair(minOf(first, second), maxOf(first, second))
    }

    private fun isLocked(source: String?): Boolean {
        if (source == null) return false
        return source == "user" || source.startsWith("system:")
    }
}
