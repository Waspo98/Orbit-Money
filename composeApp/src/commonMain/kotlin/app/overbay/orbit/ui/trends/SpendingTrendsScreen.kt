package app.overbay.orbit.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.CanopySegmentedBar
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.trends.components.CategoryBreakdownList
import app.overbay.orbit.ui.trends.components.TopMerchantsCard
import app.overbay.orbit.ui.trends.components.TrendsChartSection
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun SpendingTrendsScreen(
    viewModel: SpendingTrendsViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()

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
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            if (!state.hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        title = "No Spending Trends Yet",
                        description = "Import or add transactions to see income, expenses, and category history over time.",
                        actionButtonText = "Go to Transactions",
                        onActionClick = { onNavigate(OrbitNavKey.Transactions()) }
                    )
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                MorphingPageHero(
                    kicker = "HISTORY",
                    title = "Spending Trends",
                    subtitle = "Spending patterns and category breakdown over time",
                    canNavigateBack = true,
                    onBack = onBack,
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, _ ->
                        CanopySegmentedBar(
                            items = TrendsPeriod.entries,
                            selectedItem = state.selectedPeriod,
                            labelProvider = { it.label },
                            onSelect = { viewModel.setPeriod(it) },
                            isCompact = isCompact,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                )

                // Scrollable Charts and Details
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Interactive Bar Chart Section (Full Width)
                    item {
                        TrendsChartSection(
                            state = state,
                            onSelectPeriod = { viewModel.setPeriod(it) },
                            onSelectChartMode = { viewModel.setChartMode(it) },
                            onSelectCategory = { viewModel.selectCategory(it) },
                            onScrubBar = { idx, item -> viewModel.onScrubBar(idx, item) }
                        )
                    }

                    if (isTwoPane) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                CategoryBreakdownList(
                                    categoryTrends = state.overview.categoryTrends,
                                    selectedCategoryId = state.selectedCategoryId,
                                    onSelectCategory = { categoryId ->
                                        viewModel.selectCategory(categoryId)
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                TopMerchantsCard(
                                    topMerchants = state.overview.topMerchants,
                                    totalPeriodSpendingCents = state.overview.totalPeriodSpendingCents,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // Category Breakdown List
                        item {
                            CategoryBreakdownList(
                                categoryTrends = state.overview.categoryTrends,
                                selectedCategoryId = state.selectedCategoryId,
                                onSelectCategory = { categoryId ->
                                    viewModel.selectCategory(categoryId)
                                }
                            )
                        }

                        // Top Merchants Card
                        item {
                            TopMerchantsCard(
                                topMerchants = state.overview.topMerchants,
                                totalPeriodSpendingCents = state.overview.totalPeriodSpendingCents
                            )
                        }
                    }
                }
            }
        }
    }
}
