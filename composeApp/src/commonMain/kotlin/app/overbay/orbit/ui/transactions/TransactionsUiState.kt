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

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categoryMap: Map<Long, Category> = emptyMap(),
    val accountMap: Map<Long, Account> = emptyMap(),
    val searchQuery: String = "",
    val quickFilter: QuickFilter = QuickFilter.ALL,
    val filterDraft: TransactionFilterDraft = TransactionFilterDraft(),
    val selectedTransactionIds: Set<Long> = emptySet(),
    val inspectingTransactionId: Long? = null,
    val showEditorSheet: Boolean = false,
    val showSplitSheet: Boolean = false,
    val showCsvImportSheet: Boolean = false,
    val showFilterSheet: Boolean = false,
    val isLoading: Boolean = false,
    val totalExpenseCents: Cents = 0L,
    val totalIncomeCents: Cents = 0L,
    val netCashflowCents: Cents = 0L
) {
    val isSelectionMode: Boolean get() = selectedTransactionIds.isNotEmpty()
    val selectedCount: Int get() = selectedTransactionIds.size

    val inspectingTransaction: Transaction?
        get() = transactions.find { it.id == inspectingTransactionId }

    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
                quickFilter != QuickFilter.ALL ||
                filterDraft.type != "all" ||
                filterDraft.datePreset != "all" ||
                filterDraft.accountId != null ||
                filterDraft.categoryId != null
}
