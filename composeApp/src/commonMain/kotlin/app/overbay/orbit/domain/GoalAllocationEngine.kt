package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountAllocationSummary
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.GoalEtaInfo
import app.overbay.orbit.data.model.GoalMonthlyPoint
import app.overbay.orbit.data.model.Transaction
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin domain calculation engine for savings goal allocations,
 * multi-account asset attribution, 6-24 month historical reconstructions,
 * over-allocation conflict detection, and trailing pace & ETA projections.
 * Strictly <= 380 lines, zero java.* dependencies.
 */
object GoalAllocationEngine {

    /**
     * Eligible accounts are unarchived asset accounts (checking, savings, investment, cash, other).
     */
    fun isAccountEligible(account: Account): Boolean {
        return !account.isArchived && account.type.isAsset
    }

    /**
     * Computes per-account allocation summaries across all active goals.
     */
    fun computeAccountSummaries(
        accounts: List<Account>,
        goals: List<Goal>
    ): List<AccountAllocationSummary> {
        val eligible = accounts.filter { isAccountEligible(it) }
        val allAllocations = goals.flatMap { it.allocations }
        val allocationsByAccount = allAllocations.groupBy { it.accountId }

        return eligible.map { acct ->
            val allocs = allocationsByAccount[acct.id] ?: emptyList()
            val maxReserve = allocs.maxOfOrNull { it.reserveAmountCents } ?: 0L
            val allocatable = max(0L, acct.currentBalanceCents - maxReserve)

            var totalAllocated = 0L
            for (alloc in allocs) {
                val amt = if (alloc.allocationType == GoalAllocationType.PERCENT) {
                    ((allocatable * alloc.allocationPercent) / 100.0).toLong()
                } else {
                    alloc.allocationAmountCents
                }
                totalAllocated += amt
            }

            val remaining = max(0L, allocatable - totalAllocated)
            val allocatedPct = if (allocatable > 0L) {
                ((totalAllocated.toDouble() / allocatable.toDouble()) * 100.0).coerceIn(0.0, 100.0)
            } else {
                0.0
            }

            AccountAllocationSummary(
                accountId = acct.id,
                accountName = acct.name,
                accountType = acct.type,
                currentBalanceCents = acct.currentBalanceCents,
                reserveAmountCents = maxReserve,
                allocatableAmountCents = allocatable,
                allocatedAmountCents = totalAllocated,
                remainingAmountCents = remaining,
                allocatedPercent = allocatedPct
            )
        }
    }

    data class AllocationConflict(
        val accountId: Long,
        val accountName: String,
        val totalPercent: Double,
        val totalAllocatedCents: Cents,
        val allocatableCents: Cents,
        val conflictingGoals: List<Goal>
    )

    /**
     * Detects if saving [draftGoal] would cause any account to exceed 100% or exceed allocatable funds.
     */
    fun detectConflicts(
        draftGoal: Goal,
        existingGoals: List<Goal>,
        accounts: List<Account>
    ): List<AllocationConflict> {
        if (draftGoal.allocations.isEmpty()) return emptyList()

        val accountMap = accounts.associateBy { it.id }
        val otherGoals = existingGoals.filter { it.id != draftGoal.id && it.id != 0L }
        val combinedGoals = otherGoals + draftGoal

        val draftAccountIds = draftGoal.allocations.map { it.accountId }.toSet()
        val conflicts = mutableListOf<AllocationConflict>()

        for (acctId in draftAccountIds) {
            val acct = accountMap[acctId] ?: continue
            val relevantGoals = combinedGoals.filter { g -> g.allocations.any { it.accountId == acctId } }
            val allocs = relevantGoals.flatMap { it.allocations.filter { a -> a.accountId == acctId } }

            val maxReserve = allocs.maxOfOrNull { it.reserveAmountCents } ?: 0L
            val allocatable = max(0L, acct.currentBalanceCents - maxReserve)

            var totalPercent = 0.0
            var totalAllocated = 0L

            for (alloc in allocs) {
                if (alloc.allocationType == GoalAllocationType.PERCENT) {
                    totalPercent += alloc.allocationPercent
                    totalAllocated += ((allocatable * alloc.allocationPercent) / 100.0).toLong()
                } else {
                    totalAllocated += alloc.allocationAmountCents
                }
            }

            val hasPercentOver = totalPercent > 100.001
            val hasBalanceOver = totalAllocated > allocatable && allocatable > 0L

            if ((hasPercentOver || hasBalanceOver) && relevantGoals.size > 1) {
                val conflictingOthers = relevantGoals.filter { it.id != draftGoal.id }
                conflicts.add(
                    AllocationConflict(
                        accountId = acctId,
                        accountName = acct.name,
                        totalPercent = totalPercent,
                        totalAllocatedCents = totalAllocated,
                        allocatableCents = allocatable,
                        conflictingGoals = conflictingOthers
                    )
                )
            }
        }

        return conflicts
    }

