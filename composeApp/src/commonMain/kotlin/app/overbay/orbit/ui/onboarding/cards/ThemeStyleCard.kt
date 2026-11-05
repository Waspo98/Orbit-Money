package app.overbay.orbit.ui.onboarding.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Visual styling and theme customizer card for the Onboarding flow.
 * Features color preset swatches, display mode selection, and live component preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStyleCard(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "Appearance & Style",
        subtitle = "Personalize colors and display modes",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Preset Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACCENT COLOR PRESET",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbitThemePreset.entries.forEach { preset ->
                        val isSelected = state.selectedThemePreset == preset
                        val presetColor = getPresetSwatchColor(preset)

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    haptics.tick()
                                    onIntent(OnboardingIntent.SelectThemePreset(preset))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = preset.displayName,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Display Mode (System, Light, Dark)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DISPLAY THEME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = OrbitThemeMode.entries
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.selectedThemeMode == mode,
                            onClick = {
                                haptics.tick()
                                onIntent(OnboardingIntent.SelectThemeMode(mode))
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            icon = {},
                            label = { Text(mode.labelWithEmoji, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            // Material You Dynamic Color Switch
            OrbitSwitchRow(
                title = "Dynamic Color (Material You)",
                subtitle = "Derive theme accents from your wallpaper (Android 12+)",
                checked = state.dynamicColorEnabled,
                onCheckedChange = { onIntent(OnboardingIntent.SetDynamicColor(it)) }
            )

            // Optional Biometric App Lock
            val biometricHelper = app.overbay.orbit.util.LocalBiometricAuthHelper.current
            val canBiometric = androidx.compose.runtime.remember { biometricHelper.canAuthenticate() }
            if (canBiometric) {
                OrbitSwitchRow(
                    title = "Enable Biometric App Lock",
                    subtitle = "Protect sensitive household data with biometric unlock",
                    checked = state.biometricOptIn,
                    onCheckedChange = { onIntent(OnboardingIntent.SetBiometricOptIn(it)) }
                )
            }

            // Live Miniature Preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "LIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Checking Account",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$4,850.25",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sample CTA")
                        }
                    }
                }
            }
        }
    }
}

private fun getPresetSwatchColor(preset: OrbitThemePreset): Color = when (preset) {
    OrbitThemePreset.ORBIT_EMERALD -> Color(0xFF10B981)
    OrbitThemePreset.OCEAN_SLATE -> Color(0xFF0EA5E9)
    OrbitThemePreset.WARM_TERRACOTTA -> Color(0xFFEA580C)
    OrbitThemePreset.ROYAL_INDIGO -> Color(0xFF6366F1)
    OrbitThemePreset.MONOCHROME_INK -> Color(0xFF475569)
}
