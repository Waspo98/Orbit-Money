package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.Transaction
import kotlin.math.abs

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

        val budgetMap = budgets.associateBy { it.categoryId }

        val activeTxns = transactions.filter { txn ->
            !txn.effectiveIsIgnored && !txn.effectiveIsTransfer
        }

        // Flow per month
        val flowSeries = months.map { m ->
            val monthTxns = activeTxns.filter { it.effectiveDate.take(7) == m }
            var income = 0L
            var expenses = 0L
            for (t in monthTxns) {
                if (t.effectiveAmountCents > 0) income += t.effectiveAmountCents
                else if (t.effectiveAmountCents < 0) expenses += -t.effectiveAmountCents
            }
            val net = income - expenses
            val savingsRate = if (income > 0) (net.toDouble() / income.toDouble()) * 100.0 else 0.0
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
        val categoryTrends = spendingCategories.map { cat ->
            val budget = budgetMap[cat.id]
            val budgetAmt = budget?.amountCents

            val series = months.map { m ->
                val catMonthTxns = activeTxns.filter {
                    it.effectiveDate.take(7) == m &&
                        it.effectiveCategoryId == cat.id &&
                        it.effectiveAmountCents < 0
                }
                val spent = catMonthTxns.sumOf { -it.effectiveAmountCents }
                val isOver = budgetAmt != null && budgetAmt > 0 && spent > budgetAmt
                CategoryTrendPoint(
                    month = m,
                    spentCents = spent,
                    transactionCount = catMonthTxns.size,
                    isOverBudget = isOver
                )
            }

            val totalSpent = series.sumOf { it.spentCents }
            val avgSpent = totalSpent / maxOf(1, months.size)
            val sortedSpents = series.map { it.spentCents }.sorted()
            val medianSpent = if (sortedSpents.isNotEmpty()) {
                val mid = sortedSpents.size / 2
                if (sortedSpents.size % 2 == 1) sortedSpents[mid] else (sortedSpents[mid - 1] + sortedSpents[mid]) / 2
            } else 0L

            val overCount = series.count { it.isOverBudget }
            val pctOfTotal = if (totalPeriodSpending > 0) {
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
        }.filter { it.totalSpentCents > 0L }.sortedByDescending { it.totalSpentCents }

        // Top merchants across period
        val periodTxns = activeTxns.filter {
            val m = it.effectiveDate.take(7)
            m in months && it.effectiveAmountCents < 0
        }
        val topMerchants = periodTxns.groupBy { it.effectiveMerchant }
            .map { (merchant, txns) ->
                val spent = txns.sumOf { -it.effectiveAmountCents }
                MerchantSpendingSummary(
                    merchant = merchant,
                    totalSpentCents = spent,
                    transactionCount = txns.size,
                    averageTransactionCents = if (txns.isNotEmpty()) spent / txns.size else 0L
                )
            }.sortedByDescending { it.totalSpentCents }.take(10)

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
