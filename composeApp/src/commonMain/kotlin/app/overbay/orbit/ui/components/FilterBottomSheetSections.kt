package app.overbay.orbit.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.transactions.TransactionSortOption
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

@Composable
fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun FilterSortSection(
    sortDraft: TransactionSortOption,
    onSortChange: (TransactionSortOption) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FilterSectionTitle("SORT BY")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransactionSortOption.entries.forEach { option ->
            val selected = sortDraft == option
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onSortChange(option)
                },
                label = { Text(option.label) }
            )
        }
    }
}

@Composable
fun FilterTypeSection(
    currentType: String,
    onTypeChange: (String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FilterSectionTitle("TRANSACTION TYPE")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "all" to "All Types",
            "expense" to "Expenses",
            "income" to "Income",
            "transfer" to "Transfers"
        ).forEach { (value, label) ->
            val selected = currentType == value
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onTypeChange(value)
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun FilterEditsSection(
    currentHasEdits: String,
    onHasEditsChange: (String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FilterSectionTitle("HAS EDITS")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "any" to "Any",
            "edited" to "Edited",
            "original" to "Original"
        ).forEach { (value, label) ->
            val selected = currentHasEdits == value
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onHasEditsChange(value)
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun FilterDateRangeSection(
    datePreset: String,
    startDate: String?,
    endDate: String?,
    onPresetChange: (String) -> Unit,
    onStartDateChange: (String?) -> Unit,
    onEndDateChange: (String?) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FilterSectionTitle("DATE RANGE")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "all" to "All Time",
            "this_month" to "This Month",
            "last_30_days" to "Last 30 Days",
            "last_90_days" to "Last 90 Days",
            "this_year" to "This Year",
            "custom" to "Custom"
        ).forEach { (value, label) ->
            val selected = datePreset == value
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onPresetChange(value)
                },
                label = { Text(label) }
            )
        }
    }

    if (datePreset == "custom") {
        Spacer(modifier = Modifier.height(10.dp))
        val today = remember { LocalDateUtils.todayLocalDate() }
        val parsedStart = remember(startDate) {
            startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        }
        val parsedEnd = remember(endDate) {
            endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DateFieldButton(
                date = parsedStart,
                onDateSelected = { onStartDateChange(it.toString()) },
                label = "Start Date",
                modifier = Modifier.weight(1f)
            )
            DateFieldButton(
                date = parsedEnd,
                onDateSelected = { onEndDateChange(it.toString()) },
                label = "End Date",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun FilterAmountRangeSection(
    minAmountCents: Long?,
    maxAmountCents: Long?,
    onMinAmountChange: (Long?) -> Unit,
    onMaxAmountChange: (Long?) -> Unit
) {
    FilterSectionTitle("AMOUNT RANGE")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CurrencyTextField(
            cents = minAmountCents ?: 0L,
            onCentsChange = { onMinAmountChange(if (it == 0L) null else it) },
            label = "Min Amount",
            modifier = Modifier.weight(1f)
        )
        CurrencyTextField(
            cents = maxAmountCents ?: 0L,
            onCentsChange = { onMaxAmountChange(if (it == 0L) null else it) },
            label = "Max Amount",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FilterAccountsSection(
    selectedAccountIds: Set<Long>,
    accountNames: Map<Long, String>,
    onToggleAccount: (Long) -> Unit,
    onClearAccounts: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FilterSectionTitle("ACCOUNTS")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedAccountIds.isEmpty(),
            onClick = {
                haptics.tick()
                onClearAccounts()
            },
            label = { Text("All Accounts") }
        )
        accountNames.forEach { (id, name) ->
            val selected = selectedAccountIds.contains(id)
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onToggleAccount(id)
                },
                label = { Text(name) }
            )
        }
    }
}

@Composable
fun FilterCategoriesSection(
    selectedCategoryIds: Set<Long>,
    uncategorizedOnly: Boolean,
    categoryNames: Map<Long, String>,
    onToggleCategory: (Long) -> Unit,
    onToggleUncategorized: () -> Unit,
    onClearCategories: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FilterSectionTitle("CATEGORIES")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedCategoryIds.isEmpty() && !uncategorizedOnly,
            onClick = {
                haptics.tick()
                onClearCategories()
            },
            label = { Text("All Categories") }
        )
        FilterChip(
            selected = uncategorizedOnly,
            onClick = {
                haptics.tick()
                onToggleUncategorized()
            },
            label = { Text("❓ Uncategorized") }
        )
        categoryNames.forEach { (id, name) ->
            val selected = selectedCategoryIds.contains(id)
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onToggleCategory(id)
                },
                label = { Text(name) }
            )
        }
    }
}

@Composable
fun FilterSwitchesSection(
    includeTransfers: Boolean,
    onIncludeTransfersChange: (Boolean) -> Unit,
    includeIgnored: Boolean,
    onIncludeIgnoredChange: (Boolean) -> Unit
) {
    FilterSectionTitle("OPTIONS")
    OrbitSwitchGroupCard {
        OrbitSwitchRow(
            title = "Include Transfers",
            subtitle = "Show internal transfers between accounts",
            checked = includeTransfers,
            onCheckedChange = onIncludeTransfersChange,
            showContainer = false
        )
        OrbitSwitchRow(
            title = "Include Ignored",
            subtitle = "Show transactions excluded from budget totals",
            checked = includeIgnored,
            onCheckedChange = onIncludeIgnoredChange,
            showContainer = false
        )
    }
}
