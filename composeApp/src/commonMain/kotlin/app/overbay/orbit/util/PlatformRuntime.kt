@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package app.overbay.orbit.util

expect object PlatformRuntime {
    val isWeb: Boolean
}
