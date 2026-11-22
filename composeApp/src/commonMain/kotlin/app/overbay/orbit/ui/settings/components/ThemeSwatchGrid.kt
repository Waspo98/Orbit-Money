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
import app.overbay.orbit.data.model.OrbitAccentColorMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.resolveOrbitPalette
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun ThemeSwatchGrid(
    selectedPreset: OrbitThemePreset?,
    isDarkPreview: Boolean,
    isOverriddenByDynamicColor: Boolean,
    onPresetSelected: (OrbitThemePreset) -> Unit,
    modifier: Modifier = Modifier,
    accentColorMode: OrbitAccentColorMode = OrbitAccentColorMode.MATCH_THEME
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
                text = "Wallpaper colors are active. Tap any palette below to switch."
            )
        }

        Column(modifier = Modifier.fillMaxWidth().alpha(gridAlpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowPresets ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowPresets.forEach { preset ->
                        ThemeSwatchCard(
                            preset = preset,
                            isSelected = !isOverriddenByDynamicColor && preset == selectedPreset,
                            isDarkPreview = isDarkPreview,
                            accentColorMode = accentColorMode,
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
    accentColorMode: OrbitAccentColorMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val palette = remember(preset, isDarkPreview, accentColorMode) {
        resolveOrbitPalette(
            themePreset = preset,
            isDark = isDarkPreview,
            accentColorMode = accentColorMode
        )
    }
    val scheme = palette.colorScheme
    val custom = palette.customColors
    val thirdDotColor = if (accentColorMode == OrbitAccentColorMode.MATCH_THEME) {
        scheme.tertiary
    } else {
        custom.income
    }

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
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mini app preview: canopy strip + tinted canvas/surface/ink/accent bars
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, custom.cardBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(Brush.horizontalGradient(listOf(custom.canopyStart, custom.canopyMid, custom.canopyEnd)))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(custom.onCanopy.copy(alpha = 0.9f))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(custom.canopyKicker)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.background)
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurface)
                        )
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurfaceVariant)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
                        )
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(scheme.secondaryContainer)
                                .border(0.5.dp, custom.cardBorder, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(thirdDotColor)
                        )
                    }
                }
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
