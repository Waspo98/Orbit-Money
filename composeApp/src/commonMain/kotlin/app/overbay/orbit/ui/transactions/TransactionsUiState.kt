package app.overbay.orbit.ui.transactions

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.ui.components.TransactionFilterDraft

enum class QuickFilter(val label: String) {
    ALL("All"),
    EXPENSES("Expenses"),
    INCOME("Income"),
    TRANSFERS("Transfers"),
    UNCATEGORIZED("Uncategorized"),
    PENDING("Pending"),
    MHA("MHA")
}

enum class TransactionSortOption(val label: String) {
    DATE_DESC("Date: Newest"),
    DATE_ASC("Date: Oldest"),
    AMOUNT_DESC("Amount: High to Low"),
    AMOUNT_ASC("Amount: Low to High"),
    MERCHANT_ASC("Merchant: A to Z")
}

data class MonthTransactionGroup(
    val monthKey: String,
    val monthLabel: String,
    val count: Int,
    val transactions: List<Transaction>
)

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val groupedTransactions: List<MonthTransactionGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categoryMap: Map<Long, Category> = emptyMap(),
    val accountMap: Map<Long, Account> = emptyMap(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val quickFilter: QuickFilter = QuickFilter.ALL,
    val filterDraft: TransactionFilterDraft = TransactionFilterDraft(),
    val sortOption: TransactionSortOption = TransactionSortOption.DATE_DESC,
    val isCompactView: Boolean = false,
    val selectedTransactionIds: Set<Long> = emptySet(),
    val inspectingTransactionId: Long? = null,
    val expandedTransactionId: Long? = null,
    val monthlyTransactionCount: Int = 0,
    val showEditorSheet: Boolean = false,
    val showSplitSheet: Boolean = false,
    val showCsvImportSheet: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showBatchDeleteConfirm: Boolean = false,
    val singleDeleteConfirmId: Long? = null,
    val showCategoryBatchPicker: Boolean = false,
    val logoFinderTransactionId: Long? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val syncToastMessage: String? = null,
    val totalExpenseCents: Cents = 0L,
    val totalIncomeCents: Cents = 0L,
    val netCashflowCents: Cents = 0L
) {
    val isSelectionMode: Boolean get() = selectedTransactionIds.isNotEmpty()
    val selectedCount: Int get() = selectedTransactionIds.size

    val inspectingTransaction: Transaction?
        get() = transactions.find { it.id == inspectingTransactionId }

    val logoFinderTransaction: Transaction?
        get() = transactions.find { it.id == logoFinderTransactionId }

    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
                quickFilter != QuickFilter.ALL ||
                filterDraft.type != "all" ||
                filterDraft.datePreset != "all" ||
                filterDraft.accountId != null ||
                filterDraft.categoryId != null
}
