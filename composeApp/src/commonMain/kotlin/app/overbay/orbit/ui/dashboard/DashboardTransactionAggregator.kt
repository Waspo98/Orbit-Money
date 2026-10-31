package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import kotlin.math.abs

internal data class DashboardTransactionScanResult(
    val monthTxns: List<Transaction>,
    val incomeCents: Cents,
    val expenseCents: Cents,
    val priorIncomeCents: Cents,
    val priorExpenseCents: Cents,
    val categorySpendingMap: Map<Long, Long>,
    val topBiggestExpenses: List<Transaction>,
    val topUncategorized: List<Transaction>,
    val uncategorizedCount: Int,
    val topRecent: List<Transaction>,
    val mhaSpendingCents: Cents,
    val mhaTransactionCount: Int
)

/**
 * Single-pass O(N) transaction scanner for the Dashboard.
 * Replaces 8 separate full-list filter/sort passes with bounded top-K insertion so cold launch
 * and live sync remain sub-millisecond even with 10,000+ transactions.
 */
internal object DashboardTransactionAggregator {

    fun scan(
        transactions: List<Transaction>,
        accountById: Map<Long, Account>,
        selectedMonth: String,
        priorMonth: String,
        currentYearStr: String,
        hiddenBiggestIds: Set<Long>
    ): DashboardTransactionScanResult {
        val monthTxns = ArrayList<Transaction>()
        var incomeCents: Cents = 0L
        var expenseCents: Cents = 0L
        var priorIncome: Cents = 0L
        var priorExpense: Cents = 0L
        val categorySpendingMap = HashMap<Long, Long>()
        val topBiggestExpenses = ArrayList<Transaction>(6)
        val topUncategorized = ArrayList<Transaction>(6)
        var uncategorizedCount = 0
        val topRecent = ArrayList<Transaction>(6)
        var mhaSpending: Cents = 0L
        var mhaTxnCount = 0

        for (txn in transactions) {
            if (txn.effectiveIsIgnored) continue

            val effDate = txn.effectiveDate
            insertTopRecent(topRecent, txn)

            if (txn.effectiveCategoryId == null) {
                uncategorizedCount++
                insertTopByDateDesc(topUncategorized, txn)
            }

            if (effDate.startsWith(currentYearStr)) {
                val isMhaEligible = txn.effectiveMhaEligible == true ||
                    (txn.effectiveMhaEligible == null && accountById[txn.accountId]?.mhaDefaultEligible == true)
                if (isMhaEligible) {
                    mhaSpending += abs(txn.effectiveAmountCents)
                    mhaTxnCount++
                }
            }

            if (txn.effectiveIsTransfer) continue

            val isSelectedMonth = effDate.length >= selectedMonth.length &&
                effDate.regionMatches(0, selectedMonth, 0, selectedMonth.length)
            if (isSelectedMonth) {
                monthTxns.add(txn)
                val amt = txn.effectiveAmountCents
                if (amt > 0L) {
                    incomeCents += amt
                } else if (amt < 0L) {
                    val absAmt = -amt
                    expenseCents += absAmt
                    val catId = txn.effectiveCategoryId ?: 0L
                    categorySpendingMap[catId] = (categorySpendingMap[catId] ?: 0L) + absAmt
                    if (txn.id !in hiddenBiggestIds) {
                        insertTopByAbsAmountDesc(topBiggestExpenses, txn, absAmt)
                    }
                }
            } else if (
                effDate.length >= priorMonth.length &&
                effDate.regionMatches(0, priorMonth, 0, priorMonth.length)
            ) {
                val amt = txn.effectiveAmountCents
                if (amt > 0L) priorIncome += amt else if (amt < 0L) priorExpense += -amt
            }
        }

        return DashboardTransactionScanResult(
            monthTxns = monthTxns,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            priorIncomeCents = priorIncome,
            priorExpenseCents = priorExpense,
            categorySpendingMap = categorySpendingMap,
            topBiggestExpenses = topBiggestExpenses,
            topUncategorized = topUncategorized,
            uncategorizedCount = uncategorizedCount,
            topRecent = topRecent,
            mhaSpendingCents = mhaSpending,
            mhaTransactionCount = mhaTxnCount
        )
    }

    private fun insertTopRecent(top: ArrayList<Transaction>, candidate: Transaction, maxSize: Int = 5) {
        val date = candidate.effectiveDate
        val id = candidate.id
        if (top.size == maxSize) {
            val tail = top[maxSize - 1]
            val cmpTail = date.compareTo(tail.effectiveDate)
            if (cmpTail < 0 || (cmpTail == 0 && id <= tail.id)) return
        }
        var insertAt = top.size
        for (i in 0 until top.size) {
            val cur = top[i]
            val cmp = date.compareTo(cur.effectiveDate)
            if (cmp > 0 || (cmp == 0 && id > cur.id)) {
                insertAt = i
                break
            }
        }
        if (insertAt < maxSize) {
            top.add(insertAt, candidate)
            if (top.size > maxSize) top.removeAt(maxSize)
        }
    }

    private fun insertTopByDateDesc(top: ArrayList<Transaction>, candidate: Transaction, maxSize: Int = 5) {
        val date = candidate.effectiveDate
        if (top.size == maxSize && date <= top[maxSize - 1].effectiveDate) return
        var insertAt = top.size
        for (i in 0 until top.size) {
            if (date > top[i].effectiveDate) {
                insertAt = i
                break
            }
        }
        if (insertAt < maxSize) {
            top.add(insertAt, candidate)
            if (top.size > maxSize) top.removeAt(maxSize)
        }
    }

    private fun insertTopByAbsAmountDesc(
        top: ArrayList<Transaction>,
        candidate: Transaction,
        absAmount: Long,
        maxSize: Int = 5
    ) {
        if (top.size == maxSize && absAmount <= abs(top[maxSize - 1].effectiveAmountCents)) return
        var insertAt = top.size
        for (i in 0 until top.size) {
            if (absAmount > abs(top[i].effectiveAmountCents)) {
                insertAt = i
                break
            }
        }
        if (insertAt < maxSize) {
            top.add(insertAt, candidate)
            if (top.size > maxSize) top.removeAt(maxSize)
        }
    }
}
