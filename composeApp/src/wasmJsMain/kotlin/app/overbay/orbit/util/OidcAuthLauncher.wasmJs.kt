package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.overbay.orbit.data.network.ServerUrlNormalizer

@JsFun("(url) => { window.location.href = url; }")
private external fun jsRedirectToOidc(url: String)

@Composable
actual fun rememberOidcAuthLauncher(): OidcAuthLauncher {
    return remember {
        object : OidcAuthLauncher {
            override fun launchOidcLogin(serverUrl: String, loginPath: String) {
                val base = ServerUrlNormalizer.normalize(serverUrl)
                PlatformStorage().saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, base.ifEmpty { "/" })
                val resolvedUrl = if (loginPath.startsWith("http")) {
                    loginPath
                } else if (base.isNotEmpty()) {
                    "$base${if (loginPath.startsWith("/")) loginPath else "/$loginPath"}"
                } else {
                    if (loginPath.startsWith("/")) loginPath else "/$loginPath"
                }
                try {
                    jsRedirectToOidc(resolvedUrl)
                } catch (_: Throwable) {}
            }
        }
    }
}
