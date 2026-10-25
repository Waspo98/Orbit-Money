package app.overbay.orbit.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun rememberDevicePosture(): DevicePosture {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val isFoldable = widthDp in 550..900 && !isLandscape
    val isFolded = isFoldable && widthDp < 600

    return remember(widthDp, heightDp, isLandscape) {
        DevicePosture(
            isFoldable = isFoldable,
            isFolded = isFolded,
            isLandscape = isLandscape,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp
        )
    }
}
