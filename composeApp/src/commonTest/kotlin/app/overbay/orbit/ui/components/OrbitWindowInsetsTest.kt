package app.overbay.orbit.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class OrbitWindowInsetsTest {

    private val density = Density(density = 2f, fontScale = 1f)
    private val ltr = LayoutDirection.Ltr

    @Test
    fun fullscreenPhone_usesExactStatusBarHeightAndStripsOtherSides() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(left = 10.dp, top = 36.dp, right = 12.dp, bottom = 24.dp),
            captionBar = WindowInsets(0, 0, 0, 0),
            displayCutout = WindowInsets(top = 28.dp),
            isInMultiWindowMode = false
        )

        assertEquals(72, resolved.getTop(density))
        assertEquals(0, resolved.getBottom(density))
        assertEquals(0, resolved.getLeft(density, ltr))
        assertEquals(0, resolved.getRight(density, ltr))
    }

    @Test
    fun splitScreenWithCaptionBar_usesCaptionBarWithoutDoublePadding() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(top = 0.dp, bottom = 24.dp),
            captionBar = WindowInsets(top = 38.dp),
            displayCutout = WindowInsets(0, 0, 0, 0),
            isInMultiWindowMode = true
        )

        assertEquals(76, resolved.getTop(density))
        assertEquals(0, resolved.getBottom(density))
    }

    @Test
    fun splitScreenWithZeroInsets_appliesMultiWindowMinimumTopBuffer() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(0, 0, 0, 0),
            captionBar = WindowInsets(0, 0, 0, 0),
            displayCutout = WindowInsets(0, 0, 0, 0),
            isInMultiWindowMode = true
        )

        val expectedPx = with(density) { MULTI_WINDOW_MIN_TOP_BUFFER.roundToPx() }
        assertEquals(expectedPx, resolved.getTop(density))
        assertEquals(0, resolved.getBottom(density))
    }

    @Test
    fun splitScreenWithTightCaptionBar_enforcesMultiWindowMinimumFloor() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(0, 0, 0, 0),
            captionBar = WindowInsets(top = 18.dp),
            displayCutout = WindowInsets(0, 0, 0, 0),
            isInMultiWindowMode = true
        )

        val expectedPx = with(density) { MULTI_WINDOW_MIN_TOP_BUFFER.roundToPx() }
        assertEquals(expectedPx, resolved.getTop(density))
    }

    @Test
    fun splitScreenTopHalfWithStatusBar_preservesLargerStatusBarInset() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(top = 44.dp),
            captionBar = WindowInsets(0, 0, 0, 0),
            displayCutout = WindowInsets(0, 0, 0, 0),
            isInMultiWindowMode = true
        )

        assertEquals(88, resolved.getTop(density))
    }

    @Test
    fun fullscreenWithLargerDisplayCutout_preservesDisplayCutoutInset() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(top = 24.dp),
            captionBar = WindowInsets(0, 0, 0, 0),
            displayCutout = WindowInsets(top = 40.dp),
            isInMultiWindowMode = false
        )

        assertEquals(80, resolved.getTop(density))
    }

    @Test
    fun webOrZeroInsetFullscreen_remainsZero() {
        val resolved = resolveOrbitTopSystemBarInsets(
            systemBars = WindowInsets(0, 0, 0, 0),
            captionBar = WindowInsets(0, 0, 0, 0),
            displayCutout = WindowInsets(0, 0, 0, 0),
            isInMultiWindowMode = false
        )

        assertEquals(0, resolved.getTop(density))
        assertEquals(0, resolved.getBottom(density))
    }
}
