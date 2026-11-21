package app.overbay.orbit.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun rememberDevicePosture(): DevicePosture {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    var multiWindowChangeCount by remember(activity) { mutableIntStateOf(0) }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        if (componentActivity != null) {
            val listener = Consumer<MultiWindowModeChangedInfo> {
                multiWindowChangeCount += 1
            }
            componentActivity.addOnMultiWindowModeChangedListener(listener)
            onDispose {
                componentActivity.removeOnMultiWindowModeChangedListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    val isInMultiWindowMode = remember(activity, configuration, multiWindowChangeCount) {
        activity?.isInMultiWindowMode == true
    }

    val isFoldableHardware = remember(context) { FoldableDetector.isFoldableDevice(context) }
    val isFoldable = isFoldableHardware || (widthDp in 550..900 && !isLandscape)
    val isFolded = isFoldable && widthDp < 600

    return remember(widthDp, heightDp, isLandscape, isFoldable, isFolded, isInMultiWindowMode) {
        DevicePosture(
            isFoldable = isFoldable,
            isFolded = isFolded,
            isLandscape = isLandscape,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            isInMultiWindowMode = isInMultiWindowMode
        )
    }
}
