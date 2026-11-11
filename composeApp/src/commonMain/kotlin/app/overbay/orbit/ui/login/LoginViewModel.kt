package app.overbay.orbit.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.ServerUrlNormalizer
import app.overbay.orbit.domain.DemoSeedGenerator
import app.overbay.orbit.util.OidcCallbackBridge
import app.overbay.orbit.util.PlatformRuntime
import app.overbay.orbit.util.PlatformStorage
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class LoginViewModel(
    private val repository: OrbitRepository,
    private val customApiClient: OrbitApiClient? = null,
    private val storage: PlatformStorage = PlatformStorage(),
    private val isWebPlatform: Boolean = PlatformRuntime.isWeb,
    scope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = scope ?: (viewModelScope + Dispatchers.Default)

    private val _uiState = MutableStateFlow(
        LoginUiState(step = if (isWebPlatform) LoginStep.SERVER_AUTH else LoginStep.MODE_SELECT)
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    var currentUiState by mutableStateOf(_uiState.value)
        private set

    private inline fun modifyState(transform: (LoginUiState) -> LoginUiState) {
        val next = _uiState.updateAndGet(transform)
        Snapshot.withMutableSnapshot { currentUiState = next }
    }

    init {
        val initialUrl = ServerUrlNormalizer.normalize(repository.preferences.value.selfHostedServerUrl)
        val hasKnownServer = isWebPlatform || initialUrl.isNotBlank()
        val initialStep = if (hasKnownServer) LoginStep.SERVER_AUTH else LoginStep.MODE_SELECT
        modifyState {
            it.copy(
                step = initialStep,
                serverUrl = initialUrl,
                customServerInput = initialUrl.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX }
            )
        }
        val alreadySelfHostedAuthed =
            repository.storageMode.value == StorageMode.SELF_HOSTED && repository.isAuthenticated.value
        if (!alreadySelfHostedAuthed && hasKnownServer) {
            fetchAuthConfig(initialUrl)
        }
        this.scope.launch {
            combine(
                repository.isAuthenticated,
                repository.storageMode
            ) { authed, mode -> authed && mode == StorageMode.SELF_HOSTED }
                .collect { isSelfHostedAuthed ->
                    modifyState {
                        it.copy(
                            isAuthenticated = isSelfHostedAuthed,
                            isSubmitting = if (isSelfHostedAuthed) false else it.isSubmitting,
                            errorMessage = if (isSelfHostedAuthed) null else it.errorMessage
                        )
                    }
                }
        }
        this.scope.launch {
            combine(
                OidcCallbackBridge.isExchanging,
                OidcCallbackBridge.exchangeError
            ) { exchanging, err -> exchanging to err }
                .collect { (exchanging, err) ->
                    modifyState {
                        it.copy(
                            isSubmitting = exchanging || it.isSubmitting,
                            errorMessage = err ?: it.errorMessage
                        )
                    }
                }
        }
    }

    fun resetToModeSelect() {
        modifyState {
            it.copy(
                step = LoginStep.MODE_SELECT,
                customServerInput = it.serverUrl.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX },
                serverError = null,
                errorMessage = null
            )
        }
    }

    fun processIntent(intent: LoginIntent) {
        when (intent) {
            LoginIntent.SelectLocalMode -> modifyState {
                it.copy(
                    step = LoginStep.LOCAL_SETUP,
                    errorMessage = null,
                    serverError = null
                )
            }
            LoginIntent.SelectSelfHostedMode -> modifyState {
                val prefilled = it.serverUrl.ifBlank {
                    it.customServerInput.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX }
                }
                it.copy(
                    step = LoginStep.SERVER_URL,
                    customServerInput = prefilled,
                    serverError = null,
                    errorMessage = null
                )
            }
            LoginIntent.BackToModeSelect -> modifyState {
                it.copy(
                    step = LoginStep.MODE_SELECT,
                    serverError = null,
                    errorMessage = null
                )
            }
            is LoginIntent.SelectLocalSeed -> modifyState {
                it.copy(selectedLocalSeed = intent.choice, errorMessage = null)
            }
            is LoginIntent.SetBackupFile -> modifyState {
                it.copy(
                    backupFileName = intent.fileName,
                    backupJsonContent = intent.jsonContent,
                    errorMessage = null
                )
            }
            LoginIntent.ConfirmLocalSetup -> confirmLocalSetup()
            is LoginIntent.UpdateUsername -> modifyState {
                it.copy(username = intent.username, errorMessage = null)
            }
            is LoginIntent.UpdatePassword -> modifyState {
                it.copy(password = intent.password, errorMessage = null)
            }
            LoginIntent.TogglePasswordVisibility -> modifyState {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }
            LoginIntent.SubmitPasswordLogin -> submitPasswordLogin()
            LoginIntent.PrepareOidcLogin -> prepareOidcLogin()
            LoginIntent.SubmitSampleDataLogin -> submitSampleDataLogin()
            is LoginIntent.SetShowServerConfig -> modifyState {
                val targetStep = if (intent.show) {
                    LoginStep.SERVER_URL
                } else if (it.serverUrl.isNotBlank() || isWebPlatform) {
                    LoginStep.SERVER_AUTH
                } else {
                    LoginStep.MODE_SELECT
                }
                it.copy(
                    step = targetStep,
                    customServerInput = if (intent.show) {
                        it.serverUrl.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX }
                    } else {
                        it.customServerInput
                    },
                    serverError = null
                )
            }
            is LoginIntent.UpdateServerInput -> modifyState {
                it.copy(
                    customServerInput = ServerUrlNormalizer.sanitizeInput(intent.url),
                    serverError = null
                )
            }
            LoginIntent.ConnectAndSaveServer -> connectAndSaveServer()
            LoginIntent.UseOfflineLocalStorage -> switchToOfflineLocalMode()
            LoginIntent.ClearNavigationFlags -> modifyState {
                it.copy(isAuthenticated = false, switchedToLocalMode = false)
            }
        }
    }

    fun fetchAuthConfig(targetUrl: String = _uiState.value.serverUrl) {
        val normalized = ServerUrlNormalizer.normalize(targetUrl)
        modifyState { it.copy(isLoadingConfig = true) }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(normalized)
            val result = client.getAuthConfig()
            if (result.isSuccess) {
                val cfg = result.getOrThrow()
                val localEnabled = cfg.localEnabled || (cfg.authProvider == "local" && !cfg.oidcEnabled)
                val oidcEnabled = cfg.oidcEnabled || cfg.authProvider == "oidc" || cfg.authProvider == "both"
                modifyState {
                    it.copy(
                        isLoadingConfig = false,
                        localEnabled = localEnabled,
                        oidcEnabled = oidcEnabled,
                        oidcLoginUrl = cfg.oidcLoginUrl?.ifBlank { "/api/auth/oidc/login" } ?: "/api/auth/oidc/login",
                        oidcLoginLabel = cfg.oidcLoginLabel?.ifBlank { "Log in with OIDC" } ?: "Log in with OIDC",
                        sampleDataEnabled = cfg.sampleDataEnabled
                    )
                }
            } else {
                modifyState {
                    it.copy(
                        isLoadingConfig = false,
                        localEnabled = true,
                        oidcEnabled = false,
                        sampleDataEnabled = false
                    )
                }
            }
        }
    }

    private fun connectAndSaveServer() {
        val rawInput = _uiState.value.customServerInput
        val normalized = ServerUrlNormalizer.normalize(rawInput)
        if (!isWebPlatform && normalized.isBlank()) {
            modifyState { it.copy(serverError = "Please enter a server address") }
            return
        }
        modifyState {
            it.copy(
                customServerInput = normalized.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX },
                isTestingServer = true,
                serverError = null
            )
        }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(normalized)
            val cfgResult = client.getAuthConfig()
            if (cfgResult.isSuccess) {
                val cfg = cfgResult.getOrThrow()
                val localEnabled = cfg.localEnabled || (cfg.authProvider == "local" && !cfg.oidcEnabled)
                val oidcEnabled = cfg.oidcEnabled || cfg.authProvider == "oidc" || cfg.authProvider == "both"
                repository.updatePreferences(
                    repository.preferences.value.copy(selfHostedServerUrl = normalized)
                )
                modifyState {
                    it.copy(
                        step = LoginStep.SERVER_AUTH,
                        serverUrl = normalized,
                        customServerInput = normalized.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX },
                        isTestingServer = false,
                        serverError = null,
                        errorMessage = null,
                        localEnabled = localEnabled,
                        oidcEnabled = oidcEnabled,
                        oidcLoginUrl = cfg.oidcLoginUrl?.ifBlank { "/api/auth/oidc/login" } ?: "/api/auth/oidc/login",
                        oidcLoginLabel = cfg.oidcLoginLabel?.ifBlank { "Log in with OIDC" } ?: "Log in with OIDC",
                        sampleDataEnabled = cfg.sampleDataEnabled
                    )
                }
            } else {
                val healthOk = client.checkHealth().getOrDefault(false)
                if (healthOk) {
                    repository.updatePreferences(
                        repository.preferences.value.copy(selfHostedServerUrl = normalized)
                    )
                    modifyState {
                        it.copy(
                            step = LoginStep.SERVER_AUTH,
                            serverUrl = normalized,
                            customServerInput = normalized.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX },
                            isTestingServer = false,
                            serverError = null,
                            errorMessage = null
                        )
                    }
                } else {
                    val targetLabel = normalized.ifBlank { "server" }
                    modifyState {
                        it.copy(
                            isTestingServer = false,
                            serverError = "Could not connect to $targetLabel. Check the address and ensure the server is online."
                        )
                    }
                }
            }
        }
    }

    private fun submitPasswordLogin() {
        val state = _uiState.value
        val normalizedUrl = ServerUrlNormalizer.normalize(state.serverUrl)
        if (!isWebPlatform && normalizedUrl.isBlank()) {
            modifyState {
                it.copy(
                    step = LoginStep.SERVER_URL,
                    errorMessage = "Please connect to your Orbit Money server first."
                )
            }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            modifyState { it.copy(errorMessage = "Username and password are required.") }
            return
        }
        modifyState { it.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(normalizedUrl)
            val result = client.login(state.username.trim(), state.password)
            if (result.isSuccess) {
                val me = result.getOrThrow()
                val userLabel = me.displayName ?: me.username ?: state.username.trim()
                repository.markAuthenticated(normalizedUrl, userLabel)
                modifyState { it.copy(isSubmitting = false, isAuthenticated = true) }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Invalid username or password"
                modifyState { it.copy(isSubmitting = false, errorMessage = msg) }
            }
        }
    }

    private fun prepareOidcLogin() {
        val normalizedUrl = ServerUrlNormalizer.normalize(_uiState.value.serverUrl)
        if (!isWebPlatform && normalizedUrl.isBlank()) {
            modifyState {
                it.copy(
                    step = LoginStep.SERVER_URL,
                    errorMessage = "Please connect to your Orbit Money server first."
                )
            }
            return
        }
        storage.saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, normalizedUrl.ifEmpty { "/" })
        modifyState { it.copy(serverUrl = normalizedUrl, errorMessage = null) }
        scope.launch {
            repository.prepareForOidcLogin(normalizedUrl)
        }
    }

    private fun submitSampleDataLogin() {
        val normalizedUrl = ServerUrlNormalizer.normalize(_uiState.value.serverUrl)
        modifyState { it.copy(isSampleLoading = true, errorMessage = null) }
        scope.launch {
            val deviceId = getOrCreateSampleDeviceId()
            val client = customApiClient ?: repository.createApiClient(normalizedUrl)
            val result = client.loginWithSampleData(deviceId)
            if (result.isSuccess) {
                val me = result.getOrThrow()
                val userLabel = me.displayName ?: "Sample Data"
                repository.markAuthenticated(normalizedUrl, userLabel)
                modifyState { it.copy(isSampleLoading = false, isAuthenticated = true) }
            } else {
                modifyState {
                    it.copy(
                        isSampleLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not load sample data"
                    )
                }
            }
        }
    }

    private fun confirmLocalSetup() {
        val state = _uiState.value
        if (state.selectedLocalSeed == LocalSeedChoice.RESTORE_FILE && state.backupJsonContent.isNullOrBlank()) {
            modifyState { it.copy(errorMessage = "Please select or paste a backup JSON file first.") }
            return
        }
        modifyState { it.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            repository.setStorageMode(StorageMode.LOCAL)
            when (state.selectedLocalSeed) {
                LocalSeedChoice.DEMO -> {
                    val snapshot = DemoSeedGenerator.generateDemoSnapshot()
                    repository.loadDemoData(snapshot)
                }
                LocalSeedChoice.RESTORE_FILE -> {
                    val restoreResult = repository.restoreBackupJson(state.backupJsonContent.orEmpty())
                    if (restoreResult.isFailure) {
                        modifyState {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = restoreResult.exceptionOrNull()?.message ?: "Could not restore backup file."
                            )
                        }
                        return@launch
                    }
                }
                LocalSeedChoice.FRESH -> {
                    // Retains clean workspace with standard default categories
                }
            }
            repository.updatePreferences(
                repository.preferences.value.copy(
                    storageMode = StorageMode.LOCAL,
                    onboardingCompleted = true
                )
            )
            modifyState { it.copy(isSubmitting = false, switchedToLocalMode = true) }
        }
    }

    private fun switchToOfflineLocalMode() {
        scope.launch {
            repository.setStorageMode(StorageMode.LOCAL)
            repository.updatePreferences(
                repository.preferences.value.copy(
                    storageMode = StorageMode.LOCAL,
                    onboardingCompleted = true
                )
            )
            modifyState { it.copy(switchedToLocalMode = true) }
        }
    }

    private fun getOrCreateSampleDeviceId(): String {
        val existing = storage.loadString(SAMPLE_DEVICE_ID_KEY)
        if (!existing.isNullOrBlank() && existing.length >= 16) return existing
        val chars = "0123456789abcdef"
        val generated = buildString(48) {
            repeat(48) { append(chars[Random.nextInt(chars.length)]) }
        }
        storage.saveString(SAMPLE_DEVICE_ID_KEY, generated)
        return generated
    }

    companion object {
        const val SAMPLE_DEVICE_ID_KEY = "orbit_money_sample_device_id"
    }
}
