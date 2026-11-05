package app.overbay.orbit.util

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
actual fun rememberBiometricAuthHelper(): BiometricAuthHelper {
    val context = LocalContext.current
    return remember(context) {
        object : BiometricAuthHelper {
            override fun canAuthenticate(): Boolean {
                val bm = BiometricManager.from(context)
                val status = bm.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                return status == BiometricManager.BIOMETRIC_SUCCESS
            }

            override fun authenticate(
                title: String,
                subtitle: String,
                negativeButtonText: String,
                onSuccess: () -> Unit,
                onError: (errorCode: Int, errString: String) -> Unit,
                onFailed: () -> Unit
            ) {
                val activity = context.findFragmentActivity() ?: run {
                    onSuccess()
                    return
                }

                val executor = ContextCompat.getMainExecutor(activity)
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errorCode, errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailed()
                    }
                }

                val prompt = BiometricPrompt(activity, executor, callback)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(negativeButtonText)
                    .build()

                prompt.authenticate(promptInfo)
            }
        }
    }
}
