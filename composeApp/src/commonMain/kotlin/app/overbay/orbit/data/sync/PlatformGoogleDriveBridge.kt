package app.overbay.orbit.data.sync

import androidx.compose.runtime.Composable

/**
 * Attaches the platform-specific Google Drive client to [CloudSyncManager].
 */
@Composable
expect fun BindPlatformGoogleDriveClient(
    cloudSyncManager: CloudSyncManager,
    googleClientId: String
)
