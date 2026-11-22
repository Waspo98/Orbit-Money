package app.overbay.orbit.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.overbay.orbit.data.model.OrbitAccentColorMode
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemePreset
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeAndColorsTest {

    private fun assertPaletteContrast(
        palette: ResolvedOrbitPalette,
        label: String,
        minPrimaryContrast: Double = 4.5,
        minIncomeContrast: Double = 4.5
    ) {
        val s = palette.colorScheme
        val c = palette.customColors

        assertTrue(contrastRatio(s.onSurface, s.surface) >= 7.0, "$label onSurface/surface=${contrastRatio(s.onSurface, s.surface)}")
        assertTrue(contrastRatio(s.onBackground, s.background) >= 7.0, "$label onBackground/background=${contrastRatio(s.onBackground, s.background)}")
        assertTrue(contrastRatio(s.onSurfaceVariant, s.surfaceContainer) >= 4.5, "$label onSurfaceVariant/surfaceContainer=${contrastRatio(s.onSurfaceVariant, s.surfaceContainer)}")
        assertTrue(contrastRatio(s.primary, s.surface) >= minPrimaryContrast, "$label primary/surface=${contrastRatio(s.primary, s.surface)}")
        assertTrue(contrastRatio(s.onPrimary, s.primary) >= minPrimaryContrast, "$label onPrimary/primary=${contrastRatio(s.onPrimary, s.primary)}")
        assertTrue(contrastRatio(s.onPrimaryContainer, s.primaryContainer) >= 4.5, "$label onPrimaryContainer/primaryContainer=${contrastRatio(s.onPrimaryContainer, s.primaryContainer)}")
        assertTrue(contrastRatio(s.onSecondaryContainer, s.secondaryContainer) >= 4.5, "$label onSecondaryContainer/secondaryContainer=${contrastRatio(s.onSecondaryContainer, s.secondaryContainer)}")
        assertTrue(contrastRatio(s.onTertiaryContainer, s.tertiaryContainer) >= 4.5, "$label onTertiaryContainer/tertiaryContainer=${contrastRatio(s.onTertiaryContainer, s.tertiaryContainer)}")

        assertTrue(contrastRatio(c.onCanopy, c.canopyStart) >= 7.0, "$label onCanopy/canopyStart=${contrastRatio(c.onCanopy, c.canopyStart)}")
        assertTrue(contrastRatio(c.onCanopy, c.canopyEnd) >= 5.0, "$label onCanopy/canopyEnd=${contrastRatio(c.onCanopy, c.canopyEnd)}")
        assertTrue(contrastRatio(c.income, s.surface) >= minIncomeContrast, "$label income/surface=${contrastRatio(c.income, s.surface)}")
        assertTrue(contrastRatio(c.danger, s.surface) >= 4.5, "$label danger/surface=${contrastRatio(c.danger, s.surface)}")
        assertTrue(contrastRatio(c.warning, s.surface) >= 4.5, "$label warning/surface=${contrastRatio(c.warning, s.surface)}")
        assertTrue(contrastRatio(c.onIncomeContainer, c.incomeContainer) >= 4.5, "$label onIncomeContainer=${contrastRatio(c.onIncomeContainer, c.incomeContainer)}")
        assertTrue(contrastRatio(c.onDangerContainer, c.dangerContainer) >= 4.5, "$label onDangerContainer=${contrastRatio(c.onDangerContainer, c.dangerContainer)}")
        assertTrue(contrastRatio(c.onWarningContainer, c.warningContainer) >= 4.5, "$label onWarningContainer=${contrastRatio(c.onWarningContainer, c.warningContainer)}")
    }

    @Test
    fun testAllPresetsPopulateAllM3RolesWithoutDefaultPurpleLeaks() {
        assertEquals(6, OrbitThemePreset.entries.size)
        assertEquals("Spaceship Red", OrbitThemePreset.SPACESHIP_RED.displayName)
        assertEquals(SpaceshipRedLightPrimary, getPresetColorScheme(OrbitThemePreset.SPACESHIP_RED, isDark = false).primary)
        assertEquals(SpaceshipRedDarkPrimary, getPresetColorScheme(OrbitThemePreset.SPACESHIP_RED, isDark = true).primary)

        val defaultLight = lightColorScheme()
        val defaultDark = darkColorScheme()

        for (preset in OrbitThemePreset.entries) {
            val light = getPresetPalette(preset, isDark = false)
            val dark = getPresetPalette(preset, isDark = true)

            assertNotEquals(defaultLight.secondary, light.colorScheme.secondary, "${preset.name} light secondary")
            assertNotEquals(defaultLight.secondaryContainer, light.colorScheme.secondaryContainer, "${preset.name} light secondaryContainer")
            assertNotEquals(defaultLight.tertiary, light.colorScheme.tertiary, "${preset.name} light tertiary")
            assertNotEquals(defaultLight.onSurfaceVariant, light.colorScheme.onSurfaceVariant, "${preset.name} light onSurfaceVariant")
            assertNotEquals(defaultLight.outlineVariant, light.colorScheme.outlineVariant, "${preset.name} light outlineVariant")
            assertNotEquals(defaultLight.surfaceContainerHighest, light.colorScheme.surfaceContainerHighest, "${preset.name} light surfaceContainerHighest")

            assertNotEquals(defaultDark.secondary, dark.colorScheme.secondary, "${preset.name} dark secondary")
            assertNotEquals(defaultDark.secondaryContainer, dark.colorScheme.secondaryContainer, "${preset.name} dark secondaryContainer")
            assertNotEquals(defaultDark.tertiary, dark.colorScheme.tertiary, "${preset.name} dark tertiary")
            assertNotEquals(defaultDark.onSurfaceVariant, dark.colorScheme.onSurfaceVariant, "${preset.name} dark onSurfaceVariant")
            assertNotEquals(defaultDark.outlineVariant, dark.colorScheme.outlineVariant, "${preset.name} dark outlineVariant")
            assertNotEquals(defaultDark.surfaceContainerHighest, dark.colorScheme.surfaceContainerHighest, "${preset.name} dark surfaceContainerHighest")

            assertEquals(light.colorScheme.outlineVariant, light.customColors.cardBorder, "${preset.name} light cardBorder")
            assertEquals(dark.colorScheme.outlineVariant, dark.customColors.cardBorder, "${preset.name} dark cardBorder")

            val minBrandContrast = if (preset == OrbitThemePreset.ORBIT_EMERALD) 3.7 else 4.5
            assertPaletteContrast(
                light,
                "${preset.name} Light",
                minPrimaryContrast = minBrandContrast,
                minIncomeContrast = minBrandContrast
            )
            assertPaletteContrast(dark, "${preset.name} Dark")
            assertPaletteContrast(applyAmoledOverrides(dark), "${preset.name} AMOLED")
        }
    }

    @Test
    fun testNonEmeraldPresetsDoNotInheritEmeraldBordersCanopiesOrSemantics() {
        val emeraldLight = getPresetCustomColors(OrbitThemePreset.ORBIT_EMERALD, isDark = false)
        val emeraldDark = getPresetCustomColors(OrbitThemePreset.ORBIT_EMERALD, isDark = true)

        for (preset in OrbitThemePreset.entries) {
            if (preset == OrbitThemePreset.ORBIT_EMERALD) continue
            val customLight = getPresetCustomColors(preset, isDark = false)
            val customDark = getPresetCustomColors(preset, isDark = true)

            assertNotEquals(emeraldLight.cardBorder, customLight.cardBorder, "${preset.name} light cardBorder")
            assertNotEquals(emeraldDark.cardBorder, customDark.cardBorder, "${preset.name} dark cardBorder")
            assertNotEquals(emeraldLight.income, customLight.income, "${preset.name} light income")
            assertNotEquals(emeraldDark.income, customDark.income, "${preset.name} dark income")
            assertNotEquals(emeraldLight.canopyMid, customLight.canopyMid, "${preset.name} light canopyMid")
            assertNotEquals(emeraldDark.canopyMid, customDark.canopyMid, "${preset.name} dark canopyMid")
            assertNotEquals(emeraldLight.canopyGlow, customLight.canopyGlow, "${preset.name} light canopyGlow")
            assertNotEquals(emeraldDark.canopyGlow, customDark.canopyGlow, "${preset.name} dark canopyGlow")
            assertNotEquals(emeraldLight.canopyKicker, customLight.canopyKicker, "${preset.name} light canopyKicker")
            assertNotEquals(emeraldDark.canopyKicker, customDark.canopyKicker, "${preset.name} dark canopyKicker")
        }
    }

    @Test
    fun testSeedGeneratorMeetsContrastAcrossCuratedAndExtremeSeeds() {
        val testSeeds = CuratedSeedSwatches.map { it.second } + listOf(
            "#FFFFFF",
            "#000000",
            "#808080",
            "#FFFF00",
            "#00FF00",
            "#FF00FF",
            "#FF0000",
            "#00FFFF"
        )

        for (hex in testSeeds) {
            val seed = hex.toComposeColor()
            val light = generatePaletteFromSeed(seed, isDark = false)
            val dark = generatePaletteFromSeed(seed, isDark = true)
            val amoled = applyAmoledOverrides(dark)

            assertPaletteContrast(light, "Seed $hex Light")
            assertPaletteContrast(dark, "Seed $hex Dark")
            assertPaletteContrast(amoled, "Seed $hex AMOLED")

            assertEquals(Color.Black, amoled.colorScheme.background)
            assertEquals(Color.Black, amoled.colorScheme.surface)
            assertEquals(amoled.colorScheme.outlineVariant, amoled.customColors.cardBorder)
        }
    }

    @Test
    fun testDynamicCustomColorsHarmonizesBordersAndCanopyContrast() {
        val terracottaLight = getPresetColorScheme(OrbitThemePreset.WARM_TERRACOTTA, isDark = false)
        val terracottaDark = getPresetColorScheme(OrbitThemePreset.WARM_TERRACOTTA, isDark = true)
        val dynamicLight = dynamicCustomColors(terracottaLight, isDark = false)
        val dynamicDark = dynamicCustomColors(terracottaDark, isDark = true)
        val emeraldLight = emeraldCustomColors(isDark = false)
        val emeraldDark = emeraldCustomColors(isDark = true)

        assertEquals(terracottaLight.outlineVariant, dynamicLight.cardBorder)
        assertEquals(terracottaDark.outlineVariant, dynamicDark.cardBorder)
        assertEquals(terracottaLight.onSurface, dynamicLight.expense)
        assertEquals(terracottaDark.onSurface, dynamicDark.expense)
        assertNotEquals(emeraldLight.canopyMid, dynamicLight.canopyMid)
        assertNotEquals(emeraldDark.canopyMid, dynamicDark.canopyMid)
        assertTrue(contrastRatio(dynamicLight.onCanopy, dynamicLight.canopyStart) >= 7.0)
        assertTrue(contrastRatio(dynamicLight.onCanopy, dynamicLight.canopyEnd) >= 7.0)
        assertTrue(contrastRatio(dynamicDark.onCanopy, dynamicDark.canopyStart) >= 7.0)
        assertTrue(contrastRatio(dynamicDark.onCanopy, dynamicDark.canopyEnd) >= 7.0)
    }

    @Test
    fun testResolveOrbitPalettePrecedenceAndFallback() {
        val presetOnly = resolveOrbitPalette(
            themePreset = OrbitThemePreset.ROYAL_INDIGO,
            isDark = false
        )
        assertEquals(IndigoLightPrimary, presetOnly.colorScheme.primary)

        val customActive = resolveOrbitPalette(
            themePreset = OrbitThemePreset.ROYAL_INDIGO,
            isDark = false,
            useCustomTheme = true,
            customAccentHex = "#0D9488"
        )
        assertNotEquals(IndigoLightPrimary, customActive.colorScheme.primary)

        val invalidCustomFallsBack = resolveOrbitPalette(
            themePreset = OrbitThemePreset.ROYAL_INDIGO,
            isDark = false,
            useCustomTheme = true,
            customAccentHex = "not-a-hex"
        )
        assertEquals(IndigoLightPrimary, invalidCustomFallsBack.colorScheme.primary)

        val terracottaLight = getPresetColorScheme(OrbitThemePreset.WARM_TERRACOTTA, isDark = false)
        val dynamicWins = resolveOrbitPalette(
            themePreset = OrbitThemePreset.ROYAL_INDIGO,
            isDark = false,
            useCustomTheme = true,
            customAccentHex = "#0D9488",
            dynamicScheme = terracottaLight
        )
        assertEquals(TerracottaLightPrimary, dynamicWins.colorScheme.primary)
    }

    @Test
    fun testHexNormalizationAndHslRoundTrip() {
        assertEquals("#0D9488", normalizeAccentHex("#0d9488"))
        assertEquals("#0D9488", normalizeAccentHex("0d9488"))
        assertEquals("#FFAA00", normalizeAccentHex("#fa0"))
        assertNull(normalizeAccentHex(""))
        assertNull(normalizeAccentHex("#12345"))
        assertNull(normalizeAccentHex("#GGGGGG"))

        val sample = Color(0xFF7C3AED)
        val hsl = sample.toHsl()
        val roundTrip = hslToColor(hsl.hue, hsl.saturation, hsl.lightness)
        assertTrue(abs(sample.red - roundTrip.red) < 0.01f)
        assertTrue(abs(sample.green - roundTrip.green) < 0.01f)
        assertTrue(abs(sample.blue - roundTrip.blue) < 0.01f)
        assertEquals("#7C3AED", sample.toHexString())

        val fallback = Color(0xFF0B6E4F)
        assertEquals(Color(0xFF00FF00), "#00FF00".toComposeColor(fallback))
        assertEquals(Color(0xFF112233), "112233".toComposeColor(fallback))
        assertEquals(Color(0x80112233), "#80112233".toComposeColor(fallback))
        assertEquals(fallback, "".toComposeColor(fallback))
        assertEquals(fallback, "not-a-color".toComposeColor(fallback))
        assertEquals(fallback, null.toComposeColor(fallback))
    }

    @Test
    fun testOrbitSpringsDampingAndStiffnessAreValid() {
        val fast = OrbitSprings.FloatSpatialFast
        val default = OrbitSprings.FloatSpatialDefault
        val slow = OrbitSprings.spatialSlow<Float>()
        val effects = OrbitSprings.FloatEffectsFast

        assertTrue(fast.dampingRatio in 0.5f..1.0f)
        assertTrue(default.dampingRatio in 0.5f..1.0f)
        assertTrue(slow.dampingRatio in 0.5f..1.0f)
        assertEquals(1.0f, effects.dampingRatio)

        assertTrue(fast.stiffness > default.stiffness)
        assertTrue(default.stiffness > slow.stiffness)
    }

    @Test
    fun testTabularNumStyleHasTnumFeatureSetting() {
        assertEquals("tnum", DefaultTabularNumStyle.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.displayLarge.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.headlineMedium.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.titleLarge.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.labelMedium.fontFeatureSettings)
        assertTrue(OrbitTypography.bodyLarge.letterSpacing.value <= 0f)
        assertTrue(OrbitTypography.bodyMedium.letterSpacing.value <= 0f)
        assertTrue(OrbitTypography.titleMedium.letterSpacing.value <= 0f)
    }

    @Test
    fun testCuratedSeedSwatchesAreWithinSliderBoundsAndRoundTripExactHex() {
        for ((name, hex) in CuratedSeedSwatches) {
            val hsl = hex.toComposeColor().toHsl()
            assertTrue(hsl.saturation in 0.05f..1.0f, "$name ($hex) saturation=${hsl.saturation}")
            assertTrue(hsl.lightness in 0.24f..0.62f, "$name ($hex) lightness=${hsl.lightness}")
            val roundTripHex = hslToColor(hsl.hue, hsl.saturation, hsl.lightness).toHexString()
            assertEquals(hex.uppercase(), roundTripHex, "$name round-trip mismatch")
        }
    }

    @Test
    fun testAccentColorModesAcrossAllPresetsLightDarkAndAmoled() {
        val variants = listOf(
            Triple(false, false, "Light"),
            Triple(true, false, "Dark"),
            Triple(true, true, "AMOLED")
        )

        for (preset in OrbitThemePreset.entries) {
            for ((isDark, amoled, variantLabel) in variants) {
                val minBrandContrast = if (!isDark && preset == OrbitThemePreset.ORBIT_EMERALD) 3.7 else 4.5
                val basePreset = getPresetPalette(preset, isDark)

                for (mode in OrbitAccentColorMode.entries) {
                    val resolved = resolveOrbitPalette(
                        themePreset = preset,
                        isDark = isDark,
                        amoledDark = amoled,
                        accentColorMode = mode
                    )
                    val label = "${preset.name} $variantLabel ${mode.name}"
                    assertPaletteContrast(
                        palette = resolved,
                        label = label,
                        minPrimaryContrast = minBrandContrast,
                        minIncomeContrast = if (mode == OrbitAccentColorMode.ALWAYS_GREEN_RED) 4.5 else minBrandContrast
                    )

                    when (mode) {
                        OrbitAccentColorMode.ALWAYS_GREEN_RED -> {
                            assertEquals(if (isDark) AbsoluteIncomeDark else AbsoluteIncomeLight, resolved.customColors.income, label)
                            assertEquals(if (isDark) AbsoluteWarningDark else AbsoluteWarningLight, resolved.customColors.warning, label)
                            assertEquals(if (isDark) AbsoluteDangerDark else AbsoluteDangerLight, resolved.customColors.danger, label)
                            assertEquals(resolved.customColors.danger, resolved.colorScheme.error, label)
                            assertEquals(resolved.customColors.income, resolved.customColors.chartPositive, label)
                            assertEquals(resolved.customColors.danger, resolved.customColors.chartNegative, label)
                        }
                        OrbitAccentColorMode.TINTED_GREEN_RED -> {
                            assertEquals(basePreset.customColors.income, resolved.customColors.income, label)
                            assertEquals(basePreset.customColors.warning, resolved.customColors.warning, label)
                            assertEquals(basePreset.customColors.danger, resolved.customColors.danger, label)
                            assertEquals(basePreset.colorScheme.error, resolved.colorScheme.error, label)
                        }
                        OrbitAccentColorMode.MATCH_THEME -> {
                            assertEquals(resolved.colorScheme.primary, resolved.customColors.income, label)
                            assertEquals(resolved.colorScheme.primaryContainer, resolved.customColors.incomeContainer, label)
                            assertEquals(resolved.colorScheme.secondaryContainer, resolved.customColors.warningContainer, label)
                            assertEquals(resolved.colorScheme.tertiaryContainer, resolved.customColors.dangerContainer, label)
                            assertEquals(resolved.customColors.danger, resolved.colorScheme.error, label)
                            assertEquals(resolved.customColors.dangerContainer, resolved.colorScheme.errorContainer, label)
                            assertEquals(resolved.colorScheme.primary, resolved.customColors.chartPositive, label)
                            assertEquals(resolved.customColors.danger, resolved.customColors.chartNegative, label)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testAccentColorModesWithCustomSeedsAndDynamicScheme() {
        val seeds = listOf("#0D9488", "#E11D48", "#7C3AED", "#3F3F46", "#FFFF00", "#000000")
        for (hex in seeds) {
            for (isDark in listOf(false, true)) {
                for (mode in OrbitAccentColorMode.entries) {
                    val palette = resolveOrbitPalette(
                        themePreset = OrbitThemePreset.ORBIT_EMERALD,
                        isDark = isDark,
                        useCustomTheme = true,
                        customAccentHex = hex,
                        accentColorMode = mode
                    )
                    assertPaletteContrast(palette, "Seed $hex dark=$isDark ${mode.name}")
                    if (mode == OrbitAccentColorMode.MATCH_THEME) {
                        assertEquals(palette.colorScheme.primary, palette.customColors.income)
                        assertEquals(palette.customColors.danger, palette.colorScheme.error)
                    }
                }
            }
        }

        val dynamicLight = getPresetColorScheme(OrbitThemePreset.ROYAL_INDIGO, isDark = false)
        val dynamicMatch = resolveOrbitPalette(
            themePreset = OrbitThemePreset.ORBIT_EMERALD,
            isDark = false,
            dynamicScheme = dynamicLight,
            accentColorMode = OrbitAccentColorMode.MATCH_THEME
        )
        assertEquals(dynamicLight.primary, dynamicMatch.customColors.income)
        assertEquals(dynamicLight.tertiaryContainer, dynamicMatch.colorScheme.errorContainer)
        assertPaletteContrast(dynamicMatch, "Dynamic Light MATCH_THEME")

        val dynamicAbsolute = resolveOrbitPalette(
            themePreset = OrbitThemePreset.ORBIT_EMERALD,
            isDark = false,
            dynamicScheme = dynamicLight,
            accentColorMode = OrbitAccentColorMode.ALWAYS_GREEN_RED
        )
        assertEquals(AbsoluteIncomeLight, dynamicAbsolute.customColors.income)
        assertEquals(AbsoluteDangerLight, dynamicAbsolute.colorScheme.error)
        assertPaletteContrast(dynamicAbsolute, "Dynamic Light ALWAYS_GREEN_RED")
    }

    @Test
    fun testOrbitPreferencesAccentColorModeDefaultAndSerializationFallback() {
        assertEquals(OrbitAccentColorMode.MATCH_THEME, OrbitPreferences().accentColorMode)
        val json = Json { ignoreUnknownKeys = true }
        val legacyDecoded = json.decodeFromString(OrbitPreferences.serializer(), "{}")
        assertEquals(OrbitAccentColorMode.MATCH_THEME, legacyDecoded.accentColorMode)
    }
}
