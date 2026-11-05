package app.overbay.orbit.data.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun BindPlatformGoogleDriveClient(
    cloudSyncManager: CloudSyncManager,
    googleClientId: String
) {
    val wasmClient = remember { WasmGoogleDriveClient() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(cloudSyncManager, googleClientId) {
        cloudSyncManager.setRemoteClient(wasmClient)
        if (googleClientId.isNotBlank()) {
            wasmClient.initializeGisAuth(googleClientId) { email ->
                if (email != null) {
                    coroutineScope.launch {
                        cloudSyncManager.connect(email)
                    }
                }
            }
        }
    }
}
