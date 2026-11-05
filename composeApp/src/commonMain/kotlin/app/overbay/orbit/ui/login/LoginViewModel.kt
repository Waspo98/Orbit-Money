package app.overbay.orbit.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.ServerUrlNormalizer
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
import kotlinx.coroutines.flow.update
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

    private val _uiState = MutableStateFlow(LoginUiState(showServerConfig = !isWebPlatform))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        val initialUrl = ServerUrlNormalizer.normalize(repository.preferences.value.selfHostedServerUrl)
        val shouldShowServerBox = !isWebPlatform && initialUrl.isBlank()
        _uiState.update {
            it.copy(
                serverUrl = initialUrl,
                customServerInput = initialUrl,
                showServerConfig = shouldShowServerBox
            )
        }
        val alreadySelfHostedAuthed =
            repository.storageMode.value == StorageMode.SELF_HOSTED && repository.isAuthenticated.value
        if (!alreadySelfHostedAuthed && (isWebPlatform || initialUrl.isNotBlank())) {
            fetchAuthConfig(initialUrl)
        }
        this.scope.launch {
            combine(
                repository.isAuthenticated,
                repository.storageMode
            ) { authed, mode -> authed && mode == StorageMode.SELF_HOSTED }
                .collect { isSelfHostedAuthed ->
                    _uiState.update {
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
                    _uiState.update {
                        it.copy(
                            isSubmitting = exchanging || it.isSubmitting,
                            errorMessage = err ?: it.errorMessage
                        )
                    }
                }
        }
    }

    fun processIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.UpdateUsername -> _uiState.update {
                it.copy(username = intent.username, errorMessage = null)
            }
            is LoginIntent.UpdatePassword -> _uiState.update {
                it.copy(password = intent.password, errorMessage = null)
            }
            LoginIntent.TogglePasswordVisibility -> _uiState.update {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }
            LoginIntent.SubmitPasswordLogin -> submitPasswordLogin()
            LoginIntent.PrepareOidcLogin -> prepareOidcLogin()
            LoginIntent.SubmitSampleDataLogin -> submitSampleDataLogin()
            is LoginIntent.SetShowServerConfig -> _uiState.update {
                it.copy(
                    showServerConfig = intent.show,
                    customServerInput = if (intent.show) it.serverUrl else it.customServerInput,
                    serverError = null
                )
            }
            is LoginIntent.UpdateServerInput -> _uiState.update {
                it.copy(customServerInput = intent.url, serverError = null)
            }
            LoginIntent.ConnectAndSaveServer -> connectAndSaveServer()
            LoginIntent.UseOfflineLocalStorage -> switchToOfflineLocalMode()
            LoginIntent.ClearNavigationFlags -> _uiState.update {
                it.copy(isAuthenticated = false, switchedToLocalMode = false)
            }
        }
    }

    fun fetchAuthConfig(targetUrl: String = _uiState.value.serverUrl) {
        val normalized = ServerUrlNormalizer.normalize(targetUrl)
        _uiState.update { it.copy(isLoadingConfig = true) }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(normalized)
            val result = client.getAuthConfig()
            if (result.isSuccess) {
                val cfg = result.getOrThrow()
                val localEnabled = cfg.localEnabled || (cfg.authProvider == "local" && !cfg.oidcEnabled)
                val oidcEnabled = cfg.oidcEnabled || cfg.authProvider == "oidc" || cfg.authProvider == "both"
                _uiState.update {
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
                _uiState.update {
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
            _uiState.update { it.copy(serverError = "Please enter a server address") }
            return
        }
        _uiState.update {
            it.copy(
                customServerInput = normalized,
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
                _uiState.update {
                    it.copy(
                        serverUrl = normalized,
                        customServerInput = normalized,
                        showServerConfig = false,
                        isTestingServer = false,
                        serverError = null,
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
                    _uiState.update {
                        it.copy(
                            serverUrl = normalized,
                            customServerInput = normalized,
                            showServerConfig = false,
                            isTestingServer = false,
                            serverError = null
                        )
                    }
                } else {
                    val targetLabel = normalized.ifBlank { "server" }
                    _uiState.update {
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
            _uiState.update {
                it.copy(
                    showServerConfig = true,
                    errorMessage = "Please connect to your Orbit Money server first."
                )
            }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username and password are required.") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(normalizedUrl)
            val result = client.login(state.username.trim(), state.password)
            if (result.isSuccess) {
                val me = result.getOrThrow()
                val userLabel = me.displayName ?: me.username ?: state.username.trim()
                repository.markAuthenticated(normalizedUrl, userLabel)
                _uiState.update { it.copy(isSubmitting = false, isAuthenticated = true) }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Invalid username or password"
                _uiState.update { it.copy(isSubmitting = false, errorMessage = msg) }
            }
        }
    }

    private fun prepareOidcLogin() {
        val normalizedUrl = ServerUrlNormalizer.normalize(_uiState.value.serverUrl)
        if (!isWebPlatform && normalizedUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    showServerConfig = true,
                    errorMessage = "Please connect to your Orbit Money server first."
                )
            }
            return
        }
        storage.saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, normalizedUrl.ifEmpty { "/" })
        _uiState.update { it.copy(serverUrl = normalizedUrl, errorMessage = null) }
        scope.launch {
            repository.prepareForOidcLogin(normalizedUrl)
        }
    }

    private fun submitSampleDataLogin() {
        val normalizedUrl = ServerUrlNormalizer.normalize(_uiState.value.serverUrl)
        _uiState.update { it.copy(isSampleLoading = true, errorMessage = null) }
        scope.launch {
            val deviceId = getOrCreateSampleDeviceId()
            val client = customApiClient ?: repository.createApiClient(normalizedUrl)
            val result = client.loginWithSampleData(deviceId)
            if (result.isSuccess) {
                val me = result.getOrThrow()
                val userLabel = me.displayName ?: "Sample Data"
                repository.markAuthenticated(normalizedUrl, userLabel)
                _uiState.update { it.copy(isSampleLoading = false, isAuthenticated = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isSampleLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not load sample data"
                    )
                }
            }
        }
    }

    private fun switchToOfflineLocalMode() {
        scope.launch {
            repository.setStorageMode(StorageMode.LOCAL)
            _uiState.update { it.copy(switchedToLocalMode = true) }
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
