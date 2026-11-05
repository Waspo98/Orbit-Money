package app.overbay.orbit.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

fun performOrbitHaptic(view: View?, type: OrbitHapticType) {
    if (view == null) return
    try {
        val constant = when (type) {
            OrbitHapticType.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
            OrbitHapticType.CONFIRM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
            }
            OrbitHapticType.REJECT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
            OrbitHapticType.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
            OrbitHapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    } catch (_: Throwable) {}
}

@Composable
actual fun rememberOrbitHaptics(enabled: Boolean): OrbitHaptics {
    val view = LocalView.current
    return remember(view, enabled) {
        object : OrbitHaptics {
            override fun perform(type: OrbitHapticType) {
                if (!enabled) return
                performOrbitHaptic(view, type)
            }
        }
    }
}
