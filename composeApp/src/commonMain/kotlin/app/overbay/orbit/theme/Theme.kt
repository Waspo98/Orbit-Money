package app.overbay.orbit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset

val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

fun getPresetPalette(preset: OrbitThemePreset, isDark: Boolean): ResolvedOrbitPalette =
    OrbitPresetPalettes.get(preset, isDark)

fun getPresetColorScheme(preset: OrbitThemePreset, isDark: Boolean): ColorScheme =
    OrbitPresetPalettes.get(preset, isDark).colorScheme

fun getPresetCustomColors(preset: OrbitThemePreset, isDark: Boolean): CustomThemeColors =
    OrbitPresetPalettes.get(preset, isDark).customColors

/**
 * Single canonical theme resolution pipeline shared by [OrbitTheme], live preview cards,
 * and unit tests:
 * 1. Android 12+ Material You dynamic scheme (when enabled and supported)
 * 2. Seed-generated Custom Accent palette (when enabled with a valid hex seed)
 * 3. Curated built-in preset palette
 * 4. Pure-black AMOLED surface & border calibration (when dark + AMOLED active)
 */
fun resolveOrbitPalette(
    themePreset: OrbitThemePreset,
    isDark: Boolean,
    amoledDark: Boolean = false,
    useCustomTheme: Boolean = false,
    customAccentHex: String? = null,
    dynamicScheme: ColorScheme? = null
): ResolvedOrbitPalette {
    val base = when {
        dynamicScheme != null -> ResolvedOrbitPalette(
            colorScheme = dynamicScheme,
            customColors = dynamicCustomColors(dynamicScheme, isDark)
        )
        useCustomTheme && normalizeAccentHex(customAccentHex) != null -> {
            val normalized = normalizeAccentHex(customAccentHex)!!
            generatePaletteFromSeed(normalized.toComposeColor(EmeraldLightPrimary), isDark)
        }
        else -> getPresetPalette(themePreset, isDark)
    }
    return if (isDark && amoledDark) applyAmoledOverrides(base) else base
}

@Composable
expect fun getPlatformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme?

@Composable
fun OrbitTheme(
    themePreset: OrbitThemePreset = OrbitThemePreset.ORBIT_EMERALD,
    themeMode: OrbitThemeMode = OrbitThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    useCustomTheme: Boolean = false,
    customAccentHex: String? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        OrbitThemeMode.SYSTEM -> isSystemInDarkTheme()
        OrbitThemeMode.LIGHT -> false
        OrbitThemeMode.DARK -> true
    }

    val dynamicScheme = getPlatformColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor)
    val palette = remember(themePreset, darkTheme, amoledDark, useCustomTheme, customAccentHex, dynamicScheme) {
        resolveOrbitPalette(
            themePreset = themePreset,
            isDark = darkTheme,
            amoledDark = amoledDark,
            useCustomTheme = useCustomTheme,
            customAccentHex = customAccentHex,
            dynamicScheme = dynamicScheme
        )
    }

    val typography = rememberOrbitTypography()

    CompositionLocalProvider(
        LocalCustomThemeColors provides palette.customColors
    ) {
        MaterialTheme(
            colorScheme = palette.colorScheme,
            typography = typography,
            shapes = OrbitShapes,
            content = content
        )
    }
}

/**
 * Standard alias for [OrbitTheme].
 */
@Composable
fun OrbitMoneyTheme(
    themePreset: OrbitThemePreset = OrbitThemePreset.ORBIT_EMERALD,
    themeMode: OrbitThemeMode = OrbitThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    useCustomTheme: Boolean = false,
    customAccentHex: String? = null,
    content: @Composable () -> Unit
) = OrbitTheme(
    themePreset = themePreset,
    themeMode = themeMode,
    dynamicColor = dynamicColor,
    amoledDark = amoledDark,
    useCustomTheme = useCustomTheme,
    customAccentHex = customAccentHex,
    content = content
)

/**
 * Global accessor object for easy ergonomic access to custom financial tokens, typography, and springs.
 */
object OrbitTheme {
    val colors: CustomThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCustomThemeColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val springs: OrbitSprings
        get() = OrbitSprings
}

val MaterialTheme.orbitColors: CustomThemeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomThemeColors.current
