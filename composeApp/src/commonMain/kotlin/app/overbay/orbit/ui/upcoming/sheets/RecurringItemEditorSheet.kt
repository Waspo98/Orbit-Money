package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitAmountTypeCard
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalScaffold
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
    onViewHistory: ((UpcomingItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        RecurringItemEditorContent(item, categories, accounts, onSave, onDelete, onDismiss, onViewHistory)
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
    onViewHistory: ((UpcomingItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = item != null && item.id > 0L
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

    var selectedCategoryId by remember(item) {
        val catId = item?.categoryId
        if (catId == null && item?.kind == UpcomingKind.GIVING) {
            val defGiving = categories.find { it.name.equals("Charitable Donations", ignoreCase = true) }
                ?: categories.find { it.name.contains("giving", ignoreCase = true) || it.name.contains("charit", ignoreCase = true) }
            mutableStateOf(defGiving?.id)
        } else {
            mutableStateOf(catId)
        }
    }
    var selectedAccountId by remember(item) { mutableStateOf(item?.accountId) }

    var scheduleMode by remember(item) {
        mutableStateOf(
            when (item?.recurrenceRule) {
                is RecurrenceRule.MonthDays -> ScheduleMode.MONTH_DAYS
                is RecurrenceRule.MonthWeekdays -> ScheduleMode.MONTH_WEEKDAYS
                else -> ScheduleMode.STANDARD
            }
        )
    }
    var monthDayOne by remember(item) {
        val rule = item?.recurrenceRule as? RecurrenceRule.MonthDays
        mutableStateOf(rule?.days?.firstOrNull() ?: selectedDate.dayOfMonth)
    }
    var monthDayTwo by remember(item) {
        val rule = item?.recurrenceRule as? RecurrenceRule.MonthDays
        mutableStateOf(rule?.days?.getOrNull(1))
    }
    var ordinalOne by remember(item) {
        val rule = item?.recurrenceRule as? RecurrenceRule.MonthWeekdays
        mutableStateOf(rule?.ordinals?.firstOrNull() ?: 1)
    }
    var ordinalTwo by remember(item) {
        val rule = item?.recurrenceRule as? RecurrenceRule.MonthWeekdays
        mutableStateOf(rule?.ordinals?.getOrNull(1))
    }
    var weekday by remember(item) {
        val rule = item?.recurrenceRule as? RecurrenceRule.MonthWeekdays
        mutableStateOf(rule?.weekdays?.firstOrNull() ?: 5)
    }

    if (showDeleteConfirm && item != null && item.id > 0L) {
        OrbitConfirmationDialog(
            title = "Delete Recurring Schedule",
            message = "Are you sure you want to delete \"${name.ifBlank { item.merchant ?: "this item" }}\"? This will remove all upcoming projections.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete(item.id)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    OrbitModalScaffold(
        title = if (isEditing) "Edit Recurring Schedule" else "New Recurring Schedule",
        onDismiss = onDismiss,
        headerTrailingActions = {
            if (isEditing) {
                if (onViewHistory != null) {
                    OrbitActionChip(
                        label = "History",
                        onClick = {
                            haptics.tick()
                            onViewHistory(item)
                        }
                    )
                }
                OrbitActionChip(
                    label = "Delete",
                    leadingIcon = Icons.Default.Delete,
                    isDestructive = true,
                    onClick = {
                        haptics.reject()
                        showDeleteConfirm = true
                    }
                )
            }
        },
        confirmText = "Save",
        confirmEnabled = name.isNotBlank() && rawAmountCents > 0L,
        onConfirm = {
            val trimmedName = name.trim()
            val rule = if (frequency == RecurrenceFrequency.MONTHLY || frequency == RecurrenceFrequency.SEMIMONTHLY) {
                when (scheduleMode) {
                    ScheduleMode.MONTH_DAYS -> {
                        val days = listOfNotNull(monthDayOne, if (frequency == RecurrenceFrequency.SEMIMONTHLY) monthDayTwo else null)
                        if (days.isNotEmpty()) RecurrenceRule.MonthDays(days) else null
                    }
                    ScheduleMode.MONTH_WEEKDAYS -> {
                        val ords = listOfNotNull(ordinalOne, if (frequency == RecurrenceFrequency.SEMIMONTHLY) ordinalTwo else null)
                        if (ords.isNotEmpty()) RecurrenceRule.MonthWeekdays(ords, listOf(weekday)) else null
                    }
                    ScheduleMode.STANDARD -> null
                }
            } else null

            val baseItem = item ?: UpcomingItem(name = trimmedName, direction = "expense", nextDate = selectedDate.toString())
            val updated = baseItem.copy(
                name = trimmedName,
                merchant = trimmedName,
                amountCents = rawAmountCents,
                kind = kind,
                direction = if (isExpense) "expense" else "income",
                frequencyType = frequency,
                frequencyInterval = if (frequency == RecurrenceFrequency.CUSTOM) customInterval else 1,
                frequencyUnit = if (frequency == RecurrenceFrequency.CUSTOM) customUnit else "months",
                recurrenceRule = rule,
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
        bodyPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        bodySpacing = 0.dp,
        modifier = modifier
    ) {

        RecurringKindSelector(
            selectedKind = kind,
            onKindSelected = { newKind ->
                kind = newKind
                if (newKind == UpcomingKind.INCOME) {
                    isExpense = false
                } else {
                    isExpense = true
                    if (newKind == UpcomingKind.GIVING && selectedCategoryId == null) {
                        val defGiving = categories.find { it.name.equals("Charitable Donations", ignoreCase = true) }
                            ?: categories.find { it.name.contains("giving", ignoreCase = true) || it.name.contains("charit", ignoreCase = true) }
                        if (defGiving != null) {
                            selectedCategoryId = defGiving.id
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

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

        RecurringAmountStrategySection(
            amountStrategy = amountStrategy,
            lookbackMonths = lookbackMonths,
            onStrategyChanged = { amountStrategy = it },
            onLookbackChanged = { lookbackMonths = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name / Merchant") },
            placeholder = { Text("e.g. Netflix, Church Tithe, Employer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                    RecurrenceFrequency.WEEKLY, RecurrenceFrequency.BIWEEKLY,
                    RecurrenceFrequency.SEMIMONTHLY, RecurrenceFrequency.MONTHLY,
                    RecurrenceFrequency.BIMONTHLY, RecurrenceFrequency.QUARTERLY,
                    RecurrenceFrequency.YEARLY, RecurrenceFrequency.CUSTOM
                ),
                optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onOptionSelected = { frequency = it },
                modifier = Modifier.weight(0.52f)
            )
        }

        RecurringScheduleModeSection(
            frequency = frequency,
            scheduleMode = scheduleMode,
            monthDayOne = monthDayOne,
            monthDayTwo = monthDayTwo,
            ordinalOne = ordinalOne,
            ordinalTwo = ordinalTwo,
            weekday = weekday,
            onScheduleModeChanged = { scheduleMode = it },
            onMonthDayOneChanged = { monthDayOne = it },
            onMonthDayTwoChanged = { monthDayTwo = it },
            onOrdinalOneChanged = { ordinalOne = it },
            onOrdinalTwoChanged = { ordinalTwo = it },
            onWeekdayChanged = { weekday = it }
        )

        RecurringCustomScheduleFields(
            frequency = frequency,
            customInterval = customInterval,
            customUnit = customUnit,
            onIntervalChanged = { customInterval = it },
            onUnitChanged = { customUnit = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

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

        RecurringStatusAndNotesSection(
            status = status,
            notes = notes,
            onStatusChanged = { status = it },
            onNotesChanged = { notes = it }
        )
    }
}
