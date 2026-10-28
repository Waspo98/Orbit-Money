package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents

enum class AccountTypeCategory(val displayName: String) {
    CASH_AND_CHECKING("Cash & Checking"),
    CREDIT_CARDS("Credit Cards"),
    INVESTMENTS("Investments & Retirement"),
    REAL_ESTATE("Real Estate & Property"),
    LOANS("Loans & Mortgages")
}

data class AccountGroupDisplay(
    val category: AccountTypeCategory,
    val title: String,
    val accounts: List<Account>,
    val totalCents: Cents
)

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val accountGroups: List<AccountGroupDisplay> = emptyList(),
    val totalAssetsCents: Cents = 0L,
    val totalLiabilitiesCents: Cents = 0L,
    val netWorthCents: Cents = 0L,
    val cashCents: Cents = 0L,
    val creditCardCents: Cents = 0L,
    val investmentsCents: Cents = 0L,
    val showArchived: Boolean = false,
    val isReorderMode: Boolean = false,
    val selectedAccountId: Long? = null,
    val selectedAccountTransactions: List<Transaction> = emptyList(),
    val editingAccount: Account? = null,
    val showEditorSheet: Boolean = false,
    val editingCardProfile: CreditCardProfile? = null,
    val cardProfileAccountId: Long? = null,
    val showCardProfileSheet: Boolean = false,
    val mergingAccount: Account? = null,
    val showMergeDialog: Boolean = false,
    val historyAccount: Account? = null,
    val showHistorySheet: Boolean = false,
    val isPhoneViewingDetail: Boolean = false,
    val deleteConfirmAccountId: Long? = null,
    val isSyncing: Boolean = false,
    val syncErrorMessage: String? = null,
    val isLoading: Boolean = false
) {
    val selectedAccount: Account?
        get() = accounts.find { it.id == selectedAccountId }
}
