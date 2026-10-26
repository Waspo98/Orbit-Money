package app.overbay.orbit.ui.mha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.PeriodNavigatorPill
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.mha.components.MhaTaxSavingsCard
import app.overbay.orbit.ui.mha.components.MhaThreeLimitHeroCard
import app.overbay.orbit.ui.mha.components.MhaTransactionsTab
import app.overbay.orbit.ui.mha.dialogs.MhaConfigSheet
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun MhaTrackerScreen(
    viewModel: MhaTrackerViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                canNavigateBack = true,
                onBack = { onNavigate(OrbitNavKey.Dashboard) },
                scrollState = heroScrollState,
                expandedToolbar = {
                    PeriodNavigatorPill(
                        label = "${state.selectedYear}",
                        onPrevious = { viewModel.setYear(state.selectedYear - 1) },
                        onNext = { viewModel.setYear(state.selectedYear + 1) },
                        canGoNext = state.selectedYear < 2030,
                        onClickLabel = { viewModel.setYear(2026) }
                    )
                }
            )

            if (posture.supportsTwoPane) {
                // Foldable / Tablet Two-Pane Split
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Hero 3-Limit Card + Tax Savings Card
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            MhaThreeLimitHeroCard(
                                actualSpentCents = state.actualEligibleHousingSpentCents,
                                designatedCapCents = state.designatedCapCents,
                                fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                                excludableCapCents = state.excludableAllowanceCents,
                                bindingLimit = state.limitingFactor,
                                onConfigureClick = { viewModel.openConfigSheet() }
                            )
                        }
                        item {
                            MhaTaxSavingsCard(
                                taxResult = state.taxCalculationResult,
                                projectedAnnualSavingsCents = state.projectedAnnualSavingsCents,
                                taxProfile = state.taxProfile
                            )
                        }
                    }

                    // Right Pane: Transaction Review Queue
                    Column(
                        modifier = Modifier.weight(1.2f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MhaTransactionsTab(
                            transactions = state.visibleTransactions,
                            selectedTab = state.selectedTabFilter,
                            eligibleCount = state.eligibleTransactions.size,
                            reviewQueueCount = state.reviewQueueTransactions.size,
                            allCount = state.allYearOutflows.size,
                            searchQuery = state.searchQuery,
                            onTabChange = { viewModel.setTabFilter(it) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onToggleEligible = { id, current -> viewModel.toggleTransactionEligibility(id, current) },
                            onResetEligible = { id -> viewModel.resetTransactionEligibility(id) }
                        )
                    }
                }
            } else {
                // Compact Mobile Single-Column Scroll
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        MhaThreeLimitHeroCard(
                            actualSpentCents = state.actualEligibleHousingSpentCents,
                            designatedCapCents = state.designatedCapCents,
                            fairMarketRentalValueCents = state.fairMarketRentalValueCents,
                            excludableCapCents = state.excludableAllowanceCents,
                            bindingLimit = state.limitingFactor,
                            onConfigureClick = { viewModel.openConfigSheet() }
                        )
                    }

                    item {
                        MhaTaxSavingsCard(
                            taxResult = state.taxCalculationResult,
                            projectedAnnualSavingsCents = state.projectedAnnualSavingsCents,
                            taxProfile = state.taxProfile
                        )
                    }

                    item {
                        MhaTransactionsTab(
                            transactions = state.visibleTransactions,
                            selectedTab = state.selectedTabFilter,
                            eligibleCount = state.eligibleTransactions.size,
                            reviewQueueCount = state.reviewQueueTransactions.size,
                            allCount = state.allYearOutflows.size,
                            searchQuery = state.searchQuery,
                            onTabChange = { viewModel.setTabFilter(it) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onToggleEligible = { id, current -> viewModel.toggleTransactionEligibility(id, current) },
                            onResetEligible = { id -> viewModel.resetTransactionEligibility(id) }
                        )
                    }
                }
            }
        }
    }

    if (state.showConfigSheet) {
        MhaConfigSheet(
            taxProfile = state.taxProfile,
            designatedCapCents = state.designatedCapCents,
            fairMarketRentalValueCents = state.fairMarketRentalValueCents,
            onDismiss = { viewModel.closeConfigSheet() },
            onSave = { updatedProfile, cap, fmrv ->
                viewModel.updateMhaConfig(cap, fmrv, updatedProfile)
            }
        )
    }
}
