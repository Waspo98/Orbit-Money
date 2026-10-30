package app.overbay.orbit.ui.networth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.CanopySegmentedBar
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.networth.components.AccountContributionsCard
import app.overbay.orbit.ui.networth.components.AssetLiabilityBreakdownCard
import app.overbay.orbit.ui.networth.components.NetWorthChartCard
import app.overbay.orbit.ui.networth.components.NetWorthSignalsCard
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
                    canopyBar = { _, isCompact, barHeight ->
                        CanopySegmentedBar(
                            items = NetWorthHorizon.entries,
                            selectedItem = state.selectedHorizon,
                            labelProvider = { it.label },
                            onSelect = { viewModel.setHorizon(it) },
                            isCompact = isCompact,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        CanopySettingsButton(
                            buttonSize = barHeight,
                            onClick = { onNavigate(OrbitNavKey.Settings) }
                        )
                    },
                    persistentBottomBar = {
                        NetWorthHeroKpiStrip(
                            netWorthCents = state.snapshot.netWorthCents,
                            thisMonthDeltaCents = state.thisMonthDeltaCents,
                            ytdDeltaCents = state.ytdDeltaCents,
                            debtSharePercent = state.debtSharePercent
                        )
                    }
                )

                if (state.accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateCard(
                            title = "No Accounts Tracked",
                            description = "Add your financial accounts to reconstruct historical net worth, monitor asset allocation, and track debts.",
                            actionButtonText = "Add First Account",
                            onActionClick = { onNavigate(OrbitNavKey.Accounts()) }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Interactive Area Chart Card (Full Width)
                        item {
                            NetWorthChartCard(
                                points = state.filteredPoints,
                                selectedPeriod = state.selectedHorizon,
                                scrubbedPoint = state.scrubbedPoint,
                                onSelectPeriod = { viewModel.setHorizon(it) },
                                onPointScrubbed = { viewModel.selectScrubPoint(it) }
                            )
                        }

                        // Asset & Liability Breakdown Card
                        item {
                            AssetLiabilityBreakdownCard(
                                snapshot = state.snapshot,
                                isTwoPane = isTwoPane
                            )
                        }

                        // Signals & Contributions Card
                        if (isTwoPane) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    NetWorthSignalsCard(
                                        largestAsset = state.largestAsset,
                                        largestLiability = state.largestLiability,
                                        trackedAccountCount = state.trackedAccountCount,
                                        modifier = Modifier.weight(1f)
                                    )

                                    AccountContributionsCard(
                                        contributions = state.topContributingAccounts,
                                        onAccountClick = { acctId ->
                                            onNavigate(OrbitNavKey.Accounts(accountId = acctId))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            item {
                                NetWorthSignalsCard(
                                    largestAsset = state.largestAsset,
                                    largestLiability = state.largestLiability,
                                    trackedAccountCount = state.trackedAccountCount
                                )
                            }

                            item {
                                AccountContributionsCard(
                                    contributions = state.topContributingAccounts,
                                    onAccountClick = { acctId ->
                                        onNavigate(OrbitNavKey.Accounts(accountId = acctId))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthHeroKpiStrip(
    netWorthCents: Cents,
    thisMonthDeltaCents: Cents,
    ytdDeltaCents: Cents,
    debtSharePercent: Double
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KpiItem(
                label = "Current",
                value = MoneyUtils.formatCents(netWorthCents, showCents = false),
                valueColor = if (netWorthCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
            )
            KpiItem(
                label = "This Month",
                value = (if (thisMonthDeltaCents > 0L) "+" else "") + MoneyUtils.formatCents(thisMonthDeltaCents, showCents = false),
                valueColor = if (thisMonthDeltaCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
            )
            KpiItem(
                label = "YTD",
                value = (if (ytdDeltaCents > 0L) "+" else "") + MoneyUtils.formatCents(ytdDeltaCents, showCents = false),
                valueColor = if (ytdDeltaCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
            )
            KpiItem(
                label = "Debt Share",
                value = "${debtSharePercent.toInt()}%",
                valueColor = if (debtSharePercent > 50.0) OrbitColorTokens.Amber else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KpiItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularNumStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor
        )
    }
}
