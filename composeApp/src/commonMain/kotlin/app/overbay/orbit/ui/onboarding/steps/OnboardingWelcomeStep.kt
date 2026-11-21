package app.overbay.orbit.ui.onboarding.steps

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingBenefitRow
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingWelcomeStep(
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
        OnboardingHeroHeader(
            title = "Welcome to Orbit Money",
            subtitle = "Clear, private household finance that you actually own.",
            useBrandLogo = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingCardContainer {
            OnboardingBenefitRow(
                icon = OrbitIcons.CloudSync,
                title = "Your Data, Your Way",
                description = "Keep everything offline on this device, or connect your own self-hosted Docker server."
            )
            OnboardingBenefitRow(
                icon = OrbitIcons.BankLink,
                title = "Easy Bank Sync & Imports",
                description = "Connect read-only bank feeds through SimpleFIN Bridge, import CSVs from other apps, or track manually."
            )
            OnboardingBenefitRow(
                icon = OrbitIcons.Budgets,
                title = "Budgets, Goals & Long-Term Planning",
                description = "See monthly cash flow, upcoming bills, net worth, and retirement projections in one calm workspace."
            )
            OnboardingBenefitRow(
                icon = OrbitIcons.Palette,
                title = "Built for Every Screen",
                description = "Smooth spring motion and adaptive layouts across phones, tablets, foldables, and web."
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = {
                haptics.confirm()
                onIntent(OnboardingIntent.Next)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(
            onClick = {
                haptics.tick()
                onIntent(OnboardingIntent.ExploreSampleDataQuickStart)
            },
            enabled = !state.isSubmittingAuth
        ) {
            if (state.isSubmittingAuth) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text = "Just looking around? Explore Sample Data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
