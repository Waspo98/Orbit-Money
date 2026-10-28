package app.overbay.orbit.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.NetWorthEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountsViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    private val _editingAccount = MutableStateFlow<Account?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _editingCardProfile = MutableStateFlow<CreditCardProfile?>(null)
    private val _cardProfileAccountId = MutableStateFlow<Long?>(null)
    private val _showCardProfileSheet = MutableStateFlow(false)
    private val _mergingAccount = MutableStateFlow<Account?>(null)
    private val _showMergeDialog = MutableStateFlow(false)
    private val _historyAccount = MutableStateFlow<Account?>(null)
    private val _showHistorySheet = MutableStateFlow(false)
    private val _showArchived = MutableStateFlow(false)
    private val _isReorderMode = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repository.accounts,
        repository.transactions
    ) { accounts, transactions ->
        AccountsDataBundle(accounts, transactions)
    }

    private val dialogFlow = combine(
        _editingAccount,
        _showEditorSheet,
        _editingCardProfile,
        _cardProfileAccountId,
        _showCardProfileSheet
    ) { editAcct, showEdit, cardProfile, cardAcctId, showCard ->
        AccountsSheetBundle(editAcct, showEdit, cardProfile, cardAcctId, showCard)
    }

    private val actionFlow = combine(
        _selectedAccountId,
        _mergingAccount,
        _showMergeDialog,
        _historyAccount,
        _showHistorySheet
    ) { selId, mergeAcct, showMerge, histAcct, showHist ->
        AccountsActionBundle(selId, mergeAcct, showMerge, histAcct, showHist)
    }

    private val syncFlow = combine(
        _isSyncing,
        _syncErrorMessage,
        _showArchived,
        _isReorderMode
    ) { syncing, err, showArchived, reorder ->
        SyncReorderBundle(syncing, err, showArchived, reorder)
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        dataFlow,
        dialogFlow,
        actionFlow,
        syncFlow
    ) { data, dialog, action, syncReorder ->
        val filteredAccounts = if (syncReorder.showArchived) {
            data.accounts
        } else {
            data.accounts.filter { !it.isArchived }
        }
        val sortedAccounts = filteredAccounts.sortedWith(
            compareBy<Account> { it.isArchived }.thenBy { it.sortOrder }.thenBy { it.name }
        )
        val groups = groupAccounts(sortedAccounts)
        val snapshot = NetWorthEngine.computeCurrentSnapshot(data.accounts)

        val selId = action.selectedAccountId
        val activeTxnsAccountId = selId ?: action.historyAccount?.id
        val selectedTxns = if (activeTxnsAccountId != null) {
            data.transactions
                .filter { it.accountId == activeTxnsAccountId }
                .sortedByDescending { it.effectiveDate }
        } else emptyList()

        AccountsUiState(
            accounts = sortedAccounts,
            accountGroups = groups,
            totalAssetsCents = snapshot.totalAssetsCents,
            totalLiabilitiesCents = snapshot.totalLiabilitiesCents,
            netWorthCents = snapshot.netWorthCents,
            cashCents = snapshot.cashCents,
            creditCardCents = snapshot.creditDebtCents,
            investmentsCents = snapshot.investmentsCents,
            showArchived = syncReorder.showArchived,
            isReorderMode = syncReorder.isReorderMode,
            selectedAccountId = selId,
            selectedAccountTransactions = selectedTxns,
            editingAccount = dialog.editingAccount,
            showEditorSheet = dialog.showEditorSheet,
            editingCardProfile = dialog.editingCardProfile,
            cardProfileAccountId = dialog.cardProfileAccountId,
            showCardProfileSheet = dialog.showCardProfileSheet,
            mergingAccount = action.mergingAccount,
            showMergeDialog = action.showMergeDialog,
            historyAccount = action.historyAccount,
            showHistorySheet = action.showHistorySheet,
            isSyncing = syncReorder.syncing,
            syncErrorMessage = syncReorder.err,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState(isLoading = true)
    )

    fun selectAccount(accountId: Long?) {
        _selectedAccountId.value = accountId
    }

    fun selectAccountForInspect(accountId: Long?) {
        if (
            accountId != null &&
            _selectedAccountId.value == accountId &&
            !_showEditorSheet.value &&
            !_showCardProfileSheet.value &&
            !_showHistorySheet.value
        ) {
            _selectedAccountId.value = null
        } else {
            _selectedAccountId.value = accountId
            _showEditorSheet.value = false
            _editingAccount.value = null
            _showCardProfileSheet.value = false
            _editingCardProfile.value = null
            _showHistorySheet.value = false
            _historyAccount.value = null
        }
    }

    fun dismissInspectorPane() {
        _selectedAccountId.value = null
        _showEditorSheet.value = false
        _editingAccount.value = null
        _showCardProfileSheet.value = false
        _editingCardProfile.value = null
        _cardProfileAccountId.value = null
        _showHistorySheet.value = false
        _historyAccount.value = null
    }

    fun openAddAccount(defaultType: AccountType = AccountType.CHECKING) {
        _showCardProfileSheet.value = false
        _showHistorySheet.value = false
        _editingAccount.value = Account(
            id = 0L,
            name = "",
            type = defaultType,
            institution = "",
            isManual = true,
            currentBalanceCents = 0L
        )
        _showEditorSheet.value = true
    }

    fun openEditAccount(account: Account) {
        _selectedAccountId.value = account.id
        _showCardProfileSheet.value = false
        _showHistorySheet.value = false
        _editingAccount.value = account
        _showEditorSheet.value = true
    }

    fun closeEditorSheet() {
        _showEditorSheet.value = false
        _editingAccount.value = null
    }

    fun saveAccount(account: Account) {
        viewModelScope.launch {
            repository.saveAccount(account)
            closeEditorSheet()
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
            if (_selectedAccountId.value == accountId) {
                _selectedAccountId.value = null
            }
            closeEditorSheet()
        }
    }

    fun deleteAccount(account: Account) {
        deleteAccount(account.id)
    }

    fun toggleShowArchived() {
        _showArchived.value = !_showArchived.value
    }

    fun toggleReorderMode() {
        _isReorderMode.value = !_isReorderMode.value
    }

    fun toggleArchiveAccount(account: Account) {
        viewModelScope.launch {
            repository.saveAccount(account.copy(isArchived = !account.isArchived))
        }
    }

    fun openCreditCardProfile(account: Account) {
        _selectedAccountId.value = account.id
        _showEditorSheet.value = false
        _showHistorySheet.value = false
        _cardProfileAccountId.value = account.id
        _editingCardProfile.value = account.creditCardProfile ?: CreditCardProfile(accountId = account.id)
        _showCardProfileSheet.value = true
    }

    fun closeCreditCardProfile() {
        _showCardProfileSheet.value = false
        _editingCardProfile.value = null
        _cardProfileAccountId.value = null
    }

    fun saveCreditCardProfile(profile: CreditCardProfile) {
        val accountId = _cardProfileAccountId.value ?: return
        viewModelScope.launch {
            val account = uiState.value.accounts.find { it.id == accountId }
            if (account != null) {
                repository.saveAccount(account.copy(creditCardProfile = profile))
            }
            closeCreditCardProfile()
        }
    }

    fun openMergeDialog(sourceAccount: Account) {
        _mergingAccount.value = sourceAccount
        _showMergeDialog.value = true
    }

    fun closeMergeDialog() {
        _showMergeDialog.value = false
        _mergingAccount.value = null
    }

    fun mergeAccountInto(sourceAccountId: Long, targetAccountId: Long, combineBalances: Boolean) {
        viewModelScope.launch {
            repository.mergeAccounts(
                sourceAccountId = sourceAccountId,
                targetAccountId = targetAccountId,
                combineBalances = combineBalances
            )
            closeMergeDialog()
        }
    }

    fun mergeAccountInto(sourceAccount: Account, targetAccountId: Long, combineBalances: Boolean) {
        mergeAccountInto(sourceAccount.id, targetAccountId, combineBalances)
    }

    fun openBalanceHistory(account: Account) {
        _selectedAccountId.value = account.id
        _showEditorSheet.value = false
        _showCardProfileSheet.value = false
        _historyAccount.value = account
        _showHistorySheet.value = true
    }

    fun closeBalanceHistory() {
        _showHistorySheet.value = false
        _historyAccount.value = null
    }

    fun syncSimpleFin() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncErrorMessage.value = null
            when (val res = repository.sync(force = true)) {
                is SyncResult.Error -> _syncErrorMessage.value = res.message
                else -> {}
            }
            _isSyncing.value = false
        }
    }

    fun clearSyncError() {
        _syncErrorMessage.value = null
    }

    companion object {
        fun groupAccounts(accounts: List<Account>): List<AccountGroupDisplay> {
            val groups = mutableListOf<AccountGroupDisplay>()

            val cash = accounts.filter { it.type in setOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH) }
            if (cash.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.CASH_AND_CHECKING,
                        title = "Cash & Checking",
                        accounts = cash,
                        totalCents = cash.sumOf { it.currentBalanceCents }
                    )
                )
            }

            val credit = accounts.filter { it.type == AccountType.CREDIT }
            if (credit.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.CREDIT_CARDS,
                        title = "Credit Cards",
                        accounts = credit,
                        totalCents = credit.sumOf { it.currentBalanceCents }
                    )
                )
            }

            val investments = accounts.filter { it.type == AccountType.INVESTMENT }
            if (investments.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.INVESTMENTS,
                        title = "Investments & Retirement",
                        accounts = investments,
                        totalCents = investments.sumOf { it.currentBalanceCents }
                    )
                )
            }

            val realEstate = accounts.filter { it.type == AccountType.MORTGAGE && it.estimatedValueCents != null }
            if (realEstate.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.REAL_ESTATE,
                        title = "Real Estate",
                        accounts = realEstate,
                        totalCents = realEstate.sumOf { it.homeEquityCents ?: 0L }
                    )
                )
            }

            val loans = accounts.filter { it.type == AccountType.LOAN || (it.type == AccountType.MORTGAGE && it.estimatedValueCents == null) }
            if (loans.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.LOANS,
                        title = "Loans & Mortgages",
                        accounts = loans,
                        totalCents = loans.sumOf { it.currentBalanceCents }
                    )
                )
            }
            return groups
        }
    }
}

private data class AccountsDataBundle(
    val accounts: List<Account>,
    val transactions: List<Transaction>
)

private data class AccountsSheetBundle(
    val editingAccount: Account?,
    val showEditorSheet: Boolean,
    val editingCardProfile: CreditCardProfile?,
    val cardProfileAccountId: Long?,
    val showCardProfileSheet: Boolean
)

private data class AccountsActionBundle(
    val selectedAccountId: Long?,
    val mergingAccount: Account?,
    val showMergeDialog: Boolean,
    val historyAccount: Account?,
    val showHistorySheet: Boolean
)

private data class SyncReorderBundle(
    val syncing: Boolean,
    val err: String?,
    val showArchived: Boolean,
    val isReorderMode: Boolean
)
