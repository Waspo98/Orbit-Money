package app.overbay.orbit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.dashboard.cards.AccountsOverviewCard
import app.overbay.orbit.ui.dashboard.cards.BiggestTransactionsCard
import app.overbay.orbit.ui.dashboard.cards.BudgetPulseCard
import app.overbay.orbit.ui.dashboard.cards.CategorizeRecentCard
import app.overbay.orbit.ui.dashboard.cards.GoalFocusCard
import app.overbay.orbit.ui.dashboard.cards.GoalsProgressCard
import app.overbay.orbit.ui.dashboard.cards.MhaSummaryCard
import app.overbay.orbit.ui.dashboard.cards.MonthComparisonCard
import app.overbay.orbit.ui.dashboard.cards.MortgageSnapshotCard
import app.overbay.orbit.ui.dashboard.cards.NetCashflowHeroCard
import app.overbay.orbit.ui.dashboard.cards.RecentTransactionsCard
import app.overbay.orbit.ui.dashboard.cards.RetirementSnapshotCard
import app.overbay.orbit.ui.dashboard.cards.SubscriptionsCard
import app.overbay.orbit.ui.dashboard.cards.TopSpendingCard
import app.overbay.orbit.ui.dashboard.cards.UncategorizedCard
import app.overbay.orbit.ui.dashboard.cards.UpcomingBillsCard
import app.overbay.orbit.ui.dashboard.dialogs.DashboardCustomizeSheet
import app.overbay.orbit.ui.dashboard.dialogs.TransactionReviewSheet
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Editorial Orbit Money Dashboard screen supporting all 16 customizable cards.
 * Conforms strictly to <= 6 parameter rule and < 400-line ceiling.
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
                is DashboardAction.SetCardWideSpan -> viewModel.setCardWideSpan(action.cardType, action.wideSpan)
                is DashboardAction.MoveCard -> viewModel.moveCard(action.fromIndex, action.toIndex)
                is DashboardAction.MoveCardUp -> viewModel.moveCardUp(action.cardType)
                is DashboardAction.MoveCardDown -> viewModel.moveCardDown(action.cardType)
                DashboardAction.ResetCardsToDefault -> viewModel.resetCardsToDefault()
                is DashboardAction.MarkBillPaid -> viewModel.markUpcomingPaid(action.upcomingItemId, action.dueDateIso)
                is DashboardAction.HideBiggestTransaction -> viewModel.hideBiggestTransaction(action.transactionId)
                DashboardAction.ResetHiddenBiggestTransactions -> viewModel.resetHiddenBiggestTransactions()
                is DashboardAction.SetGoalFocus -> viewModel.setGoalFocus(action.goalId)
                is DashboardAction.SetRetirementAge -> viewModel.setRetirementAge(action.age)
                is DashboardAction.SetRetirementPreset -> viewModel.setRetirementPreset(action.presetKey)
                DashboardAction.OpenCategorizeReview -> viewModel.openCategorizeReview()
                DashboardAction.CloseCategorizeReview -> viewModel.closeCategorizeReview()
                is DashboardAction.ApplyCategory -> viewModel.applyCategory(action.transactionId, action.categoryId)
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
    val heroScrollState = rememberHeroScrollState()
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
            // Edge-to-edge curved green canopy hero — no month selector in Dashboard header
            MorphingPageHero(
                kicker = "FINANCIAL ORBIT",
                title = "Dashboard",
                subtitle = "${uiState.dateLabel} · ${uiState.greeting}",
                scrollState = heroScrollState
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
                val visibleConfigs = uiState.customization.cardConfigs
                    .ifEmpty {
                        uiState.visibleCards.mapIndexed { idx, type ->
                            DashboardCardConfigItem(
                                type = type,
                                title = type.defaultTitle,
                                description = type.defaultDescription,
                                isVisible = true,
                                sortOrder = idx,
                                wideSpan = type.defaultWideSpan
                            )
                        }
                    }
                    .filter { config ->
                        config.isVisible && (if (config.type == DashboardCardType.MHA_SUMMARY) uiState.mhaEnabled else true)
                    }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1440.dp)
                            .fillMaxWidth()
                            .padding(horizontal = if (posture.supportsTwoPane) 20.dp else 16.dp, vertical = 14.dp)
                    ) {
                        if (posture.supportsTwoPane) {
                            val gridRows = buildDashboardGridRows(visibleConfigs)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                gridRows.forEach { row ->
                                    if (row.isFullSpan) {
                                        DashboardCardItem(row.leftCard, uiState, onAction, onNavigate)
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Max),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            ) {
                                                DashboardCardItem(
                                                    cardType = row.leftCard,
                                                    uiState = uiState,
                                                    onAction = onAction,
                                                    onNavigate = onNavigate,
                                                    modifier = Modifier.fillMaxHeight()
                                                )
                                            }
                                            if (row.rightCard != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    DashboardCardItem(
                                                        cardType = row.rightCard,
                                                        uiState = uiState,
                                                        onAction = onAction,
                                                        onNavigate = onNavigate,
                                                        modifier = Modifier.fillMaxHeight()
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            visibleConfigs.forEachIndexed { index, config ->
                                DashboardCardItem(config.type, uiState, onAction, onNavigate)
                                if (index < visibleConfigs.lastIndex) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Centered Customize Dashboard Button
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OrbitSecondaryPillButton(
                                text = "Customize Dashboard",
                                onClick = { onAction(DashboardAction.OpenCustomizeSheet) }
                            )
                        }

                        Spacer(modifier = Modifier.height(bottomContentPadding))
                    }
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

        // Category Review Bottom Sheet
        if (uiState.isReviewModalOpen) {
            TransactionReviewSheet(
                transactions = uiState.uncategorized.transactions,
                onApplyCategory = { txnId, catId -> onAction(DashboardAction.ApplyCategory(txnId, catId)) },
                onDismiss = { onAction(DashboardAction.CloseCategorizeReview) }
            )
        }
    }
}

private data class DashboardGridRow(
    val leftCard: DashboardCardType,
    val rightCard: DashboardCardType? = null,
    val isFullSpan: Boolean = false
)

private fun buildDashboardGridRows(visibleConfigs: List<DashboardCardConfigItem>): List<DashboardGridRow> {
    val rows = mutableListOf<DashboardGridRow>()
    var pendingLeft: DashboardCardType? = null

    for (config in visibleConfigs) {
        if (config.wideSpan == 2) {
            if (pendingLeft != null) {
                rows.add(DashboardGridRow(leftCard = pendingLeft, rightCard = null, isFullSpan = false))
                pendingLeft = null
            }
            rows.add(DashboardGridRow(leftCard = config.type, rightCard = null, isFullSpan = true))
        } else {
            if (pendingLeft == null) {
                pendingLeft = config.type
            } else {
                rows.add(DashboardGridRow(leftCard = pendingLeft, rightCard = config.type, isFullSpan = false))
                pendingLeft = null
            }
        }
    }

    if (pendingLeft != null) {
        rows.add(DashboardGridRow(leftCard = pendingLeft, rightCard = null, isFullSpan = false))
    }

    return rows
}

@Composable
private fun DashboardCardItem(
    cardType: DashboardCardType,
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    when (cardType) {
        DashboardCardType.ACCOUNTS_OVERVIEW -> AccountsOverviewCard(
            accounts = uiState.accounts,
            onClick = { onNavigate(OrbitNavKey.Accounts()) },
            modifier = modifier
        )
        DashboardCardType.BUDGET_PULSE -> BudgetPulseCard(
            summary = uiState.budgetPulse,
            onClick = { onNavigate(OrbitNavKey.Budgets) },
            modifier = modifier
        )
        DashboardCardType.TOP_SPENDING -> TopSpendingCard(
            state = uiState.topSpending,
            onClick = { onNavigate(OrbitNavKey.Budgets) },
            modifier = modifier
        )
        DashboardCardType.CASHFLOW_HERO -> NetCashflowHeroCard(
            cashflow = uiState.cashflow,
            onClick = { onNavigate(OrbitNavKey.SpendingTrends) },
            modifier = modifier
        )
        DashboardCardType.BIGGEST_TRANSACTIONS -> BiggestTransactionsCard(
            state = uiState.biggestTransactions,
            onClick = { onNavigate(OrbitNavKey.Transactions()) },
            onHideTransaction = { onAction(DashboardAction.HideBiggestTransaction(it)) },
            onResetHidden = { onAction(DashboardAction.ResetHiddenBiggestTransactions) },
            modifier = modifier
        )
        DashboardCardType.SUBSCRIPTIONS -> SubscriptionsCard(
            state = uiState.subscriptions,
            onClick = { onNavigate(OrbitNavKey.Upcoming) },
            modifier = modifier
        )
        DashboardCardType.UNCATEGORIZED -> UncategorizedCard(
            state = uiState.uncategorized,
            onClick = { onNavigate(OrbitNavKey.Transactions(uncategorizedOnly = true)) },
            modifier = modifier
        )
        DashboardCardType.CATEGORIZE_RECENT -> CategorizeRecentCard(
            onOpenReview = { onAction(DashboardAction.OpenCategorizeReview) },
            modifier = modifier
        )
        DashboardCardType.MONTH_COMPARISON -> MonthComparisonCard(
            state = uiState.monthComparison,
            onClick = { onNavigate(OrbitNavKey.SpendingTrends) },
            modifier = modifier
        )
        DashboardCardType.GOALS_PROGRESS -> GoalsProgressCard(
            state = uiState.goals,
            onClick = { onNavigate(OrbitNavKey.Goals) },
            modifier = modifier
        )
        DashboardCardType.GOAL_FOCUS -> GoalFocusCard(
            state = uiState.goalFocus,
            onSelectGoal = { onAction(DashboardAction.SetGoalFocus(it)) },
            onClick = { onNavigate(OrbitNavKey.Goals) },
            modifier = modifier
        )
        DashboardCardType.RETIREMENT_SNAPSHOT -> RetirementSnapshotCard(
            state = uiState.retirement,
            onChangeAge = { onAction(DashboardAction.SetRetirementAge(it)) },
            onChangePreset = { onAction(DashboardAction.SetRetirementPreset(it)) },
            onClick = { onNavigate(OrbitNavKey.RetirementCalculator) },
            modifier = modifier
        )
        DashboardCardType.MHA_SUMMARY -> MhaSummaryCard(
            state = uiState.mha,
            onClick = { onNavigate(OrbitNavKey.MhaTracker) },
            modifier = modifier
        )
        DashboardCardType.UPCOMING_BILLS -> UpcomingBillsCard(
            bills = uiState.upcoming.items,
            onReconcile = { id, date -> onAction(DashboardAction.MarkBillPaid(id, date)) },
            onViewAll = { onNavigate(OrbitNavKey.Upcoming) },
            modifier = modifier
        )
        DashboardCardType.MORTGAGE_SNAPSHOT -> MortgageSnapshotCard(
            state = uiState.mortgage,
            onClick = { onNavigate(OrbitNavKey.Accounts()) },
            modifier = modifier
        )
        DashboardCardType.RECENT_TRANSACTIONS -> RecentTransactionsCard(
            transactions = uiState.recentTransactions.transactions,
            onSelectTransaction = { onNavigate(OrbitNavKey.Transactions(it)) },
            onViewAll = { onNavigate(OrbitNavKey.Transactions()) },
            modifier = modifier
        )
    }
}
