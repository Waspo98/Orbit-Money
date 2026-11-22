package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.SimpleFinDirectClient
import app.overbay.orbit.domain.CsvColumnMapping
import app.overbay.orbit.ui.login.LocalSeedChoice
import app.overbay.orbit.util.OidcCallbackBridge
import app.overbay.orbit.util.PlatformStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun createVm(scope: TestScope): Pair<OrbitRepository, OnboardingViewModel> {
        val storage = PlatformStorage()
        storage.delete("orbit_workspace_snapshot.json")
        storage.delete("orbit_startup_preferences.json")
        val repo = OrbitRepository(storage = storage, scope = scope.backgroundScope)
        val vm = OnboardingViewModel(
            repository = repo,
            storage = storage,
            isWebPlatform = false,
            scope = scope.backgroundScope
        )
        return repo to vm
    }

    @Test
    fun csvImportAutoSuggestsNextDaySimpleFinCutoverAndAct2PersistsSettings() = runTest(UnconfinedTestDispatcher()) {
        val (repo, vm) = createVm(this)
        vm.beginSession(isReplay = false)

        // Appearance persists live
        vm.processIntent(OnboardingIntent.SetThemePreset(OrbitThemePreset.OCEAN_SLATE))
        assertEquals(OrbitThemePreset.OCEAN_SLATE, repo.preferences.value.themePreset)

        // Local fresh path — selecting storage mode must not auto-advance until Next is tapped
        vm.processIntent(OnboardingIntent.Next) // -> APPEARANCE
        vm.processIntent(OnboardingIntent.Next) // -> STORAGE_MODE
        assertEquals(OnboardingStep.STORAGE_MODE, vm.uiState.value.currentStep)
        vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
        assertEquals(OnboardingStep.STORAGE_MODE, vm.uiState.value.currentStep)
        vm.processIntent(OnboardingIntent.Next) // -> LOCAL_WORKSPACE
        assertEquals(OnboardingStep.LOCAL_WORKSPACE, vm.uiState.value.currentStep)
        vm.processIntent(OnboardingIntent.ConfirmLocalWorkspace)
        assertEquals(OnboardingStep.DATA_CONNECT, vm.uiState.value.currentStep)

        // Mint CSV import -> auto-suggests latestDate + 1 day for SimpleFIN cutover
        val mintCsv = """
            Date,Description,Original Description,Amount,Transaction Type,Category,Account Name
            11/10/2026,Coffee Shop,COFFEE SHOP,5.50,debit,Dining,Main Checking
            11/18/2026,Paycheck,EMPLOYER,2500.00,credit,Income,Main Checking
        """.trimIndent()
        vm.processIntent(OnboardingIntent.LoadCsvText("mint.csv", mintCsv))
        vm.processIntent(OnboardingIntent.ConfirmCsvImport)

        assertEquals(2, vm.uiState.value.csvImportedCount)
        assertEquals("2026-11-18", vm.uiState.value.csvLatestDateIso)
        assertEquals("2026-11-19", vm.uiState.value.csvSuggestedCutoverDate)
        assertEquals("2026-11-19", vm.uiState.value.simpleFinCutoverDate)

        // Enter Act 2, enable Tax Deductions, disable Credit Card Perks
        vm.processIntent(OnboardingIntent.EnterAct2Personalize)
        vm.processIntent(OnboardingIntent.ToggleFeature("mha"))
        vm.processIntent(OnboardingIntent.ToggleFeature("card_perks"))
        vm.processIntent(OnboardingIntent.Next) // -> ADDITIONAL_ACCOUNTS

        // Add manual account
        vm.processIntent(OnboardingIntent.UpdateManualAccountName("Vanguard 401k"))
        vm.processIntent(OnboardingIntent.UpdateManualAccountType(AccountType.INVESTMENT))
        vm.processIntent(OnboardingIntent.UpdateManualAccountBalance(125_000_00L))
        vm.processIntent(OnboardingIntent.SaveManualAccount)
        assertTrue(repo.accounts.value.any { it.name == "Vanguard 401k" && it.currentBalanceCents == 125_000_00L })

        // Add household member with optional birth year + income + contribution
        vm.processIntent(OnboardingIntent.Next) // -> HOUSEHOLD
        vm.processIntent(OnboardingIntent.UpdateMemberName("Alex"))
        vm.processIntent(OnboardingIntent.UpdateMemberBirthYear("1992"))
        vm.processIntent(OnboardingIntent.UpdateMemberIncome(95_000_00L))
        vm.processIntent(OnboardingIntent.UpdateMemberRetirementPct("12"))
        vm.processIntent(OnboardingIntent.SaveHouseholdMember)
        val savedMember = repo.snapshot.value.members.single()
        assertEquals("Alex", savedMember.name)
        assertEquals("1992-01-01", savedMember.birthDate)
        assertEquals(95_000_00L, savedMember.grossIncomeAnnualCents)
        assertEquals(12.0, savedMember.employeeContributionPercent)

        // Configure retirement & tax deductions (General mode)
        vm.processIntent(OnboardingIntent.Next) // -> RETIREMENT
        vm.processIntent(OnboardingIntent.UpdateRetirementAge(60))
        vm.processIntent(OnboardingIntent.UpdateRetirementPreset(RetirementScenarioPreset.AGGRESSIVE))
        vm.processIntent(OnboardingIntent.Next) // -> TAX_DEDUCTIONS
        vm.processIntent(OnboardingIntent.UpdateTaxTrackingMode(TaxTrackingMode.GENERAL))
        vm.processIntent(OnboardingIntent.UpdateTaxFilingStatus(FilingStatus.MARRIED_JOINT))
        vm.processIntent(OnboardingIntent.UpdateTaxState("CO"))
        vm.processIntent(OnboardingIntent.Next) // -> ALL_SET
        vm.processIntent(OnboardingIntent.FinishOnboarding)

        assertTrue(vm.uiState.value.isCompleted)
        assertTrue(repo.preferences.value.onboardingCompleted)
        assertTrue(repo.preferences.value.mhaTrackerEnabled)
        assertTrue("card_perks" in repo.preferences.value.hiddenRouteNames)
        assertEquals(60, repo.preferences.value.dashboardRetirementAge)
        assertEquals("AGGRESSIVE", repo.preferences.value.dashboardRetirementPreset)
        assertEquals(TaxTrackingMode.GENERAL, repo.snapshot.value.mhaTaxProfile.trackingMode)
        assertEquals("CO", repo.snapshot.value.mhaTaxProfile.state)
        assertEquals("household", repo.snapshot.value.mhaTaxProfile.mode)

        // Only the top 5 default dashboard cards remain visible even when Goals, MHA, and Housing are enabled
        val visibleWidgetIds = repo.preferences.value.dashboardWidgets
            .filter { it.isVisible }
            .sortedBy { it.sortOrder }
            .map { it.widgetId }
        assertEquals(
            listOf("accounts", "recent_transactions", "categorize_recent", "subscriptions", "retirement"),
            visibleWidgetIds
        )
        assertFalse(repo.preferences.value.dashboardWidgets.first { it.widgetId == "goals_progress" }.isVisible)
        assertFalse(repo.preferences.value.dashboardWidgets.first { it.widgetId == "mha" }.isVisible)
        assertFalse(repo.preferences.value.dashboardWidgets.first { it.widgetId == "mortgage" }.isVisible)
    }

    @Test
    fun selectingStorageModeDoesNotAutoAdvanceUntilNextIsPressed() = runTest(UnconfinedTestDispatcher()) {
        val (_, vm) = createVm(this)
        vm.beginSession(isReplay = false)
        vm.processIntent(OnboardingIntent.Next) // -> APPEARANCE
        vm.processIntent(OnboardingIntent.Next) // -> STORAGE_MODE
        assertEquals(OnboardingStep.STORAGE_MODE, vm.uiState.value.currentStep)

        vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
        assertEquals(StorageMode.SELF_HOSTED, vm.uiState.value.selectedStorageMode)
        assertEquals(OnboardingStep.STORAGE_MODE, vm.uiState.value.currentStep)

        vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
        assertEquals(StorageMode.LOCAL, vm.uiState.value.selectedStorageMode)
        assertEquals(OnboardingStep.STORAGE_MODE, vm.uiState.value.currentStep)

        vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
        vm.processIntent(OnboardingIntent.Next)
        assertEquals(OnboardingStep.SERVER_URL, vm.uiState.value.currentStep)
    }

    @Test
    fun replayModePrefillsExistingSettingsAndDoesNotClearOnboardingCompleted() = runTest(UnconfinedTestDispatcher()) {
        val (repo, vm) = createVm(this)
        repo.updatePreferences(
            repo.preferences.value.copy(
                onboardingCompleted = true,
                dashboardRetirementAge = 58,
                mhaTrackerEnabled = true,
                hiddenRouteNames = listOf("housing_calculator")
            )
        )

        vm.beginSession(isReplay = true)
        assertEquals(OnboardingStep.APPEARANCE, vm.uiState.value.currentStep)
        assertEquals(58, vm.uiState.value.retirementAge)
        assertTrue("mha" in vm.uiState.value.enabledFeatures)
        assertFalse("housing_calculator" in vm.uiState.value.enabledFeatures)
        assertTrue(repo.preferences.value.onboardingCompleted)
    }

    @Test
    fun concurrentPlatformStorageWritesDuringOidcLaunchDoNotThrow() = runTest {
        withContext(Dispatchers.Default) {
            val jobs = (1..4).map { worker ->
                async {
                    val s = PlatformStorage()
                    repeat(100) { iter ->
                        s.saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, "https://orbit-$worker-$iter.example.com")
                    }
                }
            }
            jobs.awaitAll()
        }
        val finalValue = PlatformStorage().loadString(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
        assertTrue(finalValue?.startsWith("https://orbit-") == true)
        PlatformStorage().delete(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun prepareOidcLoginFromFreshLocalInstallStaysOnServerAuthAndDetectsExistingServerOnCallback() =
        runTest(UnconfinedTestDispatcher()) {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val storage = PlatformStorage().apply { clear() }
            val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val engine = object : HttpClientEngineBase("onboarding-oidc-mock") {
                override val dispatcher: CoroutineDispatcher = dispatcher
                override val config: HttpClientEngineConfig = HttpClientEngineConfig()
                override suspend fun execute(data: HttpRequestData): HttpResponseData {
                    val body = when (data.url.encodedPath) {
                        "/api/auth/config" -> """{"authProvider":"both","localEnabled":true,"oidcEnabled":true,"oidcLoginUrl":"/api/auth/oidc/login","oidcLoginLabel":"Sign in with Authentik"}"""
                        "/api/auth/oidc/native-exchange" -> """{"authenticated":true,"username":"neal","displayName":"Neal","authProvider":"both","authMethod":"oidc"}"""
                        "/api/auth/me" -> """{"authenticated":true,"username":"neal","displayName":"Neal","authProvider":"both","authMethod":"oidc","household":{"id":1,"name":"Home","role":"owner"}}"""
                        "/api/accounts" -> """{"items":[{"id":10,"name":"Main Checking","type":"checking","current_balance":1500.0}]}"""
                        "/api/transactions" -> """{"items":[],"total":0,"page":1,"totalPages":1}"""
                        "/api/categories" -> """{"items":[]}"""
                        "/api/simplefin/status" -> """{"connected":true,"syncEnabled":true}"""
                        else -> """{"items":[]}"""
                    }
                    return HttpResponseData(
                        statusCode = HttpStatusCode.OK,
                        requestTime = GMTDate(),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        version = HttpProtocolVersion.HTTP_1_1,
                        body = ByteReadChannel(body),
                        callContext = callContext()
                    )
                }
            }
            val apiClient = OrbitApiClient(
                baseUrlProvider = { "https://orbit.example.com" },
                storage = storage,
                customHttpClient = HttpClient(engine) { install(ContentNegotiation) { json(wireJson) } }
            )
            val repo = OrbitRepository(storage = storage, customApiClient = apiClient, scope = backgroundScope)
            repo.initialize() // Fresh local install: storageMode=LOCAL, isAuthenticated=true
            val vm = OnboardingViewModel(
                repository = repo,
                customApiClient = apiClient,
                storage = storage,
                isWebPlatform = false,
                scope = backgroundScope
            )
            vm.beginSession(isReplay = false)
            vm.processIntent(OnboardingIntent.Next) // -> APPEARANCE
            vm.processIntent(OnboardingIntent.Next) // -> STORAGE_MODE
            vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
            vm.processIntent(OnboardingIntent.Next) // -> SERVER_URL
            vm.processIntent(OnboardingIntent.UpdateServerInput("https://orbit.example.com"))
            vm.processIntent(OnboardingIntent.TestAndConnectServer)
            assertEquals(OnboardingStep.SERVER_AUTH, vm.uiState.value.currentStep)

            // Pressing OIDC login must keep us on SERVER_AUTH while awaiting callback (never false-auth!)
            vm.processIntent(OnboardingIntent.PrepareOidcLogin)
            assertEquals(OnboardingStep.SERVER_AUTH, vm.uiState.value.currentStep)
            assertFalse(repo.isAuthenticated.value)

            // OIDC callback completes -> existing accounts are loaded before onboarding advances to ALL_SET
            val res = repo.completeOidcNativeExchange("test-exchange-code")
            assertTrue(res.isSuccess)
            assertTrue(vm.uiState.value.existingServerDetected)
            assertEquals(OnboardingStep.ALL_SET, vm.uiState.value.currentStep)
            assertFalse(repo.preferences.value.onboardingCompleted)
            storage.clear()
        }

    @OptIn(InternalAPI::class)
    @Test
    fun selfHostedSimpleFinDefaultsCutoverDateAndMhaTogglePersistsToServer() =
        runTest(UnconfinedTestDispatcher()) {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val storage = PlatformStorage().apply { clear() }
            val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            var capturedSimpleFinSetupBody = ""
            var capturedMhaSettingsBody = ""
            val engine = object : HttpClientEngineBase("onboarding-selfhosted-mock") {
                override val dispatcher: CoroutineDispatcher = dispatcher
                override val config: HttpClientEngineConfig = HttpClientEngineConfig()
                override suspend fun execute(data: HttpRequestData): HttpResponseData {
                    val reqBody = when (val c = data.body) {
                        is OutgoingContent.ByteArrayContent -> c.bytes().decodeToString()
                        else -> ""
                    }
                    when (data.url.encodedPath) {
                        "/api/simplefin/setup" -> capturedSimpleFinSetupBody = reqBody
                        "/api/mha/settings" -> capturedMhaSettingsBody = reqBody
                    }
                    val body = when (data.url.encodedPath) {
                        "/api/auth/config" -> """{"authProvider":"local","localEnabled":true,"oidcEnabled":false}"""
                        "/api/auth/login" -> """{"authenticated":true,"username":"neal"}"""
                        "/api/auth/me" -> """{"authenticated":true,"username":"neal","authProvider":"local","household":{"id":1,"name":"Home","role":"owner"}}"""
                        "/api/simplefin/setup" -> """{"connected":true,"syncEnabled":true,"cutoverDate":"2026-11-21"}"""
                        "/api/simplefin/status" -> """{"connected":true,"syncEnabled":true,"cutoverDate":"2026-11-21"}"""
                        "/api/simplefin/sync" -> """{"status":"ok"}"""
                        "/api/mha/settings" -> """{"enabled":true}"""
                        "/api/mha" -> """{"enabled":true}"""
                        "/api/transactions" -> """{"items":[],"total":0,"page":1,"totalPages":1}"""
                        else -> """{"items":[]}"""
                    }
                    return HttpResponseData(
                        statusCode = HttpStatusCode.OK,
                        requestTime = GMTDate(),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        version = HttpProtocolVersion.HTTP_1_1,
                        body = ByteReadChannel(body),
                        callContext = callContext()
                    )
                }
            }
            val apiClient = OrbitApiClient(
                baseUrlProvider = { "https://orbit.example.com" },
                storage = storage,
                customHttpClient = HttpClient(engine) { install(ContentNegotiation) { json(wireJson) } }
            )
            val repo = OrbitRepository(storage = storage, customApiClient = apiClient, scope = backgroundScope)
            repo.initialize()
            val vm = OnboardingViewModel(repo, customApiClient = apiClient, storage = storage, isWebPlatform = false, scope = backgroundScope)
            vm.beginSession(isReplay = false)
            vm.processIntent(OnboardingIntent.Next) // APPEARANCE
            vm.processIntent(OnboardingIntent.Next) // STORAGE_MODE
            vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
            vm.processIntent(OnboardingIntent.Next) // SERVER_URL
            vm.processIntent(OnboardingIntent.UpdateServerInput("https://orbit.example.com"))
            vm.processIntent(OnboardingIntent.TestAndConnectServer)
            vm.processIntent(OnboardingIntent.UpdateUsername("neal"))
            vm.processIntent(OnboardingIntent.UpdatePassword("secret"))
            vm.processIntent(OnboardingIntent.SubmitPasswordLogin)
            assertEquals(OnboardingStep.DATA_CONNECT, vm.uiState.value.currentStep)

            vm.processIntent(OnboardingIntent.UpdateSimpleFinToken("SAMPLE_TOKEN"))
            vm.processIntent(OnboardingIntent.ConnectSimpleFin)
            assertTrue(capturedSimpleFinSetupBody.contains("\"cutoverDate\":\""))
            assertTrue(vm.uiState.value.simpleFinConnected)

            vm.processIntent(OnboardingIntent.Next) // PERSONALIZE_FORK
            vm.processIntent(OnboardingIntent.EnterAct2Personalize) // FEATURES
            vm.processIntent(OnboardingIntent.ToggleFeature("mha"))
            vm.processIntent(OnboardingIntent.Next) // ADDITIONAL_ACCOUNTS
            assertTrue(capturedMhaSettingsBody.contains("\"enabled\":true"))
            storage.clear()
        }

    @Test
    fun localLiabilityAccountsStoreNegativeBalancesAndRestoreForcesLocalMode() =
        runTest(UnconfinedTestDispatcher()) {
            val (repo, vm) = createVm(this)
            vm.beginSession(isReplay = false)
            vm.processIntent(OnboardingIntent.Next) // APPEARANCE
            vm.processIntent(OnboardingIntent.Next) // STORAGE_MODE
            vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
            vm.processIntent(OnboardingIntent.Next) // LOCAL_WORKSPACE
            vm.processIntent(OnboardingIntent.SelectLocalSeed(LocalSeedChoice.FRESH))
            vm.processIntent(OnboardingIntent.ConfirmLocalWorkspace)
            vm.processIntent(OnboardingIntent.Next) // PERSONALIZE_FORK
            vm.processIntent(OnboardingIntent.EnterAct2Personalize) // FEATURES
            vm.processIntent(OnboardingIntent.Next) // ADDITIONAL_ACCOUNTS

            vm.processIntent(OnboardingIntent.UpdateManualAccountName("Home Mortgage"))
            vm.processIntent(OnboardingIntent.UpdateManualAccountType(AccountType.MORTGAGE))
            vm.processIntent(OnboardingIntent.UpdateManualAccountBalance(320_000_00L))
            vm.processIntent(OnboardingIntent.UpdateManualAccountEstValue(450_000_00L))
            vm.processIntent(OnboardingIntent.SaveManualAccount)

            val mortgage = repo.accounts.value.single { it.name == "Home Mortgage" }
            assertEquals(-320_000_00L, mortgage.currentBalanceCents)
            assertEquals(450_000_00L, mortgage.estimatedValueCents)

            // Restoring a backup exported from Self-Hosted under Local workspace must keep LOCAL mode
            val selfHostedSnapshotJson = testJson.encodeToString(
                OrbitWorkspaceSnapshot.serializer(),
                OrbitWorkspaceSnapshot.empty().copy(
                    preferences = OrbitPreferences(
                        storageMode = StorageMode.SELF_HOSTED,
                        selfHostedServerUrl = "https://remote.example.com",
                        onboardingCompleted = true
                    )
                )
            )
            vm.beginSession(isReplay = false)
            vm.processIntent(OnboardingIntent.Next) // APPEARANCE
            vm.processIntent(OnboardingIntent.Next) // STORAGE_MODE
            vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
            vm.processIntent(OnboardingIntent.Next) // LOCAL_WORKSPACE
            vm.processIntent(OnboardingIntent.SelectLocalSeed(LocalSeedChoice.RESTORE_FILE))
            vm.processIntent(OnboardingIntent.SetBackupFile("backup.json", selfHostedSnapshotJson))
            vm.processIntent(OnboardingIntent.ConfirmLocalWorkspace)

            assertEquals(StorageMode.LOCAL, repo.storageMode.value)
            assertEquals(StorageMode.LOCAL, repo.preferences.value.storageMode)
            assertEquals(OnboardingStep.ALL_SET, vm.uiState.value.currentStep)
        }

    @OptIn(InternalAPI::class)
    @Test
    fun webOidcRedirectDuringOnboardingResumesAtAllSetInsteadOfWelcome() =
        runTest(UnconfinedTestDispatcher()) {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val storage = PlatformStorage().apply { clear() }
            storage.saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, "/")
            storage.saveString(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY, "1")
            val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val engine = object : HttpClientEngineBase("onboarding-web-oidc-mock") {
                override val dispatcher: CoroutineDispatcher = dispatcher
                override val config: HttpClientEngineConfig = HttpClientEngineConfig()
                override suspend fun execute(data: HttpRequestData): HttpResponseData {
                    val body = when (data.url.encodedPath) {
                        "/api/auth/me" -> """{"authenticated":true,"username":"neal","displayName":"Neal","authProvider":"oidc","authMethod":"oidc","household":{"id":1,"name":"Home","role":"owner"}}"""
                        "/api/accounts" -> """{"items":[{"id":1,"name":"Checking","type":"checking","current_balance":1000.0}]}"""
                        "/api/transactions" -> """{"items":[],"total":0,"page":1,"totalPages":1}"""
                        "/api/simplefin/status" -> """{"connected":false,"syncEnabled":false}"""
                        else -> """{"items":[]}"""
                    }
                    return HttpResponseData(
                        statusCode = HttpStatusCode.OK,
                        requestTime = GMTDate(),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        version = HttpProtocolVersion.HTTP_1_1,
                        body = ByteReadChannel(body),
                        callContext = callContext()
                    )
                }
            }
            val apiClient = OrbitApiClient(
                baseUrlProvider = { "" },
                storage = storage,
                customHttpClient = HttpClient(engine) { install(ContentNegotiation) { json(wireJson) } }
            )
            val repo = OrbitRepository(storage = storage, customApiClient = apiClient, scope = backgroundScope)
            repo.initialize()
            val vm = OnboardingViewModel(repo, customApiClient = apiClient, storage = storage, isWebPlatform = true, scope = backgroundScope)
            vm.beginSession(isReplay = false)

            assertEquals(OnboardingStep.ALL_SET, vm.uiState.value.currentStep)
            assertTrue(vm.uiState.value.existingServerDetected)
            storage.clear()
        }

    @OptIn(InternalAPI::class, ExperimentalEncodingApi::class)
    @Test
    fun failedLocalSimpleFinSyncDoesNotMaskErrorAsConnected() =
        runTest(UnconfinedTestDispatcher()) {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val storage = PlatformStorage().apply { clear() }
            val engine = object : HttpClientEngineBase("simplefin-fail-mock") {
                override val dispatcher: CoroutineDispatcher = dispatcher
                override val config: HttpClientEngineConfig = HttpClientEngineConfig()
                override suspend fun execute(data: HttpRequestData): HttpResponseData {
                    val isClaim = data.url.encodedPath.endsWith("/claim")
                    val body = if (isClaim) "https://u:p@bridge.example.com/simplefin" else "Internal Error"
                    val status = if (isClaim) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                    return HttpResponseData(
                        statusCode = status,
                        requestTime = GMTDate(),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                        version = HttpProtocolVersion.HTTP_1_1,
                        body = ByteReadChannel(body),
                        callContext = callContext()
                    )
                }
            }
            val sfClient = SimpleFinDirectClient(HttpClient(engine))
            val repo = OrbitRepository(storage = storage, customSimpleFinClient = sfClient, scope = backgroundScope)
            repo.initialize()
            val vm = OnboardingViewModel(repo, storage = storage, isWebPlatform = false, scope = backgroundScope)
            vm.beginSession(isReplay = false)
            vm.processIntent(OnboardingIntent.Next) // APPEARANCE
            vm.processIntent(OnboardingIntent.Next) // STORAGE_MODE
            vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
            vm.processIntent(OnboardingIntent.Next) // LOCAL_WORKSPACE
            vm.processIntent(OnboardingIntent.ConfirmLocalWorkspace) // -> DATA_CONNECT

            val token = Base64.encode("https://bridge.example.com/claim".encodeToByteArray())
            vm.processIntent(OnboardingIntent.UpdateSimpleFinToken(token))
            vm.processIntent(OnboardingIntent.ConnectSimpleFin)

            assertFalse(vm.uiState.value.simpleFinConnected)
            assertTrue(!vm.uiState.value.simpleFinError.isNullOrBlank())
            storage.clear()
        }

    @Test
    fun unmappedCsvHeadersKeepInspectionAndExpandColumnMapperForRecovery() = runTest(UnconfinedTestDispatcher()) {
        val (_, vm) = createVm(this)
        vm.beginSession(isReplay = false)

        val csv = "ColA,ColB,ColC\n2026-10-01,Cafe,-4.50"
        vm.processIntent(OnboardingIntent.LoadCsvText("custom.csv", csv))

        val failedState = vm.uiState.value
        assertNotNull(failedState.csvInspection)
        assertNotNull(failedState.csvMapping)
        assertTrue(failedState.csvMapperExpanded)
        assertNotNull(failedState.csvError)
        assertNull(failedState.csvPreview)

        vm.processIntent(
            OnboardingIntent.UpdateCsvMapping(
                CsvColumnMapping(dateColumn = "ColA", merchantColumn = "ColB", amountColumn = "ColC")
            )
        )
        val recovered = vm.uiState.value
        assertEquals(1, recovered.csvPreview?.transactions?.size)
        assertNull(recovered.csvError)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun cancellingOidcAndFallingBackToPasswordLoginAdvancesCleanlyOnce() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = PlatformStorage().apply { clear() }
        val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val engine = object : HttpClientEngineBase("onboarding-oidc-fallback-mock") {
            override val dispatcher: CoroutineDispatcher = dispatcher
            override val config: HttpClientEngineConfig = HttpClientEngineConfig()
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                val body = when (data.url.encodedPath) {
                    "/api/auth/config" -> """{"authProvider":"both","localEnabled":true,"oidcEnabled":true}"""
                    "/api/auth/login" -> """{"authenticated":true,"username":"neal"}"""
                    "/api/auth/me" -> """{"authenticated":true,"username":"neal","authProvider":"local","household":{"id":1,"name":"Home","role":"owner"}}"""
                    "/api/transactions" -> """{"items":[],"total":0,"page":1,"totalPages":1}"""
                    else -> """{"items":[]}"""
                }
                return HttpResponseData(
                    statusCode = HttpStatusCode.OK,
                    requestTime = GMTDate(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    version = HttpProtocolVersion.HTTP_1_1,
                    body = ByteReadChannel(body),
                    callContext = callContext()
                )
            }
        }
        val apiClient = OrbitApiClient({ "https://orbit.example.com" }, storage, HttpClient(engine) { install(ContentNegotiation) { json(wireJson) } })
        val repo = OrbitRepository(storage = storage, customApiClient = apiClient, scope = backgroundScope)
        repo.initialize()
        val vm = OnboardingViewModel(repo, customApiClient = apiClient, storage = storage, isWebPlatform = false, scope = backgroundScope)
        vm.beginSession(isReplay = false)
        vm.processIntent(OnboardingIntent.Next) // APPEARANCE
        vm.processIntent(OnboardingIntent.Next) // STORAGE_MODE
        vm.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
        vm.processIntent(OnboardingIntent.Next) // SERVER_URL
        vm.processIntent(OnboardingIntent.UpdateServerInput("https://orbit.example.com"))
        vm.processIntent(OnboardingIntent.TestAndConnectServer)
        vm.processIntent(OnboardingIntent.PrepareOidcLogin)
        assertEquals("1", storage.loadString(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY))

        vm.processIntent(OnboardingIntent.UpdateUsername("neal"))
        vm.processIntent(OnboardingIntent.UpdatePassword("secret"))
        vm.processIntent(OnboardingIntent.SubmitPasswordLogin)

        assertEquals(OnboardingStep.DATA_CONNECT, vm.uiState.value.currentStep)
        assertNull(storage.loadString(OidcCallbackBridge.PENDING_ONBOARDING_OIDC_KEY))
        storage.clear()
    }
}
