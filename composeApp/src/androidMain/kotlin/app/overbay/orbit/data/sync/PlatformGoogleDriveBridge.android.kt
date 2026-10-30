package app.overbay.orbit.data.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn

@Composable
actual fun BindPlatformGoogleDriveClient(
    cloudSyncManager: CloudSyncManager,
    googleClientId: String
) {
    val context = LocalContext.current
    LaunchedEffect(cloudSyncManager, googleClientId) {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)?.account
        if (googleAccount != null) {
            val client = AndroidGoogleDriveClient(context, googleAccount)
            cloudSyncManager.setRemoteClient(client)
            cloudSyncManager.connect(googleAccount.name)
        }
    }
}
