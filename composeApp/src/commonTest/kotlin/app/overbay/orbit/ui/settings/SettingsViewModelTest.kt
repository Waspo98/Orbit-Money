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
import app.overbay.orbit.data.network.SimpleFinDirectClient
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

    @OptIn(InternalAPI::class)
    private fun createJsonMockHttpClient(
        handler: (String) -> Pair<HttpStatusCode, String>
    ): HttpClient {
        val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val engine = object : HttpClientEngineBase("settings-mock-engine") {
            override val dispatcher: CoroutineDispatcher = testDispatcher
            override val config: HttpClientEngineConfig = HttpClientEngineConfig()
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                val (status, json) = handler(data.url.encodedPath)
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
        return HttpClient(engine) { install(ContentNegotiation) { json(wireJson) } }
    }

    @Test
    fun testSetupSimpleFinBridge_localModePersistsTokenAndDrafts() = runTest(testDispatcher) {
        val localStorage = PlatformStorage().apply { clear() }
        val localScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val mockClient = createJsonMockHttpClient { HttpStatusCode.OK to """{"errors":[],"accounts":[]}""" }
            val localRepo = OrbitRepository(
                storage = localStorage,
                scope = localScope,
                customSimpleFinClient = SimpleFinDirectClient(mockClient)
            )
            localRepo.initialize()
            val localVm = SettingsViewModel(localRepo, localScope)
            testScheduler.advanceUntilIdle()

            localVm.setupSimpleFinBridge("  https://user:pass@bridge.simplefin.org/simplefin  ", "2026-02-01")
            testScheduler.advanceUntilIdle()

            assertEquals("https://user:pass@bridge.simplefin.org/simplefin", localVm.uiState.value.preferences.simpleFinToken)
            assertEquals("https://user:pass@bridge.simplefin.org/simplefin", localRepo.preferences.value.simpleFinToken)
            assertEquals("2026-02-01", localVm.uiState.value.preferences.simpleFinCutoverDate)
            assertEquals("https://user:pass@bridge.simplefin.org/simplefin", localVm.uiState.value.simpleFinTokenDraft)
            assertFalse(localVm.uiState.value.isConnectingSimpleFin)
        } finally {
            localScope.cancel()
            localStorage.clear()
        }
    }

    @Test
    fun testSettingsSectionSearch_matchesTitlesAndKeywords() {
        val amoledMatches = SettingsSection.search("amoled")
        assertTrue(SettingsSection.APPEARANCE in amoledMatches)
        assertFalse(SettingsSection.BANK_FEEDS in amoledMatches)

        val hapticsMatches = SettingsSection.search("haptics")
        assertEquals(listOf(SettingsSection.APPEARANCE), hapticsMatches)
        assertEquals("Look and Feel", SettingsSection.APPEARANCE.title)

        val appLockMatches = SettingsSection.search("app lock")
        assertEquals(listOf(SettingsSection.SYNC_AND_SHARING), appLockMatches)

        val featuresMatches = SettingsSection.search("features")
        assertEquals(listOf(SettingsSection.WORKSPACE_NAVIGATION), featuresMatches)
        assertEquals("Turn On/Off App Features", SettingsSection.WORKSPACE_NAVIGATION.title)

        val simpleFinMatches = SettingsSection.search("simplefin")
        assertEquals(listOf(SettingsSection.BANK_FEEDS), simpleFinMatches)
        assertEquals("SimpleFIN Sync", SettingsSection.BANK_FEEDS.title)
        assertEquals(SettingsSection.entries, SettingsSection.search("   "))
    }

    @Test
    fun testMhaTrackerEnabled_clearsHiddenMhaRoute() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        repository.updatePreferences(repository.preferences.value.copy(hiddenRouteNames = listOf("mha", "goals")))
        testScheduler.advanceUntilIdle()

        viewModel.setMhaTrackerEnabled(true)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.preferences.mhaTrackerEnabled)
        assertFalse("mha" in viewModel.uiState.value.preferences.hiddenRouteNames)
        assertTrue("goals" in viewModel.uiState.value.preferences.hiddenRouteNames)

        viewModel.setMhaTrackerEnabled(false)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.preferences.mhaTrackerEnabled)
    }

    @Test
    fun testBackupRestorePaste_invalidJsonSetsInlineSheetError() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.openRestorePasteDialog()
        viewModel.updateRestorePasteDraft("{invalid-json")
        viewModel.previewBackupForRestore("{invalid-json")

        val dialog = viewModel.uiState.value.activeDialog
        assertTrue(dialog is SettingsDialogState.BackupRestorePaste)
        assertFalse(dialog.errorMessage.isNullOrBlank())

        viewModel.updateRestorePasteDraft("{}")
        val cleared = viewModel.uiState.value.activeDialog as SettingsDialogState.BackupRestorePaste
        assertNull(cleared.errorMessage)
    }

    private suspend fun withSelfHostedTestHarness(
        testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        handler: (String) -> Pair<HttpStatusCode, String>,
        block: suspend (OrbitRepository, SettingsViewModel) -> Unit
    ) {
        val remoteStorage = PlatformStorage().apply { clear() }
        LocalSnapshotEngine(remoteStorage).saveStartupPreferences(
            OrbitPreferences(
                storageMode = StorageMode.SELF_HOSTED,
                selfHostedServerUrl = "https://orbit.example.com",
                onboardingCompleted = true
            )
        )
        val apiClient = OrbitApiClient(
            baseUrlProvider = { "https://orbit.example.com" },
            storage = remoteStorage,
            customHttpClient = createJsonMockHttpClient(handler)
        )
        val remoteScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val remoteRepo = OrbitRepository(remoteStorage, customApiClient = apiClient, scope = remoteScope)
            remoteRepo.initialize()
            val remoteViewModel = SettingsViewModel(remoteRepo, remoteScope)
            testScheduler.advanceUntilIdle()
            block(remoteRepo, remoteViewModel)
        } finally {
            remoteScope.cancel()
            remoteStorage.clear()
        }
    }

    @Test
    fun testSetupSimpleFinBridge_failureDoesNotMarkConnected() = runTest(testDispatcher) {
        withSelfHostedTestHarness(
            testScheduler = testScheduler,
            handler = { path ->
                if (path == "/api/simplefin/setup") {
                    HttpStatusCode.BadRequest to """{"error":"Invalid SimpleFIN claim token"}"""
                } else {
                    HttpStatusCode.OK to """{"items":[]}"""
                }
            }
        ) { remoteRepo, remoteViewModel ->
            remoteViewModel.setupSimpleFinBridge("invalid_token", "2026-01-01")
            testScheduler.advanceUntilIdle()

            assertNull(remoteViewModel.uiState.value.preferences.simpleFinToken)
            assertNull(remoteRepo.preferences.value.simpleFinToken)
            assertFalse(remoteViewModel.uiState.value.isConnectingSimpleFin)
            assertTrue(remoteViewModel.uiState.value.statusMessage?.contains("failed", ignoreCase = true) == true)
        }
    }

    @Test
    fun testRestoreAndPartnerInviteFailures_surfaceInlineDialogErrors() = runTest(testDispatcher) {
        withSelfHostedTestHarness(
            testScheduler = testScheduler,
            handler = { path ->
                when (path) {
                    "/api/data/orbit-restore" -> HttpStatusCode.BadRequest to """{"error":"Backup rejected"}"""
                    "/api/household-sharing/shares" -> HttpStatusCode.BadRequest to """{"error":"Invite limit reached"}"""
                    else -> HttpStatusCode.OK to """{"items":[]}"""
                }
            }
        ) { _, remoteViewModel ->
            val validBackupJson = repository.exportBackupJson()
            remoteViewModel.previewBackupForRestore(validBackupJson)
            remoteViewModel.confirmRestoreBackup()
            testScheduler.advanceUntilIdle()

            val restoreDialog = remoteViewModel.uiState.value.activeDialog as SettingsDialogState.RestorePreview
            assertFalse(restoreDialog.isRestoring)
            assertTrue(restoreDialog.errorMessage?.contains("Restore failed", ignoreCase = true) == true)

            remoteViewModel.showDialog(SettingsDialogState.PartnerInvite())
            remoteViewModel.inviteHouseholdPartner("partner@example.com", "member", "write")
            testScheduler.advanceUntilIdle()

            val inviteDialog = remoteViewModel.uiState.value.activeDialog as SettingsDialogState.PartnerInvite
            assertFalse(inviteDialog.isSending)
            assertTrue(inviteDialog.errorMessage?.contains("Invite failed", ignoreCase = true) == true)

            remoteViewModel.logout()
            testScheduler.advanceUntilIdle()
            assertTrue(remoteViewModel.uiState.value.simpleFinLogs.isEmpty())
            assertNull(remoteViewModel.uiState.value.householdSharing)
        }
    }

    @Test
    fun testServerUrlDraft_debouncesAndPersistsAcrossReapplyRules() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.updateServerUrlDraft("  https://orbit.example.org  ")
        assertEquals("  https://orbit.example.org  ", viewModel.uiState.value.serverUrlDraft)

        testScheduler.advanceUntilIdle()
        assertEquals("https://orbit.example.org", repository.preferences.value.selfHostedServerUrl)

        viewModel.reapplyAllRules()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.statusMessage?.contains("Reapplied rules", ignoreCase = true) == true)
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

    @Test
    fun testSelfHostedSimpleFinAuthAndSharingOptimisticUpdate() = runTest(testDispatcher) {
        withSelfHostedTestHarness(
            testScheduler = testScheduler,
            handler = { path ->
                when (path) {
                    "/api/auth/me" -> HttpStatusCode.OK to """{"authenticated":true,"username":"neal@example.com","displayName":"Neal","authProvider":"both","authMethod":"oidc","household":{"id":1,"name":"Home","role":"owner"}}"""
                    "/api/simplefin/status" -> HttpStatusCode.OK to """{"connected":true,"syncEnabled":true,"cutoverDate":"2026-01-15"}"""
                    "/api/household-sharing" -> HttpStatusCode.OK to """{"householdId":1,"householdName":"Home","currentUserId":1,"currentUserRole":"owner","currentUserAccessLevel":"write","canManageSharing":true,"canRemoveUsers":true,"users":[{"id":2,"displayName":"Partner","role":"member","accessLevel":"write"}],"shares":[{"id":10,"invited_email":"guest@example.com","role":"member","access_level":"write","status":"pending"}]}"""
                    "/api/household-sharing/users/2/access" -> HttpStatusCode.OK to """{"success":true}"""
                    "/api/household-sharing/shares/10/access" -> HttpStatusCode.OK to """{"success":true}"""
                    else -> HttpStatusCode.OK to """{"items":[]}"""
                }
            }
        ) { _, remoteViewModel ->
            assertEquals("oidc", remoteViewModel.uiState.value.authMethod)
            assertEquals("Neal", remoteViewModel.uiState.value.currentUser)

            remoteViewModel.openSection(SettingsSection.BANK_FEEDS)
            testScheduler.advanceUntilIdle()
            assertTrue(remoteViewModel.uiState.value.simpleFinStatus.connected)
            assertEquals("2026-01-15", remoteViewModel.uiState.value.simpleFinStatus.cutoverDate)
            assertNull(remoteViewModel.uiState.value.preferences.simpleFinToken)

            remoteViewModel.openSection(SettingsSection.SYNC_AND_SHARING)
            testScheduler.advanceUntilIdle()
            assertFalse(remoteViewModel.uiState.value.isLoadingSharing)
            assertEquals("write", remoteViewModel.uiState.value.householdSharing?.users?.firstOrNull()?.accessLevel)

            remoteViewModel.updateHouseholdMemberAccess(2L, "read")
            assertFalse(remoteViewModel.uiState.value.isLoadingSharing)
            assertEquals("read", remoteViewModel.uiState.value.householdSharing?.users?.firstOrNull()?.accessLevel)

            remoteViewModel.updatePartnerAccess(10L, "read")
            assertFalse(remoteViewModel.uiState.value.isLoadingSharing)
            assertEquals("read", remoteViewModel.uiState.value.householdSharing?.shares?.firstOrNull()?.accessLevel)
            testScheduler.advanceUntilIdle()
        }
    }
}
