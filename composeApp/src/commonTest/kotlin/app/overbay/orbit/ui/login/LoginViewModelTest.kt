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
    fun testInitialState_nativeWithoutServerUrlOpensServerConfig() {
        val viewModel = LoginViewModel(
            repository = repository,
            storage = storage,
            isWebPlatform = false,
            scope = testScope
        )
        val state = viewModel.uiState.value
        assertTrue(state.showServerConfig, "Native app without saved server URL should show Server Address input")
        assertEquals("", state.serverUrl)
        assertTrue(state.localEnabled)
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
        assertTrue(repository.isAuthenticated.value)
    }
}
