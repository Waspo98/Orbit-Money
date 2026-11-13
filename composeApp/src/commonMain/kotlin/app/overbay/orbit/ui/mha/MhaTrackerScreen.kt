package app.overbay.orbit.ui.mha

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.*
import app.overbay.orbit.ui.mha.components.MhaAutoDefaultsSection
import app.overbay.orbit.ui.mha.components.MhaTaxSavingsCard
import app.overbay.orbit.ui.mha.components.MhaTransactionsTab
import app.overbay.orbit.ui.mha.dialogs.MhaConfigContent
import app.overbay.orbit.ui.mha.dialogs.MhaConfigSheet
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
    val currentYear = remember { LocalDateUtils.todayLocalDate().year }
    val maxNavYear = maxOf(currentYear, state.availableYears.maxOrNull() ?: currentYear)
    val canGoNext = state.selectedYear < maxNavYear

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
                    subtitle = "Calculate projected MHA tax savings and track eligible housing expenses",
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, barHeight ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            PeriodNavigatorPill(
                                label = "Tax Year ${state.selectedYear}",
                                compactLabel = "${state.selectedYear}",
                                isCompact = isCompact,
                                onPrevious = { viewModel.setYear(state.selectedYear - 1) },
                                onNext = { viewModel.setYear(state.selectedYear + 1) },
                                canGoNext = canGoNext,
                                onClickLabel = { showYearMenu = true },
                                modifier = Modifier.fillMaxSize()
                            )

                            DropdownMenu(
                                expanded = showYearMenu,
                                onDismissRequest = { showYearMenu = false }
                            ) {
                                state.availableYears.forEach { yr ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "$yr",
                                                fontWeight = if (yr == state.selectedYear) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setYear(yr)
                                            showYearMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        CanopyIconButton(
                            icon = OrbitIcons.ViewSliders,
                            contentDescription = "Configure MHA Limits",
                            onClick = {
                                if (state.showConfigSheet) viewModel.closeConfigSheet() else viewModel.openConfigSheet()
                            },
                            buttonSize = barHeight,
                            isActive = state.showConfigSheet
                        )

                        CanopySettingsButton(
                            buttonSize = barHeight,
                            onClick = { onNavigate(OrbitNavKey.Settings) }
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

                val summaryCards: LazyListScope.() -> Unit = {
                    item {
                        MhaTaxSavingsCard(
                            taxResult = state.taxCalculationResult,
                            eligibleExpensesCents = state.actualEligibleExpensesCents,
                            eligibleTransactionCount = state.eligibleTransactions.size,
                            selectedYear = state.selectedYear,
                            taxProfile = state.taxProfile,
                            onEditSettings = {
                                if (isTwoPane && state.showConfigSheet) viewModel.closeConfigSheet() else viewModel.openConfigSheet()
                            }
                        )
                    }
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
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
                        // Foldable / Tablet / Wide Two-Pane Split
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left Pane: Summary Card + Auto Defaults
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                summaryCards()
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

                            // Right Pane: Morphs between Transaction Review Queue and MHA Settings Content
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
                                            designatedCapCents = state.designatedCapCents,
                                            fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                                            mhaEnabled = state.mhaEnabled,
                                            onToggleMhaEnabled = { viewModel.setMhaEnabled(it) },
                                            onDismiss = { viewModel.closeConfigSheet() },
                                            onSave = { updatedProfile, cap, fmrv ->
                                                viewModel.updateMhaConfig(cap, fmrv, updatedProfile)
                                            }
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        MhaTransactionsTab(
                                            transactions = state.visibleTransactions,
                                            selectedTab = state.selectedTabFilter,
                                            eligibleCount = state.eligibleTransactions.size,
                                            reviewQueueCount = state.reviewQueueTransactions.size,
                                            allCount = state.allYearOutflows.size,
                                            searchQuery = viewModel.searchQueryInput,
                                            onTabChange = { viewModel.setTabFilter(it) },
                                            onSearchChange = { viewModel.setSearchQuery(it) },
                                            onToggleEligible = { id, current -> viewModel.toggleTransactionEligibility(id, current) },
                                            onResetEligible = { id -> viewModel.resetTransactionMhaOverride(id) },
                                            onIgnoreFromBudget = { id, ignored -> viewModel.setTransactionIgnored(id, ignored) },
                                            onInspectTransaction = { id -> onNavigate(OrbitNavKey.Transactions(transactionId = id)) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Compact Mobile Single-Column Scroll
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            summaryCards()

                            item {
                                MhaTransactionsTab(
                                    transactions = state.visibleTransactions,
                                    selectedTab = state.selectedTabFilter,
                                    eligibleCount = state.eligibleTransactions.size,
                                    reviewQueueCount = state.reviewQueueTransactions.size,
                                    allCount = state.allYearOutflows.size,
                                    searchQuery = viewModel.searchQueryInput,
                                    onTabChange = { viewModel.setTabFilter(it) },
                                    onSearchChange = { viewModel.setSearchQuery(it) },
                                    onToggleEligible = { id, current -> viewModel.toggleTransactionEligibility(id, current) },
                                    onResetEligible = { id -> viewModel.resetTransactionMhaOverride(id) },
                                    onIgnoreFromBudget = { id, ignored -> viewModel.setTransactionIgnored(id, ignored) },
                                    onInspectTransaction = { id -> onNavigate(OrbitNavKey.Transactions(transactionId = id)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isTwoPane && state.showConfigSheet) {
            MhaConfigSheet(
                taxProfile = state.taxProfile,
                designatedCapCents = state.designatedCapCents,
                fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                mhaEnabled = state.mhaEnabled,
                onToggleMhaEnabled = { viewModel.setMhaEnabled(it) },
                onDismiss = { viewModel.closeConfigSheet() },
                onSave = { updatedProfile, cap, fmrv ->
                    viewModel.updateMhaConfig(cap, fmrv, updatedProfile)
                }
            )
        }
    }
}
