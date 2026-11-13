package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.transactions.components.TransactionOptionsBlock
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorContent(
    transaction: Transaction?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (
        id: Long,
        merchant: String,
        amountCents: Cents,
        date: String,
        categoryId: Long?,
        notes: String?,
        isTransfer: Boolean,
        isIgnored: Boolean,
        mhaEligible: Boolean?,
        accountId: Long?
    ) -> Unit,
    onResetToOriginal: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCreateRule: ((Transaction) -> Unit)? = null,
    onMarkRecurring: ((Transaction) -> Unit)? = null,
    onImportCsv: (() -> Unit)? = null,
    showMhaOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = transaction != null && transaction.id > 0L

    var isPickingCategory by remember(transaction?.id) { mutableStateOf(false) }
    var merchant by remember(transaction) { mutableStateOf(transaction?.effectiveMerchant ?: "") }
    var rawAmountCents by remember(transaction) {
        mutableStateOf(abs(transaction?.effectiveAmountCents ?: 0L))
    }
    var isExpense by remember(transaction) {
        mutableStateOf(transaction?.isExpense ?: true)
    }
    var selectedDate by remember(transaction) {
        val parsed = transaction?.effectiveDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed ?: LocalDateUtils.todayLocalDate())
    }
    var selectedCategoryId by remember(transaction) {
        mutableStateOf(transaction?.effectiveCategoryId)
    }
    var selectedAccountId by remember(transaction, accounts) {
        mutableStateOf(transaction?.accountId ?: accounts.firstOrNull()?.id ?: 1L)
    }
    var notes by remember(transaction) { mutableStateOf(transaction?.notes ?: "") }
    var isTransfer by remember(transaction) { mutableStateOf(transaction?.effectiveIsTransfer ?: false) }
    var isIgnored by remember(transaction) { mutableStateOf(transaction?.effectiveIsIgnored ?: false) }
    var mhaEligible by remember(transaction) { mutableStateOf(transaction?.effectiveMhaEligible) }

    BackHandler(enabled = isPickingCategory) {
        haptics.tick()
        isPickingCategory = false
    }

    val hasEdits = transaction != null && (
        transaction.editSource == EditSource.USER ||
            transaction.editedMerchant != null ||
            transaction.editedAmountCents != null ||
            transaction.editedDate != null ||
            transaction.editedCategoryIdSource != null ||
            transaction.editedCategoryId != null ||
            transaction.editedIsTransfer != null ||
            transaction.editedIsIgnored != null ||
            transaction.editedMhaEligibleSource != null
        )

    val statementSubtitle = remember(transaction, accounts) {
        if (transaction == null || transaction.id <= 0L) {
            null
        } else {
            val friendlyDate = LocalDateUtils.parseIsoDate(transaction.originalDate)
                ?.let { LocalDateUtils.formatDisplayDate(it) }
                ?: transaction.originalDate
            val acctName = accounts.find { it.id == transaction.accountId }?.name?.takeIf { it.isNotBlank() }
                ?: transaction.accountName.takeIf { it.isNotBlank() }
            val prefix = if (acctName != null) "$acctName • " else ""
            "${prefix}Statement: ${transaction.originalMerchant} • $friendlyDate • ${
                MoneyUtils.formatCents(transaction.originalAmountCents, signed = true)
            }"
        }
    }

    val isInsideSheet = LocalOrbitSheetDismiss.current != null

    Column(
        modifier = if (isInsideSheet) modifier.fillMaxWidth() else modifier.fillMaxSize()
    ) {
        OrbitSheetHeader(
            title = when {
                isPickingCategory -> "Select Category"
                isEditing -> "Edit Transaction"
                else -> "New Transaction"
            },
            subtitle = if (isPickingCategory) merchant.takeIf { it.isNotBlank() } else statementSubtitle,
            onNavigateBack = if (isPickingCategory) ({ isPickingCategory = false }) else null,
            onDismiss = onDismiss,
            trailingActions = {
                OrbitScaleBadgeVisibility(visible = !isPickingCategory) {
                    if (!isEditing && onImportCsv != null) {
                        OrbitActionChip(
                            label = "Import CSV",
                            onClick = { onImportCsv() }
                        )
                    } else if (isEditing) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptics.reject()
                                    onDelete(transaction.id)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Transaction",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        AnimatedContent(
            targetState = isPickingCategory,
            transitionSpec = {
                (fadeIn(OrbitSprings.FloatEffectsDefault) togetherWith fadeOut(OrbitSprings.FloatEffectsFast))
                    .using(SizeTransform(clip = false) { _, _ -> OrbitSprings.IntSizeAccordionFast })
            },
            modifier = Modifier.weight(1f, fill = !isInsideSheet).fillMaxWidth(),
            label = "transactionEditorStageTransition"
        ) { pickingCategory ->
            if (pickingCategory) {
                InlineCategoryPickerStage(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    currentSavedCategoryId = transaction?.effectiveCategoryId,
                    onSelectCategory = { chosenId ->
                        selectedCategoryId = chosenId
                        isPickingCategory = false
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OrbitSegmentedTabRow(
                        items = listOf(true, false),
                        selected = isExpense,
                        onSelect = { isExpense = it },
                        label = { if (it) "Expense" else "Income" }
                    )

                    if (!isEditing && accounts.isNotEmpty()) {
                        val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.first()
                        OrbitDropdownField(
                            label = "Account",
                            selectedText = selectedAccount.name,
                            options = accounts,
                            optionLabel = { it.name },
                            onOptionSelected = { selectedAccountId = it.id }
                        )
                    }

                    val selectedCategory = categories.find { it.id == selectedCategoryId }
                    TransactionCategoryField(
                        selectedCategory = selectedCategory,
                        isTransfer = isTransfer,
                        onClick = { isPickingCategory = true }
                    )

                    OrbitTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant") },
                        placeholder = { Text("e.g. Trader Joe's, Electric Utility") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        DateFieldButton(
                            date = selectedDate,
                            onDateSelected = { selectedDate = it },
                            label = "Date",
                            modifier = Modifier.weight(0.5f)
                        )

                        CurrencyTextField(
                            cents = rawAmountCents,
                            onCentsChange = { rawAmountCents = it },
                            label = "Amount",
                            prefixSymbol = if (isExpense) "-$" else "+$",
                            isPositiveAccent = !isExpense,
                            modifier = Modifier.weight(0.5f)
                        )
                    }

                    OrbitTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Add memo, tag, or receipt note…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 2
                    )

                    TransactionOptionsBlock(
                        isTransfer = isTransfer,
                        onTransferChange = { isTransfer = it },
                        mhaEligible = mhaEligible,
                        onMhaEligibleChange = { mhaEligible = it },
                        isIgnored = isIgnored,
                        onIgnoredChange = { isIgnored = it },
                        onSplit = if (isEditing) ({ onSplit(transaction.id) }) else null,
                        onCreateRule = if (isEditing && onCreateRule != null) ({ onCreateRule(transaction) }) else null,
                        onMarkRecurring = if (isEditing && onMarkRecurring != null) ({ onMarkRecurring(transaction) }) else null,
                        onRestoreOriginal = if (isEditing && hasEdits) ({ onResetToOriginal(transaction.id) }) else null,
                        showMhaOption = showMhaOption
                    )
                }
            }
        }

        OrbitAccordionVisibility(visible = !isPickingCategory) {
            OrbitModalFooter(
                confirmText = if (isEditing) "Save" else "Add Transaction",
                confirmEnabled = merchant.isNotBlank() && rawAmountCents > 0L,
                onDismiss = onDismiss,
                onConfirm = {
                    val signedAmount = if (isExpense) -rawAmountCents else rawAmountCents
                    val isoDate = selectedDate.toString()
                    onSave(
                        transaction?.id ?: 0L,
                        merchant,
                        signedAmount,
                        isoDate,
                        selectedCategoryId,
                        notes.ifBlank { null },
                        isTransfer,
                        isIgnored,
                        mhaEligible,
                        selectedAccountId
                    )
                }
            )
        }
    }
}
