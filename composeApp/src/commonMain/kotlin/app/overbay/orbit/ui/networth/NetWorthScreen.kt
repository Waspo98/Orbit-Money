package app.overbay.orbit.ui.networth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.networth.components.AssetLiabilityBreakdownCard
import app.overbay.orbit.ui.networth.components.NetWorthChartCard
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun NetWorthScreen(
    viewModel: NetWorthViewModel,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                MorphingPageHero(
                    kicker = "BALANCE SHEET",
                    title = "Net Worth",
                    subtitle = "Assets minus liabilities",
                    canNavigateBack = true,
                    onBack = onBack,
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, _ ->
                        CanopySegmentedBar(
                            items = NetWorthPeriod.entries,
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

                // Scrollable Area Chart and Breakdown Cards
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Interactive Area Chart Card (Full Width)
                    item {
                        NetWorthChartCard(
                            points = state.filteredPoints,
                            selectedPeriod = state.selectedPeriod,
                            scrubbedPoint = state.scrubbedPoint,
                            onSelectPeriod = { viewModel.setPeriod(it) },
                            onPointScrubbed = { viewModel.selectScrubPoint(it) }
                        )
                    }

                    // Asset & Liability Breakdown Card (2-column split on wide screens)
                    item {
                        AssetLiabilityBreakdownCard(
                            snapshot = state.snapshot,
                            isTwoPane = isTwoPane
                        )
                    }
                }
            }
        }
    }
}
