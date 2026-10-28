package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import kotlin.math.abs

data class TransferMatchResult(
    val updatedTransactions: List<Transaction>,
    val pairsCreated: Int
) {
    val matchCount: Int get() = pairsCreated
}

object TransferMatcherEngine {

    const val MAX_TRANSFER_DAYS = 4

    /**
     * Pairs opposite-sign transactions of equal absolute amount across distinct accounts within ±4 days.
     */
    fun matchTransfers(
        transactions: List<Transaction>,
        categories: Map<Long, Category> = emptyMap()
    ): TransferMatchResult {
        // Find transfer candidates: transfer category, or marked transfer, not ignored, not already paired
        val candidates = transactions.filter { txn ->
            val cat = txn.effectiveCategoryId?.let { categories[it] }
            val isTransferCategory = cat?.isTransfer == true || txn.effectiveIsTransfer || txn.isTransfer
            isTransferCategory &&
                !txn.effectiveIsIgnored &&
                txn.transferPairId == null &&
                txn.editedIsTransferSource != "user"
        }.sortedWith(compareByDescending<Transaction> { it.effectiveDate }.thenBy { it.id })

        val consumedIds = mutableSetOf<Long>()
        val pairedMap = mutableMapOf<Long, Long>() // id -> peerId

        for (i in candidates.indices) {
            val a = candidates[i]
            if (consumedIds.contains(a.id)) continue

            for (j in (i + 1) until candidates.size) {
                val b = candidates[j]
                if (consumedIds.contains(b.id)) continue

                // Check criteria:
                // 1. Different accounts
                // 2. Opposite sign amounts
                // 3. Exact matching absolute cents
                // 4. Within ±4 days
                val diffAccounts = a.accountId != b.accountId
                val oppositeSigns = (a.effectiveAmountCents > 0) != (b.effectiveAmountCents > 0)
                val sameMagnitude = abs(a.effectiveAmountCents) == abs(b.effectiveAmountCents)
                val dayDelta = daysBetween(a.effectiveDate, b.effectiveDate)

                if (diffAccounts && oppositeSigns && sameMagnitude && dayDelta <= MAX_TRANSFER_DAYS) {
                    consumedIds.add(a.id)
                    consumedIds.add(b.id)
                    pairedMap[a.id] = b.id
                    pairedMap[b.id] = a.id
                    break
                }
            }
        }

        if (pairedMap.isEmpty()) {
            return TransferMatchResult(transactions, 0)
        }

        val updated = transactions.map { txn ->
            val peerId = pairedMap[txn.id]
            if (peerId != null) {
                txn.copy(
                    transferPairId = peerId,
                    editedIsTransfer = true,
                    editedIsTransferSource = "system:transfer_matcher",
                    editSource = if (txn.editSource == EditSource.NONE) EditSource.SYSTEM else txn.editSource
                )
            } else {
                txn
            }
        }

        return TransferMatchResult(updated, pairedMap.size / 2)
    }

    fun matchTransfers(transactions: List<Transaction>): List<Transaction> =
        matchTransfers(transactions, emptyMap()).updatedTransactions

    private fun daysBetween(date1: String, date2: String): Int {
        val days1 = isoToEpochDay(date1)
        val days2 = isoToEpochDay(date2)
        return abs(days1 - days2)
    }

    private fun isoToEpochDay(iso: String): Int {
        val parts = iso.split("-")
        if (parts.size < 3) return 0
        val y = parts[0].toIntOrNull() ?: 2026
        val m = parts[1].toIntOrNull() ?: 1
        val d = parts[2].toIntOrNull() ?: 1
        // Gregorian epoch day algorithm
        val a = (14 - m) / 12
        val yPrime = y + 4800 - a
        val mPrime = m + 12 * a - 3
        return d + (153 * mPrime + 2) / 5 + 365 * yPrime + yPrime / 4 - yPrime / 100 + yPrime / 400 - 32045
    }
}
