package app.overbay.orbit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.sync.BindPlatformGoogleDriveClient
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.BiometricLockOverlay
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalBiometricAuthHelper
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOidcAuthLauncher
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.OidcCallbackBridge
import app.overbay.orbit.util.PlatformStorage
import app.overbay.orbit.util.rememberBiometricAuthHelper
import app.overbay.orbit.util.rememberDevicePosture
import app.overbay.orbit.util.rememberFileImportExportHelper
import app.overbay.orbit.util.rememberOidcAuthLauncher
import app.overbay.orbit.util.rememberOrbitHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

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
    val oidcLauncher = rememberOidcAuthLauncher()
    val repo = repository ?: remember { OrbitRepository(PlatformStorage()) }
    var isInitialized by remember(repo) { mutableStateOf(repository != null) }

    LaunchedEffect(repo) {
        try {
            withContext(Dispatchers.Default) {
                repo.initialize()
            }
        } catch (_: Throwable) {
            // Ensure initialization error never hangs startup screen
        } finally {
            isInitialized = true
        }
    }

    LaunchedEffect(isInitialized, repo) {
        if (!isInitialized) return@LaunchedEffect
        OidcCallbackBridge.pendingCode.collect { code ->
            if (!code.isNullOrBlank()) {
                OidcCallbackBridge.consumeExchangeCode(code)
                OidcCallbackBridge.setExchanging(true)
                val result = repo.completeOidcNativeExchange(code)
                OidcCallbackBridge.setExchanging(false)
                result.exceptionOrNull()?.let { err ->
                    OidcCallbackBridge.setExchangeError(err.message ?: "SSO Login failed")
                }
            }
        }
    }

    val preferences by repo.preferences.collectAsStateWithLifecycle()
    val isAuthenticated by repo.isAuthenticated.collectAsStateWithLifecycle()
    var isBiometricLocked by remember(preferences.biometricLockEnabled) {
        mutableStateOf(preferences.biometricLockEnabled)
    }

    BindPlatformGoogleDriveClient(
        cloudSyncManager = repo.cloudSyncManager,
        googleClientId = preferences.googleDriveClientId
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    var backgroundedTimestamp by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(lifecycleOwner, preferences.biometricLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                backgroundedTimestamp = Clock.System.now().toEpochMilliseconds()
            } else if (event == Lifecycle.Event.ON_START) {
                val bgTime = backgroundedTimestamp
                if (bgTime != null && preferences.biometricLockEnabled) {
                    val elapsed = Clock.System.now().toEpochMilliseconds() - bgTime
                    if (elapsed >= 30_000L) {
                        isBiometricLocked = true
                    }
                }
                backgroundedTimestamp = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activePreset = themePreset ?: preferences.themePreset
    val activeMode = themeMode ?: preferences.themeMode
    val activeDynamicColor = preferences.dynamicColor

    val targetInitialNavKey = initialNavKey ?: if (
        !preferences.onboardingCompleted ||
        (preferences.storageMode == StorageMode.SELF_HOSTED && !isAuthenticated)
    ) {
        OrbitNavKey.Login
    } else {
        OrbitNavKey.Dashboard
    }

    CompositionLocalProvider(
        LocalDevicePosture provides posture,
        LocalOrbitHaptics provides haptics,
        LocalBiometricAuthHelper provides biometricHelper,
        LocalFileImportExportHelper provides fileHelper,
        LocalOidcAuthLauncher provides oidcLauncher
    ) {
        OrbitTheme(
            themePreset = activePreset,
            themeMode = activeMode,
            dynamicColor = activeDynamicColor,
            amoledDark = preferences.amoledDarkMode
        ) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (!isInitialized) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
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
