package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalBiometricAuthHelper

private val BIOMETRIC_TIMEOUT_OPTIONS = listOf(0, 60, 300, 900)

@Composable
fun AppLockCard(
    preferences: OrbitPreferences,
    onBiometricLockChange: (Boolean) -> Unit,
    onBiometricTimeoutChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val biometricHelper = LocalBiometricAuthHelper.current
    val canAuth = remember { biometricHelper.canAuthenticate() }
    val selectedTimeout = BIOMETRIC_TIMEOUT_OPTIONS.firstOrNull {
        it == preferences.biometricTimeoutSeconds
    } ?: 300

    OrbitSectionCard(
        title = "App Lock",
        subtitle = "Protect your household finances with device biometrics",
        modifier = modifier
    ) {
        OrbitSwitchRow(
            title = "Biometric App Lock",
            subtitle = if (canAuth) {
                "Require fingerprint or face unlock when reopening Orbit"
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
                        onError = { _, _ -> }
                    )
                } else {
                    onBiometricLockChange(false)
                }
            },
            showContainer = false
        )

        OrbitAccordionVisibility(visible = preferences.biometricLockEnabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Auto-Lock Timeout",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                OrbitSegmentedTabRow(
                    items = BIOMETRIC_TIMEOUT_OPTIONS,
                    selected = selectedTimeout,
                    onSelect = onBiometricTimeoutChange,
                    label = { seconds ->
                        when (seconds) {
                            0 -> "Immediate"
                            60 -> "1 min"
                            300 -> "5 min"
                            900 -> "15 min"
                            else -> "5 min"
                        }
                    }
                )
            }
        }
    }
}
