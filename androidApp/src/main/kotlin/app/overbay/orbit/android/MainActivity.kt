package app.overbay.orbit.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import app.overbay.orbit.App
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.util.DisplayRefreshRateHelper

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure adaptive high refresh rate (120Hz) without battery-draining locked modes
        DisplayRefreshRateHelper.configureHighRefreshRate(window, display)

        val navKey = extractNavKeyFromIntent(intent)

        setContent {
            App(initialNavKey = navKey)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractNavKeyFromIntent(intent: Intent?): OrbitNavKey? {
        val uri: Uri = intent?.data ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "orbitmoney" && scheme != "orbitbeta") return null

        val hostAndPath = "${uri.host ?: ""}${uri.path ?: ""}"
        return when {
            hostAndPath.contains("add-transaction") -> OrbitNavKey.Transactions()
            hostAndPath.contains("budgets") -> OrbitNavKey.Budgets
            hostAndPath.contains("accounts") -> OrbitNavKey.Accounts()
            else -> null
        }
    }
}
