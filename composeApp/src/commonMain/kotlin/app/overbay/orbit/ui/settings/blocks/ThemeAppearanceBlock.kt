package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeAppearanceBlock(
    preferences: OrbitPreferences,
    onPresetChange: (OrbitThemePreset) -> Unit,
    onThemeModeChange: (OrbitThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAmoledDarkChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Appearance & Theme",
        subtitle = "Material Design 3 Expressive tokens and color schemes",
        modifier = modifier
    ) {
        // Theme Mode (Light / Dark / System)
        Text(
            text = "Display Mode",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = OrbitThemeMode.entries
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = preferences.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {},
                    label = { Text(mode.labelWithEmoji, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // AMOLED Pure Black Selector (shown when Dark or System mode selected)
        if (preferences.themeMode != OrbitThemeMode.LIGHT) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Dark Style",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !preferences.amoledDarkMode,
                    onClick = { onAmoledDarkChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                    label = { Text("Soft Dark", style = MaterialTheme.typography.labelMedium) }
                )
                SegmentedButton(
                    selected = preferences.amoledDarkMode,
                    onClick = { onAmoledDarkChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                    label = { Text("AMOLED Black", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Palette
        Text(
            text = "Color Palette Preset",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val presets = OrbitThemePreset.entries
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.take(3).forEach { preset ->
                    FilterChip(
                        selected = preferences.themePreset == preset,
                        onClick = { onPresetChange(preset) },
                        label = { Text(preset.displayName) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.drop(3).forEach { preset ->
                    FilterChip(
                        selected = preferences.themePreset == preset,
                        onClick = { onPresetChange(preset) },
                        label = { Text(preset.displayName) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Color Switch
        OrbitSwitchRow(
            title = "Dynamic Material You Colors",
            subtitle = "Harmonize with device wallpaper palette (Android 12+)",
            checked = preferences.dynamicColor,
            onCheckedChange = onDynamicColorChange
        )
    }
}
