package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun SimpleFinBankSyncBlock(
    token: String?,
    onTokenChange: (String?) -> Unit,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawToken by remember(token) { mutableStateOf(token ?: "") }
    val isConnected = !token.isNullOrBlank()

    OrbitSectionCard(
        title = "SimpleFIN Bank Feeds",
        subtitle = "Connect read-only bank data directly without third-party aggregators",
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isConnected) "Bank Feed Active" else "No Connection Configured",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isConnected) "Direct SimpleFIN bridge configured" else "Enter a Setup Token from bridge.simplefin.org",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isConnected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = rawToken,
            onValueChange = { rawToken = it },
            label = { Text("SimpleFIN Setup Token") },
            placeholder = { Text("https://bridge.simplefin.org/simplefin/claim/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (isConnected) {
                Button(
                    onClick = onTriggerSync,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Sync Bank Data")
                }
            }
            Button(
                onClick = { onTokenChange(rawToken.ifBlank { null }) },
                enabled = rawToken != (token ?: "")
            ) {
                Text("Save Token")
            }
        }
    }
}
