package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.ResolvedOrbitPalette
import app.overbay.orbit.theme.resolveOrbitPalette
import app.overbay.orbit.ui.components.LiveThemePreviewCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.ui.settings.components.ThemeSwatchGrid
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.PlatformRuntime

@Composable
fun OnboardingAppearanceStep(
    state: OnboardingUiState,
    posture: DevicePosture,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = state.preferences
    val isDarkPreview = when (prefs.themeMode) {
        OrbitThemeMode.DARK -> true
        OrbitThemeMode.LIGHT -> false
        OrbitThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val useDynamic = !PlatformRuntime.isWeb && prefs.dynamicColor
    val currentScheme = MaterialTheme.colorScheme
    val currentCustom = OrbitTheme.colors
    val previewPalette = remember(prefs, isDarkPreview, useDynamic, currentScheme, currentCustom) {
        if (useDynamic) {
            ResolvedOrbitPalette(colorScheme = currentScheme, customColors = currentCustom)
        } else {
            resolveOrbitPalette(
                themePreset = prefs.themePreset,
                isDark = isDarkPreview,
                amoledDark = prefs.amoledDarkMode,
                useCustomTheme = prefs.useCustomTheme,
                customAccentHex = prefs.customAccentHex
            )
        }
    }

    if (posture.supportsTwoPane) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingHeroHeader(
                    title = "Make Orbit Yours",
                    subtitle = "Pick your light or dark style and color palette. Changes preview live right away."
                )
                LiveThemePreviewCard(palette = previewPalette)
            }
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppearanceControlsBlock(prefs = prefs, isDarkPreview = isDarkPreview, onIntent = onIntent)
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingHeroHeader(
                title = "Make Orbit Yours",
                subtitle = "Pick a light or dark look and color palette. You can change this anytime in Settings."
            )
            LiveThemePreviewCard(palette = previewPalette)
            AppearanceControlsBlock(prefs = prefs, isDarkPreview = isDarkPreview, onIntent = onIntent)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AppearanceControlsBlock(
    prefs: OrbitPreferences,
    isDarkPreview: Boolean,
    onIntent: (OnboardingIntent) -> Unit
) {
    OnboardingCardContainer(contentPaddingDp = 14) {
        Text(
            text = "Display Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        OrbitSegmentedTabRow(
            items = OrbitThemeMode.entries,
            selected = prefs.themeMode,
            onSelect = { onIntent(OnboardingIntent.SetThemeMode(it)) },
            label = { it.shortLabel }
        )

        Text(
            text = "Dark Surface",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OrbitSegmentedTabRow(
            items = listOf(false, true),
            selected = prefs.amoledDarkMode,
            onSelect = { onIntent(OnboardingIntent.SetAmoledDark(it)) },
            label = { if (it) "AMOLED Black" else "Soft Dark" }
        )
    }

    OnboardingCardContainer(contentPaddingDp = 14) {
        Text(
            text = "Color Palette",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        ThemeSwatchGrid(
            selectedPreset = if (prefs.useCustomTheme) null else prefs.themePreset,
            isDarkPreview = isDarkPreview,
            isOverriddenByDynamicColor = !PlatformRuntime.isWeb && prefs.dynamicColor,
            onPresetSelected = { onIntent(OnboardingIntent.SetThemePreset(it)) }
        )
        if (!PlatformRuntime.isWeb) {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            OrbitSwitchRow(
                title = "Match Wallpaper Colors",
                subtitle = "Use Android Material You colors from your wallpaper",
                checked = prefs.dynamicColor,
                onCheckedChange = { onIntent(OnboardingIntent.SetDynamicColor(it)) },
                showContainer = false
            )
        }
    }
}
