package app.overbay.orbit.ui.calculators.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.calculators.retirement.cards.HouseholdIncomeAssumptionsCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementAccountsListCard
import app.overbay.orbit.ui.calculators.retirement.cards.RetirementProjectionHeroCard
import app.overbay.orbit.ui.components.MorphingPageHero

@Composable
fun RetirementCalculatorScreen(
    viewModel: RetirementViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        MorphingPageHero(
            title = "Retirement Calculator",
            subtitle = "Coast FIRE, target nest egg projection & portfolio runway"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RetirementProjectionHeroCard(
                uiState = uiState,
                onAgeScrubbed = viewModel::onAgeScrubbed
            )

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

            RetirementAccountsListCard(
                uiState = uiState,
                onCurrentBalanceChange = viewModel::setCurrentBalance,
                onMonthlySavingsChange = viewModel::setMonthlySavings,
                onToggleUseConnectedAccounts = viewModel::setUseConnectedAccounts
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
