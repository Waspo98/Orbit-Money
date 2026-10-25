package app.overbay.orbit.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.util.LocalOrbitHaptics

data class TransactionFilterDraft(
    val type: String = "all", // "all", "expense", "income", "transfer"
    val datePreset: String = "all", // "all", "this_month", "last_month", "this_year"
    val accountId: Long? = null,
    val categoryId: Long? = null
)

/**
 * Draft-state bottom sheet for transaction filtering.
 * Edits remain local until 'Apply Filters' is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    initialFilter: TransactionFilterDraft,
    onApply: (TransactionFilterDraft) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    accountNames: Map<Long, String> = emptyMap(),
    categoryNames: Map<Long, String> = emptyMap()
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initialFilter) { mutableStateOf(initialFilter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Filter Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 1. Transaction Type
            Text(
                text = "TRANSACTION TYPE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                    val selected = draft.type == value
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(type = value)
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Date Range Presets
            Text(
                text = "DATE RANGE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "All Time",
                    "this_month" to "This Month",
                    "last_month" to "Last Month",
                    "this_year" to "This Year"
                ).forEach { (value, label) ->
                    val selected = draft.datePreset == value
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(datePreset = value)
                        },
                        label = { Text(label) }
                    )
                }
            }

            // 3. Accounts Filter
            if (accountNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "ACCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = draft.accountId == null,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(accountId = null)
                        },
                        label = { Text("All Accounts") }
                    )
                    accountNames.forEach { (id, name) ->
                        val selected = draft.accountId == id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.tick()
                                draft = draft.copy(accountId = if (selected) null else id)
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }

            // 4. Categories Filter
            if (categoryNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = draft.categoryId == null,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(categoryId = null)
                        },
                        label = { Text("All Categories") }
                    )
                    categoryNames.forEach { (id, name) ->
                        val selected = draft.categoryId == id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.tick()
                                draft = draft.copy(categoryId = if (selected) null else id)
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }

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
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset All")
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        onApply(draft)
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
