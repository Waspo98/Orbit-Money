package app.overbay.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.transactions.TransactionSortOption
import app.overbay.orbit.util.LocalOrbitHaptics

data class TransactionFilterDraft(
    val type: String = "all", // "all", "expense", "income", "transfer"
    val datePreset: String = "all", // "all", "this_month", "last_30_days", "last_90_days", "this_year", "custom"
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val accountIds: Set<Long> = if (accountId != null) setOf(accountId) else emptySet(),
    val categoryIds: Set<Long> = if (categoryId != null) setOf(categoryId) else emptySet(),
    val uncategorizedOnly: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val hasEditsFilter: String = "any", // "any", "edited", "original"
    val includeTransfers: Boolean = true,
    val includeIgnored: Boolean = true
)

/**
 * Rich draft-state bottom sheet for transaction filtering and sorting.
 * Edits remain local until 'Apply Filters' is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    initialFilter: TransactionFilterDraft,
    onApply: (TransactionFilterDraft) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialSort: TransactionSortOption = TransactionSortOption.DATE_DESC,
    onApplySort: (TransactionSortOption) -> Unit = {},
    accountNames: Map<Long, String> = emptyMap(),
    categoryNames: Map<Long, String> = emptyMap()
) {
    val haptics = LocalOrbitHaptics.current
    var draft by remember(initialFilter) { mutableStateOf(initialFilter) }
    var sortDraft by remember(initialSort) { mutableStateOf(initialSort) }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Filter & Sort Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 0. Sort By
            FilterSortSection(
                sortDraft = sortDraft,
                onSortChange = { sortDraft = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Transaction Type
            FilterTypeSection(
                currentType = draft.type,
                onTypeChange = { draft = draft.copy(type = it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Has Edits
            FilterEditsSection(
                currentHasEdits = draft.hasEditsFilter,
                onHasEditsChange = { draft = draft.copy(hasEditsFilter = it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Date Range
            FilterDateRangeSection(
                datePreset = draft.datePreset,
                startDate = draft.startDate,
                endDate = draft.endDate,
                onPresetChange = { draft = draft.copy(datePreset = it) },
                onStartDateChange = { draft = draft.copy(startDate = it) },
                onEndDateChange = { draft = draft.copy(endDate = it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Amount Range
            FilterAmountRangeSection(
                minAmountCents = draft.minAmountCents,
                maxAmountCents = draft.maxAmountCents,
                onMinAmountChange = { draft = draft.copy(minAmountCents = it) },
                onMaxAmountChange = { draft = draft.copy(maxAmountCents = it) }
            )

            // 5. Accounts Filter
            if (accountNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                FilterAccountsSection(
                    selectedAccountIds = draft.accountIds,
                    accountNames = accountNames,
                    onToggleAccount = { id ->
                        val next = if (draft.accountIds.contains(id)) draft.accountIds - id else draft.accountIds + id
                        draft = draft.copy(accountIds = next, accountId = next.firstOrNull())
                    },
                    onClearAccounts = {
                        draft = draft.copy(accountIds = emptySet(), accountId = null)
                    }
                )
            }

            // 6. Categories Filter
            if (categoryNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                FilterCategoriesSection(
                    selectedCategoryIds = draft.categoryIds,
                    uncategorizedOnly = draft.uncategorizedOnly,
                    categoryNames = categoryNames,
                    onToggleCategory = { id ->
                        val next = if (draft.categoryIds.contains(id)) draft.categoryIds - id else draft.categoryIds + id
                        draft = draft.copy(categoryIds = next, categoryId = next.firstOrNull(), uncategorizedOnly = false)
                    },
                    onToggleUncategorized = {
                        draft = draft.copy(uncategorizedOnly = !draft.uncategorizedOnly, categoryIds = emptySet(), categoryId = null)
                    },
                    onClearCategories = {
                        draft = draft.copy(categoryIds = emptySet(), categoryId = null, uncategorizedOnly = false)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Switches: Include Transfers & Include Ignored
            FilterSwitchesSection(
                includeTransfers = draft.includeTransfers,
                onIncludeTransfersChange = { draft = draft.copy(includeTransfers = it) },
                includeIgnored = draft.includeIgnored,
                onIncludeIgnoredChange = { draft = draft.copy(includeIgnored = it) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons (Title Case)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        draft = TransactionFilterDraft()
                        sortDraft = TransactionSortOption.DATE_DESC
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset All")
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        onApply(draft)
                        onApplySort(sortDraft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
