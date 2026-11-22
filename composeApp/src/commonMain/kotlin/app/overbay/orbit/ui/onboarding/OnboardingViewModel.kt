package app.overbay.orbit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.ServerUrlNormalizer
import app.overbay.orbit.domain.DemoSeedGenerator
import app.overbay.orbit.ui.login.LocalSeedChoice
import app.overbay.orbit.ui.login.LoginUiState
import app.overbay.orbit.util.OidcCallbackBridge
import app.overbay.orbit.util.PlatformRuntime
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class OnboardingViewModel(
    internal val repository: OrbitRepository,
    private val customApiClient: OrbitApiClient? = null,
    private val storage: PlatformStorage = PlatformStorage(),
    private val isWebPlatform: Boolean = PlatformRuntime.isWeb,
    scope: CoroutineScope? = null
) : ViewModel() {

    internal val scope: CoroutineScope = scope ?: (viewModelScope + Dispatchers.Default)

    internal val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val persistMutex = Mutex()
    private var awaitingSelfHostedAuth = false
    private var wasExchangingOidc = false
    internal var lastFailedLocalSimpleFinInput: String? = null

    init {
        this.scope.launch {
            combine(
                repository.snapshot,
                repository.simpleFinStatus
            ) { snap, sfStatus ->
                snap to sfStatus
            }.collect { (snap, sfStatus) ->
                val prefs = snap.preferences
                _uiState.update { cur ->
                    cur.copy(
                        preferences = prefs,
                        existingAccounts = snap.accounts.filter { !it.isArchived },
                        householdMembers = snap.members,
                        simpleFinConnected = cur.simpleFinConnected || sfStatus.connected
                    )
                }
            }
        }

        this.scope.launch {
            combine(
                repository.isAuthenticated,
                repository.storageMode,
                OidcCallbackBridge.isExchanging,
                OidcCallbackBridge.exchangeError
            ) { authed, mode, exchanging, err ->
                Quad(authed, mode, exchanging, err)
            }.collect { (authed, mode, exchanging, err) ->
                if (awaitingSelfHostedAuth && authed && mode == StorageMode.SELF_HOSTED) {
                    awaitingSelfHostedAuth = false
                    wasExchangingOidc = false
                    handleSelfHostedAuthComplete()
                } else if (err != null) {
                    wasExchangingOidc = false
                    _uiState.update { cur ->
                        cur.copy(isSubmittingAuth = exchanging, authError = err)
                    }
                } else if (exchanging) {
                    wasExchangingOidc = true
                    _uiState.update { cur -> cur.copy(isSubmittingAuth = true) }
                } else if (wasExchangingOidc) {
                    wasExchangingOidc = false
                    _uiState.update { cur -> cur.copy(isSubmittingAuth = false) }
                }
            }
        }
    }

    fun beginSession(isReplay: Boolean) {
        awaitingSelfHostedAuth = false
        wasExchangingOidc = false
        lastFailedLocalSimpleFinInput = null
        OidcCallbackBridge.setExchangeError(null)
        val resumingOidc = !isReplay && storage.loadString(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY) == "1"
        if (resumingOidc || isReplay) {
            storage.delete(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY)
        }

        val prefs = repository.preferences.value
        val snap = repository.snapshot.value
        val profile = snap.mhaTaxProfile
        val preset = RetirementScenarioPreset.entries.firstOrNull {
            it.name.equals(prefs.dashboardRetirementPreset, ignoreCase = true)
        } ?: RetirementScenarioPreset.BALANCED

        val initialFeatures = if (isReplay) {
            OnboardingStepResolver.ALL_OPTIONAL_FEATURES
                .map { it.routeName }
                .filter { route ->
                    if (route == "mha") prefs.mhaTrackerEnabled else route !in prefs.hiddenRouteNames
                }
                .toSet()
        } else {
            OnboardingStepResolver.defaultEnabledFeatures()
        }

        val normalizedUrl = ServerUrlNormalizer.normalize(prefs.selfHostedServerUrl)
        val initialTaxMode = if (isReplay && prefs.mhaTrackerEnabled) profile.trackingMode else TaxTrackingMode.GENERAL
        val alreadyAuthedSelfHosted = repository.isAuthenticated.value && repository.storageMode.value == StorageMode.SELF_HOSTED
        val hasExistingWorkspace = snap.accounts.any { !it.isArchived }
        val initialStep = when {
            isReplay -> OnboardingStep.APPEARANCE
            resumingOidc && alreadyAuthedSelfHosted ->
                if (hasExistingWorkspace) OnboardingStep.ALL_SET else OnboardingStep.DATA_CONNECT
            resumingOidc -> OnboardingStep.SERVER_AUTH
            else -> OnboardingStep.WELCOME
        }
        if (resumingOidc && !alreadyAuthedSelfHosted) {
            awaitingSelfHostedAuth = true
        }

        _uiState.value = OnboardingUiState(
            currentStep = initialStep,
            isReplay = isReplay,
            transitionDirection = 1,
            isCompleted = false,
            preferences = prefs,
            selectedStorageMode = when {
                isReplay -> prefs.storageMode
                resumingOidc -> StorageMode.SELF_HOSTED
                isWebPlatform -> StorageMode.SELF_HOSTED
                else -> StorageMode.LOCAL
            },
            serverUrl = normalizedUrl,
            serverInput = normalizedUrl.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX },
            existingServerDetected = resumingOidc && alreadyAuthedSelfHosted && hasExistingWorkspace,
            isSubmittingAuth = resumingOidc && !alreadyAuthedSelfHosted && OidcCallbackBridge.isExchanging.value,
            enteredAct2 = isReplay,
            enabledFeatures = initialFeatures,
            existingAccounts = snap.accounts.filter { !it.isArchived },
            householdMembers = snap.members,
            simpleFinConnected = repository.simpleFinStatus.value.connected ||
                (prefs.storageMode == StorageMode.LOCAL && !prefs.simpleFinToken.isNullOrBlank()),
            retirementAge = prefs.dashboardRetirementAge.coerceIn(40, 75),
            retirementPreset = preset,
            taxTrackingMode = initialTaxMode,
            taxFilingStatus = profile.filingStatus,
            taxStateCode = profile.state,
            taxCapCents = profile.designatedCapCents,
            taxFmrvCents = profile.fairMarketRentalValueCents
        )

        if (resumingOidc && !alreadyAuthedSelfHosted) {
            loadServerAuthConfigForResume(normalizedUrl)
        }
    }

    fun processIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Next -> advanceStep()
            OnboardingIntent.Back -> goBackStep()
            OnboardingIntent.SkipToDashboard -> finishSetup(commitAct2 = false)
            OnboardingIntent.ExploreSampleDataQuickStart -> startLocalSampleQuickly()

            is OnboardingIntent.SetThemeMode -> updatePrefs { it.copy(themeMode = intent.mode) }
            is OnboardingIntent.SetAmoledDark -> updatePrefs { it.copy(amoledDarkMode = intent.enabled) }
            is OnboardingIntent.SetThemePreset -> updatePrefs { it.copy(themePreset = intent.preset) }
            is OnboardingIntent.SetDynamicColor -> updatePrefs { it.copy(dynamicColor = intent.enabled) }

            is OnboardingIntent.SelectStorageMode -> selectStorageMode(intent.mode)
            is OnboardingIntent.SelectLocalSeed -> _uiState.update { it.copy(selectedLocalSeed = intent.seed, authError = null) }
            is OnboardingIntent.SetBackupFile -> _uiState.update {
                it.copy(backupFileName = intent.fileName, backupJsonContent = intent.content, authError = null)
            }
            OnboardingIntent.ConfirmLocalWorkspace -> confirmLocalWorkspace()

            is OnboardingIntent.UpdateServerInput -> _uiState.update {
                it.copy(serverInput = ServerUrlNormalizer.sanitizeInput(intent.url), serverError = null)
            }
            is OnboardingIntent.ToggleDockerHelp -> _uiState.update { it.copy(dockerHelpExpanded = intent.expanded) }
            OnboardingIntent.TestAndConnectServer -> testAndConnectServer()
            is OnboardingIntent.UpdateUsername -> _uiState.update { it.copy(username = intent.username, authError = null) }
            is OnboardingIntent.UpdatePassword -> _uiState.update { it.copy(password = intent.password, authError = null) }
            OnboardingIntent.TogglePasswordVisibility -> _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            OnboardingIntent.SubmitPasswordLogin -> submitPasswordLogin()
            OnboardingIntent.PrepareOidcLogin -> prepareOidcLogin()
            OnboardingIntent.SubmitServerSampleData -> submitServerSampleData()

            is OnboardingIntent.ToggleCsvPaste -> _uiState.update { it.copy(csvPasteExpanded = intent.expanded) }
            is OnboardingIntent.LoadCsvText -> inspectAndPreviewCsvImpl(intent.fileName, intent.rawCsv, overrideMap = null)
            is OnboardingIntent.ToggleCsvMapper -> _uiState.update { it.copy(csvMapperExpanded = intent.expanded) }
            is OnboardingIntent.UpdateCsvMapping -> inspectAndPreviewCsvImpl(_uiState.value.csvFileName, _uiState.value.csvRawText, overrideMap = intent.mapping)
            OnboardingIntent.ConfirmCsvImport -> commitCsvImportImpl()
            is OnboardingIntent.UpdateSimpleFinToken -> _uiState.update { it.copy(simpleFinToken = intent.token, simpleFinError = null) }
            is OnboardingIntent.UpdateSimpleFinCutoverDate -> _uiState.update { it.copy(simpleFinCutoverDate = intent.isoDate) }
            is OnboardingIntent.ToggleSimpleFinAdvancedDate -> _uiState.update { it.copy(showSimpleFinAdvancedDate = intent.expanded) }
            OnboardingIntent.ConnectSimpleFin -> connectSimpleFinImpl()

            OnboardingIntent.EnterAct2Personalize -> {
                _uiState.update { it.copy(enteredAct2 = true, transitionDirection = 1, currentStep = OnboardingStep.FEATURES) }
            }
            is OnboardingIntent.ToggleFeature -> toggleFeature(intent.routeName)
            is OnboardingIntent.UpdateManualAccountName -> _uiState.update {
                it.copy(manualAccountDraft = it.manualAccountDraft.copy(name = intent.name, error = null))
            }
            is OnboardingIntent.UpdateManualAccountType -> _uiState.update {
                it.copy(manualAccountDraft = it.manualAccountDraft.copy(type = intent.type, error = null))
            }
            is OnboardingIntent.UpdateManualAccountBalance -> _uiState.update {
                it.copy(manualAccountDraft = it.manualAccountDraft.copy(balanceCents = intent.cents))
            }
            is OnboardingIntent.UpdateManualAccountEstValue -> _uiState.update {
                it.copy(manualAccountDraft = it.manualAccountDraft.copy(estimatedValueCents = intent.cents))
            }
            OnboardingIntent.SaveManualAccount -> saveManualAccountImpl()

            is OnboardingIntent.UpdateMemberName -> _uiState.update {
                it.copy(memberDraft = it.memberDraft.copy(name = intent.name, error = null))
            }
            is OnboardingIntent.UpdateMemberRole -> _uiState.update {
                it.copy(memberDraft = it.memberDraft.copy(role = intent.role))
            }
            is OnboardingIntent.UpdateMemberBirthYear -> _uiState.update {
                it.copy(memberDraft = it.memberDraft.copy(birthYearText = intent.yearText.filter(Char::isDigit).take(4)))
            }
            is OnboardingIntent.UpdateMemberIncome -> _uiState.update {
                it.copy(memberDraft = it.memberDraft.copy(annualGrossCents = intent.cents))
            }
            is OnboardingIntent.UpdateMemberRetirementPct -> _uiState.update {
                val cleaned = intent.pctText.filter { c -> c.isDigit() || c == '.' }.take(5)
                it.copy(memberDraft = it.memberDraft.copy(retirementContribPercentText = cleaned))
            }
            OnboardingIntent.SaveHouseholdMember -> saveHouseholdMemberImpl()
            is OnboardingIntent.RemoveHouseholdMember -> scope.launch { repository.deleteHouseholdMember(intent.id) }

            is OnboardingIntent.UpdateRetirementAge -> _uiState.update { it.copy(retirementAge = intent.age.coerceIn(40, 75)) }
            is OnboardingIntent.UpdateRetirementPreset -> _uiState.update { it.copy(retirementPreset = intent.preset) }

            is OnboardingIntent.UpdateTaxTrackingMode -> _uiState.update { it.copy(taxTrackingMode = intent.mode) }
            is OnboardingIntent.UpdateTaxFilingStatus -> _uiState.update { it.copy(taxFilingStatus = intent.status) }
            is OnboardingIntent.UpdateTaxState -> _uiState.update { it.copy(taxStateCode = intent.stateCode.take(2).uppercase()) }
            is OnboardingIntent.UpdateTaxCap -> _uiState.update { it.copy(taxCapCents = intent.cents) }
            is OnboardingIntent.UpdateTaxFmrv -> _uiState.update { it.copy(taxFmrvCents = intent.cents) }

            is OnboardingIntent.SetBiometricLock -> updatePrefs { it.copy(biometricLockEnabled = intent.enabled) }
            OnboardingIntent.FinishOnboarding -> finishSetup(commitAct2 = true)
            OnboardingIntent.ClearCompletedFlag -> _uiState.update { it.copy(isCompleted = false) }
        }
    }

    private fun advanceStep() {
        val cur = _uiState.value
        val steps = cur.resolvedSteps
        val idx = steps.indexOf(cur.currentStep)
        scope.launch {
            persistMutex.withLock {
                if (cur.currentStep == OnboardingStep.FEATURES) persistFeatureChoicesImpl(cur)
                if (cur.currentStep == OnboardingStep.RETIREMENT) persistRetirementChoicesImpl(cur)
                if (cur.currentStep == OnboardingStep.TAX_DEDUCTIONS) persistTaxChoicesImpl(cur)
            }
        }
        val nextStep = steps.getOrNull(idx + 1) ?: return
        _uiState.update { it.copy(currentStep = nextStep, transitionDirection = 1) }
        if (cur.currentStep == OnboardingStep.STORAGE_MODE &&
            nextStep == OnboardingStep.SERVER_URL &&
            isWebPlatform &&
            cur.serverUrl.isBlank()
        ) {
            testAndConnectServer()
        }
    }

    private fun goBackStep() {
        val cur = _uiState.value
        val steps = cur.resolvedSteps
        val idx = steps.indexOf(cur.currentStep)
        if (idx <= 0) return
        _uiState.update { it.copy(currentStep = steps[idx - 1], transitionDirection = -1) }
    }

    private fun updatePrefs(transform: (OrbitPreferences) -> OrbitPreferences) {
        scope.launch {
            persistMutex.withLock {
                repository.updatePreferences(transform(repository.preferences.value))
            }
        }
    }

    private fun selectStorageMode(mode: StorageMode) {
        OidcCallbackBridge.setExchangeError(null)
        if (mode == StorageMode.LOCAL) {
            storage.delete(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY)
        }
        _uiState.update {
            it.copy(
                selectedStorageMode = mode,
                authError = null,
                serverError = null
            )
        }
    }

    private fun startLocalSampleQuickly() {
        storage.delete(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY)
        _uiState.update { it.copy(isSubmittingAuth = true, authError = null) }
        scope.launch {
            persistMutex.withLock {
                repository.setStorageMode(StorageMode.LOCAL)
                repository.loadDemoData(DemoSeedGenerator.generateDemoSnapshot())
                repository.updatePreferences(
                    repository.preferences.value.copy(storageMode = StorageMode.LOCAL, onboardingCompleted = true)
                )
            }
            _uiState.update { it.copy(isSubmittingAuth = false, isCompleted = true) }
        }
    }

    private fun confirmLocalWorkspace() {
        val state = _uiState.value
        if (state.selectedLocalSeed == LocalSeedChoice.RESTORE_FILE && state.backupJsonContent.isNullOrBlank()) {
            _uiState.update { it.copy(authError = "Please select or paste a backup JSON file first.") }
            return
        }
        storage.delete(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY)
        _uiState.update { it.copy(isSubmittingAuth = true, authError = null) }
        scope.launch {
            persistMutex.withLock {
                repository.setStorageMode(StorageMode.LOCAL)
                when (state.selectedLocalSeed) {
                    LocalSeedChoice.DEMO -> repository.loadDemoData(DemoSeedGenerator.generateDemoSnapshot())
                    LocalSeedChoice.RESTORE_FILE -> {
                        val res = repository.restoreBackupJson(state.backupJsonContent.orEmpty())
                        if (res.isFailure) {
                            _uiState.update {
                                it.copy(isSubmittingAuth = false, authError = res.exceptionOrNull()?.message ?: "Could not restore backup.")
                            }
                            return@launch
                        }
                        repository.updatePreferences(
                            repository.preferences.value.copy(
                                storageMode = StorageMode.LOCAL,
                                onboardingCompleted = false
                            )
                        )
                    }
                    LocalSeedChoice.FRESH -> Unit
                }
            }
            val nextStep = if (state.selectedLocalSeed == LocalSeedChoice.FRESH) {
                OnboardingStep.DATA_CONNECT
            } else {
                OnboardingStep.ALL_SET
            }
            _uiState.update {
                it.copy(isSubmittingAuth = false, currentStep = nextStep, transitionDirection = 1)
            }
        }
    }

    private fun testAndConnectServer() {
        val normalized = ServerUrlNormalizer.normalize(_uiState.value.serverInput)
        if (!isWebPlatform && normalized.isBlank()) {
            _uiState.update { it.copy(serverError = "Please enter your server address.") }
            return
        }
        _uiState.update {
            it.copy(serverInput = normalized.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX }, isTestingServer = true, serverError = null)
        }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(normalized)
            val cfgRes = client.getAuthConfig()
            if (cfgRes.isSuccess) {
                val cfg = cfgRes.getOrThrow()
                val localEnabled = cfg.localEnabled || (cfg.authProvider == "local" && !cfg.oidcEnabled)
                val oidcEnabled = cfg.oidcEnabled || cfg.authProvider == "oidc" || cfg.authProvider == "both"
                repository.updatePreferences(repository.preferences.value.copy(selfHostedServerUrl = normalized))
                _uiState.update {
                    it.copy(
                        serverUrl = normalized,
                        serverInput = normalized.ifBlank { LoginUiState.DEFAULT_SERVER_PREFIX },
                        isTestingServer = false,
                        localAuthEnabled = localEnabled,
                        oidcEnabled = oidcEnabled,
                        oidcLoginUrl = cfg.oidcLoginUrl?.ifBlank { "/api/auth/oidc/login" } ?: "/api/auth/oidc/login",
                        oidcLoginLabel = cfg.oidcLoginLabel?.ifBlank { "Log in with OIDC" } ?: "Log in with OIDC",
                        sampleDataEnabled = cfg.sampleDataEnabled,
                        currentStep = OnboardingStep.SERVER_AUTH,
                        transitionDirection = 1
                    )
                }
            } else if (client.checkHealth().getOrDefault(false)) {
                repository.updatePreferences(repository.preferences.value.copy(selfHostedServerUrl = normalized))
                _uiState.update {
                    it.copy(serverUrl = normalized, isTestingServer = false, currentStep = OnboardingStep.SERVER_AUTH, transitionDirection = 1)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isTestingServer = false,
                        serverError = "Couldn't reach ${normalized.ifBlank { "that server" }}. Check the address and make sure the container is running."
                    )
                }
            }
        }
    }

    private fun loadServerAuthConfigForResume(serverUrl: String) {
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(serverUrl)
            val cfg = client.getAuthConfig().getOrNull() ?: return@launch
            val localEnabled = cfg.localEnabled || (cfg.authProvider == "local" && !cfg.oidcEnabled)
            val oidcEnabled = cfg.oidcEnabled || cfg.authProvider == "oidc" || cfg.authProvider == "both"
            _uiState.update { cur ->
                cur.copy(
                    localAuthEnabled = localEnabled,
                    oidcEnabled = oidcEnabled,
                    oidcLoginUrl = cfg.oidcLoginUrl?.ifBlank { "/api/auth/oidc/login" } ?: "/api/auth/oidc/login",
                    oidcLoginLabel = cfg.oidcLoginLabel?.ifBlank { "Log in with OIDC" } ?: "Log in with OIDC",
                    sampleDataEnabled = cfg.sampleDataEnabled
                )
            }
        }
    }

    private fun submitPasswordLogin() {
        val state = _uiState.value
        val url = ServerUrlNormalizer.normalize(state.serverUrl)
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(authError = "Username and password are required.") }
            return
        }
        OidcCallbackBridge.setExchangeError(null)
        _uiState.update { it.copy(isSubmittingAuth = true, authError = null) }
        scope.launch {
            val client = customApiClient ?: repository.createApiClient(url)
            val res = client.login(state.username.trim(), state.password)
            if (res.isSuccess) {
                val me = res.getOrThrow()
                val label = me.displayName ?: me.username ?: state.username.trim()
                repository.markAuthenticated(url, label, me.authMethod ?: "password", completeOnboarding = false)
                handleSelfHostedAuthComplete()
            } else {
                _uiState.update {
                    it.copy(isSubmittingAuth = false, authError = res.exceptionOrNull()?.message ?: "Invalid username or password.")
                }
            }
        }
    }

    private fun prepareOidcLogin() {
        val url = ServerUrlNormalizer.normalize(_uiState.value.serverUrl)
        awaitingSelfHostedAuth = true
        OidcCallbackBridge.setExchangeError(null)
        _uiState.update { it.copy(authError = null) }
        runCatching {
            storage.saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, url.ifEmpty { "/" })
            storage.saveString(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY, "1")
        }
        scope.launch {
            runCatching { repository.prepareForOidcLogin(url) }
        }
    }

    private fun submitServerSampleData() {
        val url = ServerUrlNormalizer.normalize(_uiState.value.serverUrl)
        OidcCallbackBridge.setExchangeError(null)
        _uiState.update { it.copy(isSampleLoading = true, authError = null) }
        scope.launch {
            val deviceId = getOrCreateSampleDeviceId()
            val client = customApiClient ?: repository.createApiClient(url)
            val res = client.loginWithSampleData(deviceId)
            if (res.isSuccess) {
                val me = res.getOrThrow()
                repository.markAuthenticated(url, me.displayName ?: "Sample Data", me.authMethod ?: "sample", completeOnboarding = false)
                handleSelfHostedAuthComplete()
            } else {
                _uiState.update { it.copy(isSampleLoading = false, authError = res.exceptionOrNull()?.message ?: "Could not load sample data.") }
            }
        }
    }

    private fun handleSelfHostedAuthComplete() {
        val hasExistingWorkspace = repository.accounts.value.any { !it.isArchived }
        _uiState.update {
            it.copy(
                isSubmittingAuth = false,
                isSampleLoading = false,
                existingServerDetected = hasExistingWorkspace,
                currentStep = if (hasExistingWorkspace) OnboardingStep.ALL_SET else OnboardingStep.DATA_CONNECT,
                transitionDirection = 1,
                authError = null
            )
        }
    }

    private fun toggleFeature(routeName: String) {
        _uiState.update { cur ->
            val updated = if (routeName in cur.enabledFeatures) cur.enabledFeatures - routeName else cur.enabledFeatures + routeName
            cur.copy(enabledFeatures = updated)
        }
    }

    private fun finishSetup(commitAct2: Boolean) {
        val state = _uiState.value
        storage.delete(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY)
        scope.launch {
            persistMutex.withLock {
                if (commitAct2 && state.enteredAct2) {
                    persistFeatureChoicesImpl(state)
                    if ("retirement_calculator" in state.enabledFeatures) persistRetirementChoicesImpl(state)
                    if ("mha" in state.enabledFeatures) persistTaxChoicesImpl(state)
                }
                if (!state.isReplay) {
                    repository.updatePreferences(repository.preferences.value.copy(onboardingCompleted = true))
                }
            }
            _uiState.update { it.copy(isCompleted = true) }
        }
    }

    private fun getOrCreateSampleDeviceId(): String {
        val existing = storage.loadString(LoginViewModelSampleKey)
        if (!existing.isNullOrBlank() && existing.length >= 16) return existing
        val chars = "0123456789abcdef"
        val generated = buildString(48) { repeat(48) { append(chars[Random.nextInt(chars.length)]) } }
        storage.saveString(LoginViewModelSampleKey, generated)
        return generated
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        private const val LoginViewModelSampleKey = "orbit_money_sample_device_id"
    }
}
