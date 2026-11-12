package app.overbay.orbit.ui.transactions

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.TransactionFilterDraft
import kotlinx.datetime.LocalDate
import kotlin.math.abs

object TransactionsFilterHelper {

    fun parseSortOption(sortOption: String?): TransactionSortOption {
        if (sortOption == null) return TransactionSortOption.DATE_DESC
        val normalized = sortOption.trim().lowercase()
        return when (normalized) {
            "abs_amount_desc", "absamountdesc", "biggest_first", "biggest" -> TransactionSortOption.ABS_AMOUNT_DESC
            "abs_amount_asc", "absamountasc", "smallest_first", "smallest" -> TransactionSortOption.ABS_AMOUNT_ASC
            "date_asc", "dateasc", "oldest" -> TransactionSortOption.DATE_ASC
            "amount_desc", "amountdesc", "high_to_low" -> TransactionSortOption.AMOUNT_DESC
            "amount_asc", "amountasc", "low_to_high" -> TransactionSortOption.AMOUNT_ASC
            "merchant_asc", "merchantasc", "merchant" -> TransactionSortOption.MERCHANT_ASC
            else -> TransactionSortOption.entries.find { it.name.equals(sortOption.trim(), ignoreCase = true) }
                ?: TransactionSortOption.DATE_DESC
        }
    }

