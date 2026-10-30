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
     * Respects user and system edit locks and preserves object identity when unchanged.
     */
    fun applyRules(
        transactions: List<Transaction>,
        rules: List<TransactionRule>
    ): List<Transaction> {
        if (transactions.isEmpty()) return transactions
        val compiledRules = compileSortedRules(rules)
        var anyChanged = false
        val updated = transactions.map { txn ->
            if (txn.editSource == EditSource.USER) {
                txn
            } else {
                val next = computeCompiledEdits(txn, compiledRules)
                if (next !== txn) anyChanged = true
                next
            }
        }
        return if (anyChanged) updated else transactions
    }

    fun evaluateAll(rules: List<TransactionRule>, transactions: List<Transaction>): List<Transaction> =
        applyRules(transactions, rules)

    /**
     * Fast-path O(N) evaluation when adding a brand-new enabled rule.
     * Since conditions inspect immutable original_* fields, non-matching transactions cannot change.
     */
    fun applyNewRuleIncrementally(
        transactions: List<Transaction>,
        allRules: List<TransactionRule>,
        newRule: TransactionRule
    ): List<Transaction> {
        if (transactions.isEmpty() || !newRule.enabled || newRule.conditions.isEmpty() || newRule.actions.isEmpty()) {
            return transactions
        }
        val compiledTarget = compileRule(newRule)
        val compiledAll = compileSortedRules(allRules)
        var anyChanged = false
        val updated = transactions.map { txn ->
            if (txn.editSource == EditSource.USER) {
                txn
            } else {
                val merchantLower = txn.originalMerchant.lowercase()
                val descLower = txn.originalDescription?.lowercase()
                if (!matchesCompiledConditions(txn, merchantLower, descLower, compiledTarget.conditions)) {
                    txn
                } else {
                    val next = computeCompiledEdits(txn, compiledAll, merchantLower, descLower)
                    if (next !== txn) anyChanged = true
                    next
                }
            }
        }
        return if (anyChanged) updated else transactions
    }

    /**
     * Remaps optimistic "rule:oldRuleId" provenance tags to "rule:newRuleId" when server sync completes.
     */
    fun remapRuleSourceTag(
        transactions: List<Transaction>,
        oldRuleId: Long,
        newRuleId: Long
    ): List<Transaction> {
        if (oldRuleId == newRuleId || transactions.isEmpty()) return transactions
        val oldTag = "rule:$oldRuleId"
        val newTag = "rule:$newRuleId"
        var changed = false
        val mapped = transactions.map { txn ->
            val m = txn.editedMerchantSource == oldTag
            val c = txn.editedCategoryIdSource == oldTag
            val t = txn.editedIsTransferSource == oldTag
            val i = txn.editedIsIgnoredSource == oldTag
            val h = txn.editedMhaEligibleSource == oldTag
            if (!m && !c && !t && !i && !h) {
                txn
            } else {
                changed = true
                txn.copy(
                    editedMerchantSource = if (m) newTag else txn.editedMerchantSource,
                    editedCategoryIdSource = if (c) newTag else txn.editedCategoryIdSource,
                    editedIsTransferSource = if (t) newTag else txn.editedIsTransferSource,
                    editedIsIgnoredSource = if (i) newTag else txn.editedIsIgnoredSource,
                    editedMhaEligibleSource = if (h) newTag else txn.editedMhaEligibleSource
                )
            }
        }
        return if (changed) mapped else transactions
    }

    fun computeTransactionEdits(
        transaction: Transaction,
        rules: List<TransactionRule>
    ): Transaction {
        if (transaction.editSource == EditSource.USER) return transaction
        return computeCompiledEdits(transaction, rules.map(::compileRule))
    }

    fun matchesRule(
        transaction: Transaction,
        rule: TransactionRule
    ): Boolean {
        if (rule.conditions.isEmpty()) return false
        val compiled = compileRule(rule)
        if (compiled.conditions.isEmpty()) return false
        return matchesCompiledConditions(
            transaction = transaction,
            merchantLower = transaction.originalMerchant.lowercase(),
            descLower = transaction.originalDescription?.lowercase(),
            conditions = compiled.conditions
        )
    }

    private fun computeCompiledEdits(
        transaction: Transaction,
        rules: List<CompiledRule>,
        precomputedMerchantLower: String? = null,
        precomputedDescLower: String? = null
    ): Transaction {
        if (transaction.editSource == EditSource.USER) return transaction

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

        if (rules.isNotEmpty()) {
            val merchantLower = precomputedMerchantLower ?: transaction.originalMerchant.lowercase()
            val descLower = precomputedDescLower ?: transaction.originalDescription?.lowercase()

            for (rule in rules) {
                if (!matchesCompiledConditions(transaction, merchantLower, descLower, rule.conditions)) continue
                val ruleSourceTag = rule.sourceTag

                for (action in rule.actions) {
                    when (action.type) {
                        RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                            if (editedMerchantSource == null && action.newName != null && action.newName != transaction.originalMerchant) {
                                editedMerchant = action.newName
                                editedMerchantSource = ruleSourceTag
                            }
                        }
                        RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                            if (editedCategoryIdSource == null && action.targetCatId != null && action.targetCatId != transaction.originalCategoryId) {
                                editedCategoryId = action.targetCatId
                                editedCategoryIdSource = ruleSourceTag
                            }
                        }
                        RuleActionType.MARK_TRANSFER -> {
                            if (editedIsTransferSource == null && !transaction.originalIsTransfer) {
                                editedIsTransfer = true
                                editedIsTransferSource = ruleSourceTag
                            }
                        }
                        RuleActionType.MARK_IGNORED -> {
                            if (editedIsIgnoredSource == null && !transaction.originalIsIgnored) {
                                editedIsIgnored = true
                                editedIsIgnoredSource = ruleSourceTag
                            }
                        }
                        RuleActionType.SET_MHA_ELIGIBLE -> {
                            if (editedMhaEligibleSource == null) {
                                editedMhaEligible = action.boolVal
                                editedMhaEligibleSource = ruleSourceTag
                            }
                        }
                    }
                }
            }
        }

        val hasAnyRuleEdits = (editedMerchantSource?.startsWith("rule:") == true) ||
            (editedCategoryIdSource?.startsWith("rule:") == true) ||
            (editedIsTransferSource?.startsWith("rule:") == true) ||
            (editedIsIgnoredSource?.startsWith("rule:") == true) ||
            (editedMhaEligibleSource?.startsWith("rule:") == true)

        val newEditSource = when {
            transaction.editSource == EditSource.USER -> EditSource.USER
            hasAnyRuleEdits -> EditSource.RULE
            else -> EditSource.NONE
        }

        if (
            transaction.editedMerchant == editedMerchant &&
            transaction.editedMerchantSource == editedMerchantSource &&
            transaction.editedCategoryId == editedCategoryId &&
            transaction.editedCategoryIdSource == editedCategoryIdSource &&
            transaction.editedIsTransfer == editedIsTransfer &&
            transaction.editedIsTransferSource == editedIsTransferSource &&
            transaction.editedIsIgnored == editedIsIgnored &&
            transaction.editedIsIgnoredSource == editedIsIgnoredSource &&
            transaction.editedMhaEligible == editedMhaEligible &&
            transaction.editedMhaEligibleSource == editedMhaEligibleSource &&
            transaction.editSource == newEditSource
        ) {
            return transaction
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
        val compiled = conditions.map(::compileCondition)
        return matchesCompiledConditions(
            transaction = transaction,
            merchantLower = transaction.originalMerchant.lowercase(),
            descLower = transaction.originalDescription?.lowercase(),
            conditions = compiled
        )
    }

    fun countMatches(transactions: List<Transaction>, conditions: List<RuleCondition>): Int {
        if (conditions.isEmpty() || transactions.isEmpty()) return 0
        val compiled = conditions.map(::compileCondition)
        return transactions.count { txn ->
            matchesCompiledConditions(txn, txn.originalMerchant.lowercase(), txn.originalDescription?.lowercase(), compiled)
        }
    }

    fun matchingTransactions(transactions: List<Transaction>, conditions: List<RuleCondition>): List<Transaction> {
        if (conditions.isEmpty() || transactions.isEmpty()) return emptyList()
        val compiled = conditions.map(::compileCondition)
        return transactions
            .filter { txn ->
                matchesCompiledConditions(txn, txn.originalMerchant.lowercase(), txn.originalDescription?.lowercase(), compiled)
            }
            .sortedWith(compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id })
    }

    private fun compileSortedRules(rules: List<TransactionRule>): List<CompiledRule> =
        rules
            .filter { it.enabled && it.conditions.isNotEmpty() && it.actions.isNotEmpty() }
            .sortedWith(compareByDescending<TransactionRule> { it.priority }.thenBy { it.id })
            .map(::compileRule)

    private fun compileRule(rule: TransactionRule): CompiledRule = CompiledRule(
        sourceTag = "rule:${rule.id}",
        conditions = rule.conditions.map(::compileCondition),
        actions = rule.actions.map(::compileAction)
    )

    private fun compileCondition(condition: RuleCondition): CompiledCondition {
        val parsedRange = if (
            condition.operator == RuleOperator.BETWEEN &&
            condition.minAmountCentsValue == null &&
            condition.maxAmountCentsValue == null
        ) parseAmountRange(condition.value) else null
        return CompiledCondition(
            field = condition.field,
            operator = condition.operator,
            targetLower = condition.value.trim().lowercase(),
            compiledRegex = if (condition.operator == RuleOperator.REGEX) {
                runCatching { Regex(condition.value, RegexOption.IGNORE_CASE) }.getOrNull()
            } else null,
            targetId = condition.targetId ?: condition.value.toLongOrNull(),
            targetAmountCents = condition.amountCentsValue ?: MoneyUtils.dollarsToCents(condition.value),
            minAmountCents = condition.minAmountCentsValue ?: parsedRange?.first ?: 0L,
            maxAmountCents = condition.maxAmountCentsValue ?: parsedRange?.second ?: Long.MAX_VALUE
        )
    }

    private fun compileAction(action: RuleAction): CompiledAction = CompiledAction(
        type = action.type,
        newName = action.textValue ?: action.value.takeIf { it.isNotBlank() },
        targetCatId = action.targetCategoryId ?: action.value.toLongOrNull(),
        boolVal = action.booleanValue ?: (action.value.equals("1", ignoreCase = true) || action.value.equals("true", ignoreCase = true))
    )

    private fun matchesCompiledConditions(
        transaction: Transaction,
        merchantLower: String,
        descLower: String?,
        conditions: List<CompiledCondition>
    ): Boolean {
        if (conditions.isEmpty()) return false
        for (cond in conditions) {
            if (!matchesCompiledCondition(transaction, merchantLower, descLower, cond)) return false
        }
        return true
    }

    private fun matchesCompiledCondition(
        transaction: Transaction,
        merchantLower: String,
        descLower: String?,
        cond: CompiledCondition
    ): Boolean = when (cond.field) {
        RuleConditionField.MERCHANT -> {
            matchesSingleText(transaction.originalMerchant, merchantLower, cond) ||
                (descLower != null && matchesSingleText(transaction.originalDescription ?: "", descLower, cond))
        }
        RuleConditionField.ORIGINAL_DESCRIPTION -> {
            descLower != null && matchesSingleText(transaction.originalDescription ?: "", descLower, cond)
        }
        RuleConditionField.AMOUNT -> {
            val amountCents = abs(transaction.originalAmountCents)
            when (cond.operator) {
                RuleOperator.EQUALS, RuleOperator.EXACT -> amountCents == cond.targetAmountCents
                RuleOperator.GREATER_THAN -> amountCents > cond.targetAmountCents
                RuleOperator.LESS_THAN -> amountCents < cond.targetAmountCents
                RuleOperator.BETWEEN -> amountCents in cond.minAmountCents..cond.maxAmountCents
                else -> false
            }
        }
        RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID -> {
            when (cond.operator) {
                RuleOperator.IS, RuleOperator.EQUALS, RuleOperator.EXACT -> transaction.accountId == cond.targetId
                RuleOperator.IS_NOT -> transaction.accountId != cond.targetId
                else -> false
            }
        }
        RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID -> {
            when (cond.operator) {
                RuleOperator.IS, RuleOperator.EQUALS, RuleOperator.EXACT -> transaction.originalCategoryId == cond.targetId
                RuleOperator.IS_NOT -> transaction.originalCategoryId != cond.targetId
                else -> false
            }
        }
        RuleConditionField.NOTES -> {
            val notes = transaction.notes
            notes != null && matchesSingleText(notes, notes.lowercase(), cond)
        }
    }

    private fun matchesSingleText(rawCandidate: String, candidateLower: String, cond: CompiledCondition): Boolean =
        when (cond.operator) {
            RuleOperator.EQUALS, RuleOperator.EXACT -> candidateLower == cond.targetLower
            RuleOperator.CONTAINS -> candidateLower.contains(cond.targetLower)
            RuleOperator.STARTS_WITH -> candidateLower.startsWith(cond.targetLower)
            RuleOperator.ENDS_WITH -> candidateLower.endsWith(cond.targetLower)
            RuleOperator.REGEX -> cond.compiledRegex?.containsMatchIn(rawCandidate) == true
            else -> false
        }

    private class CompiledRule(
        val sourceTag: String,
        val conditions: List<CompiledCondition>,
        val actions: List<CompiledAction>
    )

    private class CompiledCondition(
        val field: RuleConditionField,
        val operator: RuleOperator,
        val targetLower: String,
        val compiledRegex: Regex?,
        val targetId: Long?,
        val targetAmountCents: Long,
        val minAmountCents: Long,
        val maxAmountCents: Long
    )

    private class CompiledAction(
        val type: RuleActionType,
        val newName: String?,
        val targetCatId: Long?,
        val boolVal: Boolean
    )

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
        val rawFirst = MoneyUtils.dollarsToCents(parts[0], fallback = Long.MIN_VALUE)
        val rawSecond = MoneyUtils.dollarsToCents(parts[1], fallback = Long.MIN_VALUE)
        if (rawFirst == Long.MIN_VALUE || rawSecond == Long.MIN_VALUE) return null
        val first = abs(rawFirst)
        val second = abs(rawSecond)
        return Pair(minOf(first, second), maxOf(first, second))
    }

    private fun isLocked(source: String?): Boolean {
        if (source == null) return false
        return source == "user" || source.startsWith("system:")
    }
}
