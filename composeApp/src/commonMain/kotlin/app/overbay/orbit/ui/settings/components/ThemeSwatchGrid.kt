package app.overbay.orbit.ui.settings.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.getPresetColorScheme
import app.overbay.orbit.theme.getPresetCustomColors
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun ThemeSwatchGrid(
    selectedPreset: OrbitThemePreset,
    isDarkPreview: Boolean,
    isOverriddenByDynamicColor: Boolean,
    onPresetSelected: (OrbitThemePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridAlpha by animateFloatAsState(
        targetValue = if (isOverriddenByDynamicColor) 0.5f else 1f,
        animationSpec = OrbitSprings.FloatEffectsFast,
        label = "swatchGridAlpha"
    )
    val rows = remember { OrbitThemePreset.entries.chunked(2) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isOverriddenByDynamicColor) {
            SettingsCalloutBanner(
                icon = OrbitIcons.InfoCircle,
                text = "Wallpaper colors are active and currently override preset palettes."
            )
        }

        Column(modifier = Modifier.fillMaxWidth().alpha(gridAlpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowPresets ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowPresets.forEach { preset ->
                        ThemeSwatchCard(
                            preset = preset,
                            isSelected = preset == selectedPreset,
                            isDarkPreview = isDarkPreview,
                            onClick = { onPresetSelected(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowPresets.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    preset: OrbitThemePreset,
    isSelected: Boolean,
    isDarkPreview: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = getPresetColorScheme(preset, isDarkPreview)
    val custom = getPresetCustomColors(preset, isDarkPreview)

    Surface(
        modifier = modifier
            .orbitTactilePress(pressedScale = 0.975f, interactionSource = interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptics.tick()
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(custom.canopyStart, custom.canopyEnd)))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.width(30.dp).height(18.dp)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .border(1.dp, scheme.surface.copy(alpha = 0.7f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 11.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                            .border(1.dp, scheme.surface.copy(alpha = 0.7f), CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(scheme.background)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
