package app.overbay.orbit.ui.calculators.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.cards.HsaBridgeAnalysisCard
import app.overbay.orbit.ui.calculators.retirement.cards.MemberRetirementBreakdownCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementAccountsListCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementHistoryTrajectoryCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementProjectionHeroCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementRunwayCard
import app.overbay.orbit.ui.calculators.retirement.cards.WhatIfSandboxCard
import app.overbay.orbit.ui.calculators.retirement.components.QuickAmountSheet
import app.overbay.orbit.ui.calculators.retirement.dialogs.AssumptionsSheet
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.components.rememberOrbitLazyListState
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture
import kotlin.math.roundToInt

@Composable
fun RetirementCalculatorScreen(
    viewModel: RetirementViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val actions = remember(viewModel) {
        RetirementActions(
            onSelectTab = viewModel::selectTab,
            onSelectPreset = viewModel::setPreset,
            onSetRetirementAge = viewModel::setRetirementAge,
            onSetRunwayEndAge = viewModel::setRunwayEndAge,
            onSetTargetMode = viewModel::setTargetMode,
            onSetMonthlySavings = viewModel::setMonthlySavings,
            onResetMonthlySavings = viewModel::resetMonthlySavingsToHouseholdPace,
            onSetAnnualSpending = viewModel::setAnnualSpending,
            onApplySpendingFraction = viewModel::applySpendingIncomeFraction,
            onResetAnnualSpending = viewModel::resetAnnualSpendingToGuideline,
            onSetCurrentBalance = viewModel::setCurrentBalance,
            onResetCurrentBalance = viewModel::resetCurrentBalanceToConnectedAccounts,
            onSetCustomTarget = viewModel::setCustomTarget,
            onSetLegacyAmount = viewModel::setLegacyAmount,
            onSelectHorizon = viewModel::setSelectedHorizon,
            onOpenAssumptionsSheet = viewModel::openAssumptionsSheet,
            onDismissAssumptionsSheet = viewModel::dismissAssumptionsSheet,
            onOpenAmountEditor = viewModel::openAmountEditor,
            onDismissAmountEditor = viewModel::dismissAmountEditor,
            onCommitAmountEditor = viewModel::commitAmountEditor
        )
    }

    RetirementCalculatorContent(
        uiState = uiState,
        actions = actions,
        posture = posture,
        onNavigate = onNavigate,
        modifier = modifier
    )
}

