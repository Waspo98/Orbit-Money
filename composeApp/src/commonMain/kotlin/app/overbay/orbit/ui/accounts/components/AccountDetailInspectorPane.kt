package app.overbay.orbit.ui.accounts.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.accounts.sheets.AccountBalanceHistoryContent
import app.overbay.orbit.ui.accounts.sheets.AccountEditorContent
import app.overbay.orbit.ui.accounts.cards.CardEditorSheetContent
import app.overbay.orbit.ui.accounts.cards.CreditCardInspectorDetailContent
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class AccountInspectorSubMode { DETAIL, EDIT, PERKS, HISTORY }

/**
 * Right-hand morphing inspector pane for Accounts on unfolded phones, tablets, and desktops.
 * Shows the read-only Account Detail view by default when an account row is tapped, and morphs
 * smoothly into the Edit, Credit Card Perks, or Balance History views when triggered.
 *
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
                        transactions = state.selectedAccountTransactions,
                        balanceRecords = state.historyBalanceRecords,
                        onAddRecord = { viewModel.openAddBalanceRecord(histAcct) },
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.closeBalanceHistory() else onClosePane()
                        }
                    )
                }
            }
            AccountInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    if (inspecting.type == AccountType.CREDIT) {
                        CreditCardInspectorDetailContent(
                            account = inspecting,
                            transactions = state.selectedAccountTransactions,
                            householdMembers = state.householdMembers,
                            rewardsToolsEnabled = state.rewardsToolsEnabled,
                            onEditCard = { viewModel.openCreditCardProfile(inspecting) },
                            onToggleBenefit = { bId -> viewModel.toggleCardBenefit(inspecting.id, bId) },
                            onOpenPerkUsage = { bId -> viewModel.openPerkUsage(inspecting.id, bId) },
                            onSeeFullTransactions = { onNavigate(OrbitNavKey.Transactions(accountId = inspecting.id)) },
                            onClosePane = onClosePane
                        )
                    } else {
                        AccountReadOnlyDetailContent(
                            account = inspecting,
                            transactions = state.selectedAccountTransactions,
                            hasClosureAlert = inspecting.id in state.closureCandidateAccountIds,
                            onReviewClosure = { viewModel.closurePrompts.openForAccount(inspecting.id) },
                            onClose = onClosePane,
                            onEdit = { viewModel.openEditAccount(inspecting) },
                            onFindLogo = { viewModel.accountLogos.open(inspecting) },
                            onUseAccountIcon = { viewModel.accountLogos.resetToAccountIconAsync(inspecting.id) },
                            onReportLogoStatus = { k, s -> viewModel.accountLogos.reportStatus(k, s) },
                            onHistory = { viewModel.openBalanceHistory(inspecting) },
                            onSnapshotReminder = { viewModel.requestSnapshotReminder(onNavigate) },
                            onMerge = { viewModel.openMergeDialog(inspecting) },
                            onToggleArchive = { viewModel.toggleArchiveAccount(inspecting) },
                            onDelete = { viewModel.requestDeleteAccount(inspecting.id) },
                            onUnlinkSimpleFin = if (!inspecting.simplefinAccountId.isNullOrBlank()) {
                                { viewModel.unlinkSimpleFin(inspecting.id) }
                            } else null,
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
    hasClosureAlert: Boolean = false,
    onReviewClosure: (() -> Unit)? = null,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onFindLogo: () -> Unit,
    onUseAccountIcon: () -> Unit,
    onReportLogoStatus: (String, String) -> Unit,
    onHistory: () -> Unit,
    onSnapshotReminder: () -> Unit,
    onMerge: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    onSeeTransactions: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val isLiability = account.type.isLiability
    val headlineCents = if (account.isProperty) {
        account.estimatedValueCents ?: account.currentBalanceCents
    } else {
        account.currentBalanceCents
    }
    val balanceColor = when {
        account.isProperty -> colors.income
        isLiability -> if (account.currentBalanceCents != 0L) colors.expense else MaterialTheme.colorScheme.onSurface
        headlineCents >= 0L -> colors.income
        else -> colors.expense
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AccountInstitutionMark(
                    account = account,
                    size = 48.dp,
                    cornerRadius = 12.dp,
                    logoPadding = 5.dp,
                    onFindLogo = onFindLogo,
                    onUseAccountIcon = onUseAccountIcon,
                    onReportLogoStatus = onReportLogoStatus
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        OrbitBadge(
                            text = if (account.isProperty) "Property" else account.type.displayName,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = MoneyUtils.formatCents(headlineCents),
                        style = TabularNumStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        color = balanceColor
                    )
                    val metaParts = listOfNotNull(
                        account.institution?.takeIf { it.isNotBlank() },
                        account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                        if (account.isProperty) "Estimated Property Value"
                        else if (account.isManual) "Manual Account" else "Synced Account"
                    )
                    if (metaParts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = metaParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { haptics.tick(); onClose() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (account.type == AccountType.MORTGAGE && account.homeEquityCents != null) {
            item {
                Text(
                    text = "Home Equity: ${MoneyUtils.formatCents(account.homeEquityCents!!)} (Est. Property ${MoneyUtils.formatCents(account.estimatedValueCents ?: 0L)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.income,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(10.dp))
            AccountInspectorActionsSection(
                account = account,
                onEdit = onEdit,
                onHistory = onHistory,
                onSnapshotReminder = onSnapshotReminder,
                onMerge = onMerge,
                onSeeTransactions = onSeeTransactions,
                onToggleArchive = onToggleArchive,
                onDelete = onDelete,
                onUnlinkSimpleFin = onUnlinkSimpleFin,
                hasClosureAlert = hasClosureAlert,
                onReviewClosure = onReviewClosure
            )
        }

        item {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "RECENT TRANSACTIONS (${transactions.size})",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions recorded for this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(transactions.take(15), key = { it.id }) { txn ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OrbitCompactListRow(
                        title = txn.effectiveMerchant,
                        subtitle = txn.effectiveDate,
                        amount = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                        amountColor = if (txn.effectiveAmountCents > 0L) colors.income else MaterialTheme.colorScheme.onSurface,
                        showTopDivider = false,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