    fun filterTransactions(
        transactions: List<Transaction>,
        query: String,
        quick: QuickFilter,
        draft: TransactionFilterDraft,
        categoryMap: Map<Long, Category>
    ): List<Transaction> {
        val today = LocalDateUtils.todayLocalDate()
        val currentYearMonth = LocalDateUtils.currentYearMonth()
        val currentYear = currentYearMonth.take(4)

        val targetAccountIds = if (draft.accountIds.isNotEmpty()) {
            draft.accountIds
        } else if (draft.accountId != null) {
            setOf(draft.accountId)
        } else emptySet()

        val targetCategoryIds = if (draft.categoryIds.isNotEmpty()) {
            draft.categoryIds
        } else if (draft.categoryId != null) {
            setOf(draft.categoryId)
        } else emptySet()

        return transactions.filter { txn ->
            // 1. Text Search Query
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val matchMerchant = txn.effectiveMerchant.lowercase().contains(q)
                val matchDesc = txn.originalDescription?.lowercase()?.contains(q) ?: false
                val matchNotes = txn.notes?.lowercase()?.contains(q) ?: false
                val matchCategory = txn.effectiveCategoryId?.let { categoryMap[it]?.name?.lowercase()?.contains(q) } ?: false
                if (!matchMerchant && !matchDesc && !matchNotes && !matchCategory) return@filter false
            }

            // 2. Quick Filter Bar
            when (quick) {
                QuickFilter.ALL -> {}
                QuickFilter.EXPENSES -> if (!txn.isExpense || txn.effectiveIsTransfer) return@filter false
                QuickFilter.INCOME -> if (!txn.isIncome || txn.effectiveIsTransfer) return@filter false
                QuickFilter.TRANSFERS -> if (!txn.effectiveIsTransfer) return@filter false
                QuickFilter.UNCATEGORIZED -> if (txn.effectiveCategoryId != null || txn.effectiveIsTransfer) return@filter false
                QuickFilter.PENDING -> if (!txn.isPending) return@filter false
                QuickFilter.MHA -> if (txn.effectiveMhaEligible != true) return@filter false
            }

            // 3. Draft Filter: Type
            when (draft.type.lowercase()) {
                "expense", "expenses" -> if (!txn.isExpense || txn.effectiveIsTransfer) return@filter false
                "income" -> if (!txn.isIncome || txn.effectiveIsTransfer) return@filter false
                "transfer", "transfers" -> if (!txn.effectiveIsTransfer) return@filter false
            }

            // 4. Draft Filter: Include Transfers & Include Ignored
            if (!draft.includeTransfers && txn.effectiveIsTransfer) return@filter false
            if (!draft.includeIgnored && txn.effectiveIsIgnored) return@filter false

            // 5. Draft Filter: Has Edits
            when (draft.hasEditsFilter.lowercase()) {
                "edited", "yes" -> {
                    val hasEdit = txn.editSource != EditSource.NONE ||
                            txn.editedMerchant != null ||
                            txn.editedCategoryId != null ||
                            txn.editedCategoryIdSource != null ||
                            txn.editedAmountCents != null ||
                            txn.editedDate != null ||
                            txn.editedIsTransfer != null ||
                            txn.editedIsIgnored != null ||
                            txn.editedMhaEligibleSource != null
                    if (!hasEdit) return@filter false
                }
                "original", "no" -> {
                    val hasEdit = txn.editSource != EditSource.NONE ||
                            txn.editedMerchant != null ||
                            txn.editedCategoryId != null ||
                            txn.editedCategoryIdSource != null ||
                            txn.editedAmountCents != null ||
                            txn.editedDate != null ||
                            txn.editedIsTransfer != null ||
                            txn.editedIsIgnored != null ||
                            txn.editedMhaEligibleSource != null
                    if (hasEdit) return@filter false
                }
            }

            // 6. Draft Filter: Accounts
            if (targetAccountIds.isNotEmpty() && !targetAccountIds.contains(txn.accountId)) {
                return@filter false
            }

            // 7. Draft Filter: Categories
            if (draft.uncategorizedOnly) {
                if (txn.effectiveCategoryId != null || txn.effectiveIsTransfer) return@filter false
            } else if (targetCategoryIds.isNotEmpty()) {
                val catId = txn.effectiveCategoryId
                if (catId == null || !targetCategoryIds.contains(catId)) return@filter false
            }

            // 8. Draft Filter: Amount Range (absolute cents)
            val absAmount = abs(txn.effectiveAmountCents)
            if (draft.minAmountCents != null && absAmount < draft.minAmountCents) return@filter false
            if (draft.maxAmountCents != null && absAmount > draft.maxAmountCents) return@filter false

            // 9. Draft Filter: Date Ranges & Presets
            when (draft.datePreset) {
                "this_month" -> if (txn.effectiveDate.take(7) != currentYearMonth) return@filter false
                "this_year" -> if (txn.effectiveDate.take(4) != currentYear) return@filter false
                "last_30_days" -> {
                    val txnDate = runCatching { LocalDate.parse(txn.effectiveDate) }.getOrNull()
                    if (txnDate == null || txnDate.toEpochDays() < (today.toEpochDays() - 30)) return@filter false
                }
                "last_90_days" -> {
                    val txnDate = runCatching { LocalDate.parse(txn.effectiveDate) }.getOrNull()
                    if (txnDate == null || txnDate.toEpochDays() < (today.toEpochDays() - 90)) return@filter false
                }
                "custom" -> {
                    if (draft.startDate != null && txn.effectiveDate < draft.startDate) return@filter false
                    if (draft.endDate != null && txn.effectiveDate > draft.endDate) return@filter false
                }
            }

            // Fallback for custom bounds regardless of preset name
            if (draft.datePreset != "custom") {
                if (draft.startDate != null && txn.effectiveDate < draft.startDate) return@filter false
                if (draft.endDate != null && txn.effectiveDate > draft.endDate) return@filter false
            }

            true
        }
    }

    fun sortTransactions(
        transactions: List<Transaction>,
        sort: TransactionSortOption
    ): List<Transaction> = when (sort) {
        TransactionSortOption.DATE_DESC -> transactions.sortedWith(
            compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id }
        )
        TransactionSortOption.DATE_ASC -> transactions.sortedWith(
            compareBy<Transaction> { it.effectiveDate }.thenBy { it.id }
        )
        TransactionSortOption.AMOUNT_DESC -> transactions.sortedByDescending { it.effectiveAmountCents }
        TransactionSortOption.AMOUNT_ASC -> transactions.sortedBy { it.effectiveAmountCents }
        TransactionSortOption.ABS_AMOUNT_DESC -> transactions.sortedByDescending { abs(it.effectiveAmountCents) }
        TransactionSortOption.ABS_AMOUNT_ASC -> transactions.sortedBy { abs(it.effectiveAmountCents) }
        TransactionSortOption.MERCHANT_ASC -> transactions.sortedBy { it.effectiveMerchant.lowercase() }
    }

    fun paginateTransactions(
        transactions: List<Transaction>,
        page: Int,
        pageSize: Int
    ): List<Transaction> {
        if (pageSize <= 0 || transactions.isEmpty()) return transactions
        val safePage = page.coerceAtLeast(1)
        val fromIndex = (safePage - 1) * pageSize
        if (fromIndex >= transactions.size) return emptyList()
        val toIndex = (fromIndex + pageSize).coerceAtMost(transactions.size)
        return transactions.subList(fromIndex, toIndex)
    }

    data class RepoSnapshot(
        val transactions: List<Transaction>,
        val categories: List<Category>,
        val accounts: List<app.overbay.orbit.data.model.Account>,
        val nextRulePriority: Int,
        val preferences: OrbitPreferences = OrbitPreferences()
    )

    data class FilterParams(
        val query: String,
        val quick: QuickFilter,
        val draft: TransactionFilterDraft,
        val sort: TransactionSortOption,
        val isCompactView: Boolean
    )

    fun buildBaseUiState(
        snapshot: RepoSnapshot,
        filterParams: FilterParams,
        pageSize: Int,
        page: Int
    ): TransactionsUiState {
        val (txns, categories, accounts, nextPriority, preferences) = snapshot
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val filteredRaw = filterTransactions(
            transactions = txns,
            query = filterParams.query,
            quick = filterParams.quick,
            draft = filterParams.draft,
            categoryMap = categoryMap
        )

        val sorted = sortTransactions(filteredRaw, filterParams.sort)
        val paginated = paginateTransactions(sorted, page, pageSize)

        val currentMonthKey = LocalDateUtils.currentYearMonth()
        val monthlyCount = txns.count { it.effectiveDate.take(7) == currentMonthKey && !it.effectiveIsIgnored }

        var totalExpense = 0L
        var totalIncome = 0L
        for (txn in sorted) {
            if (!txn.effectiveIsTransfer && !txn.effectiveIsIgnored) {
                if (txn.effectiveAmountCents < 0) {
                    totalExpense += txn.effectiveAmountCents
                } else {
                    totalIncome += txn.effectiveAmountCents
                }
            }
        }

        val displayList = if (pageSize <= 0) sorted else paginated
        // Count per month from `sorted` (full filtered set across all pages) so month headers reflect total matches, not just the current page slice.
        val totalCountsByMonth = sorted.groupingBy { it.effectiveDate.take(7) }.eachCount()
        val grouped = displayList.groupBy { it.effectiveDate.take(7) }.map { (monthKey, items) ->
            MonthTransactionGroup(
                monthKey = monthKey,
                monthLabel = LocalDateUtils.formatMonthLabel(monthKey),
                count = totalCountsByMonth[monthKey] ?: items.size,
                transactions = items
            )
        }

        return TransactionsUiState(
            transactions = txns,
            filteredTransactions = sorted,
            paginatedTransactions = paginated,
            groupedTransactions = grouped,
            categories = categories,
            accounts = accounts,
            categoryMap = categoryMap,
            accountMap = accountMap,
            searchQuery = filterParams.query,
            quickFilter = filterParams.quick,
            filterDraft = filterParams.draft,
            sortOption = filterParams.sort,
            isCompactView = filterParams.isCompactView,
            pageSize = pageSize,
            currentPage = page,
            monthlyTransactionCount = monthlyCount,
            nextRulePriority = nextPriority,
            totalExpenseCents = totalExpense,
            totalIncomeCents = totalIncome,
            netCashflowCents = totalIncome + totalExpense,
            mhaEnabled = preferences.mhaTrackerEnabled,
            transactionPinnedActions = preferences.transactionPinnedActions,
            transactionActionOrder = preferences.transactionActionOrder
        )
    }
}
