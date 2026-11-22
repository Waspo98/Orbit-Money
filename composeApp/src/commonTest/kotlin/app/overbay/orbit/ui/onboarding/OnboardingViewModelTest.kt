package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

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
}
