package app.overbay.orbit.ui.upcoming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.upcoming.components.UpcomingCalendarStrip
import app.overbay.orbit.ui.upcoming.components.UpcomingDetailInspectorPane
import app.overbay.orbit.ui.upcoming.components.UpcomingSuggestionBanner
import app.overbay.orbit.ui.upcoming.components.UpcomingTimelineList
import app.overbay.orbit.ui.upcoming.sheets.ReconcileOccurrenceDialog
import app.overbay.orbit.ui.upcoming.sheets.RecurringItemEditorSheet
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (state.inspectingItem != null || state.showEditorSheet)

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
                    var filterMenuExpanded by remember { mutableStateOf(false) }

                    MorphingPageHero(
                        kicker = "MONEY CALENDAR",
                        title = "Upcoming",
                        subtitle = "Projected bills, subscriptions, income, and cash flow.",
                        canNavigateBack = true,
                        onBack = onBack,
                        scrollState = heroScrollState,
                        canopyBar = { _, isCompact, barHeight ->
                            val netProjection = MoneyUtils.formatCents(state.netCashflowProjectionCents, showCents = false, signed = true)
                            val dateFilter = state.selectedDateFilter
                            CanopySurfacePill(
                                expandedText = if (dateFilter != null) "Date Filter: $dateFilter (Tap to clear)" else "30-Day Net · $netProjection",
                                compactText = if (dateFilter != null) dateFilter else "Net $netProjection",
                                isCompact = isCompact,
                                leadingIcon = OrbitIcons.Upcoming,
                                onClick = if (dateFilter != null) {
                                    { viewModel.setSelectedDateFilter(null) }
                                } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            val activeFilterCount = (if (state.filterOption != UpcomingFilterOption.ALL) 1 else 0) +
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

                                DropdownMenu(
                                    expanded = filterMenuExpanded,
                                    onDismissRequest = { filterMenuExpanded = false }
                                ) {
                                    val filterOptions = listOf(
                                        UpcomingFilterOption.ALL to "All Recurring",
                                        UpcomingFilterOption.BILLS to "Bills & Expenses",
                                        UpcomingFilterOption.INCOME to "Income"
                                    )
                                    filterOptions.forEach { (option, label) ->
                                        val selected = state.filterOption == option
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
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
                                                viewModel.setFilterOption(option)
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
                    )

                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = startPad, end = endPad, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                UpcomingCalendarStrip(
                                    selectedDate = state.selectedDateFilter,
                                    items = state.activeItems,
                                    onSelectDate = { viewModel.setSelectedDateFilter(it) }
                                )
                            }

                            if (state.candidates.isNotEmpty()) {
                                item {
                                    UpcomingSuggestionBanner(
                                        candidates = state.candidates,
                                        onAccept = { viewModel.acceptCandidate(it) },
                                        onDismiss = { viewModel.dismissCandidate(it) }
                                    )
                                }
                            }

                            item {
                                UpcomingTimelineList(
                                    items = state.items,
                                    categoryMap = state.categoryMap,
                                    accountMap = state.accountMap,
                                    filterOption = state.filterOption,
                                    onFilterChanged = { viewModel.setFilterOption(it) },
                                    onItemClick = { item ->
                                        if (isTwoPane) {
                                            viewModel.selectUpcomingForInspect(item.id)
                                        } else {
                                            viewModel.openEditUpcoming(item)
                                        }
                                    },
                                    onMarkPaid = { viewModel.openReconcileDialog(it) },
                                    onAddClick = { viewModel.openAddUpcoming() },
                                    inspectingItemId = if (isTwoPane) state.inspectingItemId else null
                                )
                            }
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
                onDismiss = { viewModel.closeEditorSheet() }
            )
        }
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
