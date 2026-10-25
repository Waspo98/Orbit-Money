package app.overbay.orbit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.PeriodNavigatorPill
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.dashboard.cards.AccountsOverviewCard
import app.overbay.orbit.ui.dashboard.cards.BudgetPulseCard
import app.overbay.orbit.ui.dashboard.cards.GoalsProgressCard
import app.overbay.orbit.ui.dashboard.cards.NetCashflowHeroCard
import app.overbay.orbit.ui.dashboard.cards.RecentTransactionsCard
import app.overbay.orbit.ui.dashboard.cards.UpcomingBillsCard
import app.overbay.orbit.ui.dashboard.dialogs.DashboardCustomizeSheet
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Main dashboard screen supporting responsive 1-column scroll on compact phones
 * and 2-column masonry on foldables and wide tablets.
 * Conforms strictly to the <= 6 parameter rule and < 350-line ceiling.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                DashboardAction.Refresh -> viewModel.refresh()
                DashboardAction.PreviousMonth -> viewModel.onPreviousMonth()
                DashboardAction.NextMonth -> viewModel.onNextMonth()
                DashboardAction.OpenCustomizeSheet -> viewModel.openCustomization()
                DashboardAction.CloseCustomizeSheet -> viewModel.closeCustomization()
                is DashboardAction.ToggleCardVisibility -> viewModel.toggleCardVisibility(action.cardType)
                is DashboardAction.MoveCardUp -> viewModel.moveCardUp(action.cardType)
                is DashboardAction.MoveCardDown -> viewModel.moveCardDown(action.cardType)
                DashboardAction.ResetCardsToDefault -> viewModel.resetCardsToDefault()
                is DashboardAction.MarkBillPaid -> viewModel.markUpcomingPaid(action.upcomingItemId, action.dueDateIso)
            }
        },
        onNavigate = onNavigate,
        modifier = modifier
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val posture = LocalDevicePosture.current
    val haptics = LocalOrbitHaptics.current
    val heroScrollState = rememberHeroScrollState(maxHeight = 120.dp, minHeight = 64.dp)
    val scrollState = rememberScrollState()

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = navBarBottomPadding + 88.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            // Pinned Sticky Canopy Hero Header
            MorphingPageHero(
                title = "Dashboard",
                kicker = "Overview",
                scrollState = heroScrollState,
                actions = {
                    if (uiState.cashflow.monthLabel.isNotBlank()) {
                        PeriodNavigatorPill(
                            label = uiState.cashflow.monthLabel,
                            canGoNext = uiState.cashflow.canGoNextMonth,
                            onPrevious = { onAction(DashboardAction.PreviousMonth) },
                            onNext = { onAction(DashboardAction.NextMonth) }
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onAction(DashboardAction.OpenCustomizeSheet)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Customize Dashboard",
                            tint = OrbitTheme.colors.onCanopy,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                val visibleCards = uiState.visibleCards

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (posture.supportsTwoPane) {
                        // Wide Screen & Foldable 2-Column Split
                        val leftCards = visibleCards.filterIndexed { index, _ -> index % 2 == 0 }
                        val rightCards = visibleCards.filterIndexed { index, _ -> index % 2 != 0 }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                leftCards.forEach { cardType ->
                                    DashboardCardItem(cardType, uiState, onAction, onNavigate)
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rightCards.forEach { cardType ->
                                    DashboardCardItem(cardType, uiState, onAction, onNavigate)
                                }
                            }
                        }
                    } else {
                        // Phone 1-Column Sequential Layout
                        visibleCards.forEachIndexed { index, cardType ->
                            DashboardCardItem(cardType, uiState, onAction, onNavigate)
                            if (index < visibleCards.lastIndex) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(bottomContentPadding))
                }
            }
        }

        // Customization Bottom Sheet
        if (uiState.customization.isCustomizing) {
            DashboardCustomizeSheet(
                cards = uiState.customization.cardConfigs,
                onAction = onAction,
                onDismiss = { onAction(DashboardAction.CloseCustomizeSheet) }
            )
        }
    }
}

@Composable
private fun DashboardCardItem(
    cardType: DashboardCardType,
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onNavigate: (OrbitNavKey) -> Unit
) {
    when (cardType) {
        DashboardCardType.CASHFLOW_HERO -> {
            NetCashflowHeroCard(
                cashflow = uiState.cashflow,
                onClick = { onNavigate(OrbitNavKey.SpendingTrends) }
            )
        }
        DashboardCardType.BUDGET_PULSE -> {
            BudgetPulseCard(
                summary = uiState.budgetPulse,
                onClick = { onNavigate(OrbitNavKey.Budgets) }
            )
        }
        DashboardCardType.UPCOMING_BILLS -> {
            UpcomingBillsCard(
                bills = uiState.upcoming.items,
                onReconcile = { id, date -> onAction(DashboardAction.MarkBillPaid(id, date)) },
                onViewAll = { onNavigate(OrbitNavKey.Upcoming) }
            )
        }
        DashboardCardType.RECENT_TRANSACTIONS -> {
            RecentTransactionsCard(
                transactions = uiState.recentTransactions.transactions,
                onSelectTransaction = { onNavigate(OrbitNavKey.Transactions(it)) },
                onViewAll = { onNavigate(OrbitNavKey.Transactions()) }
            )
        }
        DashboardCardType.ACCOUNTS_OVERVIEW -> {
            AccountsOverviewCard(
                accounts = uiState.accounts,
                onClick = { onNavigate(OrbitNavKey.Accounts()) }
            )
        }
        DashboardCardType.GOALS_PROGRESS -> {
            GoalsProgressCard(
                goals = uiState.goals.goals,
                onClick = { onNavigate(OrbitNavKey.Goals) }
            )
        }
    }
}
