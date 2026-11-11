package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.accounts.cards.PerkUsageDialog
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopyMenuAction
import app.overbay.orbit.ui.components.CanopyMoreMenuButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.dashboard.dialogs.OrbitBentoCustomizeSheet
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Dedicated Card Perks & Rewards destination featuring a Dashboard-style reorderable
 * 2-column Bento grid and morphing right-hand Card Perks Inspector.
 */
@Composable
fun CardPerksScreen(
    viewModel: AccountsViewModel,
    initialAccountId: Long? = null,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()
    var isCustomizing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialAccountId) {
        if (initialAccountId != null) {
            viewModel.openAccountDeepLink(initialAccountId)
        }
    }

    val creditAccounts = remember(state.accounts) {
        state.accounts.filter { it.type == AccountType.CREDIT && !it.isArchived }
    }
    val allItems = remember(state.cardPerksWidgets, state.rewardsToolsEnabled) {
        resolveCardPerksItems(state.cardPerksWidgets, state.rewardsToolsEnabled)
    }
    val visibleItems = remember(allItems) { allItems.filter { it.isVisible } }

    val unusedPerksCents = remember(state.cardPortfolioSummary.currentPeriodPerks) {
        state.cardPortfolioSummary.currentPeriodPerks.filter { !it.isFullyUsed }.sumOf { it.remainingCents }
    }
    val unusedFormatted = MoneyUtils.formatCents(unusedPerksCents, showCents = false)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val liveCardDraft = viewModel.cardEditor.currentDraft ?: state.cardEditorDraft
        val isInspectorActive = (state.selectedAccount?.type == AccountType.CREDIT) || liveCardDraft != null
        val isDetailOpen = isTwoPane && isInspectorActive

        Scaffold(
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
                    subtitle = "$unusedFormatted in unused perks this period across ${creditAccounts.size} cards",
                    scrollState = heroScrollState,
                    canopyBar = { _, isCompact, barHeight ->
                        CanopySurfacePill(
                            expandedText = "Unused Perks · $unusedFormatted",
                            compactText = unusedFormatted,
                            isCompact = isCompact,
                            leadingIcon = OrbitIcons.CardPerks,
                            onClick = { onNavigate(OrbitNavKey.Accounts()) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        if (state.cardPortfolioSummary.availablePlayers.size > 1) {
                            val players = state.cardPortfolioSummary.availablePlayers
                            val selectedPlayer = state.cardPortfolioSummary.selectedPlayer
                            CanopyMoreMenuButton(
                                buttonSize = barHeight,
                                contentDescription = "Filter by Cardholder",
                                actions = buildList {
                                    add(
                                        CanopyMenuAction(
                                            label = "All Household",
                                            isChecked = selectedPlayer == CreditCardStrategyEngine.ALL_PLAYERS,
                                            onClick = { viewModel.selectCardPlayer(CreditCardStrategyEngine.ALL_PLAYERS) }
                                        )
                                    )
                                    players.forEach { (key, label) ->
                                        add(
                                            CanopyMenuAction(
                                                label = label,
                                                isChecked = selectedPlayer == key,
                                                onClick = { viewModel.selectCardPlayer(key) }
                                            )
                                        )
                                    }
                                }
                            )
                        }

                        CanopyIconButton(
                            icon = OrbitIcons.ViewSliders,
                            contentDescription = "Customize Credit Card Tracker",
                            onClick = { isCustomizing = true },
                            buttonSize = barHeight,
                            isActive = isCustomizing
                        )

                        CanopySettingsButton(
                            buttonSize = barHeight,
                            onClick = { onNavigate(OrbitNavKey.Settings) }
                        )
                    }
                )

                val bentoActions = remember(viewModel) {
                    CardPerksBentoActions(
                        onInspectAccount = { viewModel.selectAccountForInspect(it) },
                        onOpenCardEditor = { viewModel.openCreditCardProfile(it) },
                        onToggleBenefit = { aId, bId -> viewModel.toggleCardBenefit(aId, bId) },
                        onOpenPerkUsage = { aId, bId -> viewModel.openPerkUsage(aId, bId) },
                        onToggleShowUsedPerks = { viewModel.toggleShowUsedPerks() }
                    )
                }

                val masterContent: @Composable () -> Unit = {
                    val useTwoColBento = isTwoPane && !isDetailOpen
                    val gridRows = remember(visibleItems, useTwoColBento) {
                        if (useTwoColBento) buildCardPerksGridRows(visibleItems) else emptyList()
                    }
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val hPad = if (isTwoPane) 20.dp else 16.dp

                    if (creditAccounts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = hPad, vertical = 24.dp)) {
                            EmptyStateCard(
                                title = "No Credit Cards Yet",
                                description = "Add a credit card account in Accounts to track monthly statement credits, annual fee ROI, multipliers, and 5/24 slots.",
                                actionButtonText = "Go to Accounts",
                                onActionClick = { onNavigate(OrbitNavKey.Accounts()) }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            LazyColumn(
                                modifier = Modifier.widthIn(max = 1440.dp).fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = hPad,
                                    end = if (isDetailOpen) 0.dp else hPad,
                                    top = 14.dp,
                                    bottom = 88.dp + navBarBottom
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (useTwoColBento) {
                                    items(
                                        items = gridRows,
                                        key = { row -> "${row.leftCard.name}_${row.rightCard?.name ?: "single"}" }
                                    ) { row ->
                                        if (row.isFullSpan) {
                                            CardPerksBentoItem(row.leftCard, state, bentoActions)
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                    CardPerksBentoItem(
                                                        cardType = row.leftCard,
                                                        uiState = state,
                                                        actions = bentoActions,
                                                        modifier = Modifier.fillMaxHeight()
                                                    )
                                                }
                                                if (row.rightCard != null) {
                                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                        CardPerksBentoItem(
                                                            cardType = row.rightCard,
                                                            uiState = state,
                                                            actions = bentoActions,
                                                            modifier = Modifier.fillMaxHeight()
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
                                        items = visibleItems,
                                        key = { it.key.name }
                                    ) { item ->
                                        CardPerksBentoItem(item.key, state, bentoActions)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isTwoPane) {
                    OrbitMorphingSplitLayout(
                        isDetailOpen = isDetailOpen,
                        onDismissDetail = { viewModel.dismissInspectorPane() },
                        masterContent = masterContent,
                        detailContent = {
                            CardPerksInspectorPane(
                                state = state,
                                viewModel = viewModel,
                                onNavigate = onNavigate,
                                onClosePane = { viewModel.dismissInspectorPane() }
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
            OrbitModalSheet(onDismissRequest = { viewModel.dismissInspectorPane() }) {
                CardPerksInspectorPane(
                    state = state,
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onClosePane = { viewModel.dismissInspectorPane() }
                )
            }
        }
    }

    if (isCustomizing) {
        OrbitBentoCustomizeSheet(
            title = "Customize Credit Card Tracker",
            items = allItems,
            onToggleVisibility = { targetType ->
                viewModel.updateCardPerksWidgets {
                    allItems.map { if (it.key == targetType) it.copy(isVisible = !it.isVisible) else it }.toWidgetConfigs()
                }
            },
            onSetWideSpan = { targetType, span ->
                viewModel.updateCardPerksWidgets {
                    allItems.map { if (it.key == targetType) it.copy(wideSpan = span.coerceIn(1, 2)) else it }.toWidgetConfigs()
                }
            },
            onMoveItem = { fromIndex, toIndex ->
                if (fromIndex in allItems.indices && toIndex in allItems.indices && fromIndex != toIndex) {
                    val mutable = allItems.toMutableList()
                    val moved = mutable.removeAt(fromIndex)
                    mutable.add(toIndex, moved)
                    viewModel.updateCardPerksWidgets { mutable.toWidgetConfigs() }
                }
            },
            onReset = {
                viewModel.updateCardPerksWidgets { OrbitPreferences.defaultCardPerksWidgets() }
            },
            onDismiss = { isCustomizing = false }
        )
    }

    state.perkUsageTarget?.let { target ->
        PerkUsageDialog(
            title = target.title,
            periodLabel = target.periodLabel,
            valueCents = target.valueCents,
            initialUsedCents = target.initialUsedCents,
            onSave = { amount -> viewModel.setCardBenefitUsage(target.accountId, target.benefitId, amount) },
            onDismiss = { viewModel.closePerkUsage() }
        )
    }
}
