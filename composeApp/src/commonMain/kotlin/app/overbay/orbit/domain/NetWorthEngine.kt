package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.Transaction
import kotlin.math.abs

enum class NetWorthBucket {
    CASH,
    INVESTMENTS,
    REAL_ESTATE,
    CREDIT,
    LOANS,
    OTHER
}

data class NetWorthSnapshot(
    val cashCents: Cents,
    val investmentsCents: Cents,
    val realEstateEquityCents: Cents,
    val creditDebtCents: Cents,
    val loanDebtCents: Cents,
    val otherCents: Cents,
    val totalAssetsCents: Cents,
    val totalLiabilitiesCents: Cents,
    val netWorthCents: Cents
)

data class MonthlyNetWorthPoint(
    val month: String, // YYYY-MM
    val snapshot: NetWorthSnapshot,
    val deltaFromPreviousCents: Cents = 0L,
    val percentChange: Double? = null
)

object NetWorthEngine {

    fun bucketForAccount(account: Account): NetWorthBucket {
        return when (account.type) {
            AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH -> NetWorthBucket.CASH
            AccountType.INVESTMENT -> NetWorthBucket.INVESTMENTS
            AccountType.MORTGAGE -> if (account.estimatedValueCents != null) NetWorthBucket.REAL_ESTATE else NetWorthBucket.LOANS
            AccountType.CREDIT -> NetWorthBucket.CREDIT
            AccountType.LOAN -> NetWorthBucket.LOANS
            AccountType.OTHER -> NetWorthBucket.OTHER
        }
    }

    fun accountContributionCents(account: Account, balanceCents: Cents): Cents {
        if (account.type == AccountType.MORTGAGE && account.estimatedValueCents != null) {
            return account.estimatedValueCents - abs(balanceCents)
        }
        return balanceCents
    }

    fun computeCurrentSnapshot(accounts: List<Account>): NetWorthSnapshot {
        var cash: Cents = 0L
        var investments: Cents = 0L
        var realEstate: Cents = 0L
        var credit: Cents = 0L
        var loans: Cents = 0L
        var other: Cents = 0L
        var assets: Cents = 0L
        var liabilities: Cents = 0L

        for (acct in accounts) {
            if (acct.isArchived) continue
            val contribution = accountContributionCents(acct, acct.currentBalanceCents)
            when (bucketForAccount(acct)) {
                NetWorthBucket.CASH -> {
                    cash += contribution
                    if (contribution >= 0) assets += contribution else liabilities += abs(contribution)
                }
                NetWorthBucket.INVESTMENTS -> {
                    investments += contribution
                    if (contribution >= 0) assets += contribution else liabilities += abs(contribution)
                }
                NetWorthBucket.REAL_ESTATE -> {
                    realEstate += contribution
                    if (contribution >= 0) assets += contribution else liabilities += abs(contribution)
                }
                NetWorthBucket.CREDIT -> {
                    credit += contribution
                    liabilities += abs(minOf(0L, contribution))
                }
                NetWorthBucket.LOANS -> {
                    loans += contribution
                    liabilities += abs(minOf(0L, contribution))
                }
                NetWorthBucket.OTHER -> {
                    other += contribution
                    if (contribution >= 0) assets += contribution else liabilities += abs(contribution)
                }
            }
        }

        val netWorth = assets - liabilities
        return NetWorthSnapshot(
            cashCents = cash,
            investmentsCents = investments,
            realEstateEquityCents = realEstate,
            creditDebtCents = credit,
            loanDebtCents = loans,
            otherCents = other,
            totalAssetsCents = assets,
            totalLiabilitiesCents = liabilities,
            netWorthCents = netWorth
        )
    }

    /**
     * Reconstructs historical net worth month-by-month rolling backwards from current balances.
     */
    fun reconstructHistory(
        accounts: List<Account>,
        transactions: List<Transaction>,
        balanceRecords: List<BalanceRecord> = emptyList(),
        monthsCount: Int = 12,
        latestMonth: String
    ): List<MonthlyNetWorthPoint> {
        val activeAccounts = accounts.filter { !it.isArchived }
        val currentBalances = activeAccounts.associate { it.id to it.currentBalanceCents }.toMutableMap()

        // Group transactions by month and account
        val deltasByMonth = mutableMapOf<String, MutableMap<Long, Cents>>()
        for (txn in transactions) {
            if (txn.effectiveIsIgnored || txn.effectiveIsTransfer) continue
            val m = txn.effectiveDate.take(7)
            val monthMap = deltasByMonth.getOrPut(m) { mutableMapOf() }
            monthMap[txn.accountId] = (monthMap[txn.accountId] ?: 0L) + txn.effectiveAmountCents
        }

        // Snapshot records by account and month
        val recordsByAcctMonth = balanceRecords.associateBy { "${it.accountId}|${it.recordDate.take(7)}" }

        val monthsList = mutableListOf<String>()
        var cur = latestMonth
        for (i in 0 until monthsCount) {
            monthsList.add(cur)
            cur = addMonths(cur, -1)
        }

        val historyDescending = mutableListOf<MonthlyNetWorthPoint>()

        for (m in monthsList) {
            val acctsWithBalances = activeAccounts.map { acct ->
                val snapshotRec = recordsByAcctMonth["${acct.id}|$m"]
                val bal = snapshotRec?.balanceCents ?: (currentBalances[acct.id] ?: 0L)
                acct.copy(currentBalanceCents = bal)
            }
            val snap = computeCurrentSnapshot(acctsWithBalances)
            historyDescending.add(MonthlyNetWorthPoint(month = m, snapshot = snap))

            // Roll balances backward by subtracting net flow of this month
            val monthDeltas = deltasByMonth[m] ?: emptyMap()
            for ((acctId, netFlow) in monthDeltas) {
                currentBalances[acctId] = (currentBalances[acctId] ?: 0L) - netFlow
            }
        }

        val chronological = historyDescending.reversed()
        return chronological.mapIndexed { idx, point ->
            if (idx == 0) point else {
                val prev = chronological[idx - 1]
                val delta = point.snapshot.netWorthCents - prev.snapshot.netWorthCents
                val pct = if (prev.snapshot.netWorthCents != 0L) {
                    (delta.toDouble() / abs(prev.snapshot.netWorthCents).toDouble()) * 100.0
                } else null
                point.copy(deltaFromPreviousCents = delta, percentChange = pct)
            }
        }
    }

    private fun addMonths(month: String, delta: Int): String {
        val parts = month.split("-")
        val y = parts[0].toIntOrNull() ?: 2026
        val m = parts[1].toIntOrNull() ?: 1
        val total = y * 12 + (m - 1) + delta
        val ny = total / 12
        val nm = (total % 12) + 1
        return "$ny-${nm.toString().padStart(2, '0')}"
    }
}
