package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class OrbitHapticType {
    CLOCK_TICK,
    CONFIRM,
    REJECT,
    TAP,
    LONG_PRESS
}

interface OrbitHaptics {
    fun perform(type: OrbitHapticType)
    fun tick() = perform(OrbitHapticType.CLOCK_TICK)
    fun confirm() = perform(OrbitHapticType.CONFIRM)
    fun reject() = perform(OrbitHapticType.REJECT)
    fun tap() = perform(OrbitHapticType.TAP)
    fun longPress() = perform(OrbitHapticType.LONG_PRESS)
}

val LocalOrbitHaptics = staticCompositionLocalOf<OrbitHaptics> {
    object : OrbitHaptics {
        override fun perform(type: OrbitHapticType) {}
    }
}

@Composable
expect fun rememberOrbitHaptics(enabled: Boolean = true): OrbitHaptics
