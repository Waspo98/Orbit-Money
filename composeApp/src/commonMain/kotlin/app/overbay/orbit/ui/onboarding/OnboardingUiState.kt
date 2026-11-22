package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitThemeMode
import app.overbay.orbit.data.model.OrbitThemePreset
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CsvColumnMapping
import app.overbay.orbit.domain.CsvImportPreview
import app.overbay.orbit.domain.CsvInspectionResult
import app.overbay.orbit.ui.login.LocalSeedChoice
import app.overbay.orbit.ui.login.LoginUiState

data class ManualAccountDraft(
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val balanceCents: Cents = 0L,
    val estimatedValueCents: Cents = 0L,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

data class HouseholdMemberDraft(
    val name: String = "",
    val role: String = "adult",
    val birthYearText: String = "",
    val annualGrossCents: Cents = 0L,
    val retirementContribPercentText: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null
)

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isReplay: Boolean = false,
    val transitionDirection: Int = 1,
    val isCompleted: Boolean = false,
    val preferences: OrbitPreferences = OrbitPreferences(),
    val selectedStorageMode: StorageMode = StorageMode.LOCAL,
    val selectedLocalSeed: LocalSeedChoice = LocalSeedChoice.FRESH,
    val backupFileName: String? = null,
    val backupJsonContent: String? = null,
    val serverUrl: String = "",
    val serverInput: String = LoginUiState.DEFAULT_SERVER_PREFIX,
    val dockerHelpExpanded: Boolean = false,
    val isTestingServer: Boolean = false,
    val serverError: String? = null,
    val localAuthEnabled: Boolean = true,
    val oidcEnabled: Boolean = false,
    val oidcLoginUrl: String = "/api/auth/oidc/login",
    val oidcLoginLabel: String = "Log in with OIDC",
    val sampleDataEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmittingAuth: Boolean = false,
    val isSampleLoading: Boolean = false,
    val existingServerDetected: Boolean = false,
    val authError: String? = null,
    val csvFileName: String = "import.csv",
    val csvRawText: String = "",
    val csvPasteExpanded: Boolean = false,
    val csvInspection: CsvInspectionResult? = null,
    val csvMapping: CsvColumnMapping? = null,
    val csvMapperExpanded: Boolean = false,
    val csvPreview: CsvImportPreview? = null,
    val isImportingCsv: Boolean = false,
    val csvImportedCount: Int = 0,
    val csvImportedFormatName: String? = null,
    val csvLatestDateIso: String? = null,
    val csvSuggestedCutoverDate: String? = null,
    val csvError: String? = null,
    val simpleFinToken: String = "",
    val simpleFinCutoverDate: String? = null,
    val showSimpleFinAdvancedDate: Boolean = false,
    val isConnectingSimpleFin: Boolean = false,
    val simpleFinConnected: Boolean = false,
    val simpleFinSummaryText: String? = null,
    val simpleFinError: String? = null,
    val enteredAct2: Boolean = false,
    val enabledFeatures: Set<String> = OnboardingStepResolver.defaultEnabledFeatures(),
    val existingAccounts: List<Account> = emptyList(),
    val manualAccountDraft: ManualAccountDraft = ManualAccountDraft(),
    val householdMembers: List<HouseholdMember> = emptyList(),
    val memberDraft: HouseholdMemberDraft = HouseholdMemberDraft(),
    val retirementAge: Int = 67,
    val retirementPreset: RetirementScenarioPreset = RetirementScenarioPreset.BALANCED,
    val taxTrackingMode: TaxTrackingMode = TaxTrackingMode.GENERAL,
    val taxFilingStatus: FilingStatus = FilingStatus.MARRIED_JOINT,
    val taxStateCode: String = "IL",
    val taxCapCents: Cents? = null,
    val taxFmrvCents: Cents? = null
) {
    val resolvedSteps: List<OnboardingStep>
        get() = OnboardingStepResolver.resolveSteps(this)

    val currentStepIndex: Int
        get() = resolvedSteps.indexOf(currentStep).coerceAtLeast(0)

    val canGoBack: Boolean
        get() = currentStepIndex > 0

    val showTopSkip: Boolean
        get() = OnboardingStepResolver.canShowTopSkip(this)
}

