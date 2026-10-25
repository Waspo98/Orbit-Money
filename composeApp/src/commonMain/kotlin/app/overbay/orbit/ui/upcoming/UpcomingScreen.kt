package app.overbay.orbit.ui.upcoming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.upcoming.components.UpcomingCalendarStrip
import app.overbay.orbit.ui.upcoming.components.UpcomingSuggestionBanner
import app.overbay.orbit.ui.upcoming.components.UpcomingTimelineList
import app.overbay.orbit.ui.upcoming.sheets.ReconcileOccurrenceDialog
import app.overbay.orbit.ui.upcoming.sheets.RecurringItemEditorSheet
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalOrbitHaptics.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.tick()
                    viewModel.openAddUpcoming()
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Recurring Schedule")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Canopy
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Upcoming & Bills",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Outflow: ${MoneyUtils.formatCents(state.totalUpcomingExpenseCents, showCents = false)} • Inflow: ${MoneyUtils.formatCents(state.totalUpcomingIncomeCents, showCents = false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 14-Day Calendar Strip
                item {
                    UpcomingCalendarStrip(
                        selectedDate = state.selectedDateFilter,
                        items = state.activeItems,
                        onSelectDate = { viewModel.setSelectedDateFilter(it) }
                    )
                }

                // Recurring Suggestion Banner (if candidate detected)
                if (state.candidates.isNotEmpty()) {
                    item {
                        UpcomingSuggestionBanner(
                            candidates = state.candidates,
                            onAccept = { viewModel.acceptCandidate(it) },
                            onDismiss = { viewModel.dismissCandidate(it) }
                        )
                    }
                }

                // Timeline List
                item {
                    UpcomingTimelineList(
                        items = state.items,
                        categoryMap = state.categoryMap,
                        accountMap = state.accountMap,
                        filterOption = state.filterOption,
                        onFilterChanged = { viewModel.setFilterOption(it) },
                        onItemClick = { viewModel.openEditUpcoming(it) },
                        onMarkPaid = { viewModel.openReconcileDialog(it) },
                        onAddClick = { viewModel.openAddUpcoming() }
                    )
                }
            }
        }
    }

    // Modal Editor Sheet
    if (state.showEditorSheet) {
        RecurringItemEditorSheet(
            item = state.editingItem,
            categories = state.categories,
            accounts = state.accounts,
            onSave = { viewModel.saveUpcoming(it) },
            onDelete = { viewModel.deleteUpcoming(it) },
            onDismiss = { viewModel.closeEditorSheet() }
        )
    }

    // Reconcile Occurrence Confirmation Dialog
    val reconcilingItem = state.reconcilingItem
    if (state.showReconcileDialog && reconcilingItem != null) {
        ReconcileOccurrenceDialog(
            item = reconcilingItem,
            onConfirm = { id, date ->
                viewModel.reconcileItemPaid(id, date)
            },
            onDismiss = { viewModel.closeReconcileDialog() }
        )
    }
}
