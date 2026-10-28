package app.overbay.orbit.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.accounts.components.AccountDetailInspectorPane
import app.overbay.orbit.ui.accounts.components.AccountGroupSection
import app.overbay.orbit.ui.accounts.sheets.AccountBalanceHistorySheet
import app.overbay.orbit.ui.accounts.sheets.AccountEditorSheet
import app.overbay.orbit.ui.accounts.sheets.AccountMergeDialog
import app.overbay.orbit.ui.accounts.sheets.CreditCardProfileEditorSheet
import app.overbay.orbit.ui.components.HeroScrollState
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.OrbitTogglePill
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Editorial Accounts screen with curved green canopy hero, 2x2 bento stats grid,
 * 3-pill action row, collapsed-by-default white accordion account group cards,
 * and morphing dual-screen inspector pane.
 * Conforms strictly to <= 6 parameters and < 400-line ceiling.
 */
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    initialAccountId: Long? = null,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val snackbarHostState = remember { SnackbarHostState() }
    val heroScrollState = rememberHeroScrollState()

    var phoneViewingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(initialAccountId) {
        if (initialAccountId != null) {
            viewModel.selectAccount(initialAccountId)
            if (!posture.supportsTwoPane) {
                phoneViewingDetail = true
            }
        }
    }

    LaunchedEffect(state.syncErrorMessage) {
        state.syncErrorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (
            state.selectedAccount != null ||
                state.showEditorSheet ||
                state.showCardProfileSheet ||
                state.showHistorySheet
            )

        // Detail Screen on Phone if directly inspecting an account
        if (phoneViewingDetail && !isTwoPane && state.selectedAccount != null) {
            AccountDetailScreen(
                account = state.selectedAccount!!,
                transactions = state.selectedAccountTransactions,
                onBack = { phoneViewingDetail = false },
                onEditAccount = { viewModel.openEditAccount(state.selectedAccount!!) },
                onEditPerks = { viewModel.openCreditCardProfile(state.selectedAccount!!) },
                onMergeAccount = { viewModel.openMergeDialog(state.selectedAccount!!) }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                floatingActionButton = {
                    OrbitFloatingActionButton(
                        visible = heroScrollState.isFabVisible,
                        onClick = { viewModel.openAddAccount() },
                        contentDescription = "Add Account"
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(heroScrollState.nestedScrollConnection)
                ) {
                    MorphingPageHero(
                        kicker = "YOUR MONEY'S HOME BASE",
                        title = "Accounts",
                        subtitle = "${MoneyUtils.formatCents(state.netWorthCents)} Net Worth across ${state.accounts.size} Accounts",
                        scrollState = heroScrollState
                    )

                    val masterList: @Composable () -> Unit = {
                        AccountsMasterContent(
                            state = state,
                            viewModel = viewModel,
                            isWideScreen = isTwoPane,
                            onNavigate = onNavigate,
                            onOpenDetail = { acct ->
                                if (isTwoPane) {
                                    viewModel.selectAccountForInspect(acct.id)
                                } else {
                                    viewModel.selectAccount(acct.id)
                                    phoneViewingDetail = true
                                }
                            }
                        )
                    }

                    if (isTwoPane) {
                        OrbitMorphingSplitLayout(
                            isDetailOpen = isDetailOpen,
                            onDismissDetail = { viewModel.dismissInspectorPane() },
                            masterContent = masterList,
                            detailContent = {
                                AccountDetailInspectorPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onNavigate = onNavigate,
                                    onClosePane = { viewModel.dismissInspectorPane() }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        masterList()
                    }
                }
            }
        }

        // Compact Phone Modal Sheets & Dialogs
        if (!isTwoPane && state.showEditorSheet && state.editingAccount != null) {
            AccountEditorSheet(
                account = state.editingAccount!!,
                onSave = { viewModel.saveAccount(it) },
                onDelete = { viewModel.deleteAccount(it) },
                onDismiss = { viewModel.closeEditorSheet() }
            )
        }

        if (!isTwoPane && state.showCardProfileSheet && state.editingCardProfile != null) {
            CreditCardProfileEditorSheet(
                profile = state.editingCardProfile!!,
                onSave = { viewModel.saveCreditCardProfile(it) },
                onDismiss = { viewModel.closeCreditCardProfile() }
            )
        }

        if (!isTwoPane && state.showHistorySheet && state.historyAccount != null) {
            AccountBalanceHistorySheet(
                account = state.historyAccount!!,
                transactions = state.selectedAccountTransactions,
                onDismiss = { viewModel.closeBalanceHistory() }
            )
        }
    }

    if (state.showMergeDialog && state.mergingAccount != null) {
        AccountMergeDialog(
            sourceAccount = state.mergingAccount!!,
            availableAccounts = state.accounts,
            onMerge = { src, tgt, combine -> viewModel.mergeAccountInto(src, tgt, combine) },
            onDismiss = { viewModel.closeMergeDialog() }
        )
    }
}

@Composable
private fun AccountsMasterContent(
    state: AccountsUiState,
    viewModel: AccountsViewModel,
    isWideScreen: Boolean,
    onNavigate: (OrbitNavKey) -> Unit,
    onOpenDetail: (app.overbay.orbit.data.model.Account) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        // Secondary Action Row Below Hero
        OrbitPageActionRow {
            OrbitTogglePill(
                label = "Show archived",
                checked = state.showArchived,
                onCheckedChange = { viewModel.toggleShowArchived() }
            )
            OrbitSecondaryPillButton(
                text = if (state.isReorderMode) "Done" else "Reorder Groups",
                onClick = { viewModel.toggleReorderMode() }
            )
        }

        val startPad = if (isWideScreen) 20.dp else 16.dp
        val endPad = if (isWideScreen) 0.dp else 16.dp

        // Account Groups List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = startPad, end = endPad, top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(state.accountGroups, key = { it.category.name }) { group ->
                AccountGroupSection(
                    group = group,
                    selectedAccountId = if (isWideScreen) state.selectedAccountId else null,
                    onInspectAccount = { acct -> onOpenDetail(acct) },
                    onSeeTransactions = { acct ->
                        onNavigate(OrbitNavKey.Transactions(accountId = acct.id))
                    },
                    onEditAccount = { viewModel.openEditAccount(it) },
                    onPerksClick = { viewModel.openCreditCardProfile(it) },
                    onHistoryClick = { viewModel.openBalanceHistory(it) },
                    onMergeClick = { viewModel.openMergeDialog(it) },
                    onToggleArchive = { viewModel.toggleArchiveAccount(it) },
                    onDeleteAccount = { viewModel.deleteAccount(it) }
                )
            }
        }
    }
}
