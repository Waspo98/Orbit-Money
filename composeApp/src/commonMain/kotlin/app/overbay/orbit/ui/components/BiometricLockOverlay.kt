package app.overbay.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.util.LocalBiometricAuthHelper

@Composable
fun BiometricLockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val biometricHelper = LocalBiometricAuthHelper.current

    LaunchedEffect(biometricHelper) {
        if (biometricHelper.canAuthenticate()) {
            biometricHelper.authenticate(
                title = "Unlock Orbit Money",
                subtitle = "Confirm biometric credentials to access your household finances",
                onSuccess = onUnlock
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Orbit Money Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Biometric authentication is required to access your accounts, transactions, and budgets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    biometricHelper.authenticate(
                        title = "Unlock Orbit Money",
                        subtitle = "Confirm biometric credentials to access your household finances",
                        onSuccess = onUnlock
                    )
                },
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Unlock with Biometrics")
            }
        }
    }
}
