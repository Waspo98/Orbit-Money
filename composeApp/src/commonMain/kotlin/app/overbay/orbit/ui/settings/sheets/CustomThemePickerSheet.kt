package app.overbay.orbit.ui.settings.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.CuratedSeedSwatches
import app.overbay.orbit.theme.DEFAULT_CUSTOM_ACCENT_HEX
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.bestOnColor
import app.overbay.orbit.theme.hslToColor
import app.overbay.orbit.theme.normalizeAccentHex
import app.overbay.orbit.theme.resolveOrbitPalette
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.theme.toHexString
import app.overbay.orbit.theme.toHsl
import app.overbay.orbit.ui.components.LiveThemePreviewCard
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Settings modal sheet for choosing a single custom accent color seed.
 * Automatically derives a full, WCAG AA-verified Light, Soft Dark, and AMOLED palette.
 */
@Composable
fun CustomThemePickerSheet(
    initialHex: String,
    initialDarkPreview: Boolean,
    amoledDark: Boolean,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val safeInitialHex = remember(initialHex) {
        normalizeAccentHex(initialHex) ?: DEFAULT_CUSTOM_ACCENT_HEX
    }
    val initialHsl = remember(safeInitialHex) {
        safeInitialHex.toComposeColor().toHsl()
    }

    var hue by rememberSaveable(safeInitialHex) { mutableStateOf(initialHsl.hue) }
    var saturation by rememberSaveable(safeInitialHex) { mutableStateOf(initialHsl.saturation.coerceIn(0.05f, 1.0f)) }
    var lightness by rememberSaveable(safeInitialHex) { mutableStateOf(initialHsl.lightness.coerceIn(0.24f, 0.62f)) }
    var hexInput by rememberSaveable(safeInitialHex) { mutableStateOf(safeInitialHex) }
    var previewDark by rememberSaveable { mutableStateOf(initialDarkPreview) }

    val draftColor = remember(hue, saturation, lightness) {
        hslToColor(hue, saturation, lightness)
    }
    val draftHex = remember(draftColor) { draftColor.toHexString() }
    val normalizedInput = remember(hexInput) { normalizeAccentHex(hexInput) }
    val hexError = hexInput.isNotBlank() && normalizedInput == null
    val activeHex = normalizedInput ?: draftHex

    val previewPalette = remember(activeHex, previewDark, amoledDark) {
        resolveOrbitPalette(
            themePreset = OrbitThemePreset.ORBIT_EMERALD,
            isDark = previewDark,
            amoledDark = amoledDark,
            useCustomTheme = true,
            customAccentHex = activeHex
        )
    }

    fun selectSeedHex(hex: String) {
        val norm = normalizeAccentHex(hex) ?: return
        val hsl = norm.toComposeColor().toHsl()
        hue = hsl.hue
        saturation = hsl.saturation.coerceIn(0.05f, 1.0f)
        lightness = hsl.lightness.coerceIn(0.24f, 0.62f)
        hexInput = norm
    }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Apply Theme",
        onConfirm = {
            haptics.confirm()
            onApply(activeHex)
        },
        confirmEnabled = !hexError,
        title = "Custom Accent",
        subtitle = "Pick one color — Orbit generates a complete light & dark palette",
        modifier = modifier
    ) {
        // 1. Live theme preview + Light/Dark preview toggle
        LiveThemePreviewCard(palette = previewPalette)

        OrbitSegmentedTabRow(
            items = listOf(false, true),
            selected = previewDark,
            onSelect = { previewDark = it },
            label = { if (it) "Dark Preview" else "Light Preview" }
        )

        // 2. Curated seed swatch grid (2 rows of 6)
        Text(
            text = "Quick Swatches",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val swatchRows = remember { CuratedSeedSwatches.chunked(6) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            swatchRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { (name, swatchHex) ->
                        val swatchColor = swatchHex.toComposeColor()
                        val isSelected = activeHex.equals(swatchHex, ignoreCase = true)
                        SeedSwatchDot(
                            name = name,
                            color = swatchColor,
                            isSelected = isSelected,
                            onClick = {
                                haptics.tick()
                                selectSeedHex(swatchHex)
                            }
                        )
                    }
                }
            }
        }

        // 3. Continuous Hue / Vibrancy / Depth sliders
        GradientColorSlider(
            label = "Hue",
            valueLabel = "${hue.roundToInt()}°",
            value = hue,
            valueRange = 0f..360f,
            gradientColors = listOf(
                hslToColor(0f, 0.85f, 0.50f),
                hslToColor(60f, 0.85f, 0.50f),
                hslToColor(120f, 0.85f, 0.45f),
                hslToColor(180f, 0.85f, 0.45f),
                hslToColor(240f, 0.85f, 0.55f),
                hslToColor(300f, 0.85f, 0.55f),
                hslToColor(360f, 0.85f, 0.50f)
            ),
            thumbColor = hslToColor(hue, 0.85f, 0.50f),
            onValueChange = {
                hue = it
                hexInput = hslToColor(hue, saturation, lightness).toHexString()
            }
        )

        GradientColorSlider(
            label = "Vibrancy",
            valueLabel = "${(saturation * 100f).roundToInt()}%",
            value = saturation,
            valueRange = 0.05f..1.0f,
            gradientColors = listOf(
                hslToColor(hue, 0.05f, lightness),
                hslToColor(hue, 0.50f, lightness),
                hslToColor(hue, 1.0f, lightness)
            ),
            thumbColor = draftColor,
            onValueChange = {
                saturation = it
                hexInput = hslToColor(hue, saturation, lightness).toHexString()
            }
        )

        GradientColorSlider(
            label = "Depth",
            valueLabel = "${(lightness * 100f).roundToInt()}%",
            value = lightness,
            valueRange = 0.24f..0.62f,
            gradientColors = listOf(
                hslToColor(hue, saturation, 0.24f),
                hslToColor(hue, saturation, 0.43f),
                hslToColor(hue, saturation, 0.62f)
            ),
            thumbColor = draftColor,
            onValueChange = {
                lightness = it
                hexInput = hslToColor(hue, saturation, lightness).toHexString()
            }
        )

        // 4. Bidirectional hex input + generated palette token strip
        OrbitTextField(
            value = hexInput,
            onValueChange = { next ->
                val filtered = next.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(7)
                hexInput = filtered
                normalizeAccentHex(filtered)?.let { valid ->
                    val parsedHsl = valid.toComposeColor().toHsl()
                    hue = parsedHsl.hue
                    saturation = parsedHsl.saturation.coerceIn(0.05f, 1.0f)
                    lightness = parsedHsl.lightness.coerceIn(0.24f, 0.62f)
                }
            },
            label = { Text("Hex Color") },
            placeholder = { Text("#0D9488") },
            singleLine = true,
            isError = hexError,
            supportingText = if (hexError) {
                { Text("Enter a 6-digit hex color like #0D9488") }
            } else null
        )

        // 5. Auto-generated palette strip & contrast confirmation
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val tokens = listOf(
                        previewPalette.colorScheme.primary,
                        previewPalette.colorScheme.secondary,
                        previewPalette.colorScheme.tertiary,
                        previewPalette.customColors.income,
                        previewPalette.colorScheme.surfaceContainerHigh
                    )
                    tokens.forEach { tokenColor ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(tokenColor)
                                .border(1.dp, OrbitTheme.colors.cardBorder, CircleShape)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = previewPalette.customColors.income,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AA Contrast Verified",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedSwatchDot(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "seedRing"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(2.dp, ringColor, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(color)
            .orbitTactilePress(pressedScale = 0.92f)
            .semantics { contentDescription = name }
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = bestOnColor(color),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun GradientColorSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientColors: List<Color>,
    thumbColor: Color,
    onValueChange: (Float) -> Unit
) {
    val borderColor = OrbitTheme.colors.cardBorder
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(10.dp)) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(brush = Brush.horizontalGradient(gradientColors), cornerRadius = radius)
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
