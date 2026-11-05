package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class PostureMode {
    COMPACT_PHONE,
    UNFOLDED_FOLDABLE,
    TABLET_DESKTOP
}

data class DevicePosture(
    val isFoldable: Boolean = false,
    val isFolded: Boolean = false,
    val isLandscape: Boolean = false,
    val screenWidthDp: Int = 360,
    val screenHeightDp: Int = 640
) {
    val mode: PostureMode
        get() = when {
            screenWidthDp >= 1100 || (screenWidthDp >= 840 && isLandscape) -> PostureMode.TABLET_DESKTOP
            isFoldable && !isFolded -> PostureMode.UNFOLDED_FOLDABLE
            screenWidthDp >= 600 -> PostureMode.UNFOLDED_FOLDABLE
            else -> PostureMode.COMPACT_PHONE
        }

    val isTabletOrDesktop: Boolean get() = mode == PostureMode.TABLET_DESKTOP
    val isUnfoldedOrWide: Boolean get() = mode != PostureMode.COMPACT_PHONE
    val supportsTwoPane: Boolean get() = isUnfoldedOrWide
}

val LocalDevicePosture = staticCompositionLocalOf { DevicePosture() }

@Composable
expect fun rememberDevicePosture(): DevicePosture
