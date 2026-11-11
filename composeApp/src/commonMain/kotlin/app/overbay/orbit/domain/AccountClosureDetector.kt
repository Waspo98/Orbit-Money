package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Transaction
import kotlin.math.abs

enum class ClosureReason {
    MISSING_FROM_SIMPLEFIN,
    BALANCE_REACHED_ZERO,
    LARGE_PAYOFF_CREDIT
}

enum class AccountClosureResolution(val wireKey: String) {
    SOLD_PROPERTY("sold_property"),
    SOLD_OR_CLOSED("sold_or_closed"),
    PAID_OFF_KEEP_PROPERTY("paid_off_keep_property"),
    REFINANCED_KEEP_PROPERTY("refinanced_keep_property"),
    DISMISS("dismiss")
}

data class AccountClosureCandidate(
    val account: Account,
    val reason: ClosureReason,
    val payoffTransaction: Transaction? = null,
    val hasPropertyContext: Boolean = false,
    val propertyTitle: String? = null,
    val estimatedPropertyValueCents: Cents? = null
) {
    fun defaultResolution(): AccountClosureResolution {
        if (!hasPropertyContext) return AccountClosureResolution.SOLD_OR_CLOSED
        val memo = buildString {
            payoffTransaction?.effectiveMerchant?.let { append(it).append(' ') }
            payoffTransaction?.originalDescription?.let { append(it) }
        }.lowercase()
        val mentionsSale = Regex("\\b(sale|sold)\\b").containsMatchIn(memo)
        return if (mentionsSale) {
            AccountClosureResolution.SOLD_PROPERTY
        } else {
            AccountClosureResolution.PAID_OFF_KEEP_PROPERTY
        }
    }
}

object AccountClosureDetector {

    private const val MIN_PRINCIPAL_FOR_PAYOFF_HEURISTIC_CENTS: Cents = 1_000_00L
    private const val PAYOFF_LOOKBACK_DAYS = 90

    /**
     * Evaluates active accounts against SimpleFIN sync tracking and ledger inflows
     * to surface "Did you recently pay off or close this account?" prompts.
     */
    fun detectCandidates(
        accounts: List<Account>,
        transactions: List<Transaction>,
        todayIso: String,
        dismissedOverrides: Map<String, String> = emptyMap()
    ): List<AccountClosureCandidate> {
        val activeAccounts = accounts.filter { !it.isArchived }
        if (activeAccounts.isEmpty()) return emptyList()

        val accountById = activeAccounts.associateBy { it.id }
        val txnsByAccount = transactions
            .filter { !it.effectiveIsIgnored && it.effectiveAmountCents > 0L }
            .groupBy { it.accountId }

        val results = mutableListOf<AccountClosureCandidate>()

        for (account in activeAccounts) {
            if (account.isProperty) continue
            if (isDismissedForCurrentState(account, dismissedOverrides)) continue

            val parentProperty = account.linkedPropertyAccountId?.let { accountById[it] }
            val hasProp = ((account.estimatedValueCents ?: 0L) > 0L) || parentProperty != null
            val propTitle = parentProperty?.let { it.propertyName?.takeIf(String::isNotBlank) ?: it.name }
                ?: if ((account.estimatedValueCents ?: 0L) > 0L) {
                    account.propertyName?.takeIf(String::isNotBlank) ?: account.name
                } else null
            val propValue = parentProperty?.estimatedValueCents ?: account.estimatedValueCents

            // 1. Synced account vanished from SimpleFIN feed while institution was healthy
            if (!account.simplefinAccountId.isNullOrBlank() && !account.simplefinMissingSince.isNullOrBlank()) {
                results.add(
                    AccountClosureCandidate(
                        account = account,
                        reason = ClosureReason.MISSING_FROM_SIMPLEFIN,
                        hasPropertyContext = hasProp,
                        propertyTitle = propTitle,
                        estimatedPropertyValueCents = propValue
                    )
                )
                continue
            }

            if (account.type != AccountType.MORTGAGE && account.type != AccountType.LOAN) continue

            val positiveTxns = txnsByAccount[account.id].orEmpty()

            // 2. Mortgage or Loan balance reached $0.00
            if (account.currentBalanceCents == 0L && (!account.isManual || account.transactionCount > 0 || positiveTxns.isNotEmpty())) {
                results.add(
                    AccountClosureCandidate(
                        account = account,
                        reason = ClosureReason.BALANCE_REACHED_ZERO,
                        payoffTransaction = positiveTxns.maxByOrNull { it.effectiveDate },
                        hasPropertyContext = hasProp,
                        propertyTitle = propTitle,
                        estimatedPropertyValueCents = propValue
                    )
                )
                continue
            }

            // 3. Large payoff credit (~85%..115% of frozen remaining principal within lookback window)
            val absBalance = abs(account.currentBalanceCents)
            if (absBalance >= MIN_PRINCIPAL_FOR_PAYOFF_HEURISTIC_CENTS && positiveTxns.isNotEmpty()) {
                val minPayoff = (absBalance * 85L) / 100L
                val maxPayoff = (absBalance * 115L) / 100L
                val matchingPayoff = positiveTxns
                    .filter { txn ->
                        val amt = txn.effectiveAmountCents
                        val withinAmount = amt in minPayoff..maxPayoff
                        val withinWindow = if (todayIso.length >= 10 && txn.effectiveDate.length >= 10) {
                            val days = LocalDateUtils.daysBetween(txn.effectiveDate.take(10), todayIso.take(10))
                            days != null && days in -2..PAYOFF_LOOKBACK_DAYS
                        } else true
                        withinAmount && withinWindow
                    }
                    .maxByOrNull { it.effectiveDate }

                if (matchingPayoff != null) {
                    results.add(
                        AccountClosureCandidate(
                            account = account,
                            reason = ClosureReason.LARGE_PAYOFF_CREDIT,
                            payoffTransaction = matchingPayoff,
                            hasPropertyContext = hasProp,
                            propertyTitle = propTitle,
                            estimatedPropertyValueCents = propValue
                        )
                    )
                }
            }
        }

        return results
    }

    private fun isDismissedForCurrentState(
        account: Account,
        dismissedOverrides: Map<String, String>
    ): Boolean {
        val serverDismissed = account.closurePromptDismissedAt?.takeIf { it.isNotBlank() }
        val localDismissed = dismissedOverrides[account.id.toString()]?.takeIf { it.isNotBlank() }
        val dismissedAt = listOfNotNull(serverDismissed, localDismissed).maxOrNull() ?: return false
        val missingSince = account.simplefinMissingSince?.takeIf { it.isNotBlank() }
        // Re-prompt only if SimpleFIN newly flagged the account missing after the user's prior dismissal
        if (missingSince != null && missingSince > dismissedAt) return false
        return true
    }
}
