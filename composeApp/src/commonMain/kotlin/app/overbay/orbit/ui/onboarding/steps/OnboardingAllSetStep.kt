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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TaxTrackingLabels
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime

@Composable
fun OnboardingAllSetStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val prefs = state.preferences

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val headline = when {
            state.isReplay -> "Setup Updated"
            state.existingServerDetected -> "Welcome Back to Orbit"
            else -> "You're All Set!"
        }
        val subtitle = when {
            state.isReplay -> "Your preferences and workspace settings are saved."
            state.existingServerDetected -> "We loaded your existing household from your server—everything is right where you left it."
            else -> "Your Orbit Money workspace is ready. Everything here can be adjusted anytime in Settings."
        }

        OnboardingHeroHeader(
            title = headline,
            subtitle = subtitle,
            useBrandLogo = true
        )

        Spacer(modifier = Modifier.height(22.dp))

        OnboardingCardContainer {
            OrbitMiniStatRow(
                label = "Storage",
                value = if (prefs.storageMode == StorageMode.SELF_HOSTED) "Self-Hosted Server" else "On This Device",
                valueColor = MaterialTheme.colorScheme.primary
            )
            OrbitMiniStatRow(
                label = "Theme",
                value = "${prefs.themePreset.displayName} · ${prefs.themeMode.shortLabel}",
                showTopDivider = true
            )
            if (state.existingAccounts.isNotEmpty()) {
                OrbitMiniStatRow(
                    label = "Accounts",
                    value = "${state.existingAccounts.size} connected",
                    showTopDivider = true
                )
            }
            if (state.simpleFinConnected) {
                OrbitMiniStatRow(
                    label = "Bank Sync",
                    value = "SimpleFIN Bridge",
                    valueColor = MaterialTheme.colorScheme.primary,
                    showTopDivider = true
                )
            }
            if (state.householdMembers.isNotEmpty()) {
                OrbitMiniStatRow(
                    label = "Household",
                    value = "${state.householdMembers.size} member${if (state.householdMembers.size == 1) "" else "s"}",
                    showTopDivider = true
                )
            }
            if (state.enteredAct2 && "retirement_calculator" in state.enabledFeatures) {
                OrbitMiniStatRow(
                    label = "Retirement Goal",
                    value = "Age ${state.retirementAge} (${state.retirementPreset.label})",
                    showTopDivider = true
                )
            }
            if (state.enteredAct2 && "mha" in state.enabledFeatures) {
                OrbitMiniStatRow(
                    label = "Tax Tracking",
                    value = TaxTrackingLabels.forMode(state.taxTrackingMode).navTitle,
                    showTopDivider = true
                )
            }
        }

        if (!PlatformRuntime.isWeb) {
            Spacer(modifier = Modifier.height(14.dp))
            OnboardingCardContainer(contentPaddingDp = 12) {
                OrbitSwitchRow(
                    title = "Lock Orbit with Biometrics",
                    subtitle = "Require fingerprint or face unlock when opening the app",
                    checked = prefs.biometricLockEnabled,
                    onCheckedChange = { onIntent(OnboardingIntent.SetBiometricLock(it)) },
                    showContainer = false
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                haptics.confirm()
                onIntent(OnboardingIntent.FinishOnboarding)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (state.isReplay) "Done" else "Enter Orbit Money",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
