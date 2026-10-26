package app.overbay.orbit.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.overbay.orbit.data.network.ServerUrlNormalizer

@Composable
actual fun rememberOidcAuthLauncher(): OidcAuthLauncher {
    val context = LocalContext.current
    return remember(context) {
        object : OidcAuthLauncher {
            override fun launchOidcLogin(serverUrl: String, loginPath: String) {
                val base = ServerUrlNormalizer.normalize(serverUrl)
                if (base.isEmpty() && !loginPath.startsWith("http")) return
                if (base.isNotEmpty()) {
                    PlatformStorage().saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, base)
                }
                val resolvedPath = if (loginPath.startsWith("http")) {
                    loginPath
                } else {
                    "$base${if (loginPath.startsWith("/")) loginPath else "/$loginPath"}"
                }
                val appScheme = if (context.packageName.endsWith(".beta")) "orbitbeta" else "orbitmoney"
                val separator = if (resolvedPath.contains("?")) "&" else "?"
                val targetUrl = "${resolvedPath}${separator}app_return=1&app_scheme=$appScheme"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
