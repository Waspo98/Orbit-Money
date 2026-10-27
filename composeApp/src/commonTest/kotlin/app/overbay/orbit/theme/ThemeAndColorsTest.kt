package app.overbay.orbit.theme

import androidx.compose.ui.graphics.Color
import app.overbay.orbit.data.model.OrbitThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThemeAndColorsTest {

    @Test
    fun testAllFivePresetsProduceValidLightAndDarkColorSchemes() {
        for (preset in OrbitThemePreset.entries) {
            val lightScheme = getPresetColorScheme(preset, isDark = false)
            val darkScheme = getPresetColorScheme(preset, isDark = true)

            assertNotNull(lightScheme.primary)
            assertNotNull(darkScheme.primary)
            assertNotNull(lightScheme.surface)
            assertNotNull(darkScheme.surface)
            assertNotNull(lightScheme.surfaceContainer)
            assertNotNull(darkScheme.surfaceContainer)

            val customLight = getPresetCustomColors(preset, isDark = false)
            val customDark = getPresetCustomColors(preset, isDark = true)

            assertNotNull(customLight.income)
            assertNotNull(customDark.income)
            assertNotNull(customLight.expense)
            assertNotNull(customDark.expense)
            assertNotNull(customLight.canopyStart)
            assertNotNull(customDark.canopyEnd)
        }
    }

    @Test
    fun testHexColorParsingWithFallbacks() {
        val fallback = Color(0xFF0B6E4F)

        assertEquals(Color(0xFF00FF00), "#00FF00".toComposeColor(fallback))
        assertEquals(Color(0xFF112233), "112233".toComposeColor(fallback))
        assertEquals(Color(0x80112233), "#80112233".toComposeColor(fallback))
        assertEquals(fallback, "".toComposeColor(fallback))
        assertEquals(fallback, "not-a-color".toComposeColor(fallback))
        assertEquals(fallback, null.toComposeColor(fallback))
    }

    @Test
    fun testCustomThemePresetBuilderGeneratesProperContainers() {
        val custom = CustomThemePreset(
            id = "test_custom",
            name = "Test Custom",
            isDark = false,
            backgroundColorHex = "#FFFFFF",
            textColorHex = "#000000",
            surfaceColorHex = "#F0F0F0",
            accentColorHex = "#123456"
        )
        val scheme = createCustomColorScheme(custom)
        assertEquals(Color(0xFF123456), scheme.primary)
        assertNotNull(scheme.surfaceContainer)
        assertNotNull(scheme.surfaceContainerLow)
        assertNotNull(scheme.surfaceContainerHigh)

        val customColors = createCustomThemeColors(custom)
        assertNotNull(customColors.canopyStart)
        assertNotNull(customColors.canopyEnd)
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
        assertEquals("tnum", TabularNumStyle.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.displayLarge.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.headlineMedium.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.titleLarge.fontFeatureSettings)
        assertEquals("tnum", OrbitTypography.labelMedium.fontFeatureSettings)
        assertTrue(OrbitTypography.bodyLarge.letterSpacing.value <= 0f)
        assertTrue(OrbitTypography.bodyMedium.letterSpacing.value <= 0f)
        assertTrue(OrbitTypography.titleMedium.letterSpacing.value <= 0f)
    }
}
