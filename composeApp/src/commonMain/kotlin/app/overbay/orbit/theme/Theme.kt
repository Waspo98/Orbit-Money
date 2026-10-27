package app.overbay.orbit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset

// Preset Material 3 Color Schemes
val EmeraldLightColorScheme = lightColorScheme(
    primary = EmeraldLightPrimary,
    onPrimary = EmeraldLightOnPrimary,
    primaryContainer = EmeraldLightPrimaryContainer,
    onPrimaryContainer = EmeraldLightOnPrimaryContainer,
    secondary = EmeraldLightSecondary,
    onSecondary = EmeraldLightOnSecondary,
    secondaryContainer = EmeraldLightSecondaryContainer,
    onSecondaryContainer = EmeraldLightOnSecondaryContainer,
    tertiary = EmeraldLightTertiary,
    onTertiary = EmeraldLightOnTertiary,
    tertiaryContainer = EmeraldLightTertiaryContainer,
    onTertiaryContainer = EmeraldLightOnTertiaryContainer,
    background = EmeraldLightBackground,
    onBackground = EmeraldLightOnBackground,
    surface = EmeraldLightSurface,
    onSurface = EmeraldLightOnSurface,
    surfaceVariant = EmeraldLightSurfaceVariant,
    onSurfaceVariant = EmeraldLightOnSurfaceVariant,
    outline = EmeraldLightOutline,
    outlineVariant = EmeraldLightOutlineVariant,
    surfaceContainer = EmeraldLightSurfaceContainer,
    surfaceContainerLow = EmeraldLightSurfaceContainerLow,
    surfaceContainerHigh = EmeraldLightSurfaceContainerHigh
)

val EmeraldDarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkOnPrimary,
    primaryContainer = EmeraldDarkPrimaryContainer,
    onPrimaryContainer = EmeraldDarkOnPrimaryContainer,
    secondary = EmeraldDarkSecondary,
    onSecondary = EmeraldDarkOnSecondary,
    secondaryContainer = EmeraldDarkSecondaryContainer,
    onSecondaryContainer = EmeraldDarkOnSecondaryContainer,
    tertiary = EmeraldDarkTertiary,
    onTertiary = EmeraldDarkOnTertiary,
    tertiaryContainer = EmeraldDarkTertiaryContainer,
    onTertiaryContainer = EmeraldDarkOnTertiaryContainer,
    background = EmeraldDarkBackground,
    onBackground = EmeraldDarkOnBackground,
    surface = EmeraldDarkSurface,
    onSurface = EmeraldDarkOnSurface,
    surfaceVariant = EmeraldDarkSurfaceVariant,
    onSurfaceVariant = EmeraldDarkOnSurfaceVariant,
    outline = EmeraldDarkOutline,
    outlineVariant = EmeraldDarkOutlineVariant,
    surfaceContainer = EmeraldDarkSurfaceContainer,
    surfaceContainerLow = EmeraldDarkSurfaceContainerLow,
    surfaceContainerHigh = EmeraldDarkSurfaceContainerHigh
)

val OceanLightColorScheme = lightColorScheme(
    primary = OceanLightPrimary,
    onPrimary = OceanLightOnPrimary,
    primaryContainer = OceanLightPrimaryContainer,
    onPrimaryContainer = OceanLightOnPrimaryContainer,
    background = OceanLightBackground,
    onBackground = OceanLightOnBackground,
    surface = OceanLightSurface,
    onSurface = OceanLightOnSurface,
    outline = OceanLightOutline,
    surfaceContainer = OceanLightSurfaceContainer,
    surfaceContainerLow = OceanLightSurfaceContainerLow,
    surfaceContainerHigh = OceanLightSurfaceContainerHigh
)

val OceanDarkColorScheme = darkColorScheme(
    primary = OceanDarkPrimary,
    onPrimary = OceanDarkOnPrimary,
    primaryContainer = OceanDarkPrimaryContainer,
    onPrimaryContainer = OceanDarkOnPrimaryContainer,
    background = OceanDarkBackground,
    onBackground = OceanDarkOnBackground,
    surface = OceanDarkSurface,
    onSurface = OceanDarkOnSurface,
    outline = OceanDarkOutline,
    surfaceContainer = OceanDarkSurfaceContainer,
    surfaceContainerLow = OceanDarkSurfaceContainerLow,
    surfaceContainerHigh = OceanDarkSurfaceContainerHigh
)

val TerracottaLightColorScheme = lightColorScheme(
    primary = TerracottaLightPrimary,
    onPrimary = TerracottaLightOnPrimary,
    primaryContainer = TerracottaLightPrimaryContainer,
    onPrimaryContainer = TerracottaLightOnPrimaryContainer,
    background = TerracottaLightBackground,
    onBackground = TerracottaLightOnBackground,
    surface = TerracottaLightSurface,
    onSurface = TerracottaLightOnSurface,
    outline = TerracottaLightOutline,
    surfaceContainer = TerracottaLightSurfaceContainer,
    surfaceContainerLow = TerracottaLightSurfaceContainerLow,
    surfaceContainerHigh = TerracottaLightSurfaceContainerHigh
)

val TerracottaDarkColorScheme = darkColorScheme(
    primary = TerracottaDarkPrimary,
    onPrimary = TerracottaDarkOnPrimary,
    primaryContainer = TerracottaDarkPrimaryContainer,
    onPrimaryContainer = TerracottaDarkOnPrimaryContainer,
    background = TerracottaDarkBackground,
    onBackground = TerracottaDarkOnBackground,
    surface = TerracottaDarkSurface,
    onSurface = TerracottaDarkOnSurface,
    outline = TerracottaDarkOutline,
    surfaceContainer = TerracottaDarkSurfaceContainer,
    surfaceContainerLow = TerracottaDarkSurfaceContainerLow,
    surfaceContainerHigh = TerracottaDarkSurfaceContainerHigh
)

