package app.overbay.orbit.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.accounts.components.AccountGroupSection
import app.overbay.orbit.ui.accounts.sheets.AccountBalanceHistorySheet
import app.overbay.orbit.ui.accounts.sheets.AccountEditorSheet
import app.overbay.orbit.ui.accounts.sheets.AccountMergeDialog
import app.overbay.orbit.ui.accounts.sheets.CreditCardProfileEditorSheet
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics

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
    val haptics = LocalOrbitHaptics.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Selected account on phone navigation vs tablet 2-pane
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

    // Detail Screen on Phone
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
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        haptics.tick()
                        viewModel.openAddAccount()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Account")
                }
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
                    // Left Pane: Accounts List
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AccountsMasterList(
                            state = state,
                            onAccountClick = { acct ->
                                viewModel.selectAccount(acct.id)
                            },
                            onEditAccount = { viewModel.openEditAccount(it) },
                            onPerksClick = { viewModel.openCreditCardProfile(it) },
                            onSync = { viewModel.syncSimpleFin() },
                            onOpenDrawer = onOpenDrawer
                        )
                    }

                    // Divider
                    Surface(
                        modifier = Modifier.width(1.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ) {}

                    // Right Pane: Detail
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
                AccountsMasterList(
                    state = state,
                    onAccountClick = { acct ->
                        viewModel.selectAccount(acct.id)
                        phoneViewingDetail = true
                    },
                    onEditAccount = { viewModel.openEditAccount(it) },
                    onPerksClick = { viewModel.openCreditCardProfile(it) },
                    onSync = { viewModel.syncSimpleFin() },
                    onOpenDrawer = onOpenDrawer,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
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
private fun AccountsMasterList(
    state: AccountsUiState,
    onAccountClick: (app.overbay.orbit.data.model.Account) -> Unit,
    onEditAccount: (app.overbay.orbit.data.model.Account) -> Unit,
    onPerksClick: (app.overbay.orbit.data.model.Account) -> Unit,
    onSync: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Bar & Sync Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = onSync,
                    enabled = !state.isSyncing
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync SimpleFin", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Summary Hero Card (Total Assets, Liabilities, Net Worth)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(MoneyUtils.formatCents(state.totalAssetsCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrbitColorTokens.Green)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Liabilities", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(MoneyUtils.formatCents(state.totalLiabilitiesCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrbitColorTokens.Red)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Net Worth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(2.dp))
                        val netColor = if (state.netWorthCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
                        Text(MoneyUtils.formatCents(state.netWorthCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = netColor)
                    }
                }
            }
        }

        // Grouped Account Sections
        items(state.accountGroups, key = { it.category.name }) { group ->
            AccountGroupSection(
                group = group,
                selectedAccountId = state.selectedAccountId,
                onAccountClick = onAccountClick,
                onEditAccount = onEditAccount,
                onPerksClick = onPerksClick
            )
        }
    }
}
