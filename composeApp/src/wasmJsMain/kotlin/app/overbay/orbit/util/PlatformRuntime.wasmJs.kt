@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package app.overbay.orbit.util

actual object PlatformRuntime {
    actual val isWeb: Boolean = true
    actual var appVersionName: String = "0.8.0"
}