    /**
     * Reconstructs 6-24 month historical monthly balances for a goal from account balance records
     * and monthly net transaction deltas.
     */
    fun reconstructGoalHistory(
        goal: Goal,
        accounts: List<Account>,
        balanceRecords: List<BalanceRecord>,
        transactions: List<Transaction>,
        monthsCount: Int = 12
    ): List<GoalMonthlyPoint> {
        val currentYm = LocalDateUtils.currentYearMonth()
        val (currentYear, currentMonthNum) = LocalDateUtils.parseYearMonth(currentYm) ?: Pair(2026, 1)

        val months = (monthsCount - 1 downTo 0).map { offset ->
            LocalDateUtils.addMonthsToYearMonth(currentYm, -offset)
        }

        val accountMap = accounts.associateBy { it.id }
        val points = mutableListOf<GoalMonthlyPoint>()

        for (m in months) {
            val (y, mNum) = LocalDateUtils.parseYearMonth(m) ?: Pair(currentYear, currentMonthNum)
            val daysInM = LocalDateUtils.daysInMonth(y, mNum)
            val monthEnd = "$m-${daysInM.toString().padStart(2, '0')}"

            val monthBalance = if (goal.allocations.isNotEmpty()) {
                goal.allocations.sumOf { alloc ->
                    val acct = accountMap[alloc.accountId]
                    if (acct != null) {
                        val histAcctBal = estimateHistoricalAccountBalance(
                            account = acct,
                            monthEnd = monthEnd,
                            balanceRecords = balanceRecords,
                            transactions = transactions
                        )
                        val allocatable = max(0L, histAcctBal - alloc.reserveAmountCents)
                        if (alloc.allocationType == GoalAllocationType.PERCENT) {
                            ((allocatable * alloc.allocationPercent) / 100.0).toLong()
                        } else {
                            min(allocatable, alloc.allocationAmountCents)
                        }
                    } else 0L
                }
            } else if (goal.linkedAccountId != null) {
                val acct = accountMap[goal.linkedAccountId]
                if (acct != null) {
                    val histAcctBal = estimateHistoricalAccountBalance(
                        account = acct,
                        monthEnd = monthEnd,
                        balanceRecords = balanceRecords,
                        transactions = transactions
                    )
                    min(histAcctBal, goal.targetAmountCents)
                } else {
                    goal.currentAmountCents
                }
            } else {
                goal.currentAmountCents
            }

            val finalCents = if (m == currentYm && goal.currentAmountCents > 0L) {
                goal.currentAmountCents
            } else {
                monthBalance
            }

            points.add(GoalMonthlyPoint(month = m, amountCents = max(0L, finalCents)))
        }

        return points
    }

    /**
     * Computes trailing 7-month average monthly net pace and projected ETA.
     */
    fun computeMonthlyPaceAndEta(
        goal: Goal,
        history: List<GoalMonthlyPoint>
    ): Pair<Cents, GoalEtaInfo> {
        if (goal.targetAmountCents > 0L && goal.currentAmountCents >= goal.targetAmountCents) {
            return Pair(0L, GoalEtaInfo(date = null, months = 0, status = "complete"))
        }

        val recentPoints = history.takeLast(8)
        val paceCents: Cents = if (recentPoints.size >= 2) {
            val deltas = recentPoints.zipWithNext { a, b -> b.amountCents - a.amountCents }
            val avg = deltas.sum() / deltas.size
            avg
        } else {
            0L
        }

        if (paceCents <= 0L) {
            return Pair(paceCents, GoalEtaInfo(date = null, months = null, status = "stalled"))
        }

        val remaining = goal.remainingAmountCents
        val monthsNeeded = ceil(remaining.toDouble() / paceCents.toDouble()).toInt()
        val currentYm = LocalDateUtils.currentYearMonth()
        val projectedYm = LocalDateUtils.addMonthsToYearMonth(currentYm, monthsNeeded)

        val eta = GoalEtaInfo(
            date = projectedYm,
            months = monthsNeeded,
            status = "projected"
        )

        return Pair(paceCents, eta)
    }

    /**
     * What-If Monthly Contribution Simulator: calculates projected completion date and months saved
     * given an additional monthly boost.
     */
    fun simulateWhatIf(
        currentAmountCents: Cents,
        targetAmountCents: Cents,
        baselinePaceCents: Cents,
        additionalMonthlyCents: Cents
    ): GoalEtaInfo {
        if (targetAmountCents <= 0L || currentAmountCents >= targetAmountCents) {
            return GoalEtaInfo(date = null, months = 0, status = "complete")
        }

        val effectivePace = max(0L, baselinePaceCents) + additionalMonthlyCents
        if (effectivePace <= 0L) {
            return GoalEtaInfo(date = null, months = null, status = "stalled")
        }

        val remaining = max(0L, targetAmountCents - currentAmountCents)
        val months = ceil(remaining.toDouble() / effectivePace.toDouble()).toInt()
        val currentYm = LocalDateUtils.currentYearMonth()
        val projectedYm = LocalDateUtils.addMonthsToYearMonth(currentYm, months)

        return GoalEtaInfo(date = projectedYm, months = months, status = "projected")
    }

    private fun estimateHistoricalAccountBalance(
        account: Account,
        monthEnd: String,
        balanceRecords: List<BalanceRecord>,
        transactions: List<Transaction>
    ): Cents {
        val records = balanceRecords
            .filter { it.accountId == account.id && it.recordDate <= monthEnd }
            .sortedByDescending { it.recordDate }

        if (records.isNotEmpty()) {
            return records.first().balanceCents
        }

        val netAfterMonthEnd = transactions
            .filter { it.accountId == account.id && it.effectiveDate > monthEnd && !it.effectiveIsIgnored }
            .sumOf { it.effectiveAmountCents }

        return max(0L, account.currentBalanceCents - netAfterMonthEnd)
    }
}
