package app.overbay.orbit.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.util.LocalDevicePosture

internal val MULTI_WINDOW_MIN_TOP_BUFFER: Dp = 32.dp

/**
 * Resolves the effective top-only [WindowInsets] for Orbit's edge-to-edge canopy headers and
 * top-anchored surfaces. Combines system bars (status bars + multi-window caption bars), explicit
 * caption bar, and display cutout, plus a minimum top floor while in multi-window / split-screen
 * mode so OEM drag-handle pills never overlap top controls even if the OS omits captionBar insets.
 */
internal fun resolveOrbitTopSystemBarInsets(
    systemBars: WindowInsets,
    captionBar: WindowInsets,
    displayCutout: WindowInsets,
    isInMultiWindowMode: Boolean,
    multiWindowMinTop: Dp = MULTI_WINDOW_MIN_TOP_BUFFER
): WindowInsets {
    val combined = systemBars
        .union(captionBar)
        .union(displayCutout)
        .only(WindowInsetsSides.Top)
    return if (isInMultiWindowMode && multiWindowMinTop > 0.dp) {
        combined.union(WindowInsets(top = multiWindowMinTop))
    } else {
        combined
    }
}

@Composable
fun orbitTopSystemBarInsets(): WindowInsets {
    val isInMultiWindowMode = LocalDevicePosture.current.isInMultiWindowMode
    return resolveOrbitTopSystemBarInsets(
        systemBars = WindowInsets.systemBars,
        captionBar = WindowInsets.captionBar,
        displayCutout = WindowInsets.displayCutout,
        isInMultiWindowMode = isInMultiWindowMode
    )
}

@Composable
fun Modifier.orbitTopSystemBarPadding(): Modifier =
    this.windowInsetsPadding(orbitTopSystemBarInsets())
