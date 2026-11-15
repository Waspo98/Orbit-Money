package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.accounts.PerkToggleOrigin
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.PerkUndoSnackbarEffect
import app.overbay.orbit.ui.accounts.cards.PerkUsageDialog
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.components.rememberOrbitLazyListState
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture
import kotlinx.coroutines.launch

/**
 * Stateful destination entry for Credit Card Tracker wiring [AccountsViewModel] with lifecycle-aware collection.
 */
@Composable
fun CardPerksScreen(
    viewModel: AccountsViewModel,
    initialAccountId: Long? = null,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posture = LocalDevicePosture.current

    LaunchedEffect(initialAccountId) {
        if (initialAccountId != null) {
            viewModel.openAccountDeepLink(initialAccountId)
        }
    }

    val actions = remember(viewModel) { viewModel.toCardPerksActions() }
    CardPerksScreen(
        uiState = state,
        actions = actions,
        posture = posture,
        onNavigate = onNavigate,
        modifier = modifier
    )
}

/**
 * Stateless root composable adhering to the 5-parameter screen contract (`<= 6`).
 */
@Composable
fun CardPerksScreen(
    uiState: AccountsUiState,
    actions: CardPerksActions,
    posture: DevicePosture,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val heroScrollState = rememberHeroScrollState()
    val listState = rememberOrbitLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.syncErrorMessage) {
        val err = uiState.syncErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        actions.onClearSyncError()
    }

    PerkUndoSnackbarEffect(
        event = uiState.lastPerkUndoEvent,
        origin = PerkToggleOrigin.CARD_PERKS,
        snackbarHostState = snackbarHostState,
        onUndo = actions.onUndoPerkToggle,
        onDismissOrigin = actions.onDismissPerkUndo
    )

    val creditAccounts = remember(uiState.accounts) {
        uiState.accounts.filter { it.type == AccountType.CREDIT && !it.isArchived }
    }
    val filteredCardCount = remember(creditAccounts, uiState.selectedCardPlayer, uiState.householdMembers, uiState.playerScopedHub.walletAccountIds) {
        val walletIds = uiState.playerScopedHub.walletAccountIds
        if (walletIds.isNotEmpty() || uiState.selectedCardPlayer != CreditCardStrategyEngine.ALL_PLAYERS) {
            creditAccounts.count { it.id in walletIds || CardFormat.matchesSelectedPlayer(it, uiState.selectedCardPlayer, uiState.householdMembers) }
        } else {
            creditAccounts.size
        }
    }
    val allItems = remember(uiState.cardPerksWidgets, uiState.rewardsToolsEnabled) {
        resolveCardPerksItems(uiState.cardPerksWidgets, uiState.rewardsToolsEnabled)
    }
    val visibleItems = remember(allItems) { allItems.filter { it.isVisible } }

    val unusedPerksCents = remember(uiState.cardPortfolioSummary.currentPeriodPerks) {
        uiState.cardPortfolioSummary.currentPeriodPerks.filter { !it.isFullyUsed }.sumOf { it.remainingCents }
    }
    val unusedFormatted = CardFormat.formatAdaptiveCents(unusedPerksCents)
    val scopedOwedCents = uiState.effectiveHouseholdCredit?.totalOwedCents ?: 0L
    val heroSubtitle = remember(
        filteredCardCount,
        creditAccounts.size,
        uiState.rewardsToolsEnabled,
        uiState.cardPortfolioSummary.capturedCreditsCardYearCents,
        scopedOwedCents
    ) {
        if (creditAccounts.isEmpty()) {
            "Track credits, utilization, and rewards"
        } else {
            val cardsLabel = "$filteredCardCount active card${if (filteredCardCount == 1) "" else "s"}"
            if (uiState.rewardsToolsEnabled) {
                "$cardsLabel \u00B7 ${CardFormat.formatAdaptiveCents(uiState.cardPortfolioSummary.capturedCreditsCardYearCents)} credits captured"
            } else {
                "$cardsLabel \u00B7 ${CardFormat.formatAdaptiveCents(scopedOwedCents)} balance"
            }
        }
    }

    val hasMultiplePlayers = uiState.cardPortfolioSummary.availablePlayers.size > 1
    val showRewardsDiscovery = !uiState.rewardsToolsEnabled &&
        uiState.cardRewardsToolsPref == null &&
        creditAccounts.isNotEmpty() &&
        visibleItems.isNotEmpty()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val liveCardDraft = actions.cardEditor?.currentDraft ?: uiState.cardEditorDraft
        val isInspectorActive = (uiState.selectedAccount?.type == AccountType.CREDIT) || liveCardDraft != null
        val isDetailOpen = isTwoPane && isInspectorActive
        val useTwoColBento = isTwoPane && !isDetailOpen
        val gridRows = remember(visibleItems, useTwoColBento) {
            if (useTwoColBento) buildCardPerksGridRows(visibleItems) else emptyList()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                MorphingPageHero(
                    kicker = "REWARDS & STATEMENT CREDITS",
                    title = "Credit Card Tracker",
                    subtitle = heroSubtitle,
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, barHeight ->
                        CanopySurfacePill(
                            expandedText = "Unused \u00B7 $unusedFormatted",
                            compactText = unusedFormatted,
                            isCompact = isCompact,
                            leadingIcon = OrbitIcons.CardPerks,
                            onClick = {
                                val headerCount = (if (hasMultiplePlayers) 1 else 0) + 1
                                val perksIdx = if (useTwoColBento) {
                                    gridRows.indexOfFirst {
                                        it.leftCard == CardPerksCardType.PERKS_THIS_PERIOD ||
                                            it.rightCard == CardPerksCardType.PERKS_THIS_PERIOD
                                    }
                                } else {
                                    visibleItems.indexOfFirst { it.key == CardPerksCardType.PERKS_THIS_PERIOD }
                                }
                                if (perksIdx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(headerCount + perksIdx) }
                                } else {
                                    actions.onOpenCustomize()
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        CanopyIconButton(
                            icon = OrbitIcons.ViewSliders,
                            contentDescription = "Customize Credit Card Tracker",
                            onClick = actions.onOpenCustomize,
                            buttonSize = barHeight,
                            isActive = uiState.showCardPerksCustomize
                        )

                        CanopySettingsButton(
                            buttonSize = barHeight,
                            onClick = { onNavigate(OrbitNavKey.Settings) }
                        )
                    }
                )

                val layoutState = CardPerksFeedLayoutState(
                    isTwoPane = isTwoPane,
                    isDetailOpen = isDetailOpen,
                    useTwoColBento = useTwoColBento,
                    hasCreditAccounts = creditAccounts.isNotEmpty(),
                    hasMultiplePlayers = hasMultiplePlayers,
                    showRewardsDiscovery = showRewardsDiscovery,
                    visibleItems = visibleItems,
                    gridRows = gridRows
                )
                val masterContent: @Composable () -> Unit = {
                    CardPerksMasterContent(
                        uiState = uiState,
                        actions = actions,
                        layoutState = layoutState,
                        listState = listState,
                        onNavigate = onNavigate
                    )
                }

                if (isTwoPane) {
                    OrbitMorphingSplitLayout(
                        isDetailOpen = isDetailOpen,
                        onDismissDetail = actions.onDismissInspectorPane,
                        masterContent = masterContent,
                        detailContent = {
                            CardPerksInspectorPane(
                                state = uiState,
                                actions = actions,
                                onNavigate = onNavigate,
                                onClosePane = actions.onDismissInspectorPane
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    masterContent()
                }
            }
        }

        if (!isTwoPane && isInspectorActive) {
            OrbitModalSheet(onDismissRequest = actions.onDismissInspectorPane) {
                CardPerksInspectorPane(
                    state = uiState,
                    actions = actions,
                    onNavigate = onNavigate,
                    onClosePane = actions.onDismissInspectorPane
                )
            }
        }
    }

    if (uiState.showCardPerksCustomize) {
        CardPerksCustomizeModal(
            allItems = allItems,
            rewardsToolsEnabled = uiState.rewardsToolsEnabled,
            actions = actions
        )
    }

    uiState.perkUsageTarget?.let { target ->
        PerkUsageDialog(
            title = target.title,
            periodLabel = target.periodLabel,
            valueCents = target.valueCents,
            initialUsedCents = target.initialUsedCents,
            onSave = { amount -> actions.onSavePerkUsage(target.accountId, target.benefitId, amount) },
            onDismiss = actions.onClosePerkUsage
        )
    }
}
