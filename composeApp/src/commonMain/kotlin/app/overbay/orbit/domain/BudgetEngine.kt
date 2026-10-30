package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.data.model.DailySafeSpend
import app.overbay.orbit.data.model.MonthlyBudgetOverview
import app.overbay.orbit.data.model.Transaction
import kotlin.math.max

object BudgetEngine {

    val UNCATEGORIZED_CATEGORY = Category(
        id = -1L,
        name = "Uncategorized",
        color = "#9E9E9E",
        icon = "❓",
        isIncome = false,
        isTransfer = false
    )

    fun calculateMonthlyOverview(
        month: String, // YYYY-MM
        categories: List<Category>,
        budgets: List<CategoryBudget>,
        transactions: List<Transaction>,
        globalBudgetAmountCents: Cents? = null,
        todayDate: String = "",
        sortOption: CategorySortOption = CategorySortOption.OVER_BUDGET_FIRST
    ): MonthlyBudgetOverview {
        val monthStart = "$month-01"
        val daysInMonth = LocalDateUtils.daysInMonth(
            year = month.take(4).toIntOrNull() ?: 2026,
            month = month.substringAfter("-").take(2).toIntOrNull() ?: 1
        )
        val monthEnd = "$month-${daysInMonth.toString().padStart(2, '0')}"

        val categoryById = categories.associateBy { it.id }
        val budgetByCat = budgets.associateBy { it.categoryId }

        // Filter transactions for this month, excluding transfers and ignored
        val monthTxns = transactions.filter { txn ->
            val date = txn.effectiveDate
            date >= monthStart && date <= monthEnd &&
                !txn.effectiveIsIgnored &&
                !txn.effectiveIsTransfer
        }

        var totalIncomeCents: Cents = 0L
        var totalExpensesCents: Cents = 0L

        val spendByCat = mutableMapOf<Long?, Cents>()
        val countByCat = mutableMapOf<Long?, Int>()

        for (txn in monthTxns) {
            val amount = txn.effectiveAmountCents
            val catId = txn.effectiveCategoryId
            val cat = catId?.let { categoryById[it] }

            if (amount > 0) {
                totalIncomeCents += amount
            } else if (amount < 0) {
                totalExpensesCents += -amount
            }

            val isIncome = cat?.isIncome == true
            val isTransfer = cat?.isTransfer == true

            if (!isIncome && !isTransfer) {
                if (cat != null || amount < 0) {
                    val spent = -amount
                    spendByCat[catId] = (spendByCat[catId] ?: 0L) + spent
                    countByCat[catId] = (countByCat[catId] ?: 0) + 1
                }
            }
        }

        val budgetedItems = mutableListOf<CategoryBudgetOverviewItem>()
        val unbudgetedItems = mutableListOf<CategoryBudgetOverviewItem>()
        val inactiveItems = mutableListOf<CategoryBudgetOverviewItem>()

        val spendingCategories = categories.filter { !it.isIncome && !it.isTransfer }

        for (cat in spendingCategories) {
            val budget = budgetByCat[cat.id]
            val spent = spendByCat[cat.id] ?: 0L
            val count = countByCat[cat.id] ?: 0
            val budgetAmount = budget?.amountCents ?: 0L

            val item = buildItem(cat, budget?.id, budgetAmount, spent, count, budget?.rollover == true)

            if (budget != null) {
                budgetedItems.add(item)
            } else if (spent > 0L || count > 0) {
                unbudgetedItems.add(item)
            } else {
                inactiveItems.add(item)
            }
        }

        // Handle Uncategorized expenses
        val uncategorizedSpent = spendByCat[null] ?: 0L
        val uncategorizedCount = countByCat[null] ?: 0
        if (uncategorizedSpent > 0L || uncategorizedCount > 0) {
            val uncatItem = buildItem(
                category = UNCATEGORIZED_CATEGORY,
                budgetId = null,
                budgetAmount = 0L,
                spent = uncategorizedSpent,
                count = uncategorizedCount,
                rollover = false
            )
            unbudgetedItems.add(uncatItem)
        }

        // Sort sections
        val sortedBudgeted = sortBudgeted(budgetedItems, sortOption)
        val sortedUnbudgeted = unbudgetedItems.sortedByDescending { it.spentCents }
        val sortedInactive = inactiveItems.sortedBy { it.category.name.lowercase() }

        val totalBudgetedCents = globalBudgetAmountCents ?: budgetedItems.sumOf { it.amountCents }
        val totalSpentInBudgetCents = budgetedItems.sumOf { it.spentCents }
        val totalSpentUnbudgetedCents = unbudgetedItems.sumOf { it.spentCents }
        val totalSpentCents = totalSpentInBudgetCents + totalSpentUnbudgetedCents
        val netSavingsCents = totalIncomeCents - totalExpensesCents
        val savingsRatePercent = if (totalIncomeCents > 0L) {
            (netSavingsCents.toDouble() / totalIncomeCents.toDouble()) * 100.0
        } else 0.0

        val dailySafeSpend = calculateDailySafeSpend(
            totalBudgetedCents = totalBudgetedCents,
            totalSpentCents = totalSpentCents,
            selectedMonth = month,
            todayDate = todayDate,
            daysInMonth = daysInMonth
        )

        return MonthlyBudgetOverview(
            month = month,
            totalBudgetedCents = totalBudgetedCents,
            totalSpentInBudgetCents = totalSpentInBudgetCents,
            totalSpentUnbudgetedCents = totalSpentUnbudgetedCents,
            totalSpentCents = totalSpentCents,
            totalIncomeCents = totalIncomeCents,
            totalExpensesCents = totalExpensesCents,
            netSavingsCents = netSavingsCents,
            savingsRatePercent = savingsRatePercent,
            dailySafeSpend = dailySafeSpend,
            budgeted = sortedBudgeted,
            unbudgeted = sortedUnbudgeted,
            inactive = sortedInactive
        )
    }

