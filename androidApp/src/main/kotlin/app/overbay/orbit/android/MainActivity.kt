package app.overbay.orbit.android

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import app.overbay.orbit.App
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.util.DisplayRefreshRateHelper
import app.overbay.orbit.util.OidcCallbackBridge
import app.overbay.orbit.util.PlatformStorage

class MainActivity : FragmentActivity() {

    private var currentNavKey by mutableStateOf<OrbitNavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        PlatformStorage.baseDir = filesDir

        // Configure adaptive high refresh rate (120Hz) without battery-draining locked modes
        DisplayRefreshRateHelper.configureHighRefreshRate(window, display)

        handleIncomingIntent(intent)

        setContent {
            App(initialNavKey = currentNavKey)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        val scheme = uri.scheme?.lowercase()
        if (scheme != "orbitmoney" && scheme != "orbitbeta" && scheme != "orbitmoneybeta") return

        val hostAndPath = "${uri.host ?: ""}${uri.path ?: ""}"
        if (hostAndPath.contains("auth/callback")) {
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                OidcCallbackBridge.submitExchangeCode(code)
            }
            return
        }

        extractNavKeyFromHostAndPath(hostAndPath)?.let { currentNavKey = it }
    }

    private fun extractNavKeyFromHostAndPath(hostAndPath: String): OrbitNavKey? {
        return when {
            hostAndPath.contains("add-transaction") -> OrbitNavKey.Transactions(openAddSheet = true)
            hostAndPath.contains("budgets") -> OrbitNavKey.Budgets
            hostAndPath.contains("accounts") -> OrbitNavKey.Accounts()
            else -> null
        }
    }
}
