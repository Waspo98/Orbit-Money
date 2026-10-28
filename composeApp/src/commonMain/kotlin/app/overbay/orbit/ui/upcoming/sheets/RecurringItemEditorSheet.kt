package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringItemEditorSheet(
    item: UpcomingItem?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (UpcomingItem) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = item != null && item.id > 0L

    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var rawAmountCents by remember(item) { mutableStateOf(abs(item?.amountCents ?: 0L)) }
    var isExpense by remember(item) { mutableStateOf(item?.direction?.lowercase() != "income") }
    var selectedDate by remember(item) {
        val parsed = item?.nextDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed ?: LocalDateUtils.todayLocalDate())
    }
    var frequency by remember(item) { mutableStateOf(item?.frequencyType ?: RecurrenceFrequency.MONTHLY) }
    var selectedCategoryId by remember(item) { mutableStateOf(item?.categoryId) }
    var selectedAccountId by remember(item) { mutableStateOf(item?.accountId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (isEditing) "Edit Recurring Schedule" else "New Recurring Schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name / Merchant") },
                placeholder = { Text("e.g. Netflix, Rent, Employer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Amount + Expense/Income Pill
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

            // Next Due Date
            DateFieldButton(
                date = selectedDate,
                onDateSelected = { selectedDate = it },
                label = "Next Due Date"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Frequency Dropdown
            OrbitDropdownField(
                label = "Recurrence Frequency",
                selectedText = frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                options = listOf(
                    RecurrenceFrequency.WEEKLY,
                    RecurrenceFrequency.BIWEEKLY,
                    RecurrenceFrequency.SEMIMONTHLY,
                    RecurrenceFrequency.MONTHLY,
                    RecurrenceFrequency.QUARTERLY,
                    RecurrenceFrequency.YEARLY
                ),
                optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onOptionSelected = { frequency = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Dropdown
            val selectedCategory = categories.find { it.id == selectedCategoryId }
            OrbitDropdownField(
                label = "Category",
                selectedText = selectedCategory?.labelWithEmoji ?: "None / Unassigned",
                options = categories,
                optionLabel = { it.labelWithEmoji },
                onOptionSelected = { selectedCategoryId = it.id },
                nullOptionLabel = "None",
                onNullSelected = { selectedCategoryId = null }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Account Dropdown
            val selectedAccount = accounts.find { it.id == selectedAccountId }
            OrbitDropdownField(
                label = "Payment Account",
                selectedText = selectedAccount?.name ?: "None / Unassigned",
                options = accounts,
                optionLabel = { it.name },
                onOptionSelected = { selectedAccountId = it.id },
                nullOptionLabel = "None",
                onNullSelected = { selectedAccountId = null }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
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
                        val updated = item?.copy(
                            name = name.trim(),
                            merchant = name.trim(),
                            amountCents = rawAmountCents,
                            kind = if (isExpense) UpcomingKind.BILL else UpcomingKind.INCOME,
                            direction = if (isExpense) "expense" else "income",
                            frequencyType = frequency,
                            nextDate = selectedDate.toString(),
                            categoryId = selectedCategoryId,
                            accountId = selectedAccountId,
                            status = UpcomingStatus.ACTIVE
                        ) ?: UpcomingItem(
                            name = name.trim(),
                            merchant = name.trim(),
                            amountCents = rawAmountCents,
                            kind = if (isExpense) UpcomingKind.BILL else UpcomingKind.INCOME,
                            direction = if (isExpense) "expense" else "income",
                            frequencyType = frequency,
                            nextDate = selectedDate.toString(),
                            categoryId = selectedCategoryId,
                            accountId = selectedAccountId,
                            status = UpcomingStatus.ACTIVE
                        )
                        onSave(updated)
                    },
                    enabled = name.isNotBlank() && rawAmountCents > 0L,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        haptics.reject()
                        onDelete(item.id)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Schedule", modifier = Modifier.size(16.dp))
                    Text("Delete Schedule")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
