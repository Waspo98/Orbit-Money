package app.overbay.orbit.ui.mha

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import kotlin.math.abs

internal data class YearTransactionsClassification(
    val allOutflows: List<MhaTransactionItem>,
    val eligible: List<MhaTransactionItem>,
    val actualSpentCents: Cents
)

internal object MhaTrackerTransform {

    fun classifyYearTransactions(
        year: Int,
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>
    ): YearTransactionsClassification {
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val yearPrefix = "$year-"
        val accountMhaSet = accounts.filter { it.mhaDefaultEligible }.map { it.id }.toSet()
        val catMhaSet = categories.filter { it.mhaDefaultEligible }.map { it.id }.toSet()
        val catIgnoredSet = categories.filter { it.mhaDefaultIgnored }.map { it.id }.toSet()

        val allItems = mutableListOf<MhaTransactionItem>()
        val eligibleItems = mutableListOf<MhaTransactionItem>()
        var actualSpentCents = 0L

        for (txn in transactions) {
            if (!txn.effectiveDate.startsWith(yearPrefix)) continue
            val amount = txn.effectiveAmountCents
            val isOutflow = amount < 0L
            val isTransfer = txn.effectiveIsTransfer || (txn.effectiveCategoryId?.let { categoryMap[it]?.isTransfer } == true)
            if (!isOutflow || isTransfer) continue

            val isIgnored = txn.effectiveIsIgnored
            val catId = txn.effectiveCategoryId
            val accId = txn.accountId
            val cat = catId?.let { categoryMap[it] }
            val acct = accountMap[accId]

            val isEligible = if (isIgnored && txn.editedMhaEligible != true) {
                false
            } else {
                txn.editedMhaEligible ?: run {
                    if (catId != null && catId in catIgnoredSet) false
                    else (accId in accountMhaSet) || (catId != null && catId in catMhaSet)
                }
            }

            val item = MhaTransactionItem(
                transaction = txn,
                category = cat,
                account = acct,
                isEligible = isEligible,
                isExplicitOverride = txn.editedMhaEligible != null,
                isOverriddenByUser = txn.editedMhaEligibleSource == "user",
                eligibilityReason = resolveEligibilityReason(txn, isIgnored, cat, acct)
            )
            allItems.add(item)
            if (isEligible) {
                eligibleItems.add(item)
                actualSpentCents += abs(amount)
            }
        }

        val itemComparator = compareByDescending<MhaTransactionItem> { it.effectiveDate }.thenByDescending { it.id }
        return YearTransactionsClassification(
            allOutflows = allItems.sortedWith(itemComparator),
            eligible = eligibleItems.sortedWith(itemComparator),
            actualSpentCents = actualSpentCents
        )
    }

    fun resolveLimitingFactor(
        actualSpentCents: Cents,
        designatedCap: Cents?,
        fmrv: Cents?
    ): MhaLimitingFactor = when {
        designatedCap == null && fmrv == null -> MhaLimitingFactor.UNCAPPED_ACTUAL
        designatedCap != null && fmrv != null -> {
            val minCap = minOf(designatedCap, fmrv)
            when {
                actualSpentCents <= minCap -> MhaLimitingFactor.ACTUAL_EXPENSES
                designatedCap <= fmrv -> MhaLimitingFactor.DESIGNATION_CAP
                else -> MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE
            }
        }
        designatedCap != null -> {
            if (actualSpentCents <= designatedCap) MhaLimitingFactor.ACTUAL_EXPENSES
            else MhaLimitingFactor.DESIGNATION_CAP
        }
        else -> {
            if (actualSpentCents <= fmrv!!) MhaLimitingFactor.ACTUAL_EXPENSES
            else MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE
        }
    }

    fun resolveEligibilityReason(
        txn: Transaction,
        isIgnored: Boolean,
        cat: Category?,
        acct: Account?
    ): String = when {
        txn.editedMhaEligible == true -> "Manually marked eligible by you"
        txn.editedMhaEligible == false -> "Manually excluded from MHA by you"
        isIgnored -> "Excluded because transaction is marked ignored"
        cat?.mhaDefaultIgnored == true -> "Excluded by category rule (${cat.name})"
        acct?.mhaDefaultEligible == true && cat?.mhaDefaultEligible == true ->
            "Included via account (${acct.name}) and category (${cat.name}) defaults"
        acct?.mhaDefaultEligible == true -> "Included via designated account (${acct.name})"
        cat?.mhaDefaultEligible == true -> "Included via category default (${cat.name})"
        else -> "Not matched by an MHA account or category default"
    }

    fun buildCategoryBreakdown(eligibleItems: List<MhaTransactionItem>): List<MhaCategoryTotal> =
        eligibleItems
            .groupBy { it.category?.id ?: -1L }
            .map { (catId, items) ->
                val cat = items.firstOrNull()?.category
                MhaCategoryTotal(
                    categoryId = catId,
                    name = cat?.name ?: "Uncategorized",
                    emoji = cat?.displayEmoji ?: "❓",
                    colorHex = cat?.color,
                    totalCents = items.sumOf { abs(it.amountCents) },
                    count = items.size
                )
            }
            .sortedByDescending { it.totalCents }

    fun filterVisibleTransactions(
        source: List<MhaTransactionItem>,
        categoryId: Long?,
        searchQuery: String
    ): List<MhaTransactionItem> {
        val catFiltered = if (categoryId == null) {
            source
        } else {
            source.filter { (it.category?.id ?: -1L) == categoryId }
        }
        if (searchQuery.isBlank()) return catFiltered
        val query = searchQuery.trim().lowercase()
        return catFiltered.filter { item ->
            item.merchant.lowercase().contains(query) ||
                (item.category?.name?.lowercase()?.contains(query) == true) ||
                (item.account?.name?.lowercase()?.contains(query) == true) ||
                (item.transaction.notes?.lowercase()?.contains(query) == true)
        }
    }

    fun groupTransactionsByMonth(items: List<MhaTransactionItem>): List<MhaMonthGroup> {
        if (items.isEmpty()) return emptyList()
        return items
            .groupBy { it.effectiveDate.take(7) }
            .entries
            .sortedByDescending { it.key }
            .map { (ym, groupItems) ->
                val counted = groupItems.filter { it.isEligible }
                MhaMonthGroup(
                    yearMonth = ym,
                    monthLabel = LocalDateUtils.formatMonthLabel(ym),
                    countedTotalCents = counted.sumOf { abs(it.amountCents) },
                    countedCount = counted.size,
                    items = groupItems
                )
            }
    }
}
