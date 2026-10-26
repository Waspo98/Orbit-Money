package app.overbay.orbit

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.BiometricLockOverlay
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalBiometricAuthHelper
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformStorage
import app.overbay.orbit.util.rememberBiometricAuthHelper
import app.overbay.orbit.util.rememberDevicePosture
import app.overbay.orbit.util.rememberFileImportExportHelper
import app.overbay.orbit.util.rememberOrbitHaptics

@Composable
fun App(
    posture: DevicePosture = rememberDevicePosture(),
    themePreset: OrbitThemePreset? = null,
    themeMode: OrbitThemeMode? = null,
    repository: OrbitRepository? = null,
    initialNavKey: OrbitNavKey? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberOrbitHaptics()
    val biometricHelper = rememberBiometricAuthHelper()
    val fileHelper = rememberFileImportExportHelper()
    val repo = repository ?: remember { OrbitRepository(PlatformStorage()) }
    var isInitialized by remember(repo) { mutableStateOf(repository != null) }

    LaunchedEffect(repo) {
        repo.initialize()
        isInitialized = true
    }

    val preferences by repo.preferences.collectAsStateWithLifecycle()
    var isBiometricLocked by remember(preferences.biometricLockEnabled) {
        mutableStateOf(preferences.biometricLockEnabled)
    }

    val activePreset = themePreset ?: preferences.themePreset
    val activeMode = themeMode ?: preferences.themeMode
    val activeDynamicColor = preferences.dynamicColor

    val targetInitialNavKey = initialNavKey ?: if (preferences.onboardingCompleted) {
        OrbitNavKey.Dashboard
    } else {
        OrbitNavKey.Onboarding
    }

    CompositionLocalProvider(
        LocalDevicePosture provides posture,
        LocalOrbitHaptics provides haptics,
        LocalBiometricAuthHelper provides biometricHelper,
        LocalFileImportExportHelper provides fileHelper
    ) {
        OrbitTheme(
            themePreset = activePreset,
            themeMode = activeMode,
            dynamicColor = activeDynamicColor
        ) {
            Surface(modifier = modifier) {
                if (!isInitialized) {
                    // Wait for initial local preference snapshot load before mounting initial nav key
                } else if (isBiometricLocked && preferences.biometricLockEnabled) {
                    BiometricLockOverlay(
                        onUnlock = { isBiometricLocked = false }
                    )
                } else {
                    OrbitNavigation(
                        repository = repo,
                        initialKey = targetInitialNavKey
                    )
                }
            }
        }
    }
}
