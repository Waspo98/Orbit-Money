package app.overbay.orbit.ui.calculators.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import app.overbay.orbit.ui.components.HeroStatItem
import app.overbay.orbit.ui.components.HeroStatTone
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.rememberHeroScrollState

@Composable
fun RetirementCalculatorScreen(
    viewModel: RetirementViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState(maxHeight = 220.dp, minHeight = 64.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(heroScrollState.nestedScrollConnection)
    ) {
        // Edge-to-Edge Curved Green Canopy Hero
        MorphingPageHero(
            kicker = "LONG-RANGE PLANNING",
            title = "Retirement Calculator",
            subtitle = "All projections are shown in today's dollars",
            canNavigateBack = true,
            onBack = { onNavigate(OrbitNavKey.Dashboard) },
            scrollState = heroScrollState,
            stats = listOf(
                HeroStatItem("PROJECTED BALANCE", MoneyUtils.formatCents(uiState.scenario.projectedBalanceCents), HeroStatTone.GOOD),
                HeroStatItem("TARGET GOAL", MoneyUtils.formatCents(uiState.scenario.targetNestEggCents), HeroStatTone.DEFAULT),
                HeroStatItem("READINESS", "${uiState.scenario.readinessPercent.toInt()}%", if (uiState.scenario.readinessPercent >= 100.0) HeroStatTone.GOOD else HeroStatTone.WARN),
                HeroStatItem("MONTHLY SAVINGS", MoneyUtils.formatCents(uiState.monthlySavingsCents), HeroStatTone.DEFAULT)
            )
        )

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
