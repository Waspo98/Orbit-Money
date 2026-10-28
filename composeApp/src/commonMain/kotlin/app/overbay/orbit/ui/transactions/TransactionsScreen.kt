package app.overbay.orbit.ui.transactions

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.ui.components.FilterBottomSheet
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.transactions.components.TransactionBatchActionBar
import app.overbay.orbit.ui.transactions.components.TransactionDetailInspectorPane
import app.overbay.orbit.ui.transactions.components.TransactionsListPane
import app.overbay.orbit.ui.transactions.sheets.CsvImportSheet
import app.overbay.orbit.ui.transactions.sheets.MerchantLogoFinderDialog
import app.overbay.orbit.ui.transactions.sheets.SplitTransactionSheet
import app.overbay.orbit.ui.transactions.sheets.TransactionEditorSheet
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val haptics = LocalOrbitHaptics.current

    BackHandler(enabled = state.isSelectionMode) {
        haptics.tick()
        viewModel.clearSelection()
    }

    BackHandler(enabled = state.isSearchActive) {
        haptics.tick()
        viewModel.setSearchActive(false)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (
            state.inspectingTransaction != null ||
                state.showEditorSheet ||
                (state.showSplitSheet && state.inspectingTransaction != null)
            )

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (state.isSelectionMode) {
                    TransactionBatchActionBar(
                        selectedCount = state.selectedCount,
                        totalCount = state.filteredTransactions.size,
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onCategorize = { viewModel.setShowCategoryBatchPicker(true) },
                        onMarkTransfer = { viewModel.batchMarkTransfer(true) },
                        onToggleMha = { viewModel.batchToggleMha() },
                        onDelete = { viewModel.setShowBatchDeleteConfirm(true) }
                    )
                }
            }
        ) { paddingValues ->
            TransactionsListPane(
                state = state,
                viewModel = viewModel,
                onOpenDrawer = onOpenDrawer,
                onSelectForInspect = { id ->
                    if (isTwoPane) {
                        viewModel.selectTransactionForInspect(id)
                    } else {
                        viewModel.toggleExpandTransaction(id)
                    }
                },
                isWideScreen = isTwoPane,
                isDetailOpen = isDetailOpen,
                onDismissDetail = { viewModel.dismissInspectorPane() },
                detailPane = if (isTwoPane) {
                    {
                        TransactionDetailInspectorPane(
                            state = state,
                            viewModel = viewModel,
                            onNavigate = onNavigate,
                            onRequestDelete = { id -> viewModel.setSingleDeleteConfirmId(id) },
                            onClosePane = { viewModel.dismissInspectorPane() }
                        )
                    }
                } else null,
                modifier = Modifier.padding(paddingValues)
            )
        }

        // Compact Phone Modal Sheets (on wide screens these open inside the right morphing pane)
        if (!isTwoPane && state.showEditorSheet && state.inspectingTransactionId != null) {
            TransactionEditorSheet(
                transaction = state.inspectingTransaction,
                categories = state.categories,
                accounts = state.accounts,
                onSave = { id, merchant, amount, date, catId, notes, isTrans, isIgn, mha, acctId ->
                    viewModel.saveTransaction(id, merchant, amount, date, catId, notes, isTrans, isIgn, mha, acctId)
                },
                onResetToOriginal = { id -> viewModel.resetTransaction(id) },
                onDelete = { id -> viewModel.setSingleDeleteConfirmId(id) },
                onSplit = { id -> viewModel.openSplitSheet(id) },
                onDismiss = { viewModel.closeTransactionEditor() },
                onCreateRule = { txn ->
                    viewModel.createRuleFromTransaction(txn)
                    viewModel.closeTransactionEditor()
                    onNavigate(OrbitNavKey.Rules)
                },
                onMarkRecurring = { txn ->
                    viewModel.markTransactionRecurring(txn)
                    viewModel.closeTransactionEditor()
                    onNavigate(OrbitNavKey.Upcoming)
                }
            )
        }

        if (!isTwoPane && state.showEditorSheet && state.inspectingTransactionId == null) {
            TransactionEditorSheet(
                transaction = null,
                categories = state.categories,
                accounts = state.accounts,
                onSave = { id, merchant, amount, date, catId, notes, isTrans, isIgn, mha, acctId ->
                    viewModel.saveTransaction(id, merchant, amount, date, catId, notes, isTrans, isIgn, mha, acctId)
                },
                onResetToOriginal = {},
                onDelete = {},
                onSplit = {},
                onDismiss = { viewModel.closeTransactionEditor() },
                onImportCsv = {
                    viewModel.closeTransactionEditor()
                    viewModel.openCsvImportSheet()
                }
            )
        }

        if (!isTwoPane && state.showSplitSheet && state.inspectingTransaction != null) {
            SplitTransactionSheet(
                transaction = state.inspectingTransaction!!,
                categories = state.categories,
                onSplitConfirmed = { parentId, splits ->
                    viewModel.splitTransaction(parentId, splits)
                },
                onDismiss = { viewModel.closeSplitSheet() }
            )
        }
    }

    if (state.showCsvImportSheet) {
        CsvImportSheet(
            accounts = state.accounts,
            categories = state.categories,
            existingTransactions = state.transactions,
            onImportConfirmed = { txns, rules ->
                viewModel.importCsvTransactions(txns, rules)
            },
            onDismiss = { viewModel.closeCsvImportSheet() }
        )
    }

    if (state.showFilterSheet) {
        FilterBottomSheet(
            initialFilter = state.filterDraft,
            onApply = { draft -> viewModel.applyFilterDraft(draft) },
            onDismiss = { viewModel.closeFilterSheet() },
            accountNames = state.accounts.associate { it.id to it.name },
            categoryNames = state.categories.associate { it.id to it.labelWithEmoji }
        )
    }

    val logoFinderTxn = state.logoFinderTransaction
    if (logoFinderTxn != null) {
        MerchantLogoFinderDialog(
            transaction = logoFinderTxn,
            onSearchBrands = { query -> viewModel.searchMerchantLogos(logoFinderTxn.id, query) },
            onSaveLogoUrl = { url -> viewModel.saveMerchantLogoOverride(logoFinderTxn.id, url) },
            onUseCategoryIcon = { viewModel.useCategoryIconSuspend(logoFinderTxn.id) },
            onDismiss = { viewModel.closeLogoFinder() }
        )
    }

    if (state.showBatchDeleteConfirm) {
        OrbitConfirmationDialog(
            title = "Delete ${state.selectedCount} Transactions?",
            message = "Are you sure you want to delete these transactions? This action cannot be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.batchDelete()
                viewModel.setShowBatchDeleteConfirm(false)
            },
            onDismiss = { viewModel.setShowBatchDeleteConfirm(false) }
        )
    }

    if (state.singleDeleteConfirmId != null) {
        val deleteId = state.singleDeleteConfirmId!!
        OrbitConfirmationDialog(
            title = "Delete Transaction?",
            message = "Are you sure you want to permanently delete this transaction?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTransaction(deleteId)
                viewModel.setSingleDeleteConfirmId(null)
            },
            onDismiss = { viewModel.setSingleDeleteConfirmId(null) }
        )
    }

    if (state.showCategoryBatchPicker) {
        DropdownMenu(
            expanded = state.showCategoryBatchPicker,
            onDismissRequest = { viewModel.setShowCategoryBatchPicker(false) }
        ) {
            state.categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.labelWithEmoji) },
                    onClick = {
                        viewModel.batchCategorize(cat.id)
                        viewModel.setShowCategoryBatchPicker(false)
                    }
                )
            }
        }
    }
}
