package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun SecurityBiometricsBlock(
    preferences: OrbitPreferences,
    onBiometricLockChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onMhaTrackerChange: (Boolean) -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Security & Features",
        subtitle = "Biometrics, haptic feedback and feature toggles",
        modifier = modifier
    ) {
        // Biometric App Lock
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Biometric App Lock",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Require fingerprint or face unlock upon app resume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.biometricLockEnabled,
                onCheckedChange = onBiometricLockChange
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Haptics Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kinetic Haptic Feedback",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Physics vibrations on sliders, chart scrubbing & keypad entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.hapticsEnabled,
                onCheckedChange = onHapticsChange
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // MHA Tracker Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ministerial Housing Allowance (MHA)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enable IRC § 107 parsonage deduction tracker in navigation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.mhaTrackerEnabled,
                onCheckedChange = onMhaTrackerChange
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Data Reset & Version Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onResetData,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset Workspace")
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "v0.9.0-beta (KMP)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
