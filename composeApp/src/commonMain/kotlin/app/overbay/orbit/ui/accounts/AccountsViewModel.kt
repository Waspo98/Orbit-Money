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

    private val syncFlow = combine(_isSyncing, _syncErrorMessage) { syncing, err ->
        Pair(syncing, err)
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        dataFlow,
        dialogFlow,
        actionFlow,
        syncFlow
    ) { data, dialog, action, (syncing, syncErr) ->
        val sortedAccounts = data.accounts.sortedWith(
            compareBy<Account> { it.isArchived }.thenBy { it.sortOrder }.thenBy { it.name }
        )
        val groups = groupAccounts(sortedAccounts)
        val snapshot = NetWorthEngine.computeCurrentSnapshot(sortedAccounts)

        val selId = action.selectedAccountId ?: sortedAccounts.firstOrNull()?.id
        val selectedTxns = if (selId != null) {
            data.transactions
                .filter { it.accountId == selId }
                .sortedByDescending { it.effectiveDate }
        } else emptyList()

        AccountsUiState(
            accounts = sortedAccounts,
            accountGroups = groups,
            totalAssetsCents = snapshot.totalAssetsCents,
            totalLiabilitiesCents = snapshot.totalLiabilitiesCents,
            netWorthCents = snapshot.netWorthCents,
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
            isSyncing = syncing,
            syncErrorMessage = syncErr,
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

    fun openAddAccount(defaultType: AccountType = AccountType.CHECKING) {
        _editingAccount.value = Account(
            id = 0L,
            name = "",
            type = defaultType,
            currentBalanceCents = 0L,
            isManual = true
        )
        _showEditorSheet.value = true
    }

    fun openEditAccount(account: Account) {
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
            if (_selectedAccountId.value == null || _selectedAccountId.value == 0L) {
                _selectedAccountId.value = account.id
            }
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            closeEditorSheet()
            if (_selectedAccountId.value == id) {
                _selectedAccountId.value = null
            }
        }
    }

    fun openCreditCardProfile(account: Account) {
        _cardProfileAccountId.value = account.id
        _editingCardProfile.value = account.creditCardProfile ?: CreditCardProfile(
            accountId = account.id,
            cardName = account.name,
            issuerName = account.institution
        )
        _showCardProfileSheet.value = true
    }

    fun closeCreditCardProfile() {
        _showCardProfileSheet.value = false
        _editingCardProfile.value = null
        _cardProfileAccountId.value = null
    }

    fun saveCreditCardProfile(profile: CreditCardProfile) {
        viewModelScope.launch {
            val accountId = _cardProfileAccountId.value ?: profile.accountId
            val account = repository.accounts.value.find { it.id == accountId } ?: return@launch
            val updatedAccount = account.copy(creditCardProfile = profile)
            repository.saveAccount(updatedAccount)
            closeCreditCardProfile()
        }
    }

    fun openMergeDialog(account: Account) {
        _mergingAccount.value = account
        _showMergeDialog.value = true
    }

    fun closeMergeDialog() {
        _showMergeDialog.value = false
        _mergingAccount.value = null
    }

    fun mergeAccountInto(sourceAccountId: Long, targetAccountId: Long, combineBalances: Boolean) {
        viewModelScope.launch {
            repository.mergeAccounts(sourceAccountId, targetAccountId, combineBalances)
            closeMergeDialog()
            _selectedAccountId.value = targetAccountId
        }
    }

    fun openBalanceHistory(account: Account) {
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
                is SyncResult.Success -> {
                    _isSyncing.value = false
                }
                is SyncResult.Error -> {
                    _isSyncing.value = false
                    _syncErrorMessage.value = res.message
                }
                is SyncResult.NoOp -> {
                    _isSyncing.value = false
                }
            }
        }
    }

    companion object {
        fun groupAccounts(accounts: List<Account>): List<AccountGroupDisplay> {
            val active = accounts.filterNot { it.isArchived }

            val cash = active.filter { it.type in setOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH) }
            val credit = active.filter { it.type == AccountType.CREDIT }
            val invest = active.filter { it.type == AccountType.INVESTMENT }
            val realEstate = active.filter { it.type == AccountType.MORTGAGE || (it.type == AccountType.OTHER && it.estimatedValueCents != null) }
            val loans = active.filter { it.type == AccountType.LOAN || (it.type == AccountType.MORTGAGE && it.estimatedValueCents == null) }

            val groups = mutableListOf<AccountGroupDisplay>()
            if (cash.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.CASH_AND_CHECKING,
                        title = AccountTypeCategory.CASH_AND_CHECKING.displayName,
                        accounts = cash,
                        totalCents = cash.sumOf { it.currentBalanceCents }
                    )
                )
            }
            if (credit.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.CREDIT_CARDS,
                        title = AccountTypeCategory.CREDIT_CARDS.displayName,
                        accounts = credit,
                        totalCents = credit.sumOf { it.currentBalanceCents }
                    )
                )
            }
            if (invest.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.INVESTMENTS,
                        title = AccountTypeCategory.INVESTMENTS.displayName,
                        accounts = invest,
                        totalCents = invest.sumOf { it.currentBalanceCents }
                    )
                )
            }
            if (realEstate.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.REAL_ESTATE,
                        title = AccountTypeCategory.REAL_ESTATE.displayName,
                        accounts = realEstate,
                        totalCents = realEstate.sumOf { it.homeEquityCents ?: it.currentBalanceCents }
                    )
                )
            }
            if (loans.isNotEmpty()) {
                groups.add(
                    AccountGroupDisplay(
                        category = AccountTypeCategory.LOANS,
                        title = AccountTypeCategory.LOANS.displayName,
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
