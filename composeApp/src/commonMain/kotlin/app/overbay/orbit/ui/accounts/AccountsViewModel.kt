package app.overbay.orbit.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.accountTransactionCount
import app.overbay.orbit.data.addBalanceRecord
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.reorderAccounts
import app.overbay.orbit.data.saveCreditCardProfile
import app.overbay.orbit.data.setAccountArchived
import app.overbay.orbit.data.unlinkSimpleFinAccount
import app.overbay.orbit.domain.Cents
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
    private val _showAddRecordDialog = MutableStateFlow(false)
    private val _addRecordAccount = MutableStateFlow<Account?>(null)
    private val _isPhoneViewingDetail = MutableStateFlow(false)
    private val _deleteConfirmAccountId = MutableStateFlow<Long?>(null)
    private val _deleteBlockedAccount = MutableStateFlow<Account?>(null)
    private val _deleteBlockedTxnCount = MutableStateFlow(0)
    private val _showArchived = MutableStateFlow(false)
    private val _isReorderMode = MutableStateFlow(false)
    private val _groupOrder = MutableStateFlow(AccountTypeCategory.entries.toList())
    private val _accountSortOverrides = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val _isSyncing = MutableStateFlow(false)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repository.accounts, repository.transactions, repository.snapshot, repository.preferences
    ) { accts, txns, snap, prefs -> AccountsDataBundle(accts, txns, snap, prefs) }

    private val dialogFlow = combine(
        _editingAccount, _showEditorSheet, _editingCardProfile, _cardProfileAccountId, _showCardProfileSheet
    ) { editAcct, showEdit, cardProfile, cardAcctId, showCard ->
        AccountsSheetBundle(editAcct, showEdit, cardProfile, cardAcctId, showCard)
    }

    private val actionFlow = combine(
        _selectedAccountId, _mergingAccount, _showMergeDialog, _historyAccount, _showHistorySheet
    ) { selId, mergeAcct, showMerge, histAcct, showHist ->
        AccountsActionBundle(selId, mergeAcct, showMerge, histAcct, showHist)
    }

    private val addRecordFlow = combine(_showAddRecordDialog, _addRecordAccount) { showAdd, acct ->
        AddRecordBundle(showAdd, acct)
    }

    private val syncFlow = combine(
        _isSyncing, _syncErrorMessage, _showArchived, _isReorderMode, _groupOrder
    ) { syncing, err, showArchived, reorder, order ->
        SyncReorderBundle(syncing, err, showArchived, reorder, order)
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        dataFlow, dialogFlow, actionFlow, addRecordFlow, syncFlow
    ) { data, dialog, action, addRecord, syncReorder ->
        val filtered = if (syncReorder.showArchived) data.accounts else data.accounts.filter { !it.isArchived }
        val sorted = filtered.sortedWith(compareBy<Account> { it.isArchived }.thenBy { it.sortOrder }.thenBy { it.name })
        val groups = groupAccounts(sorted).sortedBy { grp ->
            val idx = syncReorder.groupOrder.indexOf(grp.category)
            if (idx >= 0) idx else Int.MAX_VALUE
        }
        val snapshot = NetWorthEngine.computeCurrentSnapshot(data.accounts)
        val selId = action.selectedAccountId
        val activeTxnsAccountId = selId ?: action.historyAccount?.id
        val selectedTxns = if (activeTxnsAccountId != null) {
            data.transactions.filter { it.accountId == activeTxnsAccountId }.sortedByDescending { it.effectiveDate }
        } else emptyList()
        val historyRecords = if (activeTxnsAccountId != null) {
            data.workspaceSnapshot.balanceRecords.filter { it.accountId == activeTxnsAccountId }
                .sortedWith(compareByDescending<BalanceRecord> { it.recordDate }.thenByDescending { it.id })
        } else emptyList()

        AccountsUiState(
            accounts = sorted, accountGroups = groups, totalAssetsCents = snapshot.totalAssetsCents,
            totalLiabilitiesCents = snapshot.totalLiabilitiesCents, netWorthCents = snapshot.netWorthCents,
            cashCents = snapshot.cashCents, creditCardCents = snapshot.creditDebtCents,
            investmentsCents = snapshot.investmentsCents, showArchived = syncReorder.showArchived,
            isReorderMode = syncReorder.isReorderMode, selectedAccountId = selId,
            selectedAccountTransactions = selectedTxns, editingAccount = dialog.editingAccount,
            showEditorSheet = dialog.showEditorSheet, editingCardProfile = dialog.editingCardProfile,
            cardProfileAccountId = dialog.cardProfileAccountId, showCardProfileSheet = dialog.showCardProfileSheet,
            mergingAccount = action.mergingAccount, showMergeDialog = action.showMergeDialog,
            historyAccount = action.historyAccount, showHistorySheet = action.showHistorySheet,
            historyBalanceRecords = historyRecords, showAddRecordDialog = addRecord.showAddRecordDialog,
            addRecordAccount = addRecord.addRecordAccount, accountGroupWideSpans = data.preferences.accountGroupWideSpans,
            isSyncing = syncReorder.syncing, syncErrorMessage = syncReorder.err, isLoading = false
        )
    }.combine(_isPhoneViewingDetail) { state, phoneDetail -> state.copy(isPhoneViewingDetail = phoneDetail)
    }.combine(_deleteConfirmAccountId) { state, delId -> state.copy(deleteConfirmAccountId = delId)
    }.combine(combine(_deleteBlockedAccount, _deleteBlockedTxnCount) { a, c -> a to c }) { state, (acct, count) ->
        state.copy(deleteBlockedAccount = acct, deleteBlockedTransactionCount = count)
    }.combine(_accountSortOverrides) { state, overrides ->
        if (overrides.isEmpty()) state else {
            val updated = state.accountGroups.map { grp ->
                grp.copy(accounts = grp.accounts.sortedWith(
                    compareBy<Account> { it.isArchived }.thenBy { overrides[it.id] ?: it.sortOrder }.thenBy { it.name }
                ))
            }
            state.copy(accountGroups = updated)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState(isLoading = true))

    fun selectAccount(accountId: Long?) { _selectedAccountId.value = accountId }
    fun setPhoneViewingDetail(viewing: Boolean) { _isPhoneViewingDetail.value = viewing }

    fun requestDeleteAccount(accountId: Long) {
        val count = repository.accountTransactionCount(accountId)
        if (count > 0) {
            _deleteBlockedAccount.value = uiState.value.accounts.find { it.id == accountId }
            _deleteBlockedTxnCount.value = count
        } else {
            _deleteConfirmAccountId.value = accountId
        }
    }

    fun dismissDeleteAccount() { _deleteConfirmAccountId.value = null }
    fun dismissDeleteBlocked() { _deleteBlockedAccount.value = null; _deleteBlockedTxnCount.value = 0 }

    fun openMergeFromDeleteBlocked() {
        val target = _deleteBlockedAccount.value
        dismissDeleteBlocked()
        if (target != null) openMergeDialog(target)
    }

    fun getAccountTransactionCount(accountId: Long): Int = repository.accountTransactionCount(accountId)

    fun openAccountDeepLink(accountId: Long) {
        _selectedAccountId.value = accountId
        _showEditorSheet.value = false
        _editingAccount.value = null
        _showCardProfileSheet.value = false
        _editingCardProfile.value = null
        _showHistorySheet.value = false
        _historyAccount.value = null
        _showAddRecordDialog.value = false
    }

    fun selectAccountForInspect(accountId: Long?) {
        if (accountId != null && _selectedAccountId.value == accountId &&
            !_showEditorSheet.value && !_showCardProfileSheet.value &&
            !_showHistorySheet.value && !_showAddRecordDialog.value) {
            _selectedAccountId.value = null
        } else {
            _selectedAccountId.value = accountId
            _showEditorSheet.value = false
            _editingAccount.value = null
            _showCardProfileSheet.value = false
            _editingCardProfile.value = null
            _showHistorySheet.value = false
            _historyAccount.value = null
            _showAddRecordDialog.value = false
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
        _showAddRecordDialog.value = false
    }

    fun openAddAccount(defaultType: AccountType = AccountType.CHECKING) {
        _showCardProfileSheet.value = false
        _showHistorySheet.value = false
        _editingAccount.value = Account(
            id = 0L, name = "", type = defaultType, institution = "", isManual = true, currentBalanceCents = 0L
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
                _isPhoneViewingDetail.value = false
            }
            _deleteConfirmAccountId.value = null
            closeEditorSheet()
        }
    }

    fun deleteAccount(account: Account) { deleteAccount(account.id) }
    fun toggleShowArchived() { _showArchived.value = !_showArchived.value }
    fun toggleReorderMode() { _isReorderMode.value = !_isReorderMode.value }

    fun moveGroup(fromIndex: Int, toIndex: Int) {
        val visible = uiState.value.accountGroups.map { it.category }.toMutableList()
        if (fromIndex !in visible.indices || toIndex !in visible.indices || fromIndex == toIndex) return
        val moved = visible.removeAt(fromIndex)
        visible.add(toIndex, moved)
        _groupOrder.value = visible + _groupOrder.value.filter { it !in visible }
    }

    fun moveAccountInGroup(category: AccountTypeCategory, fromIndex: Int, toIndex: Int) {
        val group = uiState.value.accountGroups.find { it.category == category } ?: return
        val list = group.accounts.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        val newOverrides = _accountSortOverrides.value.toMutableMap()
        list.forEachIndexed { idx, acct -> newOverrides[acct.id] = idx }
        _accountSortOverrides.value = newOverrides

        val allAccounts = uiState.value.accounts
        val updatedIds = allAccounts.sortedWith(
            compareBy<Account> { it.isArchived }.thenBy { newOverrides[it.id] ?: it.sortOrder }.thenBy { it.name }
        ).map { it.id }

        viewModelScope.launch { repository.reorderAccounts(updatedIds) }
    }

    fun toggleArchiveAccount(account: Account) {
        viewModelScope.launch { repository.setAccountArchived(account.id, !account.isArchived) }
    }

    fun unlinkSimpleFin(accountId: Long) {
        viewModelScope.launch { repository.unlinkSimpleFinAccount(accountId) }
    }

    fun requestSnapshotReminder(onNavigate: ((OrbitNavKey) -> Unit)? = null) {
        repository.requestOpenSettingsSubpage("preferences")
        onNavigate?.invoke(OrbitNavKey.Settings)
    }

    fun toggleGroupWideSpan(category: AccountTypeCategory) {
        viewModelScope.launch {
            val currentSpans = repository.preferences.value.accountGroupWideSpans
            val currentSpan = currentSpans[category.name] ?: 1
            val nextSpan = if (currentSpan == 1) 2 else 1
            val updated = currentSpans + (category.name to nextSpan)
            repository.updatePreferences(repository.preferences.value.copy(accountGroupWideSpans = updated))
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

    fun saveCreditCardProfile(
        profile: CreditCardProfile,
        accountName: String? = null,
        accountInstitution: String? = null
    ) {
        val accountId = _cardProfileAccountId.value ?: profile.accountId
        viewModelScope.launch {
            repository.saveCreditCardProfile(
                accountId = accountId,
                profile = profile,
                accountName = accountName,
                accountInstitution = accountInstitution
            )
            closeCreditCardProfile()
        }
    }

    fun toggleCardBenefit(accountId: Long, benefitIndex: Int) {
        val account = uiState.value.accounts.find { it.id == accountId } ?: return
        val profile = account.creditCardProfile ?: return
        val currentBenefits = profile.benefits.toMutableList()
        if (benefitIndex in currentBenefits.indices) {
            val item = currentBenefits[benefitIndex]
            currentBenefits[benefitIndex] = item.copy(usedThisYear = !item.usedThisYear)
            val updated = profile.copy(benefits = currentBenefits)
            viewModelScope.launch { repository.saveCreditCardProfile(accountId, updated) }
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

    fun openAddBalanceRecord(account: Account) {
        _addRecordAccount.value = account
        _showAddRecordDialog.value = true
    }

    fun closeAddBalanceRecord() {
        _showAddRecordDialog.value = false
        _addRecordAccount.value = null
    }

    fun addBalanceRecord(accountId: Long, date: String, balanceCents: Cents) {
        viewModelScope.launch {
            val result = repository.addBalanceRecord(accountId, date, balanceCents)
            if (result.isSuccess) {
                closeAddBalanceRecord()
            } else {
                _syncErrorMessage.value = result.exceptionOrNull()?.message ?: "Failed to add balance record"
            }
        }
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

    fun clearSyncError() { _syncErrorMessage.value = null }

    companion object {
        fun groupAccounts(accounts: List<Account>): List<AccountGroupDisplay> {
            val map = listOf(
                AccountTypeCategory.CASH_AND_CHECKING to ("Cash & Checking" to accounts.filter { it.type in setOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH) }),
                AccountTypeCategory.CREDIT_CARDS to ("Credit Cards" to accounts.filter { it.type == AccountType.CREDIT }),
                AccountTypeCategory.INVESTMENTS to ("Investments" to accounts.filter { it.type == AccountType.INVESTMENT }),
                AccountTypeCategory.REAL_ESTATE to ("Real Estate" to accounts.filter { it.type == AccountType.MORTGAGE && it.estimatedValueCents != null }),
                AccountTypeCategory.LOANS to ("Loans & Mortgages" to accounts.filter { it.type == AccountType.LOAN || (it.type == AccountType.MORTGAGE && it.estimatedValueCents == null) })
            )
            return map.mapNotNull { (cat, pair) ->
                val (title, list) = pair
                if (list.isEmpty()) null
                else AccountGroupDisplay(
                    category = cat,
                    title = title,
                    accounts = list,
                    totalCents = if (cat == AccountTypeCategory.REAL_ESTATE) list.sumOf { it.homeEquityCents ?: 0L } else list.sumOf { it.currentBalanceCents }
                )
            }
        }
    }
}

private data class AccountsDataBundle(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val workspaceSnapshot: OrbitWorkspaceSnapshot,
    val preferences: OrbitPreferences
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

private data class AddRecordBundle(
    val showAddRecordDialog: Boolean,
    val addRecordAccount: Account?
)

private data class SyncReorderBundle(
    val syncing: Boolean,
    val err: String?,
    val showArchived: Boolean,
    val isReorderMode: Boolean,
    val groupOrder: List<AccountTypeCategory> = AccountTypeCategory.entries.toList()
)
