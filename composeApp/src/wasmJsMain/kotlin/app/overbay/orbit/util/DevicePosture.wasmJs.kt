package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberDevicePosture(): DevicePosture {
    val windowInfo = LocalWindowInfo.current
    val containerSize = windowInfo.containerSize
    val widthDp = containerSize.width
    val heightDp = containerSize.height
    val isLandscape = widthDp > heightDp

    return remember(widthDp, heightDp, isLandscape) {
        DevicePosture(
            isFoldable = false,
            isFolded = false,
            isLandscape = isLandscape,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp
        )
    }
}
