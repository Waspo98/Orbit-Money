package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

interface BiometricAuthHelper {
    fun canAuthenticate(): Boolean
    fun authenticate(
        title: String = "Unlock Orbit Money",
        subtitle: String = "Confirm biometric credentials to access your financial data",
        negativeButtonText: String = "Cancel",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    )
}

val LocalBiometricAuthHelper = staticCompositionLocalOf<BiometricAuthHelper> {
    object : BiometricAuthHelper {
        override fun canAuthenticate(): Boolean = false
        override fun authenticate(
            title: String,
            subtitle: String,
            negativeButtonText: String,
            onSuccess: () -> Unit,
            onError: (errorCode: Int, errString: String) -> Unit,
            onFailed: () -> Unit
        ) {
            onSuccess()
        }
    }
}

@Composable
expect fun rememberBiometricAuthHelper(): BiometricAuthHelper
