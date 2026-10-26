package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.transactions.components.TransactionOptionsBlock
import app.overbay.orbit.ui.transactions.components.TransactionProvenanceCard
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate
import kotlin.math.abs

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
        mhaEligible: Boolean?
    ) -> Unit,
    onResetToOriginal: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCreateRule: ((Transaction) -> Unit)? = null,
    onMarkRecurring: ((Transaction) -> Unit)? = null,
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
    var notes by remember(transaction) { mutableStateOf(transaction?.notes ?: "") }
    var isTransfer by remember(transaction) { mutableStateOf(transaction?.effectiveIsTransfer ?: false) }
    var isIgnored by remember(transaction) { mutableStateOf(transaction?.effectiveIsIgnored ?: false) }
    var mhaEligible by remember(transaction) { mutableStateOf(transaction?.effectiveMhaEligible ?: false) }

    var categoryDropdownOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title Header
        Text(
            text = if (isEditing) "Edit Transaction" else "New Transaction",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

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

            Surface(
                onClick = {
                    haptics.tick()
                    isExpense = !isExpense
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isExpense) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.height(56.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpense) "Expense (-)" else "Income (+)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpense) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date Picker Button
        DateFieldButton(
            date = selectedDate,
            onDateSelected = { selectedDate = it },
            label = "Transaction Date"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Picker
        Box(modifier = Modifier.fillMaxWidth()) {
            val selectedCategory = categories.find { it.id == selectedCategoryId }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { categoryDropdownOpen = true },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedCategory?.name ?: "Uncategorized",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (selectedCategory != null) {
                        val color = try {
                            val hex = selectedCategory.color.trim().removePrefix("#")
                            Color(hex.toLong(16) or 0xFF000000)
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = categoryDropdownOpen,
                onDismissRequest = { categoryDropdownOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Uncategorized") },
                    onClick = {
                        selectedCategoryId = null
                        categoryDropdownOpen = false
                    }
                )
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.name) },
                        onClick = {
                            selectedCategoryId = cat.id
                            categoryDropdownOpen = false
                        }
                    )
                }
            }
        }

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
                        mhaEligible
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
