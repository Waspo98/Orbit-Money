package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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

@Composable
fun ThemeAppearanceBlock(
    preferences: OrbitPreferences,
    onPresetChange: (OrbitThemePreset) -> Unit,
    onThemeModeChange: (OrbitThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = preferences.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.displayName) },
                    modifier = Modifier.weight(1f)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dynamic Material You Colors",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Harmonize with device wallpaper palette (Android 12+)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.dynamicColor,
                onCheckedChange = onDynamicColorChange
            )
        }
    }
}
