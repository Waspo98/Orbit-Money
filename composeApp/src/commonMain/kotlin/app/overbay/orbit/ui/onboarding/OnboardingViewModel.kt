package app.overbay.orbit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.domain.DemoSeedGenerator
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: OrbitRepository,
    private val customApiClient: OrbitApiClient? = null,
    private val testHttpClient: HttpClient? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun processIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.SetStep -> _uiState.update { it.copy(currentStep = intent.step) }
            OnboardingIntent.NextStep -> advanceStep()
            OnboardingIntent.PreviousStep -> retreatStep()
            is OnboardingIntent.SelectStorageMode -> _uiState.update { it.copy(selectedStorageMode = intent.mode) }
            is OnboardingIntent.UpdateServerUrl -> _uiState.update {
                it.copy(serverUrl = intent.url, serverConnectionStatus = ServerConnectionStatus.Idle)
            }
            OnboardingIntent.TestServerConnection -> performServerPing()
            is OnboardingIntent.UpdateCredentials -> _uiState.update {
                it.copy(serverUsername = intent.username, serverPassword = intent.password)
            }
            OnboardingIntent.TogglePasswordVisibility -> _uiState.update {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }
            OnboardingIntent.AuthenticateServer -> performServerLogin()
            is OnboardingIntent.SelectSeedChoice -> _uiState.update { it.copy(selectedSeedChoice = intent.choice) }
            is OnboardingIntent.SetBackupFile -> _uiState.update {
                it.copy(backupFileName = intent.fileName, backupJsonContent = intent.jsonContent)
            }
            is OnboardingIntent.SelectThemePreset -> _uiState.update { it.copy(selectedThemePreset = intent.preset) }
            is OnboardingIntent.SelectThemeMode -> _uiState.update { it.copy(selectedThemeMode = intent.mode) }
            is OnboardingIntent.SetDynamicColor -> _uiState.update { it.copy(dynamicColorEnabled = intent.enabled) }
            OnboardingIntent.CompleteOnboarding -> finalizeOnboarding()
            OnboardingIntent.SkipOnboarding -> skipAllAndFinish()
        }
    }

    private fun advanceStep() {
        val steps = OnboardingStep.entries
        val nextIndex = _uiState.value.currentStep.ordinal + 1
        if (nextIndex < steps.size) {
            _uiState.update { it.copy(currentStep = steps[nextIndex]) }
        } else {
            finalizeOnboarding()
        }
    }

    private fun retreatStep() {
        val steps = OnboardingStep.entries
        val prevIndex = _uiState.value.currentStep.ordinal - 1
        if (prevIndex >= 0) {
            _uiState.update { it.copy(currentStep = steps[prevIndex]) }
        }
    }

    private fun performServerPing() {
        val url = _uiState.value.serverUrl.trim().trimEnd('/')
        if (url.isBlank()) {
            _uiState.update { it.copy(serverConnectionStatus = ServerConnectionStatus.Error("Server URL cannot be empty")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(serverConnectionStatus = ServerConnectionStatus.Checking) }
            try {
                val pingClient = testHttpClient ?: HttpClient()
                val response = pingClient.get("$url/api/auth/config")
                if (response.status.isSuccess()) {
                    _uiState.update {
                        it.copy(serverConnectionStatus = ServerConnectionStatus.Success("Connected successfully to Orbit Money server!"))
                    }
                } else {
                    _uiState.update {
                        it.copy(serverConnectionStatus = ServerConnectionStatus.Error("Server returned HTTP ${response.status.value}"))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(serverConnectionStatus = ServerConnectionStatus.Error(e.message ?: "Failed to reach server"))
                }
            }
        }
    }

    private fun performServerLogin() {
        val state = _uiState.value
        if (state.serverUsername.isBlank() || state.serverPassword.isBlank()) {
            _uiState.update { it.copy(authStatus = ServerAuthStatus.Error("Username and password are required")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(authStatus = ServerAuthStatus.Authenticating) }
            try {
                val client = customApiClient ?: repository.createApiClient(state.serverUrl.trim())
                val loginResult = client.login(state.serverUsername.trim(), state.serverPassword)
                if (loginResult.isSuccess) {
                    val user = loginResult.getOrNull()?.username ?: state.serverUsername.trim()
                    _uiState.update { it.copy(authStatus = ServerAuthStatus.Success(user)) }
                } else {
                    _uiState.update {
                        it.copy(authStatus = ServerAuthStatus.Error(loginResult.exceptionOrNull()?.message ?: "Authentication failed"))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(authStatus = ServerAuthStatus.Error(e.message ?: "Login failed")) }
            }
        }
    }

    private fun finalizeOnboarding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val state = _uiState.value

                // 1. Storage Mode
                if (state.selectedStorageMode == StorageMode.SELF_HOSTED) {
                    repository.setStorageMode(StorageMode.SELF_HOSTED, state.serverUrl.trim())
                } else {
                    repository.setStorageMode(StorageMode.LOCAL)
                }

                // 2. Data Seed
                when (state.selectedSeedChoice) {
                    SeedChoice.DEMO -> {
                        val snapshot = DemoSeedGenerator.generateDemoSnapshot()
                        repository.loadDemoData(snapshot)
                    }
                    SeedChoice.RESTORE_FILE -> {
                        state.backupJsonContent?.let { json ->
                            repository.restoreBackupJson(json)
                        }
                    }
                    SeedChoice.FRESH -> {
                        // Keeps empty accounts with standard initialized categories
                    }
                    SeedChoice.GOOGLE_DRIVE -> {
                        // Flags Google Drive sync for automatic connection
                    }
                }

                // 3. Preferences
                val updatedPref = repository.preferences.value.copy(
                    storageMode = state.selectedStorageMode,
                    selfHostedServerUrl = state.serverUrl.trim(),
                    themePreset = state.selectedThemePreset,
                    themeMode = state.selectedThemeMode,
                    dynamicColor = state.dynamicColorEnabled,
                    googleDriveSyncEnabled = (state.selectedSeedChoice == SeedChoice.GOOGLE_DRIVE),
                    onboardingCompleted = true
                )
                repository.updatePreferences(updatedPref)

                _uiState.update { it.copy(isSubmitting = false, isCompleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Setup failed") }
            }
        }
    }

    private fun skipAllAndFinish() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                repository.setStorageMode(StorageMode.LOCAL)
                val snapshot = DemoSeedGenerator.generateDemoSnapshot()
                repository.loadDemoData(snapshot)
                val updated = repository.preferences.value.copy(onboardingCompleted = true)
                repository.updatePreferences(updated)
                _uiState.update { it.copy(isSubmitting = false, isCompleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, isCompleted = true) }
            }
        }
    }
}
