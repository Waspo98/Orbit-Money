package app.overbay.orbit.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.overbay.orbit.data.network.ServerUrlNormalizer

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
                val appScheme = if (context.packageName.endsWith(".beta")) "orbitmoneybeta" else "orbitmoney"
                val separator = if (resolvedPath.contains("?")) "&" else "?"
                val targetUrl = "${resolvedPath}${separator}app_return=1&app_scheme=$appScheme"
                val uri = Uri.parse(targetUrl)
                val activity = context.findActivity()
                try {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    customTabsIntent.intent.putExtra(
                        Intent.EXTRA_REFERRER,
                        Uri.parse("android-app://${context.packageName}")
                    )
                    if (activity != null) {
                        customTabsIntent.launchUrl(activity, uri)
                    } else {
                        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        customTabsIntent.launchUrl(context, uri)
                    }
                } catch (_: Exception) {
                    try {
                        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        (activity ?: context).startActivity(fallback)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
