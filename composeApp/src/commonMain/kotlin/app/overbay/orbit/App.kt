package app.overbay.orbit

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformStorage
import app.overbay.orbit.util.rememberDevicePosture
import app.overbay.orbit.util.rememberOrbitHaptics

@Composable
fun App(
    posture: DevicePosture = rememberDevicePosture(),
    themePreset: OrbitThemePreset? = null,
    themeMode: OrbitThemeMode? = null,
    dynamicColor: Boolean? = null,
    repository: OrbitRepository? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberOrbitHaptics()
    val repo = repository ?: remember { OrbitRepository(PlatformStorage()) }

    LaunchedEffect(repo) {
        repo.initialize()
    }

    val preferences by repo.preferences.collectAsStateWithLifecycle()

    val activePreset = themePreset ?: preferences.themePreset
    val activeMode = themeMode ?: preferences.themeMode
    val activeDynamicColor = dynamicColor ?: preferences.dynamicColor

    val initialNavKey = if (preferences.onboardingCompleted) {
        OrbitNavKey.Dashboard
    } else {
        OrbitNavKey.Onboarding
    }

    CompositionLocalProvider(
        LocalDevicePosture provides posture,
        LocalOrbitHaptics provides haptics
    ) {
        OrbitTheme(
            themePreset = activePreset,
            themeMode = activeMode,
            dynamicColor = activeDynamicColor
        ) {
            Surface(modifier = modifier) {
                OrbitNavigation(
                    repository = repo,
                    initialKey = initialNavKey
                )
            }
        }
    }
}
