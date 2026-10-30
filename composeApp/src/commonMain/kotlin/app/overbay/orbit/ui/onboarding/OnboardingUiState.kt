package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.StorageMode

enum class OnboardingStep {
    WELCOME,
    STORAGE_MODE,
    DATA_SEED,
    THEME_SETUP
}

enum class SeedChoice {
    DEMO,
    FRESH,
    RESTORE_FILE,
    GOOGLE_DRIVE
}

sealed interface ServerConnectionStatus {
    data object Idle : ServerConnectionStatus
    data object Checking : ServerConnectionStatus
    data class Success(val message: String) : ServerConnectionStatus
    data class Error(val message: String) : ServerConnectionStatus
}

sealed interface ServerAuthStatus {
    data object Idle : ServerAuthStatus
    data object Authenticating : ServerAuthStatus
    data class Success(val displayName: String) : ServerAuthStatus
    data class Error(val message: String) : ServerAuthStatus
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val selectedStorageMode: StorageMode = StorageMode.LOCAL,
    val serverUrl: String = "",
    val serverConnectionStatus: ServerConnectionStatus = ServerConnectionStatus.Idle,
    val serverUsername: String = "",
    val serverPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val authStatus: ServerAuthStatus = ServerAuthStatus.Idle,
    val localAuthEnabled: Boolean = true,
    val oidcAuthEnabled: Boolean = true,
    val oidcLoginUrl: String = "/api/auth/oidc/login",
    val oidcLoginLabel: String = "Continue with Authentik / SSO",
    val selectedSeedChoice: SeedChoice = SeedChoice.DEMO,
    val backupJsonContent: String? = null,
    val backupFileName: String? = null,
    val selectedThemePreset: OrbitThemePreset = OrbitThemePreset.ORBIT_EMERALD,
    val selectedThemeMode: OrbitThemeMode = OrbitThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val biometricOptIn: Boolean = false,
    val isSubmitting: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

sealed interface OnboardingIntent {
    data class SetStep(val step: OnboardingStep) : OnboardingIntent
    data object NextStep : OnboardingIntent
    data object PreviousStep : OnboardingIntent
    data class SelectStorageMode(val mode: StorageMode) : OnboardingIntent
    data class UpdateServerUrl(val url: String) : OnboardingIntent
    data object TestServerConnection : OnboardingIntent
    data class UpdateCredentials(val username: String, val password: String) : OnboardingIntent
    data object TogglePasswordVisibility : OnboardingIntent
    data object AuthenticateServer : OnboardingIntent
    data object PrepareOidcLogin : OnboardingIntent
    data class SelectSeedChoice(val choice: SeedChoice) : OnboardingIntent
    data class SetBackupFile(val fileName: String, val jsonContent: String) : OnboardingIntent
    data class SelectThemePreset(val preset: OrbitThemePreset) : OnboardingIntent
    data class SelectThemeMode(val mode: OrbitThemeMode) : OnboardingIntent
    data class SetDynamicColor(val enabled: Boolean) : OnboardingIntent
    data class SetBiometricOptIn(val enabled: Boolean) : OnboardingIntent
    data object CompleteOnboarding : OnboardingIntent
    data object SkipOnboarding : OnboardingIntent
}
