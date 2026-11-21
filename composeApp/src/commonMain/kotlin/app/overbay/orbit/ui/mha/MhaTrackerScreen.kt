package app.overbay.orbit.ui.mha

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.components.rememberOrbitLazyListState
import app.overbay.orbit.ui.mha.components.MhaAutoDefaultsSection
import app.overbay.orbit.ui.mha.components.MhaCanopyBar
import app.overbay.orbit.ui.mha.components.MhaTaxSavingsCard
import app.overbay.orbit.ui.mha.components.MhaTransactionsHeaderAndFilters
import app.overbay.orbit.ui.mha.components.mhaCountedTransactionsItems
import app.overbay.orbit.ui.mha.dialogs.MhaConfigContent
import app.overbay.orbit.ui.mha.dialogs.MhaConfigSheet
import app.overbay.orbit.ui.mha.dialogs.MhaTransactionActionSheet
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun MhaTrackerScreen(
    viewModel: MhaTrackerViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val heroScrollState = rememberHeroScrollState()

    var showYearMenu by remember { mutableStateOf(false) }
    val maxNavYear = maxOf(state.currentYear, state.availableYears.maxOrNull() ?: state.currentYear)
    val canGoNext = state.selectedYear < maxNavYear

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(
        state.isLoading,
        state.showConfigSheet,
        state.selectedYear,
        state.taxCalculationResult?.effectiveSavingsRatePercent,
        state.designatedCapCents,
        state.fairMarketRentalValueCents
    ) {
        if (!state.isLoading && !state.showConfigSheet) {
            viewModel.recordCurrentYearRateIfChanged()
        }
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val computedEffectivePct = state.taxCalculationResult?.effectiveSavingsRatePercent
            ?: (state.taxProfile.simpleFederalRatePercent + state.taxProfile.simpleStateRatePercent)
        val onSaveYearConfig: (app.overbay.orbit.data.model.MhaTaxProfile, Long?, Long?, Double?, Boolean) -> Unit =
            { updatedProfile, cap, fmrv, customRate, lockRate ->
                viewModel.saveYearConfig(
                    year = state.selectedYear,
                    updatedProfile = updatedProfile,
                    designationCapCents = cap,
                    fairMarketRentalValueCents = fmrv,
                    customEffectiveRatePercent = customRate,
                    lockYearRate = lockRate
                )
            }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                MorphingPageHero(
                    kicker = "MINISTERIAL HOUSING ALLOWANCE",
                    title = "MHA Tracker",
                    collapsedTitle = "MHA",
                    subtitle = "Track counted housing expenses and projected tax savings",
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, barHeight ->
                        MhaCanopyBar(
                            selectedYear = state.selectedYear,
                            availableYears = state.availableYears,
                            canGoNext = canGoNext,
                            isCompact = isCompact,
                            barHeight = barHeight,
                            showYearMenu = showYearMenu,
                            onToggleYearMenu = { showYearMenu = it },
                            onSelectYear = { viewModel.setYear(it) },
                            showConfigSheet = state.showConfigSheet,
                            onToggleConfigSheet = {
                                if (state.showConfigSheet) viewModel.closeConfigSheet() else viewModel.openConfigSheet()
                            },
                            onOpenSettings = { onNavigate(OrbitNavKey.Settings()) }
                        )
                    }
                )

                OrbitSegmentedTabRow(
                    items = MhaScreenTab.entries,
                    selected = state.activeScreenTab,
                    onSelect = { viewModel.setActiveScreenTab(it) },
                    label = { tab ->
                        if (tab == MhaScreenTab.TRACKER) "Tracker & Expenses" else "Auto-Defaults"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                val summaryCard: LazyListScope.() -> Unit = {
                    item(key = "mha_summary_hero") {
                        MhaTaxSavingsCard(
                            taxResult = state.taxCalculationResult,
                            eligibleExpensesCents = state.actualEligibleExpensesCents,
                            eligibleTransactionCount = state.eligibleTransactions.size,
                            excludableAllowanceCents = state.excludableAllowanceCents,
                            designatedCapCents = state.designatedCapCents,
                            fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                            selectedYear = state.selectedYear,
                            currentYear = state.currentYear,
                            taxProfile = state.taxProfile,
                            activeYearTaxRecord = state.activeYearTaxRecord,
                            taxRateSourceLabel = state.taxRateSourceLabel,
                            isUsingStoredYearRate = state.isUsingStoredYearRate,
                            isHistoricalYearWithoutSavedRate = state.isHistoricalYearWithoutSavedRate,
                            onEditSettings = {
                                if (isTwoPane && state.showConfigSheet) viewModel.closeConfigSheet() else viewModel.openConfigSheet()
                            }
                        )
                    }
                }

                val transactionHeaderItem: LazyListScope.() -> Unit = {
                    item(key = "mha_txns_header") {
                        MhaTransactionsHeaderAndFilters(
                            visibleCount = state.visibleTransactions.size,
                            visibleTotalCents = state.visibleTotalCents,
                            actualEligibleTotalCents = state.actualEligibleExpensesCents,
                            categoryBreakdown = state.categoryBreakdown,
                            selectedCategoryId = state.selectedCategoryId,
                            showExcludedInList = state.showExcludedInList,
                            searchQuery = viewModel.searchQueryInput,
                            onSelectCategory = viewModel::setSelectedCategory,
                            onToggleShowExcluded = viewModel::setShowExcludedInList,
                            onSearchChange = viewModel::setSearchQuery
                        )
                    }
                }

                val transactionRows: LazyListScope.() -> Unit = {
                    mhaCountedTransactionsItems(
                        groupedMonths = state.groupedVisibleMonths,
                        selectedYear = state.selectedYear,
                        showExcludedInList = state.showExcludedInList,
                        searchQuery = state.searchQuery,
                        selectedCategoryId = state.selectedCategoryId,
                        onInspectItem = { item -> viewModel.openTransactionActionSheet(item) },
                        onShowExcluded = { viewModel.setShowExcludedInList(true) },
                        onClearFilters = {
                            viewModel.setSelectedCategory(null)
                            viewModel.setSearchQuery("")
                        }
                    )
                }

                AnimatedContent(
                    targetState = state.activeScreenTab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(OrbitSprings.IntOffsetSpatialDefault) { dir * it / 4 } +
                            fadeIn(OrbitSprings.FloatEffectsDefault)) togetherWith
                            (slideOutHorizontally(OrbitSprings.IntOffsetSpatialDefault) { -dir * it / 4 } +
                                fadeOut(OrbitSprings.FloatEffectsFast))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "MhaScreenTabTransition"
                ) { activeTab ->
                    if (activeTab == MhaScreenTab.DEFAULTS) {
                        val defaultsListState = rememberOrbitLazyListState("defaults")
                        LazyColumn(
                            state = defaultsListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                MhaAutoDefaultsSection(
                                    accounts = state.accounts,
                                    categories = state.categories,
                                    onToggleAccountDefault = viewModel::setMhaAccountDefault,
                                    onToggleCategoryDefault = viewModel::setMhaCategoryDefault,
                                    onToggleCategoryIgnoreDefault = viewModel::setMhaCategoryIgnoreDefault
                                )
                            }
                        }
                    } else if (isTwoPane) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val leftListState = rememberOrbitLazyListState("two_pane_left")
                            LazyColumn(
                                state = leftListState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                summaryCard()
                                item {
                                    MhaAutoDefaultsSection(
                                        accounts = state.accounts,
                                        categories = state.categories,
                                        onToggleAccountDefault = viewModel::setMhaAccountDefault,
                                        onToggleCategoryDefault = viewModel::setMhaCategoryDefault,
                                        onToggleCategoryIgnoreDefault = viewModel::setMhaCategoryIgnoreDefault
                                    )
                                }
                            }

                            AnimatedContent(
                                targetState = state.showConfigSheet,
                                transitionSpec = {
                                    fadeIn(OrbitSprings.FloatEffectsDefault) togetherWith
                                        fadeOut(OrbitSprings.FloatEffectsFast)
                                },
                                modifier = Modifier.weight(1.2f).fillMaxSize(),
                                label = "MhaRightPaneMorph"
                            ) { showConfig ->
                                if (showConfig) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        MhaConfigContent(
                                            taxProfile = state.taxProfile,
                                            selectedYear = state.selectedYear,
                                            currentYear = state.currentYear,
                                            activeYearRecord = state.activeYearTaxRecord,
                                            computedEffectiveRatePercent = computedEffectivePct,
                                            designatedCapCents = state.designatedCapCents,
                                            fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                                            mhaEnabled = state.mhaEnabled,
                                            onToggleMhaEnabled = { viewModel.setMhaEnabled(it) },
                                            onDismiss = { viewModel.closeConfigSheet() },
                                            onSave = onSaveYearConfig
                                        )
                                    }
                                } else {
                                    val rightListState = rememberOrbitLazyListState("two_pane_right_${state.selectedYear}")
                                    LazyColumn(
                                        state = rightListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        transactionHeaderItem()
                                        transactionRows()
                                    }
                                }
                            }
                        }
                    } else {
                        val compactListState = rememberOrbitLazyListState("compact_${state.selectedYear}")
                        LazyColumn(
                            state = compactListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            summaryCard()
                            transactionHeaderItem()
                            transactionRows()
                        }
                    }
                }
            }
        }

        val inspecting = state.inspectingTransaction
        if (inspecting != null) {
            MhaTransactionActionSheet(
                item = inspecting,
                onDismiss = { viewModel.openTransactionActionSheet(null) },
                onToggleEligible = { viewModel.toggleTransactionEligibility(inspecting.id, inspecting.isEligible) },
                onResetOverride = { viewModel.resetTransactionMhaOverride(inspecting.id) },
                onToggleIgnore = { viewModel.setTransactionIgnored(inspecting.id, !inspecting.transaction.effectiveIsIgnored) },
                onOpenTransaction = { onNavigate(OrbitNavKey.Transactions(transactionId = inspecting.id)) }
            )
        }

        if (!isTwoPane && state.showConfigSheet) {
            MhaConfigSheet(
                taxProfile = state.taxProfile,
                selectedYear = state.selectedYear,
                currentYear = state.currentYear,
                activeYearRecord = state.activeYearTaxRecord,
                computedEffectiveRatePercent = computedEffectivePct,
                designatedCapCents = state.designatedCapCents,
                fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                mhaEnabled = state.mhaEnabled,
                onToggleMhaEnabled = { viewModel.setMhaEnabled(it) },
                onDismiss = { viewModel.closeConfigSheet() },
                onSave = onSaveYearConfig
            )
        }
    }
}
