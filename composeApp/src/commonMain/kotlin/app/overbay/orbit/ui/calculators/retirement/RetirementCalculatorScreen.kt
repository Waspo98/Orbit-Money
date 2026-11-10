package app.overbay.orbit.ui.calculators.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.cards.HouseholdIncomeAssumptionsCard
import app.overbay.orbit.ui.calculators.retirement.cards.HsaBridgeAnalysisCard
import app.overbay.orbit.ui.calculators.retirement.cards.MemberRetirementBreakdownCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementAccountsListCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementHistoryTrajectoryCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementProjectionHeroCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementRunwayCard
import app.overbay.orbit.ui.calculators.retirement.dialogs.TargetSettingsSheet
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.rememberHeroScrollState
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
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            MorphingPageHero(
                kicker = "LONG-RANGE PLANNING",
                title = "Retirement Calculator",
                subtitle = "All projections are shown in today's dollars",
                scrollState = heroScrollState,
                canopyBar = { _, isCompact, barHeight ->
                    val proj = MoneyUtils.formatCents(uiState.scenario.projectedBalanceCents, showCents = false)
                    val funded = "${uiState.scenario.readinessPercent.roundToInt()}% Funded"
                    CanopySurfacePill(
                        expandedText = "Age ${uiState.retirementAge.toInt()} · $proj ($funded)",
                        compactText = "$proj · $funded",
                        isCompact = isCompact,
                        leadingIcon = OrbitIcons.RetirementCalculator,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CanopyIconButton(
                        icon = OrbitIcons.ViewSliders,
                        contentDescription = "Target & Runway Settings",
                        onClick = { viewModel.openTargetSettings() },
                        buttonSize = barHeight,
                        isActive = uiState.showTargetSettingsSheet
                    )

                    CanopySettingsButton(
                        buttonSize = barHeight,
                        onClick = { onNavigate(OrbitNavKey.Settings) }
                    )
                }
            )

            if (isTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Assumptions & Portfolio Accounts Inputs
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            HouseholdIncomeAssumptionsCard(
                                uiState = uiState,
                                onRetirementAgeChange = viewModel::setRetirementAge,
                                onPresetChange = viewModel::setPreset,
                                onTargetModeChange = viewModel::setTargetMode,
                                onAnnualSpendingChange = viewModel::setAnnualSpending,
                                onResetAnnualSpending = viewModel::resetAnnualSpendingToGuideline,
                                onCustomTargetChange = viewModel::setCustomTarget,
                                onLegacyAmountChange = viewModel::setLegacyAmount,
                                onNavigateToHousehold = { onNavigate(OrbitNavKey.Household) }
                            )
                        }

                        item {
                            RetirementAccountsListCard(
                                uiState = uiState,
                                onCurrentBalanceChange = viewModel::setCurrentBalance,
                                onResetCurrentBalance = viewModel::resetCurrentBalanceToConnectedAccounts,
                                onMonthlySavingsChange = viewModel::setMonthlySavings,
                                onResetMonthlySavings = viewModel::resetMonthlySavingsToHouseholdPace
                            )
                        }

                        item {
                            MemberRetirementBreakdownCard(
                                members = uiState.householdMembers,
                                accounts = uiState.allAccounts
                            )
                        }
                    }

                    // Right Column: Live Projection Hero Chart, Runway, & History
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            RetirementProjectionHeroCard(
                                uiState = uiState,
                                onAgeScrubbed = viewModel::onAgeScrubbed
                            )
                        }

                        item {
                            RetirementRunwayCard(
                                uiState = uiState,
                                onRunwayEndAgeChange = viewModel::setRunwayEndAge
                            )
                        }

                        item {
                            HsaBridgeAnalysisCard(
                                hsaBridge = uiState.scenario.hsaBridge,
                                retirementAge = uiState.retirementAge,
                                annualSpendingCents = uiState.annualSpendingCents
                            )
                        }

                        item {
                            RetirementHistoryTrajectoryCard(
                                historyPoints = uiState.historyPoints,
                                linkedAccounts = uiState.linkedAccounts,
                                selectedHorizon = uiState.selectedHorizon,
                                onSelectHorizon = viewModel::setSelectedHorizon
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        RetirementProjectionHeroCard(
                            uiState = uiState,
                            onAgeScrubbed = viewModel::onAgeScrubbed
                        )
                    }

                    item {
                        RetirementRunwayCard(
                            uiState = uiState,
                            onRunwayEndAgeChange = viewModel::setRunwayEndAge
                        )
                    }

                    item {
                        HouseholdIncomeAssumptionsCard(
                            uiState = uiState,
                            onRetirementAgeChange = viewModel::setRetirementAge,
                            onPresetChange = viewModel::setPreset,
                            onTargetModeChange = viewModel::setTargetMode,
                            onAnnualSpendingChange = viewModel::setAnnualSpending,
                            onResetAnnualSpending = viewModel::resetAnnualSpendingToGuideline,
                            onCustomTargetChange = viewModel::setCustomTarget,
                            onLegacyAmountChange = viewModel::setLegacyAmount,
                            onNavigateToHousehold = { onNavigate(OrbitNavKey.Household) }
                        )
                    }

                    item {
                        RetirementAccountsListCard(
                            uiState = uiState,
                            onCurrentBalanceChange = viewModel::setCurrentBalance,
                            onResetCurrentBalance = viewModel::resetCurrentBalanceToConnectedAccounts,
                            onMonthlySavingsChange = viewModel::setMonthlySavings,
                            onResetMonthlySavings = viewModel::resetMonthlySavingsToHouseholdPace
                        )
                    }

                    item {
                        MemberRetirementBreakdownCard(
                            members = uiState.householdMembers,
                            accounts = uiState.allAccounts
                        )
                    }

                    item {
                        HsaBridgeAnalysisCard(
                            hsaBridge = uiState.scenario.hsaBridge,
                            retirementAge = uiState.retirementAge,
                            annualSpendingCents = uiState.annualSpendingCents
                        )
                    }

                    item {
                        RetirementHistoryTrajectoryCard(
                            historyPoints = uiState.historyPoints,
                            linkedAccounts = uiState.linkedAccounts,
                            selectedHorizon = uiState.selectedHorizon,
                            onSelectHorizon = viewModel::setSelectedHorizon
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        if (uiState.showTargetSettingsSheet) {
            TargetSettingsSheet(
                targetMode = uiState.targetMode,
                customTargetAmountCents = uiState.customTargetCents,
                legacyAmountCents = uiState.legacyAmountCents,
                runwayEndAge = uiState.runwayEndAge,
                onDismiss = viewModel::closeTargetSettings,
                onSave = viewModel::saveTargetSettings
            )
        }
    }
}
