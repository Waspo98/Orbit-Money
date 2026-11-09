package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalBiometricAuthHelper

@Composable
fun SecurityBiometricsBlock(
    preferences: OrbitPreferences,
    onBiometricLockChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val biometricHelper = LocalBiometricAuthHelper.current
    val canAuth = remember { biometricHelper.canAuthenticate() }

    OrbitSectionCard(
        title = "Security & Touch Feedback",
        subtitle = "Biometric app lock and touch vibration preferences",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OrbitSwitchRow(
                title = "Biometric App Lock",
                subtitle = if (canAuth) {
                    "Require fingerprint or face unlock when reopening the app"
                } else {
                    "Biometric unlock is not available on this device"
                },
                checked = preferences.biometricLockEnabled,
                enabled = canAuth || preferences.biometricLockEnabled,
                onCheckedChange = { enable ->
                    if (enable) {
                        biometricHelper.authenticate(
                            title = "Verify Identity",
                            subtitle = "Confirm biometrics to enable App Lock",
                            onSuccess = { onBiometricLockChange(true) },
                            onError = { _, _ -> /* Keep disabled */ }
                        )
                    } else {
                        onBiometricLockChange(false)
                    }
                }
            )

            OrbitSwitchRow(
                title = "Haptic Feedback",
                subtitle = "Subtle vibrations when adjusting sliders, scrubbing charts, and tapping controls",
                checked = preferences.hapticsEnabled,
                onCheckedChange = onHapticsChange
            )
        }
    }
}
