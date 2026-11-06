package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.Transaction

data class MonthlyFlow(
    val month: String,
    val incomeCents: Cents,
    val expensesCents: Cents,
    val netCents: Cents,
    val savingsRatePercent: Double
)

data class CategoryTrendPoint(
    val month: String,
    val spentCents: Cents,
    val transactionCount: Int,
    val isOverBudget: Boolean
)

data class CategorySpendingTrend(
    val category: Category,
    val budgetAmountCents: Cents?,
    val monthlySeries: List<CategoryTrendPoint>,
    val totalSpentCents: Cents,
    val averageSpentCents: Cents,
    val medianSpentCents: Cents,
    val overBudgetMonthCount: Int,
    val percentOfTotalSpending: Double
)

data class MerchantSpendingSummary(
    val merchant: String,
    val totalSpentCents: Cents,
    val transactionCount: Int,
    val averageTransactionCents: Cents
)

data class SpendingTrendsOverview(
    val months: List<String>,
    val flowSeries: List<MonthlyFlow>,
    val categoryTrends: List<CategorySpendingTrend>,
    val topMerchants: List<MerchantSpendingSummary>,
    val totalPeriodSpendingCents: Cents,
    val totalPeriodIncomeCents: Cents
)

object SpendingTrendsEngine {

    private class MonthFlowAccumulator(var incomeCents: Long = 0L, var expensesCents: Long = 0L)
    private class CatMonthAccumulator(var spentCents: Long = 0L, var count: Int = 0)
    private class MerchantAccumulator(var spentCents: Long = 0L, var count: Int = 0)

    fun computeTrends(
        transactions: List<Transaction>,
        categories: List<Category>,
        budgets: List<CategoryBudget> = emptyList(),
        monthsCount: Int = 6,
        endMonth: String
    ): SpendingTrendsOverview {
        val months = (0 until monthsCount).map { i ->
            addMonths(endMonth, -(monthsCount - 1 - i))
        }
        val monthSet = months.toHashSet()
        val budgetMap = budgets.associateBy { it.categoryId }

        val flowByMonth = HashMap<String, MonthFlowAccumulator>(monthsCount * 2)
        val catMonthMap = HashMap<Long, HashMap<String, CatMonthAccumulator>>(categories.size * 2)
        val merchantMap = HashMap<String, MerchantAccumulator>()

        // Single O(N) pass over transactions
        for (txn in transactions) {
            if (txn.effectiveIsIgnored || txn.effectiveIsTransfer) continue
            val m = txn.effectiveDate.take(7)
            if (m !in monthSet) continue

            val amt = txn.effectiveAmountCents
            val flowAcc = flowByMonth.getOrPut(m) { MonthFlowAccumulator() }
            if (amt > 0L) {
                flowAcc.incomeCents += amt
            } else if (amt < 0L) {
                val expense = -amt
                flowAcc.expensesCents += expense

                val catId = txn.effectiveCategoryId
                if (catId != null) {
                    val byMonth = catMonthMap.getOrPut(catId) { HashMap(monthsCount * 2) }
                    val catAcc = byMonth.getOrPut(m) { CatMonthAccumulator() }
                    catAcc.spentCents += expense
                    catAcc.count += 1
                }

                val merchAcc = merchantMap.getOrPut(txn.effectiveMerchant) { MerchantAccumulator() }
                merchAcc.spentCents += expense
                merchAcc.count += 1
            }
        }

        // Flow per month
        val flowSeries = months.map { m ->
            val acc = flowByMonth[m]
            val income = acc?.incomeCents ?: 0L
            val expenses = acc?.expensesCents ?: 0L
            val net = income - expenses
            val savingsRate = if (income > 0L) (net.toDouble() / income.toDouble()) * 100.0 else 0.0
            MonthlyFlow(
                month = m,
                incomeCents = income,
                expensesCents = expenses,
                netCents = net,
                savingsRatePercent = savingsRate
            )
        }

        val totalPeriodSpending = flowSeries.sumOf { it.expensesCents }
        val totalPeriodIncome = flowSeries.sumOf { it.incomeCents }

        // Category trends
        val spendingCategories = categories.filter { !it.isIncome && !it.isTransfer }
        val categoryTrends = spendingCategories.mapNotNull { cat ->
            val byMonth = catMonthMap[cat.id] ?: return@mapNotNull null
            val budget = budgetMap[cat.id]
            val budgetAmt = budget?.amountCents

            val series = months.map { m ->
                val catAcc = byMonth[m]
                val spent = catAcc?.spentCents ?: 0L
                val count = catAcc?.count ?: 0
                val isOver = budgetAmt != null && budgetAmt > 0L && spent > budgetAmt
                CategoryTrendPoint(
                    month = m,
                    spentCents = spent,
                    transactionCount = count,
                    isOverBudget = isOver
                )
            }

            val totalSpent = series.sumOf { it.spentCents }
            if (totalSpent <= 0L) return@mapNotNull null

            val avgSpent = totalSpent / maxOf(1, months.size)
            val sortedSpents = series.map { it.spentCents }.sorted()
            val medianSpent = if (sortedSpents.isNotEmpty()) {
                val mid = sortedSpents.size / 2
                if (sortedSpents.size % 2 == 1) sortedSpents[mid] else (sortedSpents[mid - 1] + sortedSpents[mid]) / 2
            } else 0L

            val overCount = series.count { it.isOverBudget }
            val pctOfTotal = if (totalPeriodSpending > 0L) {
                (totalSpent.toDouble() / totalPeriodSpending.toDouble()) * 100.0
            } else 0.0

            CategorySpendingTrend(
                category = cat,
                budgetAmountCents = budgetAmt,
                monthlySeries = series,
                totalSpentCents = totalSpent,
                averageSpentCents = avgSpent,
                medianSpentCents = medianSpent,
                overBudgetMonthCount = overCount,
                percentOfTotalSpending = pctOfTotal
            )
        }.sortedByDescending { it.totalSpentCents }

        // Top merchants across period
        val topMerchants = merchantMap.entries
            .map { (merchant, acc) ->
                MerchantSpendingSummary(
                    merchant = merchant,
                    totalSpentCents = acc.spentCents,
                    transactionCount = acc.count,
                    averageTransactionCents = if (acc.count > 0) acc.spentCents / acc.count else 0L
                )
            }
            .sortedByDescending { it.totalSpentCents }
            .take(10)

        return SpendingTrendsOverview(
            months = months,
            flowSeries = flowSeries,
            categoryTrends = categoryTrends,
            topMerchants = topMerchants,
            totalPeriodSpendingCents = totalPeriodSpending,
            totalPeriodIncomeCents = totalPeriodIncome
        )
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
