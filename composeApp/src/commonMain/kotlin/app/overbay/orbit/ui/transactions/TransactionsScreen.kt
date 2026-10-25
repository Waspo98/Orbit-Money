package app.overbay.orbit.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.FilterBottomSheet
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.transactions.components.TransactionBatchActionBar
import app.overbay.orbit.ui.transactions.components.TransactionsListPane
import app.overbay.orbit.ui.transactions.sheets.CsvImportSheet
import app.overbay.orbit.ui.transactions.sheets.SplitTransactionSheet
import app.overbay.orbit.ui.transactions.sheets.TransactionEditorContent
import app.overbay.orbit.ui.transactions.sheets.TransactionEditorSheet
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

    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showSingleDeleteConfirmId by remember { mutableStateOf<Long?>(null) }
    var showCategoryBatchPicker by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 840.dp || posture.supportsTwoPane

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        haptics.tick()
                        viewModel.openTransactionEditor(null)
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
                }
            },
            bottomBar = {
                if (state.isSelectionMode) {
                    TransactionBatchActionBar(
                        selectedCount = state.selectedCount,
                        totalCount = state.filteredTransactions.size,
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onCategorize = { showCategoryBatchPicker = true },
                        onMarkTransfer = { viewModel.batchMarkTransfer(true) },
                        onToggleMha = { viewModel.batchToggleMha() },
                        onDelete = { showBatchDeleteConfirm = true }
                    )
                }
            }
        ) { paddingValues ->
            if (isTwoPane) {
                // Two-Pane Responsive Layout (Foldable / Tablet / Desktop)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Left Pane: Master List
                    Box(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                        TransactionsListPane(
                            state = state,
                            viewModel = viewModel,
                            onOpenDrawer = onOpenDrawer,
                            onOpenCsv = { viewModel.openCsvImportSheet() },
                            onSelectForInspect = { id -> viewModel.selectTransactionForInspect(id) }
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Right Pane: Detail Inspector
                    Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                        val inspecting = state.inspectingTransaction
                        if (inspecting != null) {
                            TransactionEditorContent(
                                transaction = inspecting,
                                categories = state.categories,
                                accounts = state.accounts,
                                onSave = { id, merchant, amount, date, catId, notes, isTrans, isIgn, mha ->
                                    viewModel.saveTransaction(id, merchant, amount, date, catId, notes, isTrans, isIgn, mha)
                                },
                                onResetToOriginal = { id -> viewModel.resetTransaction(id) },
                                onDelete = { id -> showSingleDeleteConfirmId = id },
                                onSplit = { id -> viewModel.openSplitSheet(id) },
                                onDismiss = { viewModel.selectTransactionForInspect(null) }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateCard(
                                    title = "Transaction Details",
                                    description = "Select a transaction from the list to view provenance, edit, or split."
                                )
                            }
                        }
                    }
                }
            } else {
                // Single-Pane Phone Layout
                TransactionsListPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenDrawer = onOpenDrawer,
                    onOpenCsv = { viewModel.openCsvImportSheet() },
                    onSelectForInspect = { id -> viewModel.openTransactionEditor(id) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // Modal Sheets and Dialogs
    if (state.showEditorSheet && state.inspectingTransactionId != null) {
        TransactionEditorSheet(
            transaction = state.inspectingTransaction,
            categories = state.categories,
            accounts = state.accounts,
            onSave = { id, merchant, amount, date, catId, notes, isTrans, isIgn, mha ->
                viewModel.saveTransaction(id, merchant, amount, date, catId, notes, isTrans, isIgn, mha)
            },
            onResetToOriginal = { id -> viewModel.resetTransaction(id) },
            onDelete = { id -> showSingleDeleteConfirmId = id },
            onSplit = { id -> viewModel.openSplitSheet(id) },
            onDismiss = { viewModel.closeTransactionEditor() }
        )
    }

    if (state.showEditorSheet && state.inspectingTransactionId == null) {
        TransactionEditorSheet(
            transaction = null,
            categories = state.categories,
            accounts = state.accounts,
            onSave = { id, merchant, amount, date, catId, notes, isTrans, isIgn, mha ->
                viewModel.saveTransaction(id, merchant, amount, date, catId, notes, isTrans, isIgn, mha)
            },
            onResetToOriginal = {},
            onDelete = {},
            onSplit = {},
            onDismiss = { viewModel.closeTransactionEditor() }
        )
    }

    if (state.showSplitSheet && state.inspectingTransaction != null) {
        SplitTransactionSheet(
            transaction = state.inspectingTransaction!!,
            categories = state.categories,
            onSplitConfirmed = { parentId, splits ->
                viewModel.splitTransaction(parentId, splits)
            },
            onDismiss = { viewModel.closeSplitSheet() }
        )
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
            categoryNames = state.categories.associate { it.id to it.name }
        )
    }

    if (showBatchDeleteConfirm) {
        OrbitConfirmationDialog(
            title = "Delete ${state.selectedCount} Transactions?",
            message = "Are you sure you want to delete these transactions? This action cannot be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.batchDelete()
                showBatchDeleteConfirm = false
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }

    if (showSingleDeleteConfirmId != null) {
        OrbitConfirmationDialog(
            title = "Delete Transaction?",
            message = "Are you sure you want to permanently delete this transaction?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTransaction(showSingleDeleteConfirmId!!)
                showSingleDeleteConfirmId = null
            },
            onDismiss = { showSingleDeleteConfirmId = null }
        )
    }

    if (showCategoryBatchPicker) {
        DropdownMenu(
            expanded = showCategoryBatchPicker,
            onDismissRequest = { showCategoryBatchPicker = false }
        ) {
            state.categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        viewModel.batchCategorize(cat.id)
                        showCategoryBatchPicker = false
                    }
                )
            }
        }
    }
}
