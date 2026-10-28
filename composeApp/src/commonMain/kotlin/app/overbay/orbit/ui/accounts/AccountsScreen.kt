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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.accounts.components.AccountDetailInspectorPane
import app.overbay.orbit.ui.accounts.components.AccountGroupSection
import app.overbay.orbit.ui.accounts.sheets.AccountMergeDialog
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.OrbitTogglePill
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Editorial Accounts screen with curved green canopy hero, 2x2 bento stats grid,
 * 3-pill action row, collapsed-by-default white accordion account group cards,
 * and morphing dual-screen inspector pane / phone OrbitModalSheet.
 * Conforms strictly to <= 6 parameters and < 400-line ceiling.
 */
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    initialAccountId: Long? = null,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val snackbarHostState = remember { SnackbarHostState() }
    val heroScrollState = rememberHeroScrollState()

    LaunchedEffect(initialAccountId) {
        if (initialAccountId != null) {
            viewModel.openAccountDeepLink(initialAccountId)
        }
    }

    LaunchedEffect(state.syncErrorMessage) {
        state.syncErrorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isInspectorActive = state.selectedAccount != null ||
            state.showEditorSheet ||
            state.showCardProfileSheet ||
            state.showHistorySheet
        val isDetailOpen = isTwoPane && isInspectorActive

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
                            viewModel.selectAccountForInspect(acct.id)
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

        // Compact Phone Modal Sheet hosting AccountDetailInspectorPane
        if (!isTwoPane && isInspectorActive) {
            OrbitModalSheet(
                onDismissRequest = { viewModel.dismissInspectorPane() }
            ) {
                AccountDetailInspectorPane(
                    state = state,
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onClosePane = { viewModel.dismissInspectorPane() }
                )
            }
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

    if (state.deleteConfirmAccountId != null) {
        val delId = state.deleteConfirmAccountId!!
        OrbitConfirmationDialog(
            title = "Delete Account?",
            message = "Are you sure you want to permanently delete this account?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { viewModel.deleteAccount(delId) },
            onDismiss = { viewModel.dismissDeleteAccount() }
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
                label = "Show Archived",
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
            itemsIndexed(state.accountGroups, key = { _, it -> it.category.name }) { index, group ->
                AccountGroupSection(
                    group = group,
                    selectedAccountId = state.selectedAccountId,
                    onInspectAccount = { acct -> onOpenDetail(acct) },
                    onSeeTransactions = { acct ->
                        onNavigate(OrbitNavKey.Transactions(accountId = acct.id))
                    },
                    onEditAccount = { viewModel.openEditAccount(it) },
                    onPerksClick = { viewModel.openCreditCardProfile(it) },
                    onHistoryClick = { viewModel.openBalanceHistory(it) },
                    onMergeClick = { viewModel.openMergeDialog(it) },
                    onToggleArchive = { viewModel.toggleArchiveAccount(it) },
                    onDeleteAccount = { viewModel.requestDeleteAccount(it.id) },
                    isReorderMode = state.isReorderMode,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.accountGroups.lastIndex,
                    onMoveUp = { viewModel.moveGroup(group.category, -1) },
                    onMoveDown = { viewModel.moveGroup(group.category, 1) }
                )
            }
        }
    }
}
