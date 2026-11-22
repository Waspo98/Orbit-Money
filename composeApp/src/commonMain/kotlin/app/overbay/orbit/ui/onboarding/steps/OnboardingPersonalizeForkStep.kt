package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingBenefitRow
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingPersonalizeForkStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val acctCount = state.existingAccounts.size
        val subtitle = if (acctCount > 0) {
            "You already have $acctCount account${if (acctCount == 1) "" else "s"} ready. Want to take a minute to tailor Orbit to your household?"
        } else {
            "Your workspace is ready. Want to answer a few quick, skippable questions so Orbit is set up for your life?"
        }

        OnboardingHeroHeader(
            title = "Tailor Your Workspace?",
            subtitle = subtitle,
            icon = OrbitIcons.ViewSliders
        )

        Spacer(modifier = Modifier.height(22.dp))

        OnboardingCardContainer {
            OnboardingBenefitRow(
                icon = OrbitIcons.Dashboard,
                title = "Choose Your Tools",
                description = "Keep only the screens you care about and hide the rest."
            )
            OnboardingBenefitRow(
                icon = OrbitIcons.Accounts,
                title = "Add Extra Accounts",
                description = "Track your home value, mortgage, 401(k), HSA, or cash alongside bank feeds."
            )
            OnboardingBenefitRow(
                icon = OrbitIcons.Household,
                title = "Household & Retirement Target",
                description = "Quickly set who's in your household and when you hope to retire."
            )
            OnboardingBenefitRow(
                icon = OrbitIcons.MhaTracker,
                title = "Optional Tax-Deductible Tracking",
                description = "Turn on General Deductible Expenses or Ministerial Housing Allowance if you need it."
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = {
                haptics.confirm()
                onIntent(OnboardingIntent.EnterAct2Personalize)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Personalize Orbit (~1 min)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                haptics.tick()
                onIntent(OnboardingIntent.SkipToDashboard)
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Skip to Dashboard", fontWeight = FontWeight.SemiBold)
        }
    }
}
