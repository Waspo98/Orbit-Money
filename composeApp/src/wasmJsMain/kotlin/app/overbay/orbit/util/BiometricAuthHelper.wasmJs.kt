package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberBiometricAuthHelper(): BiometricAuthHelper {
    return remember {
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
}
