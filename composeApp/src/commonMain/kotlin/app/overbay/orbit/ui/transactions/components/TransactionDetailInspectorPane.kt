package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.transactions.TransactionsUiState
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.ui.transactions.sheets.SplitTransactionContent
import app.overbay.orbit.ui.transactions.sheets.TransactionEditorContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class InspectorSubMode { DETAIL, EDIT, SPLIT }

/**
 * Right-hand morphing inspector pane for Transactions on unfolded phones, tablets, and desktops.
 * Shows the read-only Transaction Detail view by default when a row is tapped, and morphs
 * smoothly into the Edit or Split form when those actions are triggered.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun TransactionDetailInspectorPane(
    state: TransactionsUiState,
    viewModel: TransactionsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val inspecting = state.inspectingTransaction

    val subMode = when {
        state.showSplitSheet && inspecting != null -> InspectorSubMode.SPLIT
        state.showEditorSheet -> InspectorSubMode.EDIT
        else -> InspectorSubMode.DETAIL
    }

    BackHandler(enabled = (subMode == InspectorSubMode.EDIT && inspecting != null) || subMode == InspectorSubMode.SPLIT) {
        haptics.tick()
        if (subMode == InspectorSubMode.SPLIT) viewModel.closeSplitSheet() else viewModel.closeTransactionEditor()
    }

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxSize(),
        label = "transactionInspectorSubModeTransition"
    ) { mode ->
        when (mode) {
            InspectorSubMode.SPLIT -> {
                if (inspecting != null) {
                    SplitTransactionContent(
                        transaction = inspecting,
                        categories = state.categories,
                        onSplitConfirmed = { parentId, splits -> viewModel.splitTransaction(parentId, splits) },
                        onDismiss = { viewModel.closeSplitSheet() }
                    )
                }
            }
            InspectorSubMode.EDIT -> {
                TransactionEditorContent(
                    transaction = inspecting,
                    categories = state.categories,
                    accounts = state.accounts,
                    onSave = { id, merchant, amount, date, catId, notes, isTrans, isIgn, mha ->
                        viewModel.saveTransaction(id, merchant, amount, date, catId, notes, isTrans, isIgn, mha)
                    },
                    onResetToOriginal = { id -> viewModel.resetTransaction(id) },
                    onDelete = { id -> onRequestDelete(id) },
                    onSplit = { id ->
                        viewModel.closeTransactionEditor()
                        viewModel.openSplitSheet(id)
                    },
                    onDismiss = { if (inspecting != null) viewModel.closeTransactionEditor() else onClosePane() },
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
            InspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    TransactionReadOnlyDetailContent(
                        transaction = inspecting,
                        categoryName = state.categoryMap[inspecting.effectiveCategoryId]?.name,
                        categoryIcon = state.categoryMap[inspecting.effectiveCategoryId]?.icon,
                        categoryColorHex = state.categoryMap[inspecting.effectiveCategoryId]?.color,
                        accountName = state.accountMap[inspecting.accountId]?.name ?: inspecting.accountName,
                        onClose = onClosePane,
                        onEdit = { viewModel.openTransactionEditor(inspecting.id) },
                        onSplit = { viewModel.openSplitSheet(inspecting.id) },
                        onCreateRule = {
                            viewModel.createRuleFromTransaction(inspecting)
                            onNavigate(OrbitNavKey.Rules)
                        },
                        onMarkRecurring = {
                            viewModel.markTransactionRecurring(inspecting)
                            onNavigate(OrbitNavKey.Upcoming)
                        },
                        onToggleTransfer = { viewModel.toggleTransfer(inspecting.id) },
                        onToggleMha = { viewModel.toggleMha(inspecting.id) },
                        onToggleIgnore = { viewModel.toggleIgnore(inspecting.id) },
                        onDelete = { onRequestDelete(inspecting.id) },
                        onResetMerchant = { viewModel.resetMerchant(inspecting.id) },
                        onResetCategory = { viewModel.resetCategory(inspecting.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionReadOnlyDetailContent(
    transaction: Transaction,
    categoryName: String?,
    categoryIcon: String?,
    categoryColorHex: String?,
    accountName: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onSplit: () -> Unit,
    onCreateRule: () -> Unit,
    onMarkRecurring: () -> Unit,
    onToggleTransfer: () -> Unit,
    onToggleMha: () -> Unit,
    onToggleIgnore: () -> Unit,
    onDelete: () -> Unit,
    onResetMerchant: () -> Unit,
    onResetCategory: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val catColor = parseInspectorHexColor(categoryColorHex) ?: MaterialTheme.colorScheme.primary
    val amountColor = when {
        transaction.effectiveIsTransfer -> MaterialTheme.colorScheme.secondary
        transaction.isIncome -> colors.income
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = catColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = categoryIcon ?: transaction.effectiveMerchant.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = catColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.effectiveMerchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = MoneyUtils.formatCents(transaction.effectiveAmountCents, signed = true),
                    style = TabularNumStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = amountColor
                )
            }
            IconButton(onClick = { haptics.tick(); onClose() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InspectorMetaItem(label = "DATE", value = transaction.effectiveDate)
                InspectorMetaItem(label = "ACCOUNT", value = accountName.ifBlank { "Account" })
                InspectorMetaItem(
                    label = "MERCHANT",
                    value = transaction.effectiveMerchant,
                    subtitle = if (transaction.editedMerchant != null && transaction.originalMerchant.isNotBlank()) {
                        "Originally: ${transaction.originalMerchant}"
                    } else null,
                    onReset = if (transaction.editedMerchant != null || transaction.editSource == EditSource.USER) onResetMerchant else null
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InspectorMetaItem(
                    label = "CATEGORY",
                    value = categoryName ?: if (transaction.effectiveIsTransfer) "Transfer" else "Uncategorized",
                    valueColor = catColor,
                    onReset = if (transaction.editedCategoryId != null) onResetCategory else null
                )
                InspectorMetaItem(
                    label = "ORIGINAL DESCRIPTION",
                    value = transaction.originalDescription ?: transaction.originalMerchant,
                    isMono = true
                )
                InspectorMetaItem(label = "NOTES", value = transaction.notes?.takeIf { it.isNotBlank() } ?: "None")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InspectorPillAction(label = "Edit", onClick = onEdit)
            InspectorPillAction(label = "Split", onClick = onSplit)
            InspectorPillAction(label = "Create rule", onClick = onCreateRule)
            InspectorPillAction(label = "Recurring", onClick = onMarkRecurring)
            InspectorPillAction(label = if (transaction.effectiveIsTransfer) "Unmark Transfer" else "Transfer", onClick = onToggleTransfer)
            InspectorPillAction(label = if (transaction.effectiveMhaEligible == true) "Remove MHA" else "MHA eligible", onClick = onToggleMha)
            InspectorPillAction(label = if (transaction.effectiveIsIgnored) "Unignore" else "Ignore", onClick = onToggleIgnore)
            InspectorPillAction(label = "Delete", isDestructive = true, onClick = onDelete)
        }
    }
}

@Composable
private fun InspectorMetaItem(
    label: String,
    value: String,
    isMono: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitle: String? = null,
    onReset: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onReset != null) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).clickable { haptics.tick(); onReset() }
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (isMono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InspectorPillAction(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = { haptics.tick(); onClick() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (isDestructive) OrbitTheme.colors.expense else OrbitTheme.colors.cardBorder),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.expense else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun parseInspectorHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank() || !hex.startsWith("#")) return null
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or colorInt)
            8 -> Color(colorInt)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
