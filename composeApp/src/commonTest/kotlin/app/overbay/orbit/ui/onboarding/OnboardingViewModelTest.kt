package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: OnboardingViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = OnboardingViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_defaultsToWelcomeAndLocalMode() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals(StorageMode.LOCAL, state.selectedStorageMode)
        assertEquals(SeedChoice.DEMO, state.selectedSeedChoice)
        assertEquals(OrbitThemePreset.ORBIT_EMERALD, state.selectedThemePreset)
        assertEquals(OrbitThemeMode.SYSTEM, state.selectedThemeMode)
        assertFalse(state.isCompleted)
    }

    @Test
    fun testStepProgression_advancesAndRetreatsThroughAllSteps() {
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)

        viewModel.processIntent(OnboardingIntent.NextStep)
        assertEquals(OnboardingStep.STORAGE_MODE, viewModel.uiState.value.currentStep)

        viewModel.processIntent(OnboardingIntent.NextStep)
        assertEquals(OnboardingStep.DATA_SEED, viewModel.uiState.value.currentStep)

        viewModel.processIntent(OnboardingIntent.NextStep)
        assertEquals(OnboardingStep.THEME_SETUP, viewModel.uiState.value.currentStep)

        // Retreat
        viewModel.processIntent(OnboardingIntent.PreviousStep)
        assertEquals(OnboardingStep.DATA_SEED, viewModel.uiState.value.currentStep)

        viewModel.processIntent(OnboardingIntent.PreviousStep)
        assertEquals(OnboardingStep.STORAGE_MODE, viewModel.uiState.value.currentStep)
    }

    @Test
    fun testStorageModeAndServerUrlMutation() {
        viewModel.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
        assertEquals(StorageMode.SELF_HOSTED, viewModel.uiState.value.selectedStorageMode)

        val customUrl = "https://my-orbit-instance.com"
        viewModel.processIntent(OnboardingIntent.UpdateServerUrl(customUrl))
        assertEquals(customUrl, viewModel.uiState.value.serverUrl)
    }

    @Test
    fun testThemePresetAndModeSelection() {
        viewModel.processIntent(OnboardingIntent.SelectThemePreset(OrbitThemePreset.ROYAL_INDIGO))
        assertEquals(OrbitThemePreset.ROYAL_INDIGO, viewModel.uiState.value.selectedThemePreset)

        viewModel.processIntent(OnboardingIntent.SelectThemeMode(OrbitThemeMode.DARK))
        assertEquals(OrbitThemeMode.DARK, viewModel.uiState.value.selectedThemeMode)

        viewModel.processIntent(OnboardingIntent.SetDynamicColor(false))
        assertFalse(viewModel.uiState.value.dynamicColorEnabled)
    }

    @Test
    fun testCompleteOnboarding_commitsPreferencesAndSeedsData() = runTest(testDispatcher) {
        viewModel.processIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
        viewModel.processIntent(OnboardingIntent.SelectSeedChoice(SeedChoice.DEMO))
        viewModel.processIntent(OnboardingIntent.SelectThemePreset(OrbitThemePreset.WARM_TERRACOTTA))
        viewModel.processIntent(OnboardingIntent.SelectThemeMode(OrbitThemeMode.LIGHT))

        viewModel.processIntent(OnboardingIntent.CompleteOnboarding)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCompleted, "UI state must report isCompleted=true")
        assertTrue(repository.preferences.value.onboardingCompleted, "Repository preferences must report onboardingCompleted=true")
        assertEquals(OrbitThemePreset.WARM_TERRACOTTA, repository.preferences.value.themePreset)
        assertEquals(OrbitThemeMode.LIGHT, repository.preferences.value.themeMode)
        assertTrue(repository.accounts.value.isNotEmpty(), "Demo accounts must be seeded into repository")
    }

    @Test
    fun testSkipOnboarding_setsDefaultsAndCompletes() = runTest(testDispatcher) {
        viewModel.processIntent(OnboardingIntent.SkipOnboarding)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCompleted)
        assertTrue(repository.preferences.value.onboardingCompleted)
        assertTrue(repository.accounts.value.isNotEmpty(), "Demo data should be loaded upon skip")
    }
}
