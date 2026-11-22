package app.overbay.orbit.theme

import app.overbay.orbit.data.model.OrbitThemePreset

internal object OrbitPresetPalettes {
    val emeraldLight: ResolvedOrbitPalette get() = OrbitPresetPalettesLight.emerald
    val emeraldDark: ResolvedOrbitPalette get() = OrbitPresetPalettesDark.emerald
    val oceanLight: ResolvedOrbitPalette get() = OrbitPresetPalettesLight.ocean
    val oceanDark: ResolvedOrbitPalette get() = OrbitPresetPalettesDark.ocean
    val terracottaLight: ResolvedOrbitPalette get() = OrbitPresetPalettesLight.terracotta
    val terracottaDark: ResolvedOrbitPalette get() = OrbitPresetPalettesDark.terracotta
    val indigoLight: ResolvedOrbitPalette get() = OrbitPresetPalettesLight.indigo
    val indigoDark: ResolvedOrbitPalette get() = OrbitPresetPalettesDark.indigo
    val monochromeLight: ResolvedOrbitPalette get() = OrbitPresetPalettesLight.monochrome
    val monochromeDark: ResolvedOrbitPalette get() = OrbitPresetPalettesDark.monochrome
    val spaceshipRedLight: ResolvedOrbitPalette get() = OrbitPresetPalettesLight.spaceshipRed
    val spaceshipRedDark: ResolvedOrbitPalette get() = OrbitPresetPalettesDark.spaceshipRed

    fun get(preset: OrbitThemePreset, isDark: Boolean): ResolvedOrbitPalette = when (preset) {
        OrbitThemePreset.ORBIT_EMERALD -> if (isDark) emeraldDark else emeraldLight
        OrbitThemePreset.OCEAN_SLATE -> if (isDark) oceanDark else oceanLight
        OrbitThemePreset.WARM_TERRACOTTA -> if (isDark) terracottaDark else terracottaLight
        OrbitThemePreset.ROYAL_INDIGO -> if (isDark) indigoDark else indigoLight
        OrbitThemePreset.MONOCHROME_INK -> if (isDark) monochromeDark else monochromeLight
        OrbitThemePreset.SPACESHIP_RED -> if (isDark) spaceshipRedDark else spaceshipRedLight
    }
}
