package app.overbay.orbit.ui.settings

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun testSimpleFinToken_persistsToken() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.setSimpleFinToken("sfin_claim_token_123456")
        testScheduler.advanceUntilIdle()

        assertEquals("sfin_claim_token_123456", viewModel.uiState.value.preferences.simpleFinToken)
        assertEquals("sfin_claim_token_123456", repository.preferences.value.simpleFinToken)
    }

    @Test
    fun testExportAndRestoreBackup_roundtrip() = runTest(testDispatcher) {
        val testAccount = Account(id = 99L, name = "Backup Test Checking", type = AccountType.CHECKING, currentBalanceCents = 543210L)
        repository.saveAccount(testAccount)
        testScheduler.advanceUntilIdle()

        val json = viewModel.exportBackup()
        assertTrue(json.contains("Backup Test Checking"))
        assertTrue(viewModel.uiState.value.showBackupExportDialog)

        // Clear and restore
        viewModel.resetToDemoData()
        testScheduler.advanceUntilIdle()
        assertTrue(repository.accounts.value.isEmpty())

        viewModel.restoreBackup(json)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.accounts.value.size)
        assertEquals("Backup Test Checking", repository.accounts.value.first().name)
    }

    @Test
    fun testResetToDemoData_clearsWorkspace() = runTest(testDispatcher) {
        val testAccount = Account(id = 1L, name = "Temp", type = AccountType.CASH, currentBalanceCents = 1000L)
        repository.saveAccount(testAccount)
        testScheduler.advanceUntilIdle()

        viewModel.resetToDemoData()
        testScheduler.advanceUntilIdle()

        assertTrue(repository.accounts.value.isEmpty())
        assertFalse(viewModel.uiState.value.showResetConfirmation)
    }
}
