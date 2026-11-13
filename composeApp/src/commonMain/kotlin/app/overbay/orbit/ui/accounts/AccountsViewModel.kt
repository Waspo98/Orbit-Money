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
import app.overbay.orbit.data.setCardBenefitUsage
import app.overbay.orbit.data.setAccountArchived
import app.overbay.orbit.data.unlinkSimpleFinAccount
import app.overbay.orbit.domain.AccountClosureDetector
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.NetWorthEngine
import app.overbay.orbit.ui.accounts.cards.CardEditorController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class AccountsViewModel(
    private val repository: OrbitRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    todayFlow: Flow<LocalDate>? = null
) : ViewModel() {

    val cardEditor = CardEditorController(repository)
    val accountLogos = AccountLogoController(repository, viewModelScope)
    val closurePrompts = AccountClosureController(repository, viewModelScope)

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    private val _editingAccount = MutableStateFlow<Account?>(null)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _mergingAccount = MutableStateFlow<Account?>(null)
    private val _showMergeDialog = MutableStateFlow(false)
    private val _historyAccount = MutableStateFlow<Account?>(null)
    private val _showHistorySheet = MutableStateFlow(false)
    private val _showAddRecordDialog = MutableStateFlow(false)
    private val _addRecordAccount = MutableStateFlow<Account?>(null)
    private val _activeDetailTab = MutableStateFlow(AccountDetailTab.TRANSACTIONS)
    private val _isPhoneViewingDetail = MutableStateFlow(false)
    private val _deleteConfirmAccountId = MutableStateFlow<Long?>(null)
    private val _deleteWarning524Date = MutableStateFlow<String?>(null)
    private val _deleteBlockedAccount = MutableStateFlow<Account?>(null)
    private val _deleteBlockedTxnCount = MutableStateFlow(0)
    private val _showArchived = MutableStateFlow(false)
    private val _isReorderMode = MutableStateFlow(false)
    private val _groupOrder = MutableStateFlow(AccountTypeCategory.entries.toList())
    private val _accountSortOverrides = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val _isSyncing = MutableStateFlow(false)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    private val _selectedCardPlayer = MutableStateFlow(CreditCardStrategyEngine.ALL_PLAYERS)
    private val _showUsedPerks = MutableStateFlow(false)
    private val _showWhichCardWhere = MutableStateFlow(false)
    private val _selectedRewardCategory = MutableStateFlow<String?>(null)
    private val _perkUsageTarget = MutableStateFlow<PerkUsageTarget?>(null)

    private val resolvedTodayFlow = (todayFlow ?: flow {
        while (true) {
            emit(LocalDateUtils.todayLocalDate())
            delay(60_000)
        }
    }).flowOn(computeDispatcher).distinctUntilChanged()

    private val creditCardsHubFlow = combine(
        repository.accounts,
        repository.transactions,
        _selectedCardPlayer,
        resolvedTodayFlow,
        repository.snapshot.map { it.householdMembers }.distinctUntilChanged()
    ) { a, t, p, d, members ->
        CreditCardHubStateCalculator.calculate(a, t, p, d, members)
    }.distinctUntilChanged().flowOn(computeDispatcher)

    private val hubUiControlFlow = combine(
        _showUsedPerks, _showWhichCardWhere, _selectedRewardCategory
    ) { showUsed, showWhich, cat ->
        AccountsHubUiControl(showUsed, showWhich, cat)
    }

    private val dataFlow = combine(
        repository.accounts, repository.transactions, repository.snapshot, repository.preferences
    ) { accts, txns, snap, prefs -> AccountsDataBundle(accts, txns, snap, prefs) }

    private val dialogFlow = combine(
        _editingAccount,
        _showEditorSheet,
        cardEditor.draft.distinctUntilChanged { old, new ->
            (old == null) == (new == null) && old?.accountId == new?.accountId
        }
    ) { editAcct, showEdit, draft ->
        AccountsSheetBundle(editAcct, showEdit, draft)
    }

    private val actionFlow = combine(
        _selectedAccountId, _mergingAccount, _showMergeDialog, _historyAccount, _showHistorySheet
    ) { selId, mergeAcct, showMerge, histAcct, showHist ->
        AccountsActionBundle(selId, mergeAcct, showMerge, histAcct, showHist)
    }

    private val addRecordFlow = combine(_showAddRecordDialog, _addRecordAccount, _activeDetailTab) { showAdd, acct, tab ->
        AddRecordBundle(showAdd, acct, tab)
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

        val hasRewardsData = data.accounts.any { acct ->
            val strat = acct.creditCardProfile?.strategy
            !strat?.openedDate.isNullOrBlank() || (strat?.subTargetSpendCents ?: 0L) > 0L
        }
        val resolvedRewardsToolsEnabled = data.preferences.cardRewardsToolsEnabled ?: hasRewardsData
        val closureCandidates = AccountClosureDetector.detectCandidates(
            accounts = data.accounts,
            transactions = data.transactions,
            todayIso = LocalDateUtils.todayIsoDate(),
            dismissedOverrides = data.preferences.dismissedClosurePrompts
        )

        AccountsUiState(
            accounts = sorted, accountGroups = groups, totalAssetsCents = snapshot.totalAssetsCents,
            totalLiabilitiesCents = snapshot.totalLiabilitiesCents, netWorthCents = snapshot.netWorthCents,
            cashCents = snapshot.cashCents, creditCardCents = snapshot.creditDebtCents,
            investmentsCents = snapshot.investmentsCents, showArchived = syncReorder.showArchived,
            isReorderMode = syncReorder.isReorderMode, selectedAccountId = selId,
            selectedAccountTransactions = selectedTxns, activeDetailTab = addRecord.activeDetailTab,
            categoryMap = data.workspaceSnapshot.categories.associateBy { it.id },
            editingAccount = dialog.editingAccount,
            showEditorSheet = dialog.showEditorSheet, cardEditorDraft = dialog.cardEditorDraft,
            householdMembers = data.workspaceSnapshot.householdMembers,
            mergingAccount = action.mergingAccount, showMergeDialog = action.showMergeDialog,
            historyAccount = action.historyAccount, showHistorySheet = action.showHistorySheet,
            historyBalanceRecords = historyRecords, showAddRecordDialog = addRecord.showAddRecordDialog,
            addRecordAccount = addRecord.addRecordAccount, accountGroupWideSpans = data.preferences.accountGroupWideSpans,
            isSyncing = syncReorder.syncing, syncErrorMessage = syncReorder.err, isLoading = false,
            mhaEnabled = data.preferences.mhaTrackerEnabled,
            rewardsToolsEnabled = resolvedRewardsToolsEnabled,
            cardPerksWidgets = data.preferences.cardPerksWidgets,
            closureCandidates = closureCandidates
        )
    }.combine(_isPhoneViewingDetail) { state, phoneDetail -> state.copy(isPhoneViewingDetail = phoneDetail)
    }.combine(_perkUsageTarget) { state, target -> state.copy(perkUsageTarget = target)
    }.combine(accountLogos.logoFinderAccount) { state, acct ->
        state.copy(logoFinderAccount = acct?.let { target -> state.accounts.find { it.id == target.id } ?: target })
    }.combine(combine(hubUiControlFlow, creditCardsHubFlow) { ctrl, hub -> ctrl to hub }) { state, (ctrl, hub) ->
        state.copy(
            showUsedPerks = ctrl.showUsedPerks,
            showWhichCardWhere = ctrl.showWhichCardWhere,
            selectedRewardCategory = ctrl.selectedRewardCategory,
            selectedCardPlayer = hub.portfolio.selectedPlayer,
            cardPortfolioSummary = hub.portfolio,
            householdCredit = hub.householdCredit,
            cardCycles = hub.cardCycles,
            feesAhead = hub.feesAhead,
            missingBillingDatesCount = hub.missingBillingDatesCount
        )
    }.combine(combine(_deleteConfirmAccountId, _deleteWarning524Date) { delId, warnDate -> delId to warnDate }) { state, (delId, warnDate) ->
        state.copy(deleteConfirmAccountId = delId, deleteWarning524Date = warnDate)
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
    }.combine(closurePrompts.overlayFlow) { state, overlay ->
        val activePrompt = AccountClosureController.selectActivePrompt(state.closureCandidates, overlay)
        state.copy(
            activeClosurePrompt = activePrompt,
            selectedClosureResolution = AccountClosureController.selectActiveResolution(activePrompt, overlay),
            closureErrorMessage = overlay.errorMessage,
            isResolvingClosure = overlay.isResolving
        )
    }.flowOn(computeDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState(isLoading = true))

    fun selectAccount(accountId: Long?) { _selectedAccountId.value = accountId }
    fun selectDetailTab(tab: AccountDetailTab) { _activeDetailTab.value = tab }
    fun setPhoneViewingDetail(viewing: Boolean) { _isPhoneViewingDetail.value = viewing }
    fun selectCardPlayer(player: String) { _selectedCardPlayer.value = player }
    fun toggleShowUsedPerks() { _showUsedPerks.value = !_showUsedPerks.value }
    fun toggleShowWhichCardWhere() { _showWhichCardWhere.value = !_showWhichCardWhere.value }
    fun selectRewardCategory(category: String?) { _selectedRewardCategory.value = category }

    fun requestDeleteAccount(accountId: Long) {
        val count = repository.accountTransactionCount(accountId)
        if (count > 0) {
            _deleteBlockedAccount.value = uiState.value.accounts.find { it.id == accountId }
            _deleteBlockedTxnCount.value = count
        } else {
            val account = uiState.value.accounts.find { it.id == accountId }
            val strat = account?.creditCardProfile?.strategy
            val opened = strat?.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
            val today = LocalDateUtils.todayLocalDate()
            val warningDate = if (account?.type == AccountType.CREDIT && strat?.countsToward524 == true && opened != null) {
                val dropOff = LocalDateUtils.addMonthsClamped(opened, 24)
                if (dropOff > today) LocalDateUtils.formatIsoDate(dropOff) else null
            } else null
            _deleteWarning524Date.value = warningDate
            _deleteConfirmAccountId.value = accountId
        }
    }

    fun dismissDeleteAccount() {
        _deleteConfirmAccountId.value = null
        _deleteWarning524Date.value = null
    }
    fun dismissDeleteBlocked() { _deleteBlockedAccount.value = null; _deleteBlockedTxnCount.value = 0 }

    fun openMergeFromDeleteBlocked() {
        val target = _deleteBlockedAccount.value
        dismissDeleteBlocked()
        if (target != null) openMergeDialog(target)
    }

    fun getAccountTransactionCount(accountId: Long): Int = repository.accountTransactionCount(accountId)

    fun openAccountDeepLink(accountId: Long) {
        _selectedAccountId.value = accountId
        _activeDetailTab.value = AccountDetailTab.TRANSACTIONS
        _showEditorSheet.value = false
        _editingAccount.value = null
        cardEditor.clearDraft()
        _showHistorySheet.value = false
        _historyAccount.value = null
        _showAddRecordDialog.value = false
    }

    fun selectAccountForInspect(accountId: Long?) {
        if (accountId != null && _selectedAccountId.value == accountId &&
            !_showEditorSheet.value && cardEditor.draft.value == null &&
            !_showHistorySheet.value && !_showAddRecordDialog.value) {
            _selectedAccountId.value = null
        } else {
            _selectedAccountId.value = accountId
            _activeDetailTab.value = AccountDetailTab.TRANSACTIONS
            _showEditorSheet.value = false
            _editingAccount.value = null
            cardEditor.clearDraft()
            _showHistorySheet.value = false
            _historyAccount.value = null
            _showAddRecordDialog.value = false
        }
    }

    fun dismissInspectorPane() {
        _selectedAccountId.value = null
        _activeDetailTab.value = AccountDetailTab.TRANSACTIONS
        _showEditorSheet.value = false
        _editingAccount.value = null
        cardEditor.clearDraft()
        _showHistorySheet.value = false
        _historyAccount.value = null
        _showAddRecordDialog.value = false
    }

    fun openAddAccount(defaultType: AccountType = AccountType.CHECKING) {
        cardEditor.clearDraft()
        _showHistorySheet.value = false
        _editingAccount.value = Account(
            id = 0L, name = "", type = defaultType, institution = "", isManual = true, currentBalanceCents = 0L
        )
        _showEditorSheet.value = true
    }

    fun openEditAccount(account: Account) {
        _selectedAccountId.value = account.id
        cardEditor.clearDraft()
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
            val saved = repository.saveAccount(account)
            if (account.type == AccountType.CREDIT && account.creditCardProfile != null && saved.id != 0L) {
                repository.saveCreditCardProfile(
                    accountId = saved.id,
                    profile = account.creditCardProfile.copy(accountId = saved.id),
                    accountName = account.name,
                    accountInstitution = account.institution
                )
            }
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
            _deleteWarning524Date.value = null
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

    fun updateCardPerksWidgets(transform: (List<app.overbay.orbit.data.model.DashboardWidgetConfig>) -> List<app.overbay.orbit.data.model.DashboardWidgetConfig>) {
        viewModelScope.launch {
            val prefs = repository.preferences.value
            val updated = transform(prefs.cardPerksWidgets)
            repository.updatePreferences(prefs.copy(cardPerksWidgets = updated))
        }
    }

    fun openCreditCardProfile(account: Account) {
        _selectedAccountId.value = account.id
        _showEditorSheet.value = false
        _showHistorySheet.value = false
        cardEditor.startDraft(account, account.creditCardProfile)
    }

    fun closeCreditCardProfile() {
        cardEditor.clearDraft()
    }

    fun saveCreditCardProfile(
        profile: CreditCardProfile,
        accountName: String? = null,
        accountInstitution: String? = null
    ) {
        val accountId = cardEditor.draft.value?.accountId ?: profile.accountId
        viewModelScope.launch {
            repository.saveCreditCardProfile(
                accountId = accountId,
                profile = profile,
                accountName = accountName,
                accountInstitution = accountInstitution
            ).onSuccess {
                closeCreditCardProfile()
            }.onFailure { e ->
                _syncErrorMessage.value = "Couldn't save card: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun setCardBenefitUsage(accountId: Long, benefitId: String, amountCents: Cents?) {
        val account = uiState.value.accounts.find { it.id == accountId } ?: return
        val rawProfile = account.creditCardProfile ?: return
        val today = LocalDateUtils.todayLocalDate()
        val profile = CreditCardBenefitEngine.upgradeProfile(rawProfile, today)
        val benefit = profile.benefits.find { it.id == benefitId } ?: return
        val opened = profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
        val period = CreditCardBenefitEngine.currentPeriod(benefit, opened, today)
        viewModelScope.launch {
            repository.setCardBenefitUsage(
                accountId = accountId,
                benefitId = benefitId,
                periodKey = period.key,
                amountCents = amountCents,
                usedOn = if (amountCents != null && amountCents > 0) LocalDateUtils.formatIsoDate(today) else null
            ).onFailure { e ->
                _syncErrorMessage.value = "Couldn't update perk: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun toggleCardBenefit(accountId: Long, benefitId: String) {
        val account = uiState.value.accounts.find { it.id == accountId } ?: return
        val rawProfile = account.creditCardProfile ?: return
        val today = LocalDateUtils.todayLocalDate()
        val profile = CreditCardBenefitEngine.upgradeProfile(rawProfile, today)
        val benefit = profile.benefits.find { it.id == benefitId } ?: return
        val opened = profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
        val period = CreditCardBenefitEngine.currentPeriod(benefit, opened, today)
        val used = CreditCardBenefitEngine.usedCents(benefit, period)
        val periodValue = CreditCardBenefitEngine.periodValueCents(benefit)
        val targetAmount = if (used > 0L) null else if (periodValue > 0L) periodValue else 1L
        setCardBenefitUsage(accountId, benefitId, targetAmount)
    }

    fun openPerkUsage(accountId: Long, benefitId: String) {
        val account = uiState.value.accounts.find { it.id == accountId }
        val rawProfile = account?.creditCardProfile
        val today = LocalDateUtils.todayLocalDate()
        val profile = rawProfile?.let { CreditCardBenefitEngine.upgradeProfile(it, today) }
        val benefit = profile?.benefits?.find { it.id == benefitId }
        if (benefit != null && CreditCardBenefitEngine.periodValueCents(benefit) <= 0L) {
            toggleCardBenefit(accountId, benefitId)
            return
        }
        val (periodLabel, used, value) = if (benefit != null && profile != null) {
            val opened = profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
            val p = CreditCardBenefitEngine.currentPeriod(benefit, opened, today)
            Triple(p.label, CreditCardBenefitEngine.usedCents(benefit, p), CreditCardBenefitEngine.periodValueCents(benefit))
        } else Triple("", 0L, 0L)
        _perkUsageTarget.value = PerkUsageTarget(
            accountId = accountId,
            benefitId = benefitId,
            title = benefit?.title.orEmpty(),
            periodLabel = periodLabel,
            valueCents = value,
            initialUsedCents = used
        )
    }

    fun closePerkUsage() {
        _perkUsageTarget.value = null
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
        cardEditor.clearDraft()
        if (account.type == AccountType.CREDIT) {
            _historyAccount.value = account
            _showHistorySheet.value = true
        } else {
            _activeDetailTab.value = if (account.isProperty) {
                AccountDetailTab.TRANSACTIONS
            } else {
                AccountDetailTab.BALANCE_HISTORY
            }
            _showHistorySheet.value = false
            _historyAccount.value = null
        }
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
                else -> closurePrompts.resetSnoozesAfterSync()
            }
            _isSyncing.value = false
        }
    }

    fun clearSyncError() { _syncErrorMessage.value = null }

}
