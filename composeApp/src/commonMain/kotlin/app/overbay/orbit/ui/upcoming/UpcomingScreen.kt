package app.overbay.orbit.ui.upcoming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.upcoming.components.CardPaymentsDueCard
import app.overbay.orbit.ui.upcoming.components.UpcomingCalendarStrip
import app.overbay.orbit.ui.upcoming.components.UpcomingCashFlowCard
import app.overbay.orbit.ui.upcoming.components.UpcomingDetailInspectorPane
import app.overbay.orbit.ui.upcoming.components.UpcomingFilterStrip
import app.overbay.orbit.ui.upcoming.components.UpcomingSummaryStatsGrid
import app.overbay.orbit.ui.upcoming.components.upcomingTimelineItems
import app.overbay.orbit.ui.upcoming.sheets.AddFromTransactionSheet
import app.overbay.orbit.ui.upcoming.sheets.ReconcileOccurrenceDialog
import app.overbay.orbit.ui.upcoming.sheets.RecurringHistoryModal
import app.overbay.orbit.ui.upcoming.sheets.RecurringItemEditorSheet
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()
    val isTwoPane = posture.supportsTwoPane
    val isDetailOpen = isTwoPane && (state.inspectingItem != null || state.showEditorSheet)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (!state.isLoading) {
                    OrbitFloatingActionButton(
                        visible = heroScrollState.isFabVisible,
                        onClick = { viewModel.openAddUpcoming() },
                        contentDescription = "Add Recurring"
                    )
                }
            }
        ) { paddingValues ->
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
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
                    var filterMenuExpanded by remember { mutableStateOf(false) }

                    MorphingPageHero(
                        kicker = "MONEY CALENDAR",
                        title = "Upcoming",
                        subtitle = "Projected bills, subscriptions, giving, income, and cash flow.",
                        scrollState = heroScrollState,
                        canopyBar = { _, isCompact, barHeight ->
                            val netProjection = MoneyUtils.formatCents(state.netCashflowProjectionCents, showCents = false, signed = true)
                            val periodLabel = if (state.cashFlowPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) "Rest of Month Net" else "30-Day Net"
                            val dateFilter = state.selectedDateFilter
                            CanopySurfacePill(
                                expandedText = if (dateFilter != null) "Date Filter: $dateFilter (Tap to clear)" else "$periodLabel · $netProjection",
                                compactText = if (dateFilter != null) dateFilter else "Net $netProjection",
                                isCompact = isCompact,
                                leadingIcon = OrbitIcons.Upcoming,
                                onClick = if (dateFilter != null) {
                                    { viewModel.setSelectedDateFilter(null) }
                                } else null,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )

                            val activeFilterCount = (if (state.selectedFilter != UpcomingFilter.ALL) 1 else 0) +
                                (if (state.selectedDateFilter != null) 1 else 0)

                            Box {
                                CanopyIconButton(
                                    icon = OrbitIcons.Filter,
                                    contentDescription = "Filter Upcoming",
                                    onClick = { filterMenuExpanded = true },
                                    buttonSize = barHeight,
                                    isActive = activeFilterCount > 0,
                                    badgeCount = activeFilterCount
                                )

                                if (filterMenuExpanded) {
                                    DropdownMenu(
                                        expanded = true,
                                        onDismissRequest = { filterMenuExpanded = false }
                                    ) {
                                        UpcomingFilter.entries.forEach { option ->
                                            val selected = state.selectedFilter == option
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = option.label,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                trailingIcon = if (selected) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                } else null,
                                                onClick = {
                                                    viewModel.setFilter(option)
                                                    filterMenuExpanded = false
                                                }
                                            )
                                        }

                                        if (state.selectedDateFilter != null) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "Clear Date Filter (${state.selectedDateFilter})",
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setSelectedDateFilter(null)
                                                    filterMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            CanopySettingsButton(
                                buttonSize = barHeight,
                                onClick = { onNavigate(OrbitNavKey.Settings) }
                            )
                        }
                    )

                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = startPad, end = endPad, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 4-Stat Summary Hero Row/Grid (Income, Giving, Bills, Subscriptions)
                            item(key = "upcoming_summary_stats", contentType = "summary_stats") {
                                UpcomingSummaryStatsGrid(
                                    incomeCents = state.upcomingIncomeCents,
                                    givingCents = state.upcomingGivingCents,
                                    billsCents = state.upcomingBillsCents,
                                    subscriptionsCents = state.upcomingSubscriptionsCents,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Projected Cash Flow Hero Card
                            item(key = "upcoming_cashflow_card", contentType = "cashflow_card") {
                                UpcomingCashFlowCard(
                                    summary = state.cashFlowSummary,
                                    selectedPeriod = state.cashFlowPeriod,
                                    onPeriodChanged = { viewModel.setCashFlowPeriod(it) },
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Calendar Date Strip
                            item(key = "upcoming_calendar_strip", contentType = "calendar_strip") {
                                UpcomingCalendarStrip(
                                    selectedDate = state.selectedDateFilter,
                                    items = state.activeItems,
                                    onSelectDate = { viewModel.setSelectedDateFilter(it) },
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            if (state.cardPayments.isNotEmpty()) {
                                item(key = "upcoming_card_payments_due", contentType = "card_payments_due") {
                                    CardPaymentsDueCard(
                                        cardPayments = state.cardPayments,
                                        onNavigate = onNavigate,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            item(key = "upcoming_filter_strip", contentType = "filter_strip") {
                                UpcomingFilterStrip(
                                    selectedFilter = state.selectedFilter,
                                    onFilterSelected = { viewModel.setFilter(it) },
                                    suggestionsCount = state.candidates.size,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            // Action Bar: Add from Transaction
                            if (state.selectedFilter != UpcomingFilter.SUGGESTIONS) {
                                item(key = "upcoming_add_from_tx_bar", contentType = "action_bar") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.openAddFromTxModal() },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Add From Transaction",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Virtualized Recurring Timeline / Bento Lanes / Suggestions
                            upcomingTimelineItems(
                                items = state.items,
                                candidates = state.candidates,
                                categoryMap = state.categoryMap,
                                accountMap = state.accountMap,
                                selectedFilter = state.selectedFilter,
                                selectedDateFilter = state.selectedDateFilter,
                                onItemClick = { item ->
                                    if (isTwoPane) {
                                        viewModel.selectUpcomingForInspect(item.id)
                                    } else {
                                        viewModel.openEditUpcoming(item)
                                    }
                                },
                                onMarkPaid = { viewModel.openReconcileDialog(it) },
                                onAddClick = { viewModel.openAddUpcoming(it) },
                                onReviewCandidate = { viewModel.reviewCandidate(it) },
                                onDismissCandidate = { viewModel.dismissCandidate(it) },
                                projectedAmounts = state.projectedAmounts,
                                inspectingItemId = if (isTwoPane) state.inspectingItemId else null
                            )
                        }
                    }

                    if (isTwoPane) {
                        OrbitMorphingSplitLayout(
                            isDetailOpen = isDetailOpen,
                            onDismissDetail = { viewModel.dismissInspectorPane() },
                            masterContent = masterList,
                            detailContent = {
                                UpcomingDetailInspectorPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onClosePane = { viewModel.dismissInspectorPane() }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        masterList()
                    }
                }
            }
        }

        // Compact Phone Modal Editor Sheet
        if (!isTwoPane && state.showEditorSheet) {
            RecurringItemEditorSheet(
                item = state.editingItem,
                categories = state.categories,
                accounts = state.accounts,
                onSave = { viewModel.saveUpcoming(it) },
                onDelete = { viewModel.deleteUpcoming(it) },
                onDismiss = { viewModel.closeEditorSheet() },
                onViewHistory = { viewModel.openHistoryModal(it) }
            )
        }
    }

    // Reconcile Occurrence Dialog
    val reconcilingItem = state.reconcilingItem
    if (state.showReconcileDialog && reconcilingItem != null) {
        ReconcileOccurrenceDialog(
            item = reconcilingItem,
            recentTransactions = state.recentTransactions,
            onMarkPaid = { id, date, matchedTxnId ->
                viewModel.reconcileOccurrencePaid(id, date, matchedTxnId)
            },
            onSkipMissed = { id, date ->
                viewModel.reconcileOccurrenceMissed(id, date)
            },
            onResetPending = { id ->
                viewModel.reconcileOccurrenceReset(id)
            },
            onDismiss = { viewModel.closeReconcileDialog() }
        )
    }

    // Add From Transaction Modal Sheet
    if (state.showAddFromTxModal) {
        AddFromTransactionSheet(
            transactions = state.recentTransactions,
            categoryMap = state.categoryMap,
            accountMap = state.accountMap,
            onSelectTransaction = { viewModel.createFromTransaction(it) },
            onDismiss = { viewModel.closeAddFromTxModal() }
        )
    }

    // Past History Modal Sheet
    val histItem = state.historyItem
    if (state.showHistoryModal && histItem != null) {
        RecurringHistoryModal(
            item = histItem,
            matches = state.matchingHistory,
            categoryMap = state.categoryMap,
            accountMap = state.accountMap,
            onDismiss = { viewModel.closeHistoryModal() }
        )
    }
}
