package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface OidcAuthLauncher {
    fun launchOidcLogin(serverUrl: String, loginPath: String = "/api/auth/oidc/login")
}

val LocalOidcAuthLauncher = staticCompositionLocalOf<OidcAuthLauncher> {
    object : OidcAuthLauncher {
        override fun launchOidcLogin(serverUrl: String, loginPath: String) {}
    }
}

object OidcCallbackBridge {
    const val PENDING_SERVER_URL_KEY = "orbit_pending_oidc_server_url"

    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    private val _isExchanging = MutableStateFlow(false)
    val isExchanging: StateFlow<Boolean> = _isExchanging.asStateFlow()

    private val _exchangeError = MutableStateFlow<String?>(null)
    val exchangeError: StateFlow<String?> = _exchangeError.asStateFlow()

    fun submitExchangeCode(code: String) {
        if (code.isNotBlank()) {
            _exchangeError.value = null
            _pendingCode.value = code.trim()
        }
    }

    fun consumeExchangeCode(code: String) {
        if (_pendingCode.value == code) _pendingCode.value = null
    }

    fun setExchanging(exchanging: Boolean) {
        _isExchanging.value = exchanging
    }

    fun setExchangeError(message: String?) {
        _exchangeError.value = message
    }
}

@Composable
expect fun rememberOidcAuthLauncher(): OidcAuthLauncher