val IndigoLightColorScheme = lightColorScheme(
    primary = IndigoLightPrimary,
    onPrimary = IndigoLightOnPrimary,
    primaryContainer = IndigoLightPrimaryContainer,
    onPrimaryContainer = IndigoLightOnPrimaryContainer,
    background = IndigoLightBackground,
    onBackground = IndigoLightOnBackground,
    surface = IndigoLightSurface,
    onSurface = IndigoLightOnSurface,
    outline = IndigoLightOutline,
    surfaceContainer = IndigoLightSurfaceContainer,
    surfaceContainerLow = IndigoLightSurfaceContainerLow,
    surfaceContainerHigh = IndigoLightSurfaceContainerHigh
)

val IndigoDarkColorScheme = darkColorScheme(
    primary = IndigoDarkPrimary,
    onPrimary = IndigoDarkOnPrimary,
    primaryContainer = IndigoDarkPrimaryContainer,
    onPrimaryContainer = IndigoDarkOnPrimaryContainer,
    background = IndigoDarkBackground,
    onBackground = IndigoDarkOnBackground,
    surface = IndigoDarkSurface,
    onSurface = IndigoDarkOnSurface,
    outline = IndigoDarkOutline,
    surfaceContainer = IndigoDarkSurfaceContainer,
    surfaceContainerLow = IndigoDarkSurfaceContainerLow,
    surfaceContainerHigh = IndigoDarkSurfaceContainerHigh
)

val MonochromeLightColorScheme = lightColorScheme(
    primary = MonochromeLightPrimary,
    onPrimary = MonochromeLightOnPrimary,
    primaryContainer = MonochromeLightPrimaryContainer,
    onPrimaryContainer = MonochromeLightOnPrimaryContainer,
    background = MonochromeLightBackground,
    onBackground = MonochromeLightOnBackground,
    surface = MonochromeLightSurface,
    onSurface = MonochromeLightOnSurface,
    outline = MonochromeLightOutline,
    surfaceContainer = MonochromeLightSurfaceContainer,
    surfaceContainerLow = MonochromeLightSurfaceContainerLow,
    surfaceContainerHigh = MonochromeLightSurfaceContainerHigh
)

val MonochromeDarkColorScheme = darkColorScheme(
    primary = MonochromeDarkPrimary,
    onPrimary = MonochromeDarkOnPrimary,
    primaryContainer = MonochromeDarkPrimaryContainer,
    onPrimaryContainer = MonochromeDarkOnPrimaryContainer,
    background = MonochromeDarkBackground,
    onBackground = MonochromeDarkOnBackground,
    surface = MonochromeDarkSurface,
    onSurface = MonochromeDarkOnSurface,
    outline = MonochromeDarkOutline,
    surfaceContainer = MonochromeDarkSurfaceContainer,
    surfaceContainerLow = MonochromeDarkSurfaceContainerLow,
    surfaceContainerHigh = MonochromeDarkSurfaceContainerHigh
)

fun getPresetColorScheme(preset: OrbitThemePreset, isDark: Boolean): ColorScheme = when (preset) {
    OrbitThemePreset.ORBIT_EMERALD -> if (isDark) EmeraldDarkColorScheme else EmeraldLightColorScheme
    OrbitThemePreset.OCEAN_SLATE -> if (isDark) OceanDarkColorScheme else OceanLightColorScheme
    OrbitThemePreset.WARM_TERRACOTTA -> if (isDark) TerracottaDarkColorScheme else TerracottaLightColorScheme
    OrbitThemePreset.ROYAL_INDIGO -> if (isDark) IndigoDarkColorScheme else IndigoLightColorScheme
    OrbitThemePreset.MONOCHROME_INK -> if (isDark) MonochromeDarkColorScheme else MonochromeLightColorScheme
}

fun getPresetCustomColors(preset: OrbitThemePreset, isDark: Boolean): CustomThemeColors = when (preset) {
    OrbitThemePreset.ORBIT_EMERALD -> emeraldCustomColors(isDark)
    OrbitThemePreset.OCEAN_SLATE -> oceanSlateCustomColors(isDark)
    OrbitThemePreset.WARM_TERRACOTTA -> warmTerracottaCustomColors(isDark)
    OrbitThemePreset.ROYAL_INDIGO -> royalIndigoCustomColors(isDark)
    OrbitThemePreset.MONOCHROME_INK -> monochromeInkCustomColors(isDark)
}

@Composable
expect fun getPlatformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme?

@Composable
fun OrbitTheme(
    themePreset: OrbitThemePreset = OrbitThemePreset.ORBIT_EMERALD,
    themeMode: OrbitThemeMode = OrbitThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    customPreset: CustomThemePreset? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        OrbitThemeMode.SYSTEM -> isSystemInDarkTheme()
        OrbitThemeMode.LIGHT -> false
        OrbitThemeMode.DARK -> true
    }

    val dynamicScheme = getPlatformColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor)
    val baseScheme = if (customPreset != null) {
        createCustomColorScheme(customPreset, fallbackAccent = EmeraldLightPrimary)
    } else {
        getPresetColorScheme(themePreset, darkTheme)
    }

    val finalColorScheme = dynamicScheme ?: baseScheme
    val customColors = if (customPreset != null) {
        createCustomThemeColors(customPreset)
    } else if (dynamicScheme != null) {
        dynamicCustomColors(dynamicScheme, darkTheme)
    } else {
        getPresetCustomColors(themePreset, darkTheme)
    }

    val typography = rememberOrbitTypography()

    CompositionLocalProvider(
        LocalCustomThemeColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = typography,
            content = content
        )
    }
}

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
