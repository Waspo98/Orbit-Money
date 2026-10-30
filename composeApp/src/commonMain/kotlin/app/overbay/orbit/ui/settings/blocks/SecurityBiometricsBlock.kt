package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onMhaTrackerChange: (Boolean) -> Unit = {},
    onResetData: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val biometricHelper = LocalBiometricAuthHelper.current
    val canAuth = remember { biometricHelper.canAuthenticate() }

    OrbitSectionCard(
        title = "Security & Hardware Feedback",
        subtitle = "Biometrics, sensory haptics, and parsonage feature toggles",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Biometric App Lock with verification prompt
            OrbitSwitchRow(
                title = "Biometric App Lock",
                subtitle = if (canAuth) {
                    "Require fingerprint or face unlock upon app resume"
                } else {
                    "Biometric hardware not available on this device"
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

            // Haptics Switch
            OrbitSwitchRow(
                title = "Kinetic Haptic Feedback",
                subtitle = "Physics vibrations on sliders, chart scrubbing & keypad entries",
                checked = preferences.hapticsEnabled,
                onCheckedChange = onHapticsChange
            )

            // MHA Tracker Switch
            OrbitSwitchRow(
                title = "Ministerial Housing Allowance (MHA)",
                subtitle = "Enable IRC § 107 parsonage deduction tracker in navigation",
                checked = preferences.mhaTrackerEnabled,
                onCheckedChange = onMhaTrackerChange
            )

            // Data Reset Button if supplied
            if (onResetData != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onResetData,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Workspace Data")
                }
            }
        }
    }
}
