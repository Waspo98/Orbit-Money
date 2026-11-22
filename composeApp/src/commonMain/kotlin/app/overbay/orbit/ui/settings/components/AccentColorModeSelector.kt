package app.overbay.orbit.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitAccentColorMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.getPlatformColorScheme
import app.overbay.orbit.theme.resolveOrbitPalette
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Reusable 3-option Accent Colors selector shared by Settings -> Appearance
 * and Onboarding -> Appearance. Renders live positive / warning / negative color
 * preview dots computed from the user's active palette source for each option.
 */
@Composable
fun AccentColorModeSelector(
    selectedMode: OrbitAccentColorMode,
    themePreset: OrbitThemePreset,
    isDarkPreview: Boolean,
    amoledDark: Boolean,
    useCustomTheme: Boolean,
    customAccentHex: String?,
    useDynamicColor: Boolean,
    onModeSelected: (OrbitAccentColorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicScheme = getPlatformColorScheme(darkTheme = isDarkPreview, dynamicColor = useDynamicColor)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrbitAccentColorMode.entries.forEach { mode ->
            val previewPalette = remember(
                themePreset,
                isDarkPreview,
                amoledDark,
                useCustomTheme,
                customAccentHex,
                dynamicScheme,
                mode
            ) {
                resolveOrbitPalette(
                    themePreset = themePreset,
                    isDark = isDarkPreview,
                    amoledDark = amoledDark,
                    useCustomTheme = useCustomTheme,
                    customAccentHex = customAccentHex,
                    dynamicScheme = dynamicScheme,
                    accentColorMode = mode
                )
            }
            AccentColorModeRow(
                mode = mode,
                isSelected = mode == selectedMode,
                positiveColor = previewPalette.customColors.income,
                warningColor = previewPalette.customColors.warning,
                negativeColor = previewPalette.customColors.danger,
                dotBorderColor = previewPalette.customColors.cardBorder,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun AccentColorModeRow(
    mode: OrbitAccentColorMode,
    isSelected: Boolean,
    positiveColor: Color,
    warningColor: Color,
    negativeColor: Color,
    dotBorderColor: Color,
    onClick: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
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
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PreviewColorDot(color = positiveColor, borderColor = dotBorderColor)
                PreviewColorDot(color = warningColor, borderColor = dotBorderColor)
                PreviewColorDot(color = negativeColor, borderColor = dotBorderColor)
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewColorDot(
    color: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, borderColor, CircleShape)
    )
}
