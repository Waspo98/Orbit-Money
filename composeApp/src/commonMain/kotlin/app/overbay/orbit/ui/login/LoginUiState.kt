package app.overbay.orbit.ui.login

import app.overbay.orbit.util.PlatformRuntime

enum class LoginStep {
    MODE_SELECT,
    LOCAL_SETUP,
    SERVER_URL,
    SERVER_AUTH
}

enum class LocalSeedChoice {
    FRESH,
    DEMO,
    RESTORE_FILE
}

data class LoginUiState(
    val step: LoginStep = if (PlatformRuntime.isWeb) LoginStep.SERVER_AUTH else LoginStep.MODE_SELECT,
    val serverUrl: String = "",
    val customServerInput: String = DEFAULT_SERVER_PREFIX,
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
    val selectedLocalSeed: LocalSeedChoice = LocalSeedChoice.FRESH,
    val backupFileName: String? = null,
    val backupJsonContent: String? = null,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val switchedToLocalMode: Boolean = false
) {
    val showServerConfig: Boolean
        get() = step == LoginStep.SERVER_URL

    companion object {
        const val DEFAULT_SERVER_PREFIX = "https://"
    }
}

sealed interface LoginIntent {
    data object SelectLocalMode : LoginIntent
    data object SelectSelfHostedMode : LoginIntent
    data object BackToModeSelect : LoginIntent
    data class SelectLocalSeed(val choice: LocalSeedChoice) : LoginIntent
    data class SetBackupFile(val fileName: String?, val jsonContent: String?) : LoginIntent
    data object ConfirmLocalSetup : LoginIntent
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
