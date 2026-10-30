package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitAmountTypeCard
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate
import kotlin.math.abs

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
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        RecurringItemEditorContent(
            item = item,
            categories = categories,
            accounts = accounts,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringItemEditorContent(
    item: UpcomingItem?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (UpcomingItem) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = item != null && item.id > 0L

    var name by remember(item) { mutableStateOf(item?.merchant ?: item?.name ?: "") }
    var rawAmountCents by remember(item) { mutableStateOf(abs(item?.amountCents ?: 0L)) }
    var kind by remember(item) { mutableStateOf(item?.kind ?: UpcomingKind.BILL) }
    var isExpense by remember(item) { mutableStateOf(item?.direction?.lowercase() != "income") }
    var selectedDate by remember(item) {
        val parsed = item?.nextDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed ?: LocalDateUtils.todayLocalDate())
    }
    var frequency by remember(item) { mutableStateOf(item?.frequencyType ?: RecurrenceFrequency.MONTHLY) }
    var customInterval by remember(item) { mutableStateOf(item?.frequencyInterval ?: 1) }
    var customUnit by remember(item) { mutableStateOf(item?.frequencyUnit ?: "days") }
    var amountStrategy by remember(item) { mutableStateOf(item?.amountStrategy ?: AmountStrategy.FIXED) }
    var lookbackMonths by remember(item) { mutableStateOf(item?.amountLookbackMonths ?: 3) }
    var status by remember(item) { mutableStateOf(item?.status ?: UpcomingStatus.ACTIVE) }
    var notes by remember(item) { mutableStateOf(item?.notes ?: "") }
    var selectedCategoryId by remember(item) { mutableStateOf(item?.categoryId) }
    var selectedAccountId by remember(item) { mutableStateOf(item?.accountId) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Recurring Schedule" else "New Recurring Schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item != null && item.id > 0L) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics.reject()
                            onDelete(item.id)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Schedule",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4-Pill Kind Selector
        RecurringKindSelector(
            selectedKind = kind,
            onKindSelected = { newKind ->
                kind = newKind
                if (newKind == UpcomingKind.INCOME) {
                    isExpense = false
                } else {
                    isExpense = true
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Centerpiece Amount + Expense/Income Hero Card
        OrbitAmountTypeCard(
            cents = rawAmountCents,
            onCentsChange = { rawAmountCents = it },
            isExpense = isExpense,
            onIsExpenseChange = { newIsExpense ->
                isExpense = newIsExpense
                if (newIsExpense && kind == UpcomingKind.INCOME) {
                    kind = UpcomingKind.BILL
                } else if (!newIsExpense && kind != UpcomingKind.INCOME) {
                    kind = UpcomingKind.INCOME
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Amount Strategy (Fixed vs History Average)
        RecurringAmountStrategySection(
            amountStrategy = amountStrategy,
            lookbackMonths = lookbackMonths,
            onStrategyChanged = { amountStrategy = it },
            onLookbackChanged = { lookbackMonths = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Name / Merchant
        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name / Merchant") },
            placeholder = { Text("e.g. Netflix, Church Tithe, Employer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2-Column Bento Row: Next Due Date + Frequency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateFieldButton(
                date = selectedDate,
                onDateSelected = { selectedDate = it },
                label = "Next Due",
                modifier = Modifier.weight(0.48f)
            )

            OrbitDropdownField(
                label = "Frequency",
                selectedText = frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                options = listOf(
                    RecurrenceFrequency.WEEKLY,
                    RecurrenceFrequency.BIWEEKLY,
                    RecurrenceFrequency.SEMIMONTHLY,
                    RecurrenceFrequency.MONTHLY,
                    RecurrenceFrequency.BIMONTHLY,
                    RecurrenceFrequency.QUARTERLY,
                    RecurrenceFrequency.YEARLY,
                    RecurrenceFrequency.CUSTOM
                ),
                optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onOptionSelected = { frequency = it },
                modifier = Modifier.weight(0.52f)
            )
        }

        // Custom frequency fields
        RecurringCustomScheduleFields(
            frequency = frequency,
            customInterval = customInterval,
            customUnit = customUnit,
            onIntervalChanged = { customInterval = it },
            onUnitChanged = { customUnit = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

        // Status & Notes
        RecurringStatusAndNotesSection(
            status = status,
            notes = notes,
            onStatusChanged = { status = it },
            onNotesChanged = { notes = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    haptics.confirm()
                    val trimmedName = name.trim()
                    val updated = item?.copy(
                        name = trimmedName,
                        merchant = trimmedName,
                        amountCents = rawAmountCents,
                        kind = kind,
                        direction = if (isExpense) "expense" else "income",
                        frequencyType = frequency,
                        frequencyInterval = if (frequency == RecurrenceFrequency.CUSTOM) customInterval else 1,
                        frequencyUnit = if (frequency == RecurrenceFrequency.CUSTOM) customUnit else "months",
                        nextDate = selectedDate.toString(),
                        categoryId = selectedCategoryId,
                        accountId = selectedAccountId,
                        status = status,
                        amountStrategy = amountStrategy,
                        amountLookbackMonths = lookbackMonths,
                        notes = notes.trim().ifBlank { null }
                    ) ?: UpcomingItem(
                        name = trimmedName,
                        merchant = trimmedName,
                        amountCents = rawAmountCents,
                        kind = kind,
                        direction = if (isExpense) "expense" else "income",
                        frequencyType = frequency,
                        frequencyInterval = if (frequency == RecurrenceFrequency.CUSTOM) customInterval else 1,
                        frequencyUnit = if (frequency == RecurrenceFrequency.CUSTOM) customUnit else "months",
                        nextDate = selectedDate.toString(),
                        categoryId = selectedCategoryId,
                        accountId = selectedAccountId,
                        status = status,
                        amountStrategy = amountStrategy,
                        amountLookbackMonths = lookbackMonths,
                        notes = notes.trim().ifBlank { null }
                    )
                    onSave(updated)
                },
                enabled = name.isNotBlank() && rawAmountCents > 0L,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}
