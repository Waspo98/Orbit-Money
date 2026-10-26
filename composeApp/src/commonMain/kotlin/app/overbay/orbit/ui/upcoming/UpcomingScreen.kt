package app.overbay.orbit.ui.upcoming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.HeroStatItem
import app.overbay.orbit.ui.components.HeroStatTone
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitPrimaryPillButton
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.upcoming.components.UpcomingCalendarStrip
import app.overbay.orbit.ui.upcoming.components.UpcomingSuggestionBanner
import app.overbay.orbit.ui.upcoming.components.UpcomingTimelineList
import app.overbay.orbit.ui.upcoming.sheets.ReconcileOccurrenceDialog
import app.overbay.orbit.ui.upcoming.sheets.RecurringItemEditorSheet

@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState(maxHeight = 220.dp, minHeight = 64.dp)

    Scaffold(
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                // Curved Green Canopy Hero
                MorphingPageHero(
                    kicker = "MONEY CALENDAR",
                    title = "Upcoming",
                    subtitle = "Projected bills, subscriptions, income, and cash flow.",
                    canNavigateBack = true,
                    onBack = { onNavigate(OrbitNavKey.Dashboard) },
                    scrollState = heroScrollState,
                    onPrimaryAddClick = { viewModel.openAddUpcoming() },
                    stats = listOf(
                        HeroStatItem("DUE NEXT 30D", MoneyUtils.formatCents(state.totalUpcomingExpenseCents), HeroStatTone.CAUTION),
                        HeroStatItem("EXPECTED INCOME", MoneyUtils.formatCents(state.totalUpcomingIncomeCents), HeroStatTone.GOOD),
                        HeroStatItem("NET SCHEDULED", MoneyUtils.formatCents(state.netCashflowProjectionCents), if (state.netCashflowProjectionCents >= 0L) HeroStatTone.GOOD else HeroStatTone.CAUTION),
                        HeroStatItem("ACTIVE ITEMS", "${state.activeItems.size}", HeroStatTone.DEFAULT)
                    )
                )

                // Action Row Below Canopy Hero
                OrbitPageActionRow {
                    OrbitPrimaryPillButton(
                        text = "+ Add Recurring",
                        onClick = { viewModel.openAddUpcoming() }
                    )
                }

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
