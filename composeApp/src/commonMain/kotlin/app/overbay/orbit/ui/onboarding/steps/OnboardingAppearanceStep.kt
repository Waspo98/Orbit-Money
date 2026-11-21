package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.getPresetColorScheme
import app.overbay.orbit.theme.getPresetCustomColors
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.RollingTickerCents
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
                LiveMiniOrbitPreviewCard(preferences = prefs, isDarkPreview = isDarkPreview)
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
            LiveMiniOrbitPreviewCard(preferences = prefs, isDarkPreview = isDarkPreview)
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
            selectedPreset = prefs.themePreset,
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

@Composable
private fun LiveMiniOrbitPreviewCard(
    preferences: OrbitPreferences,
    isDarkPreview: Boolean,
    modifier: Modifier = Modifier
) {
    val useDynamic = !PlatformRuntime.isWeb && preferences.dynamicColor
    val targetScheme = if (useDynamic) MaterialTheme.colorScheme else getPresetColorScheme(preferences.themePreset, isDarkPreview)
    val targetCustom = if (useDynamic) OrbitTheme.colors else getPresetCustomColors(preferences.themePreset, isDarkPreview)
    val targetSurface = if (isDarkPreview && preferences.amoledDarkMode) targetScheme.background else targetScheme.surfaceContainerLow

    val colorSpec = spring<androidx.compose.ui.graphics.Color>(stiffness = Spring.StiffnessLow)
    val bgColor by animateColorAsState(targetSurface, colorSpec, label = "previewBg")
    val canopyStart by animateColorAsState(targetCustom.canopyStart, colorSpec, label = "previewCanopyStart")
    val canopyEnd by animateColorAsState(targetCustom.canopyEnd, colorSpec, label = "previewCanopyEnd")
    val primaryColor by animateColorAsState(targetScheme.primary, colorSpec, label = "previewPrimary")
    val onSurface by animateColorAsState(targetScheme.onSurface, colorSpec, label = "previewOnSurface")
    val onSurfaceVar by animateColorAsState(targetScheme.onSurfaceVariant, colorSpec, label = "previewOnSurfaceVar")
    val incomeColor by animateColorAsState(targetCustom.income, colorSpec, label = "previewIncome")
    val incomeContainer by animateColorAsState(targetCustom.incomeContainer, colorSpec, label = "previewIncomeCont")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        shadowElevation = 3.dp,
        border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(canopyStart, canopyEnd)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "THIS MONTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceVar,
                            letterSpacing = 0.8.sp
                        )
                        RollingTickerCents(
                            cents = 142_050L,
                            style = TabularNumStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = primaryColor
                        )
                    }
                    OrbitBadge(
                        text = "+24% saved",
                        containerColor = incomeContainer,
                        contentColor = incomeColor
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Monthly Budget · Groceries & Dining",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = onSurface
                    )
                    Text(
                        text = "\$540 / \$800",
                        style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = onSurfaceVar
                    )
                }
                OrbitPillProgressBar(
                    percent = 0.675f,
                    customFillColor = primaryColor,
                    height = 8.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Everyday Checking",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVar
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "\$6,420.00",
                        style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                        color = onSurface
                    )
                }
            }
        }
    }
}
