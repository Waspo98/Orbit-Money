package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Transaction

object CardTransactionClassifier {
    private val PAYMENT_HINTS = listOf(
        "credit card payment", "card payment", "cc payment",
        "payment thank you", "autopay payment", "card ending"
    )
    private val PAYMENT_REGEXES = listOf(
        Regex("""payment\s+to\s+.+\bcard\s+ending\b"""),
        Regex("""\bcard\s+ending\s+(in\s+)?[#*\d]{2,}"""),
        Regex("""payment\s*[-–]?\s*thank\s*you""")
    )
    private val NON_SPEND_HINTS = listOf(
        "annual fee", "annual membership", "membership fee", "renewal fee",
        "interest charge", "finance charge", "purchase interest", "late fee", "late payment fee",
        "cash advance", "balance transfer", "foreign transaction fee", "returned payment"
    )
    private val STATEMENT_CREDIT_HINTS = listOf(
        "statement credit", "uber cash", "dining credit",
        "travel credit", "digital entertainment credit", "hotel credit", "airline fee credit", "clear credit"
    )

    fun looksLikeCardPayment(t: Transaction): Boolean {
        val s = t.effectiveMerchant.lowercase()
        return PAYMENT_HINTS.any { it in s } || PAYMENT_REGEXES.any { it.containsMatchIn(s) }
    }

    fun isNonSpendCharge(t: Transaction): Boolean =
        NON_SPEND_HINTS.any { it in t.effectiveMerchant.lowercase() }

    fun isStatementCredit(t: Transaction): Boolean =
        t.effectiveAmountCents > 0 && STATEMENT_CREDIT_HINTS.any { it in t.effectiveMerchant.lowercase() }

    /** Signed contribution to bonus spend: purchases add, refunds subtract, everything else is 0. */
    fun bonusSpendDeltaCents(t: Transaction): Cents = when {
        t.effectiveIsTransfer || t.effectiveIsIgnored -> 0L
        looksLikeCardPayment(t) || isNonSpendCharge(t) || isStatementCredit(t) -> 0L
        else -> -t.effectiveAmountCents // purchase (-) -> +spend, refund (+) -> -spend
    }
}
