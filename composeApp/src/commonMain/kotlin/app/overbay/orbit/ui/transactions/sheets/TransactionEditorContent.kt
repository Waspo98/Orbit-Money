package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.transactions.components.TransactionOptionsBlock
import app.overbay.orbit.ui.transactions.components.TransactionProvenanceCard
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
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = transaction != null && transaction.id > 0L

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
    var mhaEligible by remember(transaction) { mutableStateOf(transaction?.effectiveMhaEligible ?: false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title Header + Optional Import CSV Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Transaction" else "New Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!isEditing && onImportCsv != null) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onImportCsv()
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Import CSV",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Provenance Card (if imported original values exist)
        if (transaction != null && transaction.id > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
            TransactionProvenanceCard(
                transaction = transaction,
                onResetToOriginal = onResetToOriginal
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Merchant Name Input
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Amount Input + Expense/Income Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CurrencyTextField(
                    cents = rawAmountCents,
                    onCentsChange = { rawAmountCents = it },
                    label = "Amount"
                )
            }

            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = isExpense,
                    onClick = {
                        haptics.tick()
                        isExpense = true
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                    label = { Text("Expense") }
                )
                SegmentedButton(
                    selected = !isExpense,
                    onClick = {
                        haptics.tick()
                        isExpense = false
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                    label = { Text("Income") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date Picker Button
        DateFieldButton(
            date = selectedDate,
            onDateSelected = { selectedDate = it },
            label = "Transaction Date"
        )

        if (!isEditing && accounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.first()
            OrbitDropdownField(
                label = "Account",
                selectedText = selectedAccount.name,
                options = accounts,
                optionLabel = { it.name },
                onOptionSelected = { selectedAccountId = it.id }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Category Picker
        val selectedCategory = categories.find { it.id == selectedCategoryId }
        OrbitDropdownField(
            label = "Category",
            selectedText = selectedCategory?.labelWithEmoji ?: "❓ Uncategorized",
            options = categories,
            optionLabel = { it.labelWithEmoji },
            onOptionSelected = { selectedCategoryId = it.id },
            nullOptionLabel = "❓ Uncategorized",
            onNullSelected = { selectedCategoryId = null },
            leadingIcon = if (selectedCategory != null) {
                {
                    val color = selectedCategory.color.toComposeColor(MaterialTheme.colorScheme.primary)
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            } else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Notes Input
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Options: Transfer, MHA & Quick Actions
        TransactionOptionsBlock(
            isTransfer = isTransfer,
            onTransferChange = { isTransfer = it },
            mhaEligible = mhaEligible,
            onMhaEligibleChange = { mhaEligible = it },
            onCreateRule = if (transaction != null && onCreateRule != null) ({ onCreateRule(transaction) }) else null,
            onMarkRecurring = if (transaction != null && onMarkRecurring != null) ({ onMarkRecurring(transaction) }) else null
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Actions Row (Title Case)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    haptics.confirm()
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
                },
                enabled = merchant.isNotBlank() && rawAmountCents > 0L,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }

        // Secondary actions: Split & Delete
        if (transaction != null && transaction.id > 0L) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onSplit(transaction.id)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Split",
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Split")
                }

                OutlinedButton(
                    onClick = {
                        haptics.reject()
                        onDelete(transaction.id)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Delete")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
