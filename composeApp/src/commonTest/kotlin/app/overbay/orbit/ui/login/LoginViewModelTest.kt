package app.overbay.orbit.ui.login

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.util.OidcCallbackBridge
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_nativeWithoutServerUrlStartsAtModeSelectWithHttpsPrefill() {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        val state = viewModel.uiState.value
        assertEquals(LoginStep.MODE_SELECT, state.step)
        assertFalse(state.showServerConfig)
        assertEquals("https://", state.customServerInput)
        assertEquals("", state.serverUrl)
        assertTrue(state.localEnabled)
    }

    @Test
    fun testInitialState_returningUserOrWebStartsAtServerAuth() = runTest(testDispatcher) {
        val webViewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = true,
            scope = testScope
        )
        assertEquals(LoginStep.SERVER_AUTH, webViewModel.uiState.value.step)

        repository.updatePreferences(
            repository.preferences.value.copy(selfHostedServerUrl = "https://orbit.example.com")
        )
        val returningViewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        assertEquals(LoginStep.SERVER_AUTH, returningViewModel.uiState.value.step)
        assertEquals("https://orbit.example.com", returningViewModel.uiState.value.serverUrl)
    }

    @Test
    fun testSelectSelfHostedMode_morphsToServerUrlWithHttpsPrefillAndDeduplicatesPaste() {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        viewModel.processIntent(LoginIntent.SelectSelfHostedMode)
        assertEquals(LoginStep.SERVER_URL, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.showServerConfig)
        assertEquals("https://", viewModel.uiState.value.customServerInput)

        // Clicking Next on bare "https://" should not advance to SERVER_AUTH
        viewModel.processIntent(LoginIntent.ConnectAndSaveServer)
        assertEquals(LoginStep.SERVER_URL, viewModel.uiState.value.step)
        assertEquals("Please enter a server address", viewModel.uiState.value.serverError)

        // Pasting full https:// URL after prefilled "https://" deduplicates scheme
        viewModel.processIntent(LoginIntent.UpdateServerInput("https://https://orbit.example.com"))
        assertEquals("https://orbit.example.com", viewModel.uiState.value.customServerInput)
        assertNull(viewModel.uiState.value.serverError)
    }

    @Test
    fun testSelectLocalMode_morphsToLocalSetupAndSeedsDemoData() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        viewModel.processIntent(LoginIntent.SelectLocalMode)
        assertEquals(LoginStep.LOCAL_SETUP, viewModel.uiState.value.step)

        viewModel.processIntent(LoginIntent.SelectLocalSeed(LocalSeedChoice.DEMO))
        assertEquals(LocalSeedChoice.DEMO, viewModel.uiState.value.selectedLocalSeed)

        viewModel.processIntent(LoginIntent.ConfirmLocalSetup)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.switchedToLocalMode)
        assertEquals(StorageMode.LOCAL, repository.storageMode.value)
        assertTrue(repository.preferences.value.onboardingCompleted)
        assertTrue(repository.accounts.value.isNotEmpty(), "Demo accounts must be seeded into repository")
    }

    @Test
    fun testUpdateCredentialsAndTogglePasswordVisibility() {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        viewModel.processIntent(LoginIntent.UpdateUsername("admin"))
        viewModel.processIntent(LoginIntent.UpdatePassword("secret123"))
        assertEquals("admin", viewModel.uiState.value.username)
        assertEquals("secret123", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isPasswordVisible)

        viewModel.processIntent(LoginIntent.TogglePasswordVisibility)
        assertTrue(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun testPrepareOidcLogin_persistsPendingServerUrlAndSelfHostedMode() = runTest(testDispatcher) {
        repository.updatePreferences(
            repository.preferences.value.copy(selfHostedServerUrl = "https://orbit.example.com")
        )
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        viewModel.processIntent(LoginIntent.PrepareOidcLogin)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "https://orbit.example.com",
            storage.loadString(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
        )
        assertEquals(StorageMode.SELF_HOSTED, repository.preferences.value.storageMode)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSubmitPasswordLogin_validatesRequiredServerAndCredentials() {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        viewModel.processIntent(LoginIntent.SubmitPasswordLogin)
        assertEquals(LoginStep.SERVER_URL, viewModel.uiState.value.step)
        assertEquals(
            "Please connect to your Orbit Money server first.",
            viewModel.uiState.value.errorMessage
        )

        val webViewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = true,
            scope = testScope
        )
        webViewModel.processIntent(LoginIntent.SubmitPasswordLogin)
        assertEquals(
            "Username and password are required.",
            webViewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun testUseOfflineLocalStorage_switchesRepositoryToLocalMode() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        viewModel.processIntent(LoginIntent.UseOfflineLocalStorage)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.switchedToLocalMode)
        assertEquals(StorageMode.LOCAL, repository.storageMode.value)
        assertTrue(repository.preferences.value.onboardingCompleted)
        assertTrue(repository.isAuthenticated.value)
    }
}
