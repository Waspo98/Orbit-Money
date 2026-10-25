package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("(duration) => { try { if (typeof navigator !== 'undefined' && navigator.vibrate) { navigator.vibrate(duration); } } catch(e) {} }")
private external fun jsVibrate(duration: Int)

@Composable
actual fun rememberOrbitHaptics(enabled: Boolean): OrbitHaptics {
    return remember(enabled) {
        object : OrbitHaptics {
            override fun perform(type: OrbitHapticType) {
                if (!enabled) return
                val durationMs = when (type) {
                    OrbitHapticType.CLOCK_TICK -> 5
                    OrbitHapticType.TAP -> 10
                    OrbitHapticType.CONFIRM -> 25
                    OrbitHapticType.REJECT -> 45
                    OrbitHapticType.LONG_PRESS -> 40
                }
                jsVibrate(durationMs)
            }
        }
    }
}
