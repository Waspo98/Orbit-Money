package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.ResolvedOrbitPalette
import app.overbay.orbit.theme.bestOnColor
import app.overbay.orbit.theme.hslToColor
import app.overbay.orbit.theme.normalizeAccentHex
import app.overbay.orbit.theme.resolveOrbitPalette
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.LiveThemePreviewCard
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.ui.settings.components.ThemeSwatchGrid
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime

fun LazyListScope.appearanceSectionItems(
    preferences: OrbitPreferences,
    onThemeModeChange: (OrbitThemeMode) -> Unit,
    onAmoledDarkChange: (Boolean) -> Unit,
    onPresetChange: (OrbitThemePreset) -> Unit,
    onEnableSavedCustomTheme: () -> Unit,
    onOpenCustomThemePicker: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit
) {
    item(key = "appearance_live_preview") {
        val isDarkPreview = when (preferences.themeMode) {
            OrbitThemeMode.DARK -> true
            OrbitThemeMode.LIGHT -> false
            OrbitThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
        val useDynamic = !PlatformRuntime.isWeb && preferences.dynamicColor
        val currentScheme = MaterialTheme.colorScheme
        val currentCustom = OrbitTheme.colors
        val previewPalette = remember(preferences, isDarkPreview, useDynamic, currentScheme, currentCustom) {
            if (useDynamic) {
                ResolvedOrbitPalette(colorScheme = currentScheme, customColors = currentCustom)
            } else {
                resolveOrbitPalette(
                    themePreset = preferences.themePreset,
                    isDark = isDarkPreview,
                    amoledDark = preferences.amoledDarkMode,
                    useCustomTheme = preferences.useCustomTheme,
                    customAccentHex = preferences.customAccentHex
                )
            }
        }
        LiveThemePreviewCard(palette = previewPalette)
    }

    item(key = "appearance_display_mode") {
        OrbitSectionCard(
            title = "Display Mode",
            subtitle = "Choose how Orbit adapts to light and dark environments"
        ) {
            OrbitSegmentedTabRow(
                items = OrbitThemeMode.entries,
                selected = preferences.themeMode,
                onSelect = onThemeModeChange,
                label = { it.shortLabel }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Dark Surface",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            OrbitSegmentedTabRow(
                items = listOf(false, true),
                selected = preferences.amoledDarkMode,
                onSelect = onAmoledDarkChange,
                label = { if (it) "AMOLED Black" else "Soft Dark" }
            )

            if (preferences.themeMode == OrbitThemeMode.LIGHT) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Applies automatically when Dark mode is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    item(key = "appearance_palette") {
        OrbitSectionCard(
            title = "Color Palette",
            subtitle = "Themes your headers, surfaces, borders, fonts, and accents"
        ) {
            val isDarkPreview = when (preferences.themeMode) {
                OrbitThemeMode.DARK -> true
                OrbitThemeMode.LIGHT -> false
                OrbitThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val isOverriddenByDynamic = !PlatformRuntime.isWeb && preferences.dynamicColor

            ThemeSwatchGrid(
                selectedPreset = if (preferences.useCustomTheme) null else preferences.themePreset,
                isDarkPreview = isDarkPreview,
                isOverriddenByDynamicColor = isOverriddenByDynamic,
                onPresetSelected = onPresetChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            CustomAccentThemeCard(
                savedHex = normalizeAccentHex(preferences.customAccentHex),
                isSelected = !isOverriddenByDynamic && preferences.useCustomTheme && normalizeAccentHex(preferences.customAccentHex) != null,
                onSelectCard = {
                    val hasSavedCustom = normalizeAccentHex(preferences.customAccentHex) != null
                    val isCurrentlyActive = !isOverriddenByDynamic && preferences.useCustomTheme && hasSavedCustom
                    if (hasSavedCustom && !isCurrentlyActive) {
                        onEnableSavedCustomTheme()
                    } else {
                        onOpenCustomThemePicker()
                    }
                },
                onCustomizeClick = onOpenCustomThemePicker
            )

            if (!PlatformRuntime.isWeb) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                OrbitSwitchRow(
                    title = "Match Wallpaper Colors",
                    subtitle = "Derive palette dynamically from your device wallpaper (Android 12+)",
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                    showContainer = false
                )
            }
        }
    }

    item(key = "look_and_feel_haptics") {
        OrbitSectionCard(
            title = "Touch Feedback",
            subtitle = "Tactile response across controls and chart scrubbers"
        ) {
            OrbitSwitchRow(
                title = "Haptic Feedback",
                subtitle = "Subtle vibrations when adjusting sliders, scrubbing charts, and tapping controls",
                checked = preferences.hapticsEnabled,
                onCheckedChange = onHapticsChange,
                showContainer = false
            )
        }
    }
}

@Composable
private fun CustomAccentThemeCard(
    savedHex: String?,
    isSelected: Boolean,
    onSelectCard: () -> Unit,
    onCustomizeClick: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val savedColor = remember(savedHex) { savedHex?.toComposeColor() }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .orbitTactilePress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptics.tick()
                onSelectCard()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (savedColor != null) {
                            Modifier.background(savedColor)
                        } else {
                            Modifier.background(
                                Brush.sweepGradient(
                                    listOf(
                                        hslToColor(0f, 0.78f, 0.52f),
                                        hslToColor(90f, 0.70f, 0.45f),
                                        hslToColor(190f, 0.78f, 0.46f),
                                        hslToColor(280f, 0.72f, 0.56f),
                                        hslToColor(360f, 0.78f, 0.52f)
                                    )
                                )
                            )
                        }
                    )
                    .border(1.dp, OrbitTheme.colors.cardBorder, CircleShape)
            ) {
                if (isSelected && savedColor != null) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = bestOnColor(savedColor),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (savedColor == null) {
                    Icon(
                        imageVector = OrbitIcons.Palette,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Custom Accent",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (savedHex != null) {
                        "$savedHex · Full light & dark tonal palette"
                    } else {
                        "Pick any color to generate a complete theme"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = {
                    haptics.tick()
                    onCustomizeClick()
                }
            ) {
                Text(
                    text = if (savedHex != null) "Customize" else "Pick Color",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
