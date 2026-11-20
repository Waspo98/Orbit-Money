package app.overbay.orbit.ui.settings

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.LocalSnapshotEngine
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.OrbitApiClient
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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = SettingsViewModel(repository, testScope)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_loadsPreferences() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(StorageMode.LOCAL, state.storageMode)
        assertEquals(OrbitThemePreset.ORBIT_EMERALD, state.preferences.themePreset)
        assertEquals(OrbitThemeMode.SYSTEM, state.preferences.themeMode)
        assertTrue(state.preferences.hapticsEnabled)
        assertFalse(state.preferences.biometricLockEnabled)
        assertEquals(SettingsDialogState.None, state.activeDialog)
    }

    @Test
    fun testSetStorageMode_updatesPreferenceAndState() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setStorageMode(StorageMode.SELF_HOSTED)
        viewModel.setServerUrl("https://my-orbit.server.local")
        testScheduler.advanceUntilIdle()

        assertEquals(StorageMode.SELF_HOSTED, viewModel.uiState.value.storageMode)
        assertEquals("https://my-orbit.server.local", viewModel.uiState.value.preferences.selfHostedServerUrl)
    }

    @Test
    fun testThemePreferences_updatesPresetAndMode() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setThemePreset(OrbitThemePreset.ROYAL_INDIGO)
        viewModel.setThemeMode(OrbitThemeMode.DARK)
        viewModel.setDynamicColor(false)
        testScheduler.advanceUntilIdle()

        val prefs = viewModel.uiState.value.preferences
        assertEquals(OrbitThemePreset.ROYAL_INDIGO, prefs.themePreset)
        assertEquals(OrbitThemeMode.DARK, prefs.themeMode)
        assertFalse(prefs.dynamicColor)
    }

    @Test
    fun testBiometricsAndHaptics_togglePreferences() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setBiometricLock(true)
        viewModel.setHapticsEnabled(false)
        viewModel.setMhaTrackerEnabled(true)
        testScheduler.advanceUntilIdle()

        val prefs = viewModel.uiState.value.preferences
        assertTrue(prefs.biometricLockEnabled)
        assertFalse(prefs.hapticsEnabled)
        assertTrue(prefs.mhaTrackerEnabled)
    }

    @Test
    fun testBiometricTimeoutSeconds_persistsPreference() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setBiometricTimeoutSeconds(60)
        testScheduler.advanceUntilIdle()

        assertEquals(60, viewModel.uiState.value.preferences.biometricTimeoutSeconds)
        assertEquals(60, repository.preferences.value.biometricTimeoutSeconds)
    }

    @Test
    fun testOpenSectionAndSearch_updatesStateAndClearsStatus() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.openSection(SettingsSection.BANK_FEEDS)
        viewModel.setSearchQuery("amoled")
        viewModel.setSearchActive(true)
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsSection.BANK_FEEDS, viewModel.uiState.value.activeSection)
        assertEquals("amoled", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.isSearchActive)

        viewModel.setupSimpleFinBridge("", null)
        assertFalse(viewModel.uiState.value.statusMessage.isNullOrBlank())

        viewModel.clearStatusMessage()
        assertNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun testSimpleFinToken_persistsToken() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setSimpleFinToken("sfin_claim_token_123456")
        testScheduler.advanceUntilIdle()

        assertEquals("sfin_claim_token_123456", viewModel.uiState.value.preferences.simpleFinToken)
        assertEquals("sfin_claim_token_123456", repository.preferences.value.simpleFinToken)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testSetupSimpleFinBridge_failureDoesNotMarkConnected() = runTest(testDispatcher) {
        val remoteStorage = PlatformStorage().apply { clear() }
        LocalSnapshotEngine(remoteStorage).saveStartupPreferences(
            OrbitPreferences(
                storageMode = StorageMode.SELF_HOSTED,
                selfHostedServerUrl = "https://orbit.example.com",
                onboardingCompleted = true
            )
        )
        val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val engine = object : HttpClientEngineBase("failing-simplefin-engine") {
            override val dispatcher: CoroutineDispatcher = testDispatcher
            override val config: HttpClientEngineConfig = HttpClientEngineConfig()
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                val path = data.url.encodedPath
                val (status, json) = if (path == "/api/simplefin/setup") {
                    HttpStatusCode.BadRequest to """{"error":"Invalid SimpleFIN claim token"}"""
                } else {
                    HttpStatusCode.OK to """{"items":[]}"""
                }
                return HttpResponseData(
                    statusCode = status,
                    requestTime = GMTDate(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    version = HttpProtocolVersion.HTTP_1_1,
                    body = ByteReadChannel(json),
                    callContext = callContext()
                )
            }
        }
        val apiClient = OrbitApiClient(
            baseUrlProvider = { "https://orbit.example.com" },
            storage = remoteStorage,
            customHttpClient = HttpClient(engine) { install(ContentNegotiation) { json(wireJson) } }
        )
        val remoteScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val remoteRepo = OrbitRepository(remoteStorage, customApiClient = apiClient, scope = remoteScope)
        remoteRepo.initialize()
        val remoteViewModel = SettingsViewModel(remoteRepo, remoteScope)
        testScheduler.advanceUntilIdle()

        remoteViewModel.setupSimpleFinBridge("invalid_token", "2026-01-01")
        testScheduler.advanceUntilIdle()

        assertNull(remoteViewModel.uiState.value.preferences.simpleFinToken)
        assertNull(remoteRepo.preferences.value.simpleFinToken)
        assertFalse(remoteViewModel.uiState.value.isConnectingSimpleFin)
        assertTrue(remoteViewModel.uiState.value.statusMessage?.contains("failed", ignoreCase = true) == true)

        remoteScope.cancel()
        remoteStorage.clear()
    }

    @Test
    fun testLoadSampleDemoData_clearsDialogAndPopulatesWorkspace() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.showDialog(SettingsDialogState.ConfirmLoadSampleData)
        assertEquals(SettingsDialogState.ConfirmLoadSampleData, viewModel.uiState.value.activeDialog)

        viewModel.loadSampleDemoData()
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsDialogState.None, viewModel.uiState.value.activeDialog)
        assertTrue(repository.accounts.value.isNotEmpty())
        assertEquals("Loaded sample household workspace", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun testExportAndRestoreBackup_previewAndRoundtrip() = runTest(testDispatcher) {
        val testAccount = Account(
            id = 99L,
            name = "Backup Test Checking",
            type = AccountType.CHECKING,
            currentBalanceCents = 543210L
        )
        repository.saveAccount(testAccount)
        testScheduler.advanceUntilIdle()

        val json = viewModel.openBackupExportJsonDialog()
        assertTrue(json.contains("Backup Test Checking"))
        assertTrue(viewModel.uiState.value.activeDialog is SettingsDialogState.BackupExport)

        viewModel.dismissDialog()
        assertEquals(SettingsDialogState.None, viewModel.uiState.value.activeDialog)

        viewModel.resetToDemoData()
        testScheduler.advanceUntilIdle()
        assertTrue(repository.accounts.value.isEmpty())

        viewModel.previewBackupForRestore(json)
        val previewDialog = viewModel.uiState.value.activeDialog
        assertTrue(previewDialog is SettingsDialogState.RestorePreview)
        assertEquals(1, previewDialog.stats.accountCount)

        viewModel.confirmRestoreBackup()
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsDialogState.None, viewModel.uiState.value.activeDialog)
        assertEquals(1, repository.accounts.value.size)
        assertEquals("Backup Test Checking", repository.accounts.value.first().name)
    }

    @Test
    fun testResetToDemoData_clearsWorkspaceAndDialog() = runTest(testDispatcher) {
        val testAccount = Account(id = 1L, name = "Temp", type = AccountType.CASH, currentBalanceCents = 1000L)
        repository.saveAccount(testAccount)
        testScheduler.advanceUntilIdle()

        viewModel.showDialog(SettingsDialogState.ConfirmResetWorkspace)
        viewModel.resetToDemoData()
        testScheduler.advanceUntilIdle()

        assertTrue(repository.accounts.value.isEmpty())
        assertEquals(SettingsDialogState.None, viewModel.uiState.value.activeDialog)
    }
}
