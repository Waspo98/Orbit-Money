package app.overbay.orbit.ui.login

import app.overbay.orbit.util.PlatformRuntime

data class LoginUiState(
    val serverUrl: String = "",
    val customServerInput: String = "",
    val showServerConfig: Boolean = !PlatformRuntime.isWeb,
    val isTestingServer: Boolean = false,
    val serverError: String? = null,
    val isLoadingConfig: Boolean = false,
    val localEnabled: Boolean = true,
    val oidcEnabled: Boolean = false,
    val oidcLoginUrl: String = "/api/auth/oidc/login",
    val oidcLoginLabel: String = "Log in with OIDC",
    val sampleDataEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSampleLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val switchedToLocalMode: Boolean = false
)

sealed interface LoginIntent {
    data class UpdateUsername(val username: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    data object TogglePasswordVisibility : LoginIntent
    data object SubmitPasswordLogin : LoginIntent
    data object PrepareOidcLogin : LoginIntent
    data object SubmitSampleDataLogin : LoginIntent
    data class SetShowServerConfig(val show: Boolean) : LoginIntent
    data class UpdateServerInput(val url: String) : LoginIntent
    data object ConnectAndSaveServer : LoginIntent
    data object UseOfflineLocalStorage : LoginIntent
    data object ClearNavigationFlags : LoginIntent
}