    private fun buildItem(
        category: Category,
        budgetId: Long?,
        budgetAmount: Cents,
        spent: Cents,
        count: Int,
        rollover: Boolean
    ): CategoryBudgetOverviewItem {
        val remaining = budgetAmount - spent
        val ratio = if (budgetAmount > 0L) spent.toDouble() / budgetAmount.toDouble() else 0.0
        val paceStatus = when {
            budgetAmount <= 0L -> BudgetPaceStatus.NO_BUDGET
            spent > budgetAmount -> BudgetPaceStatus.OVER_BUDGET
            ratio >= 0.85 -> BudgetPaceStatus.WATCH
            else -> BudgetPaceStatus.ON_TRACK
        }
        return CategoryBudgetOverviewItem(
            category = category,
            budgetId = budgetId,
            amountCents = budgetAmount,
            spentCents = spent,
            remainingCents = remaining,
            transactionCount = count,
            utilizationRatio = ratio,
            paceStatus = paceStatus,
            rollover = rollover
        )
    }

    private fun calculateDailySafeSpend(
        totalBudgetedCents: Cents,
        totalSpentCents: Cents,
        selectedMonth: String,
        todayDate: String,
        daysInMonth: Int
    ): DailySafeSpend {
        if (totalBudgetedCents <= 0L) {
            return DailySafeSpend(0L, 0, "Add category budgets", "neutral")
        }

        val currentMonth = todayDate.take(7)
        val remainingCents = totalBudgetedCents - totalSpentCents

        if (todayDate.isNotBlank() && selectedMonth < currentMonth) {
            return DailySafeSpend(
                dailyCents = 0L,
                daysRemaining = 0,
                detail = "Month complete",
                tone = if (remainingCents < 0L) "over" else "neutral"
            )
        }

        val daysRemaining = if (todayDate.isNotBlank() && selectedMonth == currentMonth && todayDate.length >= 10) {
            val todayDay = todayDate.substring(8, 10).toIntOrNull() ?: 1
            max(1, daysInMonth - todayDay + 1)
        } else {
            daysInMonth
        }

        if (remainingCents <= 0L) {
            val overCents = -remainingCents
            return DailySafeSpend(
                dailyCents = 0L,
                daysRemaining = daysRemaining,
                detail = "${MoneyUtils.formatCents(overCents)} over budget",
                tone = "over"
            )
        }

        val dailyCents = remainingCents / daysRemaining
        return DailySafeSpend(
            dailyCents = dailyCents,
            daysRemaining = daysRemaining,
            detail = "$daysRemaining ${if (daysRemaining == 1) "day" else "days"} left",
            tone = "good"
        )
    }

    private fun sortBudgeted(
        items: List<CategoryBudgetOverviewItem>,
        option: CategorySortOption
    ): List<CategoryBudgetOverviewItem> {
        return when (option) {
            CategorySortOption.OVER_BUDGET_FIRST -> items.sortedWith(
                compareByDescending<CategoryBudgetOverviewItem> { it.spentCents > it.amountCents }
                    .thenByDescending { it.utilizationRatio }
            )
            CategorySortOption.LEAST_ROOM_LEFT -> items.sortedBy { it.remainingCents }
            CategorySortOption.MOST_SPENT -> items.sortedByDescending { it.spentCents }
            CategorySortOption.NAME_A_TO_Z -> items.sortedBy { it.category.name.lowercase() }
        }
    }

    /**
     * Extracts actionable notable budget items for the month:
     * 1. Over budget categories (spent > amount)
     * 2. Near limit categories (>= 80% utilization)
     * 3. Variances: unbudgeted with spend or net refunds (spent < 0)
     */
    fun extractNotables(overview: MonthlyBudgetOverview): BudgetNotables {
        val allItems = overview.budgeted + overview.unbudgeted
        val overBudget = allItems.filter { it.amountCents > 0L && it.spentCents > it.amountCents }
        val nearLimit = allItems.filter {
            it.amountCents > 0L && it.spentCents <= it.amountCents && it.utilizationRatio >= 0.80
        }
        val variances = allItems.filter {
            (it.amountCents <= 0L && it.spentCents > 0L) || (it.spentCents < 0L)
        }
        return BudgetNotables(
            overBudget = overBudget,
            nearLimit = nearLimit,
            variances = variances
        )
    }
}

data class BudgetNotables(
    val overBudget: List<CategoryBudgetOverviewItem> = emptyList(),
    val nearLimit: List<CategoryBudgetOverviewItem> = emptyList(),
    val variances: List<CategoryBudgetOverviewItem> = emptyList()
) {
    val totalCount: Int get() = overBudget.size + nearLimit.size + variances.size
    val hasNotables: Boolean get() = totalCount > 0
}

