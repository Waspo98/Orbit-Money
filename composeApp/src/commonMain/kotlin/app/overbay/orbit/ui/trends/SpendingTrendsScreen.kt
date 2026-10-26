package app.overbay.orbit.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.trends.components.CategoryBreakdownList
import app.overbay.orbit.ui.trends.components.TopMerchantsCard
import app.overbay.orbit.ui.trends.components.TrendsChartSection

@Composable
fun SpendingTrendsScreen(
    viewModel: SpendingTrendsViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Spending Trends",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Income vs expense velocity & category breakdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Hero Period Net Flow Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL NET CASH FLOW (${state.selectedPeriod.label})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val netColor = if (state.totalPeriodNetCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
                        Text(
                            text = (if (state.totalPeriodNetCents > 0L) "+" else "") + MoneyUtils.formatCents(state.totalPeriodNetCents),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = netColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Income: ${MoneyUtils.formatCents(state.overview.totalPeriodIncomeCents)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrbitColorTokens.Green,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Expenses: ${MoneyUtils.formatCents(state.overview.totalPeriodSpendingCents)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrbitColorTokens.Red,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Interactive Bar Chart Section
            item {
                TrendsChartSection(
                    state = state,
                    onSelectPeriod = { viewModel.setPeriod(it) },
                    onSelectChartMode = { viewModel.setChartMode(it) },
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onScrubBar = { idx, item -> viewModel.onScrubBar(idx, item) }
                )
            }

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