sealed interface OnboardingIntent {
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent
    data object SkipToDashboard : OnboardingIntent
    data object ExploreSampleDataQuickStart : OnboardingIntent

    // Appearance
    data class SetThemeMode(val mode: OrbitThemeMode) : OnboardingIntent
    data class SetAmoledDark(val enabled: Boolean) : OnboardingIntent
    data class SetThemePreset(val preset: OrbitThemePreset) : OnboardingIntent
    data class SetDynamicColor(val enabled: Boolean) : OnboardingIntent

    // Storage Mode & Local Workspace
    data class SelectStorageMode(val mode: StorageMode) : OnboardingIntent
    data class SelectLocalSeed(val seed: LocalSeedChoice) : OnboardingIntent
    data class SetBackupFile(val fileName: String?, val content: String?) : OnboardingIntent
    data object ConfirmLocalWorkspace : OnboardingIntent

    // Self-Hosted Server & Auth
    data class UpdateServerInput(val url: String) : OnboardingIntent
    data class ToggleDockerHelp(val expanded: Boolean) : OnboardingIntent
    data object TestAndConnectServer : OnboardingIntent
    data class UpdateUsername(val username: String) : OnboardingIntent
    data class UpdatePassword(val password: String) : OnboardingIntent
    data object TogglePasswordVisibility : OnboardingIntent
    data object SubmitPasswordLogin : OnboardingIntent
    data object PrepareOidcLogin : OnboardingIntent
    data object SubmitServerSampleData : OnboardingIntent

    // Data Connection (CSV + SimpleFIN)
    data class ToggleCsvPaste(val expanded: Boolean) : OnboardingIntent
    data class LoadCsvText(val fileName: String, val rawCsv: String) : OnboardingIntent
    data class ToggleCsvMapper(val expanded: Boolean) : OnboardingIntent
    data class UpdateCsvMapping(val mapping: CsvColumnMapping) : OnboardingIntent
    data object ConfirmCsvImport : OnboardingIntent
    data class UpdateSimpleFinToken(val token: String) : OnboardingIntent
    data class UpdateSimpleFinCutoverDate(val isoDate: String?) : OnboardingIntent
    data class ToggleSimpleFinAdvancedDate(val expanded: Boolean) : OnboardingIntent
    data object ConnectSimpleFin : OnboardingIntent

    // Act 2 Fork & Guided Setup
    data object EnterAct2Personalize : OnboardingIntent
    data class ToggleFeature(val routeName: String) : OnboardingIntent
    data class UpdateManualAccountName(val name: String) : OnboardingIntent
    data class UpdateManualAccountType(val type: AccountType) : OnboardingIntent
    data class UpdateManualAccountBalance(val cents: Cents) : OnboardingIntent
    data class UpdateManualAccountEstValue(val cents: Cents) : OnboardingIntent
    data object SaveManualAccount : OnboardingIntent
    data class UpdateMemberName(val name: String) : OnboardingIntent
    data class UpdateMemberRole(val role: String) : OnboardingIntent
    data class UpdateMemberBirthYear(val yearText: String) : OnboardingIntent
    data class UpdateMemberIncome(val cents: Cents) : OnboardingIntent
    data class UpdateMemberRetirementPct(val pctText: String) : OnboardingIntent
    data object SaveHouseholdMember : OnboardingIntent
    data class RemoveHouseholdMember(val id: Long) : OnboardingIntent
    data class UpdateRetirementAge(val age: Int) : OnboardingIntent
    data class UpdateRetirementPreset(val preset: RetirementScenarioPreset) : OnboardingIntent
    data class UpdateTaxTrackingMode(val mode: TaxTrackingMode) : OnboardingIntent
    data class UpdateTaxFilingStatus(val status: FilingStatus) : OnboardingIntent
    data class UpdateTaxState(val stateCode: String) : OnboardingIntent
    data class UpdateTaxCap(val cents: Cents?) : OnboardingIntent
    data class UpdateTaxFmrv(val cents: Cents?) : OnboardingIntent

    // Completion
    data class SetBiometricLock(val enabled: Boolean) : OnboardingIntent
    data object FinishOnboarding : OnboardingIntent
    data object ClearCompletedFlag : OnboardingIntent
}