@Composable
fun RetirementCalculatorContent(
    uiState: RetirementUiState,
    actions: RetirementActions,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val heroScrollState = rememberHeroScrollState()
    var isDraggingControl by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            MorphingPageHero(
                kicker = "LONG-RANGE PLANNING",
                title = "Retirement Calculator",
                collapsedTitle = "Retirement",
                subtitle = "Projections in today's purchasing power",
                scrollState = heroScrollState,
                canopyBar = { _, isCompact, barHeight ->
                    val projCompact = MoneyUtils.formatCompactCents(uiState.scenario.projectedBalanceCents)
                    val funded = "${uiState.scenario.readinessPercent.roundToInt()}%"
                    CanopySurfacePill(
                        expandedText = "Age ${uiState.retirementAge.roundToInt()} · $projCompact ($funded)",
                        compactText = "$projCompact · $funded",
                        isCompact = isCompact,
                        leadingIcon = OrbitIcons.RetirementCalculator,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CanopyIconButton(
                        icon = OrbitIcons.ViewSliders,
                        contentDescription = "Planning Assumptions",
                        onClick = actions.onOpenAssumptionsSheet,
                        buttonSize = barHeight,
                        isActive = uiState.showAssumptionsSheet
                    )
                }
            )

            if (posture.supportsTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val leftListState = rememberOrbitLazyListState("left")
                    LazyColumn(
                        state = leftListState,
                        modifier = Modifier.weight(0.47f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "hero", contentType = "hero") {
                            RetirementProjectionHeroCard(
                                uiState = uiState,
                                onSelectPreset = actions.onSelectPreset,
                                isDraggingControl = isDraggingControl,
                                chartHeightDp = 232
                            )
                        }
                    }

                    val rightListState = rememberOrbitLazyListState("right")
                    Column(modifier = Modifier.weight(0.53f).fillMaxSize()) {
                        OrbitSegmentedTabRow(
                            items = RetirementWorkspaceTab.entries,
                            selected = uiState.selectedTab,
                            onSelect = actions.onSelectTab,
                            label = { it.label },
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        LazyColumn(
                            state = rightListState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            activeTabItems(
                                uiState = uiState,
                                actions = actions,
                                onDragStateChanged = { isDraggingControl = it }
                            )
                            item(key = "spacer_right", contentType = "spacer") {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            } else {
                val listState = rememberOrbitLazyListState("single")
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "hero", contentType = "hero") {
                        RetirementProjectionHeroCard(
                            uiState = uiState,
                            onSelectPreset = actions.onSelectPreset,
                            isDraggingControl = isDraggingControl,
                            chartHeightDp = 192
                        )
                    }

                    item(key = "tabs", contentType = "tabs") {
                        OrbitSegmentedTabRow(
                            items = RetirementWorkspaceTab.entries,
                            selected = uiState.selectedTab,
                            onSelect = actions.onSelectTab,
                            label = { it.label }
                        )
                    }

                    activeTabItems(
                        uiState = uiState,
                        actions = actions,
                        onDragStateChanged = { isDraggingControl = it }
                    )

                    item(key = "spacer_bottom", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        if (uiState.showAssumptionsSheet) {
            AssumptionsSheet(
                uiState = uiState,
                onSelectPreset = actions.onSelectPreset,
                onSetTargetMode = actions.onSetTargetMode,
                onSetCustomTarget = actions.onSetCustomTarget,
                onSetLegacyAmount = actions.onSetLegacyAmount,
                onSetCurrentBalance = actions.onSetCurrentBalance,
                onResetCurrentBalance = actions.onResetCurrentBalance,
                onSetRunwayEndAge = actions.onSetRunwayEndAge,
                onDismiss = actions.onDismissAssumptionsSheet
            )
        }

        val activeField = uiState.activeAmountField
        if (activeField != null) {
            val initialCents = when (activeField) {
                RetirementAmountField.MONTHLY_SAVINGS -> uiState.monthlySavingsCents
                RetirementAmountField.ANNUAL_SPENDING -> uiState.annualSpendingCents
                RetirementAmountField.STARTING_BALANCE -> uiState.currentBalanceCents
            }
            val resetDefault = when (activeField) {
                RetirementAmountField.MONTHLY_SAVINGS -> actions.onResetMonthlySavings
                RetirementAmountField.ANNUAL_SPENDING -> actions.onResetAnnualSpending
                RetirementAmountField.STARTING_BALANCE -> actions.onResetCurrentBalance
            }
            QuickAmountSheet(
                field = activeField,
                initialCents = initialCents,
                connectedBalanceCents = uiState.connectedBalanceCents,
                plannedMonthlySavingsCents = uiState.plannedMonthlySavingsCents,
                grossIncomeAnnualCents = uiState.grossIncomeAnnualCents,
                onApply = { cents -> actions.onCommitAmountEditor(activeField, cents) },
                onResetDefault = resetDefault,
                onDismiss = actions.onDismissAmountEditor
            )
        }
    }
}

private fun LazyListScope.activeTabItems(
    uiState: RetirementUiState,
    actions: RetirementActions,
    onDragStateChanged: (Boolean) -> Unit
) {
    when (uiState.selectedTab) {
        RetirementWorkspaceTab.WHAT_IF -> {
            item(key = "what_if_sandbox", contentType = "what_if") {
                WhatIfSandboxCard(
                    uiState = uiState,
                    actions = actions,
                    onDragStateChanged = onDragStateChanged
                )
            }
        }

        RetirementWorkspaceTab.RUNWAY -> {
            item(key = "runway_chart", contentType = "runway") {
                RetirementRunwayCard(
                    uiState = uiState,
                    onRunwayEndAgeChange = actions.onSetRunwayEndAge,
                    onDragStateChanged = onDragStateChanged
                )
            }
            item(key = "hsa_bridge", contentType = "hsa") {
                HsaBridgeAnalysisCard(
                    hsaBridge = uiState.scenario.hsaBridge,
                    retirementAge = uiState.retirementAge
                )
            }
        }

        RetirementWorkspaceTab.ACCOUNTS -> {
            item(key = "linked_accounts", contentType = "accounts") {
                RetirementAccountsListCard(
                    uiState = uiState,
                    onEditStartingBalance = { actions.onOpenAmountEditor(RetirementAmountField.STARTING_BALANCE) },
                    onResetCurrentBalance = actions.onResetCurrentBalance
                )
            }
            item(key = "member_breakdown", contentType = "members") {
                MemberRetirementBreakdownCard(
                    memberSummaries = uiState.memberSummaries
                )
            }
            item(key = "history_trajectory", contentType = "history") {
                RetirementHistoryTrajectoryCard(
                    filteredPoints = uiState.filteredHistoryPoints,
                    linkedAccounts = uiState.linkedAccounts,
                    selectedHorizon = uiState.selectedHorizon,
                    onSelectHorizon = actions.onSelectHorizon
                )
            }
        }
    }
}
