package app.overbay.orbit.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDevicePosture(): DevicePosture {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val isFoldableHardware = remember(context) { FoldableDetector.isFoldableDevice(context) }
    val isFoldable = isFoldableHardware || (widthDp in 550..900 && !isLandscape)
    val isFolded = isFoldable && widthDp < 600

    return remember(widthDp, heightDp, isLandscape, isFoldable, isFolded) {
        DevicePosture(
            isFoldable = isFoldable,
            isFolded = isFolded,
            isLandscape = isLandscape,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp
        )
    }
}
