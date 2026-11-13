package app.overbay.orbit.ui.accounts.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.accounts.AccountDetailTab
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.accounts.cards.CardEditorSheetContent
import app.overbay.orbit.ui.accounts.cards.CreditCardInspectorDetailContent
import app.overbay.orbit.ui.accounts.sheets.AccountBalanceHistoryContent
import app.overbay.orbit.ui.accounts.sheets.AccountEditorContent
import app.overbay.orbit.ui.accounts.sheets.AccountInlineBalanceHistorySection
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class AccountInspectorSubMode { DETAIL, EDIT, PERKS, HISTORY }

/**
 * Right-hand morphing inspector pane (and compact phone OrbitModalSheet content) for Accounts.
 * Shows the modernized Account Detail view by default when an account row is tapped, and morphs
 * smoothly into the Edit, Credit Card Perks, or Credit Card Balance History views when triggered.
 */
@Composable
fun AccountDetailInspectorPane(
    state: AccountsUiState,
    viewModel: AccountsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var cachedAccount by remember { mutableStateOf<Account?>(null) }
    val activeAccount = state.selectedAccount ?: state.editingAccount ?: state.historyAccount
    if (activeAccount != null) {
        cachedAccount = activeAccount
    }
    val inspecting = activeAccount ?: cachedAccount

    val liveCardDraft = viewModel.cardEditor.currentDraft ?: state.cardEditorDraft
    val subMode = when {
        state.showEditorSheet && state.editingAccount != null -> AccountInspectorSubMode.EDIT
        liveCardDraft != null -> AccountInspectorSubMode.PERKS
        state.showHistorySheet && state.historyAccount != null -> AccountInspectorSubMode.HISTORY
        else -> AccountInspectorSubMode.DETAIL
    }

    val canBackFromSubMode = (subMode != AccountInspectorSubMode.DETAIL && state.selectedAccount != null)
    BackHandler(enabled = canBackFromSubMode) {
        haptics.tick()
        when (subMode) {
            AccountInspectorSubMode.EDIT -> viewModel.closeEditorSheet()
            AccountInspectorSubMode.PERKS -> viewModel.cardEditor.requestClose()
            AccountInspectorSubMode.HISTORY -> viewModel.closeBalanceHistory()
            else -> {}
        }
    }

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxWidth(),
        label = "accountInspectorSubModeTransition"
    ) { mode ->
        when (mode) {
            AccountInspectorSubMode.EDIT -> {
                val editTarget = state.editingAccount ?: inspecting
                if (editTarget != null) {
                    AccountEditorContent(
                        account = editTarget,
                        availablePropertyAnchors = state.availablePropertyAnchors,
                        onSave = { viewModel.saveAccount(it) },
                        onDelete = { viewModel.requestDeleteAccount(it) },
                        onUnlinkSimpleFin = if (!editTarget.simplefinAccountId.isNullOrBlank()) {
                            { viewModel.unlinkSimpleFin(editTarget.id) }
                        } else null,
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.closeEditorSheet() else onClosePane()
                        },
                        showMhaOption = state.mhaEnabled
                    )
                }
            }

            AccountInspectorSubMode.PERKS -> {
                val draft = viewModel.cardEditor.currentDraft ?: state.cardEditorDraft
                if (draft != null) {
                    CardEditorSheetContent(
                        draft = draft,
                        controller = viewModel.cardEditor,
                        householdMembers = state.householdMembers,
                        transactions = state.selectedAccountTransactions,
                        rewardsToolsEnabled = state.rewardsToolsEnabled,
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.cardEditor.requestClose() else onClosePane()
                        }
                    )
                }
            }

            AccountInspectorSubMode.HISTORY -> {
                val histAcct = state.historyAccount ?: inspecting
                if (histAcct != null) {
                    AccountBalanceHistoryContent(
                        account = histAcct,
                        balanceRecords = state.historyBalanceRecords,
                        onAddRecord = { viewModel.openAddBalanceRecord(histAcct) },
                        onNavigateBack = if (state.selectedAccount != null) {
                            { viewModel.closeBalanceHistory() }
                        } else null,
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.closeBalanceHistory() else onClosePane()
                        }
                    )
                }
            }

            AccountInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    val unlinkAction: (() -> Unit)? = if (!inspecting.simplefinAccountId.isNullOrBlank()) {
                        { viewModel.unlinkSimpleFin(inspecting.id) }
                    } else null
                    val hasClosureAlert = inspecting.id in state.closureCandidateAccountIds

                    if (inspecting.type == AccountType.CREDIT) {
                        CreditCardInspectorDetailContent(
                            account = inspecting,
                            transactions = state.selectedAccountTransactions,
                            householdMembers = state.householdMembers,
                            rewardsToolsEnabled = state.rewardsToolsEnabled,
                            categoryMap = state.categoryMap,
                            hasClosureAlert = hasClosureAlert,
                            onReviewClosure = { viewModel.closurePrompts.openForAccount(inspecting.id) },
                            onEditCard = { viewModel.openCreditCardProfile(inspecting) },
                            onToggleBenefit = { bId -> viewModel.toggleCardBenefit(inspecting.id, bId) },
                            onOpenPerkUsage = { bId -> viewModel.openPerkUsage(inspecting.id, bId) },
                            onSeeFullTransactions = { onNavigate(OrbitNavKey.Transactions(accountId = inspecting.id)) },
                            onHistory = { viewModel.openBalanceHistory(inspecting) },
                            onAddBalanceRecord = { viewModel.openAddBalanceRecord(inspecting) },
                            onSnapshotReminder = { viewModel.requestSnapshotReminder(onNavigate) },
                            onMerge = { viewModel.openMergeDialog(inspecting) },
                            onUnlinkSimpleFin = unlinkAction,
                            onToggleArchive = { viewModel.toggleArchiveAccount(inspecting) },
                            onDelete = { viewModel.requestDeleteAccount(inspecting.id) },
                            onClosePane = onClosePane
                        )
                    } else {
                        AccountReadOnlyDetailContent(
                            account = inspecting,
                            transactions = state.selectedAccountTransactions,
                            balanceRecords = state.historyBalanceRecords,
                            categoryMap = state.categoryMap,
                            activeTab = state.activeDetailTab,
                            onSelectTab = { viewModel.selectDetailTab(it) },
                            hasClosureAlert = hasClosureAlert,
                            onReviewClosure = { viewModel.closurePrompts.openForAccount(inspecting.id) },
                            onClose = onClosePane,
                            onEdit = { viewModel.openEditAccount(inspecting) },
                            onFindLogo = { viewModel.accountLogos.open(inspecting) },
                            onUseAccountIcon = { viewModel.accountLogos.resetToAccountIconAsync(inspecting.id) },
                            onReportLogoStatus = { k, s -> viewModel.accountLogos.reportStatus(k, s) },
                            onAddBalanceRecord = { viewModel.openAddBalanceRecord(inspecting) },
                            onSnapshotReminder = { viewModel.requestSnapshotReminder(onNavigate) },
                            onMerge = { viewModel.openMergeDialog(inspecting) },
                            onToggleArchive = { viewModel.toggleArchiveAccount(inspecting) },
                            onDelete = { viewModel.requestDeleteAccount(inspecting.id) },
                            onUnlinkSimpleFin = unlinkAction,
                            onSeeTransactions = { onNavigate(OrbitNavKey.Transactions(accountId = inspecting.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountReadOnlyDetailContent(
    account: Account,
    transactions: List<Transaction>,
    balanceRecords: List<BalanceRecord>,
    categoryMap: Map<Long, Category>,
    activeTab: AccountDetailTab,
    onSelectTab: (AccountDetailTab) -> Unit,
    hasClosureAlert: Boolean = false,
    onReviewClosure: (() -> Unit)? = null,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onFindLogo: () -> Unit,
    onUseAccountIcon: () -> Unit,
    onReportLogoStatus: (String, String) -> Unit,
    onAddBalanceRecord: () -> Unit,
    onSnapshotReminder: () -> Unit,
    onMerge: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    onSeeTransactions: () -> Unit
) {
    val subtitle = remember(account) {
        listOfNotNull(
            account.institution?.takeIf { it.isNotBlank() },
            account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }
        ).joinToString(" • ").ifBlank {
            if (account.isProperty) "Real Estate Property" else account.type.displayName
        }
    }
    val showClosureBanner = (hasClosureAlert || !account.simplefinMissingSince.isNullOrBlank()) && onReviewClosure != null

    Column(modifier = Modifier.fillMaxWidth()) {
        AccountInspectorHeader(
            accountName = account.name,
            title = account.name,
            subtitle = subtitle,
            editLabel = "Edit",
            isArchived = account.isArchived,
            leadingContent = {
                AccountInstitutionMark(
                    account = account,
                    size = 40.dp,
                    cornerRadius = 10.dp,
                    logoPadding = 4.dp,
                    onFindLogo = onFindLogo,
                    onUseAccountIcon = onUseAccountIcon,
                    onReportLogoStatus = onReportLogoStatus
                )
            },
            onEdit = onEdit,
            onClose = onClose,
            onAddBalanceRecord = if (!account.isProperty) onAddBalanceRecord else null,
            onSnapshotReminder = onSnapshotReminder,
            onMerge = onMerge,
            onUnlinkSimpleFin = onUnlinkSimpleFin,
            onToggleArchive = onToggleArchive,
            onDelete = onDelete
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showClosureBanner) {
                item(key = "acct_closure_alert") {
                    AccountClosureAlertBanner(
                        account = account,
                        hasClosureAlert = hasClosureAlert,
                        onReviewClosure = onReviewClosure!!
                    )
                }
            }

            item(key = "acct_balance_hero") {
                AccountBalanceHeroCard(
                    account = account,
                    onAddBalanceRecord = if (account.isManual && !account.isProperty) onAddBalanceRecord else null
                )
            }

            if (account.isProperty) {
                item(key = "acct_property_txns") {
                    AccountRecentTransactionsSection(
                        transactions = transactions,
                        categoryMap = categoryMap,
                        onSeeTransactions = onSeeTransactions
                    )
                }
            } else {
                item(key = "acct_detail_tabs") {
                    OrbitSegmentedTabRow(
                        items = AccountDetailTab.entries,
                        selected = activeTab,
                        onSelect = onSelectTab,
                        label = { it.label },
                        badgeCount = { tab ->
                            when (tab) {
                                AccountDetailTab.TRANSACTIONS -> transactions.size
                                AccountDetailTab.BALANCE_HISTORY -> balanceRecords.size
                            }
                        }
                    )
                }

                item(key = "acct_detail_tab_body") {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = { fadeIn(OrbitSprings.FloatEffectsFast) togetherWith fadeOut(OrbitSprings.FloatEffectsFast) },
                        label = "accountDetailTabTransition"
                    ) { tab ->
                        when (tab) {
                            AccountDetailTab.TRANSACTIONS -> {
                                AccountRecentTransactionsSection(
                                    transactions = transactions,
                                    categoryMap = categoryMap,
                                    onSeeTransactions = onSeeTransactions
                                )
                            }
                            AccountDetailTab.BALANCE_HISTORY -> {
                                AccountInlineBalanceHistorySection(
                                    balanceRecords = balanceRecords,
                                    onAddRecord = onAddBalanceRecord
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
