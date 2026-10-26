package app.overbay.orbit.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.overbay.orbit.ui.accounts.components.AccountGroupSection
import app.overbay.orbit.ui.accounts.sheets.AccountBalanceHistorySheet
import app.overbay.orbit.ui.accounts.sheets.AccountEditorSheet
import app.overbay.orbit.ui.accounts.sheets.AccountMergeDialog
import app.overbay.orbit.ui.accounts.sheets.CreditCardProfileEditorSheet
import app.overbay.orbit.ui.components.HeroScrollState
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.OrbitTogglePill
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Editorial Accounts screen with curved green canopy hero, 2x2 bento stats grid,
 * 3-pill action row, and collapsed-by-default white accordion account group cards.
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

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // Detail Screen on Phone if directly inspecting an account
    if (phoneViewingDetail && !posture.supportsTwoPane && state.selectedAccount != null) {
        AccountDetailScreen(
            account = state.selectedAccount!!,
            transactions = state.selectedAccountTransactions,
            onBack = { phoneViewingDetail = false },
            onEditAccount = { viewModel.openEditAccount(state.selectedAccount!!) },
            onEditPerks = { viewModel.openCreditCardProfile(state.selectedAccount!!) },
            onMergeAccount = { viewModel.openMergeDialog(state.selectedAccount!!) },
            modifier = modifier
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
            },
            modifier = modifier
        ) { innerPadding ->
            if (posture.supportsTwoPane) {
                // Wide Screen / Foldable 2-Pane Master-Detail
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AccountsMasterContent(
                            state = state,
                            viewModel = viewModel,
                            heroScrollState = heroScrollState,
                            onNavigate = onNavigate,
                            onOpenDetail = { acct ->
                                viewModel.selectAccount(acct.id)
                            }
                        )
                    }

                    Surface(
                        modifier = Modifier.width(1.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ) {}

                    Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                        val sel = state.selectedAccount
                        if (sel != null) {
                            AccountDetailScreen(
                                account = sel,
                                transactions = state.selectedAccountTransactions,
                                onBack = null,
                                onEditAccount = { viewModel.openEditAccount(sel) },
                                onEditPerks = { viewModel.openCreditCardProfile(sel) },
                                onMergeAccount = { viewModel.openMergeDialog(sel) }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Select an account to view details",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            } else {
                // Single Pane Phone Layout
                AccountsMasterContent(
                    state = state,
                    viewModel = viewModel,
                    heroScrollState = heroScrollState,
                    onNavigate = onNavigate,
                    onOpenDetail = { acct ->
                        viewModel.selectAccount(acct.id)
                        phoneViewingDetail = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    // Modal Sheets & Dialogs
    if (state.showEditorSheet && state.editingAccount != null) {
        AccountEditorSheet(
            account = state.editingAccount!!,
            onSave = { viewModel.saveAccount(it) },
            onDelete = { viewModel.deleteAccount(it) },
            onDismiss = { viewModel.closeEditorSheet() }
        )
    }

    if (state.showCardProfileSheet && state.editingCardProfile != null) {
        CreditCardProfileEditorSheet(
            profile = state.editingCardProfile!!,
            onSave = { viewModel.saveCreditCardProfile(it) },
            onDismiss = { viewModel.closeCreditCardProfile() }
        )
    }

    if (state.showMergeDialog && state.mergingAccount != null) {
        AccountMergeDialog(
            sourceAccount = state.mergingAccount!!,
            availableAccounts = state.accounts,
            onMerge = { src, tgt, combine -> viewModel.mergeAccountInto(src, tgt, combine) },
            onDismiss = { viewModel.closeMergeDialog() }
        )
    }

    if (state.showHistorySheet && state.historyAccount != null) {
        AccountBalanceHistorySheet(
            account = state.historyAccount!!,
            transactions = state.selectedAccountTransactions,
            onDismiss = { viewModel.closeBalanceHistory() }
        )
    }
}

@Composable
private fun AccountsMasterContent(
    state: AccountsUiState,
    viewModel: AccountsViewModel,
    heroScrollState: HeroScrollState,
    onNavigate: (OrbitNavKey) -> Unit,
    onOpenDetail: (app.overbay.orbit.data.model.Account) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(heroScrollState.nestedScrollConnection)
    ) {
        MorphingPageHero(
            kicker = "YOUR MONEY'S HOME BASE",
            title = "Accounts",
            subtitle = "${MoneyUtils.formatCents(state.netWorthCents)} Net Worth across ${state.accounts.size} Accounts",
            scrollState = heroScrollState
        )

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

        // Account Groups List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(state.accountGroups, key = { it.category.name }) { group ->
                AccountGroupSection(
                    group = group,
                    selectedAccountId = state.selectedAccountId,
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
