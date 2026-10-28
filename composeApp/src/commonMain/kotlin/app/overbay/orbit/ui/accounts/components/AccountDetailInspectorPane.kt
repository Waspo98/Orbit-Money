package app.overbay.orbit.ui.accounts.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.overbay.orbit.ui.accounts.sheets.CreditCardProfileEditorContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

private enum class AccountInspectorSubMode { DETAIL, EDIT, PERKS, HISTORY }

/**
 * Right-hand morphing inspector pane for Accounts on unfolded phones, tablets, and desktops.
 * Shows the read-only Account Detail view by default when an account row is tapped, and morphs
 * smoothly into the Edit, Credit Card Perks, or Balance History views when triggered.
 * Strictly under the 400-line ceiling.
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

    val subMode = when {
        state.showEditorSheet && state.editingAccount != null -> AccountInspectorSubMode.EDIT
        state.showCardProfileSheet && state.editingCardProfile != null -> AccountInspectorSubMode.PERKS
        state.showHistorySheet && state.historyAccount != null -> AccountInspectorSubMode.HISTORY
        else -> AccountInspectorSubMode.DETAIL
    }

    val canBackFromSubMode = (subMode != AccountInspectorSubMode.DETAIL && state.selectedAccount != null)
    BackHandler(enabled = canBackFromSubMode) {
        haptics.tick()
        when (subMode) {
            AccountInspectorSubMode.EDIT -> viewModel.closeEditorSheet()
            AccountInspectorSubMode.PERKS -> viewModel.closeCreditCardProfile()
            AccountInspectorSubMode.HISTORY -> viewModel.closeBalanceHistory()
            else -> {}
        }
    }

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxSize(),
        label = "accountInspectorSubModeTransition"
    ) { mode ->
        when (mode) {
            AccountInspectorSubMode.EDIT -> {
                val editTarget = state.editingAccount ?: inspecting
                if (editTarget != null) {
                    AccountEditorContent(
                        account = editTarget,
                        onSave = { viewModel.saveAccount(it) },
                        onDelete = { viewModel.requestDeleteAccount(it) },
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.closeEditorSheet() else onClosePane()
                        }
                    )
                }
            }
            AccountInspectorSubMode.PERKS -> {
                val profile = state.editingCardProfile
                if (profile != null) {
                    CreditCardProfileEditorContent(
                        profile = profile,
                        onSave = { viewModel.saveCreditCardProfile(it) },
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.closeCreditCardProfile() else onClosePane()
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
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.closeBalanceHistory() else onClosePane()
                        }
                    )
                }
            }
            AccountInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    AccountReadOnlyDetailContent(
                        account = inspecting,
                        transactions = state.selectedAccountTransactions,
                        onClose = onClosePane,
                        onEdit = { viewModel.openEditAccount(inspecting) },
                        onPerks = { viewModel.openCreditCardProfile(inspecting) },
                        onHistory = { viewModel.openBalanceHistory(inspecting) },
                        onMerge = { viewModel.openMergeDialog(inspecting) },
                        onToggleArchive = { viewModel.toggleArchiveAccount(inspecting) },
                        onDelete = { viewModel.deleteAccount(inspecting) },
                        onSeeTransactions = { onNavigate(OrbitNavKey.Transactions(accountId = inspecting.id)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountReadOnlyDetailContent(
    account: Account,
    transactions: List<Transaction>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onPerks: () -> Unit,
    onHistory: () -> Unit,
    onMerge: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onSeeTransactions: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val isCredit = account.type == AccountType.CREDIT
    val isLiability = account.type.isLiability
    val balanceColor = when {
        isCredit || isLiability -> if (account.currentBalanceCents != 0L) colors.expense else MaterialTheme.colorScheme.onSurface
        account.currentBalanceCents >= 0L -> colors.income
        else -> colors.expense
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = account.type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = MoneyUtils.formatCents(account.currentBalanceCents),
                        style = TabularNumStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        color = balanceColor
                    )
                    val metaParts = listOfNotNull(
                        account.institution?.takeIf { it.isNotBlank() },
                        account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                        if (account.isManual) "Manual Account" else "Synced Account"
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

        if (isCredit && account.creditCardProfile?.creditLimitCents != null) {
            item {
                val limit = account.creditCardProfile.creditLimitCents
                val ratio = account.creditCardProfile.creditUtilizationRatio(account.currentBalanceCents)
                val pct = if (ratio != null) (ratio * 100.0).roundToInt() else 0
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Credit Utilization: $pct%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pct > 30) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Limit ${MoneyUtils.formatCents(limit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = if (pct > 30) colors.danger else MaterialTheme.colorScheme.primary
                    )
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
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccountPillAction(label = "Edit", onClick = onEdit)
                if (isCredit) {
                    AccountPillAction(label = "Card Perks", onClick = onPerks)
                }
                AccountPillAction(label = "Balance History", onClick = onHistory)
                AccountPillAction(label = "Merge", onClick = onMerge)
                AccountPillAction(label = "See Transactions", onClick = onSeeTransactions)
                AccountPillAction(label = if (account.isArchived) "Unarchive" else "Archive", onClick = onToggleArchive)
                AccountPillAction(label = "Delete", isDestructive = true, onClick = onDelete)
            }
        }

        if (isCredit && account.creditCardProfile != null) {
            item {
                CreditCardPerksPanel(profile = account.creditCardProfile, onEditPerks = onPerks)
            }
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = txn.effectiveMerchant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = txn.effectiveDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                            style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = if (txn.effectiveAmountCents > 0L) colors.income else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPillAction(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = { haptics.tick(); onClick() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (isDestructive) OrbitTheme.colors.danger else OrbitTheme.colors.cardBorder),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
        )
    }
}
