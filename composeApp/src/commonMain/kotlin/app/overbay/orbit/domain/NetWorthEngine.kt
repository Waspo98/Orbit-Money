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
        if (account.isProperty || account.linkedPropertyAccountId != null) {
            return NetWorthBucket.REAL_ESTATE
        }
        return when (account.type) {
            AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH -> NetWorthBucket.CASH
            AccountType.INVESTMENT -> NetWorthBucket.INVESTMENTS
            AccountType.MORTGAGE -> NetWorthBucket.REAL_ESTATE
            AccountType.CREDIT -> NetWorthBucket.CREDIT
            AccountType.LOAN -> NetWorthBucket.LOANS
            AccountType.OTHER -> NetWorthBucket.OTHER
        }
    }

    fun accountContributionCents(account: Account, balanceCents: Cents): Cents {
        if (account.isProperty) {
            return account.estimatedValueCents ?: balanceCents.coerceAtLeast(0L)
        }
        if (account.linkedPropertyAccountId != null) {
            return -abs(balanceCents)
        }
        if (account.type == AccountType.MORTGAGE) {
            val estimatedValue = account.estimatedValueCents ?: 0L
            return estimatedValue - abs(balanceCents)
        }
        return balanceCents
    }

    fun computeCurrentSnapshot(accounts: List<Account>): NetWorthSnapshot {
        val activeAccounts = accounts.filter { !it.isArchived }
        val propertyUnits = RealEstatePropertyEngine.buildPropertyUnits(activeAccounts)
        val realEstateMemberIds = RealEstatePropertyEngine.realEstateMemberAccountIds(activeAccounts)

        var cash: Cents = 0L
        var investments: Cents = 0L
        var realEstate: Cents = 0L
        var credit: Cents = 0L
        var loans: Cents = 0L
        var other: Cents = 0L
        var assets: Cents = 0L
        var liabilities: Cents = 0L

        for (unit in propertyUnits) {
            val eq = unit.netEquityCents
            realEstate += eq
            if (eq > 0L) {
                assets += eq
            } else if (eq < 0L) {
                liabilities += abs(eq)
            }
        }

        for (acct in activeAccounts) {
            if (acct.id in realEstateMemberIds) continue
            val contribution = accountContributionCents(acct, acct.currentBalanceCents)
            when (bucketForAccount(acct)) {
                NetWorthBucket.CASH -> cash += contribution
                NetWorthBucket.INVESTMENTS -> investments += contribution
                NetWorthBucket.REAL_ESTATE -> realEstate += contribution
                NetWorthBucket.CREDIT -> credit += contribution
                NetWorthBucket.LOANS -> loans += contribution
                NetWorthBucket.OTHER -> other += contribution
            }

            if (contribution > 0L) {
                assets += contribution
            } else if (contribution < 0L) {
                liabilities += abs(contribution)
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

        // Group transactions by month and account (without skipping transfers or ignored,
        // because all account movements change historical balance)
        val deltasByMonth = mutableMapOf<String, MutableMap<Long, Cents>>()
        for (txn in transactions) {
            val m = txn.effectiveDate.take(7)
            val monthMap = deltasByMonth.getOrPut(m) { mutableMapOf() }
            monthMap[txn.accountId] = (monthMap[txn.accountId] ?: 0L) + txn.effectiveAmountCents
        }

        // Snapshot records by account sorted descending by recordDate
        val recordsByAccount = balanceRecords
            .groupBy { it.accountId }
            .mapValues { (_, recs) -> recs.sortedByDescending { it.recordDate } }

        fun snapshotBalanceForMonth(accountId: Long, month: String): Cents? {
            val recs = recordsByAccount[accountId] ?: return null
            val record = recs.firstOrNull { it.recordDate.take(7) == month }
            return record?.balanceCents
        }

        // For ALL_TIME (Int.MAX_VALUE), span back to the earliest transaction/record month (at least 12 months)
        val firstMonth = if (monthsCount == Int.MAX_VALUE) {
            val earliestTxMonth = transactions
                .mapNotNull { it.effectiveDate.take(7).takeIf { d -> d.length == 7 && d.contains("-") } }
                .minOrNull()
            val earliestRecMonth = balanceRecords
                .mapNotNull { it.recordDate.take(7).takeIf { d -> d.length == 7 && d.contains("-") } }
                .minOrNull()
            val min12Month = addMonths(latestMonth, -11)
            listOfNotNull(earliestTxMonth, earliestRecMonth, min12Month).minOrNull() ?: min12Month
        } else {
            addMonths(latestMonth, -(monthsCount - 1))
        }

        val monthsList = mutableListOf<String>()
        var cursor = latestMonth
        while (cursor >= firstMonth) {
            monthsList.add(cursor)
            cursor = addMonths(cursor, -1)
        }

        val historyDescending = mutableListOf<MonthlyNetWorthPoint>()

        for (m in monthsList) {
            val acctsWithBalances = activeAccounts.map { acct ->
                val snapshotBalance = snapshotBalanceForMonth(acct.id, m)
                if (snapshotBalance != null) {
                    currentBalances[acct.id] = snapshotBalance
                }
                val bal = currentBalances[acct.id] ?: 0L
                acct.copy(currentBalanceCents = bal)
            }
            val snap = computeCurrentSnapshot(acctsWithBalances)
            historyDescending.add(MonthlyNetWorthPoint(month = m, snapshot = snap))

            // Roll balances backward by subtracting net flow of this month
            val monthDeltas = deltasByMonth[m] ?: emptyMap()
            for (acct in activeAccounts) {
                val delta = monthDeltas[acct.id] ?: 0L
                currentBalances[acct.id] = (currentBalances[acct.id] ?: 0L) - delta
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

    private fun parseYearMonthToTotalMonths(yearMonth: String): Int {
        val parts = yearMonth.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
        return y * 12 + (m - 1)
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
