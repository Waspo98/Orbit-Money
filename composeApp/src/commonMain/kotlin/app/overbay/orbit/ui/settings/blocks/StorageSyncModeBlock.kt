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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOidcAuthLauncher

@Composable
fun StorageSyncModeBlock(
    preferences: OrbitPreferences,
    isSyncing: Boolean,
    syncStatus: SyncResult,
    onStorageModeChange: (StorageMode) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onAuthenticateServer: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val oidcLauncher = LocalOidcAuthLauncher.current
    var serverUrl by remember(preferences.selfHostedServerUrl) { mutableStateOf(preferences.selfHostedServerUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    OrbitSectionCard(
        title = "Data Storage & Sync Environment",
        subtitle = "Switch between offline local-first storage and self-hosted server",
        modifier = modifier
    ) {
        // Storage mode chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = preferences.storageMode == StorageMode.LOCAL,
                onClick = { onStorageModeChange(StorageMode.LOCAL) },
                label = { Text("Local Device (Offline)") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = preferences.storageMode == StorageMode.SELF_HOSTED,
                onClick = { onStorageModeChange(StorageMode.SELF_HOSTED) },
                label = { Text("Self-Hosted Server") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (preferences.storageMode == StorageMode.SELF_HOSTED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        onServerUrlChange(it)
                    },
                    label = { Text("Server Base URL") },
                    placeholder = { Text("https://orbit.yourdomain.com") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Button(
                    onClick = onTriggerSync,
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Sync")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onServerUrlChange(serverUrl)
                    oidcLauncher.launchOidcLogin(serverUrl, "/api/auth/oidc/login")
                },
                enabled = !isSyncing && serverUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue with Authentik / SSO")
            }

            if (onAuthenticateServer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = { onAuthenticateServer(username, password) },
                        enabled = !isSyncing && username.isNotBlank() && password.isNotBlank()
                    ) {
                        Text("Login")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Status pill
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val statusText = when (syncStatus) {
                    is SyncResult.Success -> syncStatus.message
                    is SyncResult.Error -> "Sync failed: ${syncStatus.message}"
                    SyncResult.NoOp -> "Idle"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (syncStatus is SyncResult.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
