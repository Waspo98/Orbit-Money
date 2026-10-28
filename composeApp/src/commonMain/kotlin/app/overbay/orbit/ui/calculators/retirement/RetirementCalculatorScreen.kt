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
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementAccountsListCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementProjectionHeroCard
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.rememberHeroScrollState
import kotlin.math.roundToInt
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun RetirementCalculatorScreen(
    viewModel: RetirementViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
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
                canNavigateBack = true,
                onBack = onBack,
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
                    // Left Column: Assumptions & Retirement Accounts Inputs
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            HouseholdIncomeAssumptionsCard(
                                uiState = uiState,
                                onCurrentAgeChange = viewModel::setCurrentAge,
                                onRetirementAgeChange = viewModel::setRetirementAge,
                                onPresetChange = viewModel::setPreset,
                                onTargetModeChange = viewModel::setTargetMode,
                                onAnnualSpendingChange = viewModel::setAnnualSpending,
                                onCustomTargetChange = viewModel::setCustomTarget,
                                onLegacyAmountChange = viewModel::setLegacyAmount
                            )
                        }

                        item {
                            RetirementAccountsListCard(
                                uiState = uiState,
                                onCurrentBalanceChange = viewModel::setCurrentBalance,
                                onMonthlySavingsChange = viewModel::setMonthlySavings,
                                onToggleUseConnectedAccounts = viewModel::setUseConnectedAccounts
                            )
                        }
                    }

                    // Right Column: Live Projection Hero Chart & Results
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
                        HouseholdIncomeAssumptionsCard(
                            uiState = uiState,
                            onCurrentAgeChange = viewModel::setCurrentAge,
                            onRetirementAgeChange = viewModel::setRetirementAge,
                            onPresetChange = viewModel::setPreset,
                            onTargetModeChange = viewModel::setTargetMode,
                            onAnnualSpendingChange = viewModel::setAnnualSpending,
                            onCustomTargetChange = viewModel::setCustomTarget,
                            onLegacyAmountChange = viewModel::setLegacyAmount
                        )
                    }

                    item {
                        RetirementAccountsListCard(
                            uiState = uiState,
                            onCurrentBalanceChange = viewModel::setCurrentBalance,
                            onMonthlySavingsChange = viewModel::setMonthlySavings,
                            onToggleUseConnectedAccounts = viewModel::setUseConnectedAccounts
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
