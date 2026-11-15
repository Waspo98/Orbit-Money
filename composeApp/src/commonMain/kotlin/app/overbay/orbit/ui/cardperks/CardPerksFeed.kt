package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.dashboard.dialogs.OrbitBentoCustomizeItem
import app.overbay.orbit.ui.dashboard.dialogs.OrbitBentoCustomizeSheet

internal data class CardPerksFeedLayoutState(
    val isTwoPane: Boolean,
    val isDetailOpen: Boolean,
    val useTwoColBento: Boolean,
    val hasCreditAccounts: Boolean,
    val hasMultiplePlayers: Boolean,
    val showRewardsDiscovery: Boolean,
    val visibleItems: List<OrbitBentoCustomizeItem<CardPerksCardType>>,
    val gridRows: List<CardPerksGridRow>
)

@Composable
internal fun CardPerksMasterContent(
    uiState: AccountsUiState,
    actions: CardPerksActions,
    layoutState: CardPerksFeedLayoutState,
    listState: LazyListState,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hPad = if (layoutState.isTwoPane) 20.dp else 16.dp

    when {
        uiState.isLoading && !layoutState.hasCreditAccounts -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        !layoutState.hasCreditAccounts -> {
            Box(modifier = modifier.fillMaxSize().padding(horizontal = hPad, vertical = 24.dp)) {
                EmptyStateCard(
                    title = "No Credit Cards Yet",
                    description = "Add a credit card account in Accounts to track monthly statement credits, annual fee ROI, multipliers, and 5/24 slots.",
                    actionButtonText = "Go to Accounts",
                    onActionClick = { onNavigate(OrbitNavKey.Accounts()) }
                )
            }
        }
        layoutState.visibleItems.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize().padding(horizontal = hPad, vertical = 24.dp)) {
                EmptyStateCard(
                    title = "All Sections Hidden",
                    description = "Enable at least one Bento section to view your cards, perks, or rewards strategy.",
                    actionButtonText = "Customize Sections",
                    onActionClick = actions.onOpenCustomize
                )
            }
        }
        else -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 1440.dp).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = hPad,
                        end = if (layoutState.isDetailOpen) 0.dp else hPad,
                        top = 14.dp,
                        bottom = 88.dp + navBarBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (layoutState.hasMultiplePlayers) {
                        item(key = "player_filter_strip") {
                            CardholderFilterStrip(
                                availablePlayers = uiState.cardPortfolioSummary.availablePlayers,
                                selectedPlayer = uiState.cardPortfolioSummary.selectedPlayer,
                                allPlayersKey = CreditCardStrategyEngine.ALL_PLAYERS,
                                onSelectPlayer = actions.onSelectPlayer
                            )
                        }
                    }

                    item(key = "portfolio_roi_strip") {
                        PortfolioRoiSummaryStrip(uiState = uiState)
                    }

                    if (layoutState.useTwoColBento) {
                        items(
                            items = layoutState.gridRows,
                            key = { row -> "${row.leftCard.name}_${row.rightCard?.name ?: "single"}" }
                        ) { row ->
                            val rowAnimModifier = Modifier.animateItem(
                                fadeInSpec = OrbitSprings.FloatEffectsDefault,
                                placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                                fadeOutSpec = OrbitSprings.FloatEffectsFast
                            )
                            if (row.isFullSpan) {
                                CardPerksBentoItem(
                                    cardType = row.leftCard,
                                    uiState = uiState,
                                    actions = actions.bento,
                                    modifier = rowAnimModifier
                                )
                            } else {
                                Row(
                                    modifier = rowAnimModifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        CardPerksBentoItem(
                                            cardType = row.leftCard,
                                            uiState = uiState,
                                            actions = actions.bento
                                        )
                                    }
                                    if (row.rightCard != null) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            CardPerksBentoItem(
                                                cardType = row.rightCard,
                                                uiState = uiState,
                                                actions = actions.bento
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = layoutState.visibleItems,
                            key = { it.key.name }
                        ) { item ->
                            CardPerksBentoItem(
                                cardType = item.key,
                                uiState = uiState,
                                actions = actions.bento,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = OrbitSprings.FloatEffectsDefault,
                                    placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                                    fadeOutSpec = OrbitSprings.FloatEffectsFast
                                )
                            )
                        }
                    }

                    if (layoutState.showRewardsDiscovery) {
                        item(key = "rewards_tools_discovery") {
                            RewardsToolsDiscoveryBanner(
                                onEnable = { actions.onSetRewardsToolsEnabled(true) },
                                onDismiss = { actions.onSetRewardsToolsEnabled(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardPerksCustomizeModal(
    allItems: List<OrbitBentoCustomizeItem<CardPerksCardType>>,
    rewardsToolsEnabled: Boolean,
    actions: CardPerksActions
) {
    OrbitBentoCustomizeSheet(
        title = "Customize Credit Card Tracker",
        items = allItems,
        headerContent = {
            CustomizeRewardsToolsToggleRow(
                enabled = rewardsToolsEnabled,
                onToggle = { enabled -> actions.onSetRewardsToolsEnabled(enabled) }
            )
        },
        onToggleVisibility = { targetType ->
            actions.onUpdateWidgets {
                allItems.map { if (it.key == targetType) it.copy(isVisible = !it.isVisible) else it }.toWidgetConfigs()
            }
        },
        onSetWideSpan = { targetType, span ->
            actions.onUpdateWidgets {
                allItems.map { if (it.key == targetType) it.copy(wideSpan = span.coerceIn(1, 2)) else it }.toWidgetConfigs()
            }
        },
        onMoveItem = { fromIndex, toIndex ->
            if (fromIndex in allItems.indices && toIndex in allItems.indices && fromIndex != toIndex) {
                val mutable = allItems.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(toIndex, moved)
                actions.onUpdateWidgets { mutable.toWidgetConfigs() }
            }
        },
        onReset = {
            actions.onUpdateWidgets { OrbitPreferences.defaultCardPerksWidgets() }
        },
        onDismiss = actions.onDismissCustomize
    )
}
