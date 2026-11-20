package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.settings.components.ThemeSwatchGrid
import app.overbay.orbit.util.PlatformRuntime

fun LazyListScope.appearanceSectionItems(
    preferences: OrbitPreferences,
    onThemeModeChange: (OrbitThemeMode) -> Unit,
    onAmoledDarkChange: (Boolean) -> Unit,
    onPresetChange: (OrbitThemePreset) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
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
            subtitle = "Choose your accent and canopy palette"
        ) {
            val isDarkPreview = when (preferences.themeMode) {
                OrbitThemeMode.DARK -> true
                OrbitThemeMode.LIGHT -> false
                OrbitThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            ThemeSwatchGrid(
                selectedPreset = preferences.themePreset,
                isDarkPreview = isDarkPreview,
                isOverriddenByDynamicColor = !PlatformRuntime.isWeb && preferences.dynamicColor,
                onPresetSelected = onPresetChange
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
}
