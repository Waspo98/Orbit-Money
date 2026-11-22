package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.importCsvPreview
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.data.network.putMhaSettings
import app.overbay.orbit.data.setupSimpleFinBridge
import app.overbay.orbit.domain.CsvColumnMapping
import app.overbay.orbit.domain.CsvImportEngine
import app.overbay.orbit.domain.CsvImportFormat
import app.overbay.orbit.domain.CsvInspectionResult
import app.overbay.orbit.domain.LocalDateUtils
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs

internal fun OnboardingViewModel.inspectAndPreviewCsvImpl(
    fileName: String,
    rawCsv: String,
    overrideMap: CsvColumnMapping?
) {
    if (rawCsv.isBlank()) {
        _uiState.update {
            it.copy(
                csvRawText = "",
                csvInspection = null,
                csvMapping = null,
                csvPreview = null,
                csvError = null
            )
        }
        return
    }
    var inspected: CsvInspectionResult? = null
    var mapping: CsvColumnMapping? = overrideMap
    try {
        val parsedInspection = CsvImportEngine.inspectCsv(rawCsv)
        inspected = parsedInspection
        val chosenMapping = overrideMap ?: parsedInspection.suggestedMapping
        mapping = chosenMapping
        val shouldExpandMapper = if (overrideMap == null) {
            parsedInspection.detectedFormat == CsvImportFormat.GENERIC || !chosenMapping.isValid
        } else {
            _uiState.value.csvMapperExpanded
        }
        _uiState.update {
            it.copy(
                csvFileName = fileName,
                csvRawText = rawCsv,
                csvInspection = parsedInspection,
                csvMapping = chosenMapping,
                csvMapperExpanded = shouldExpandMapper,
                csvError = null
            )
        }
        val existingIds = repository.transactions.value.mapNotNull { it.externalId }.toSet()
        val catMap = repository.categories.value.associateBy { it.name.lowercase() }
        val preview = CsvImportEngine.buildPreview(rawCsv, existingIds, catMap, chosenMapping)
        _uiState.update { it.copy(csvPreview = preview, csvError = null) }
    } catch (e: Throwable) {
        _uiState.update {
            it.copy(
                csvFileName = fileName,
                csvRawText = rawCsv,
                csvInspection = inspected ?: it.csvInspection,
                csvMapping = mapping ?: it.csvMapping,
                csvMapperExpanded = if (inspected != null) true else it.csvMapperExpanded,
                csvPreview = null,
                csvError = e.message ?: "Could not parse that CSV file."
            )
        }
    }
}

internal fun OnboardingViewModel.commitCsvImportImpl() {
    val state = _uiState.value
    val preview = state.csvPreview ?: return
    _uiState.update { it.copy(isImportingCsv = true, csvError = null) }
    scope.launch {
        val res = repository.importCsvPreview(state.csvFileName, preview, fallbackAccountId = null, includeRules = true)
        if (res.isSuccess) {
            val batch = res.getOrThrow()
            val latest = preview.latestTransactionDateIso
            val suggestedCutover = latest?.let { iso ->
                runCatching { LocalDate.parse(iso).plus(1, DateTimeUnit.DAY).toString() }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    isImportingCsv = false,
                    csvImportedCount = batch.importedCount,
                    csvImportedFormatName = preview.detectedFormat.displayName,
                    csvLatestDateIso = latest,
                    csvSuggestedCutoverDate = suggestedCutover,
                    simpleFinCutoverDate = it.simpleFinCutoverDate ?: suggestedCutover,
                    csvPreview = null,
                    csvInspection = null,
                    csvRawText = "",
                    csvPasteExpanded = false
                )
            }
        } else {
            _uiState.update {
                it.copy(isImportingCsv = false, csvError = res.exceptionOrNull()?.message ?: "CSV import failed.")
            }
        }
    }
}

internal fun OnboardingViewModel.connectSimpleFinImpl() {
    val state = _uiState.value
    val token = state.simpleFinToken.trim()
    if (token.isBlank()) {
        _uiState.update { it.copy(simpleFinError = "Paste your SimpleFIN setup token first.") }
        return
    }
    _uiState.update { it.copy(isConnectingSimpleFin = true, simpleFinError = null) }
    scope.launch {
        val beforeTxCount = repository.transactions.value.size
        val cutover = state.simpleFinCutoverDate?.takeIf { it.isNotBlank() } ?: LocalDateUtils.todayIsoDate()
        val storedLocalToken = repository.preferences.value.simpleFinToken
        val effectiveToken = if (
            repository.storageMode.value == StorageMode.LOCAL &&
            token == lastFailedLocalSimpleFinInput &&
            !storedLocalToken.isNullOrBlank()
        ) {
            storedLocalToken
        } else {
            token
        }
        val res = repository.setupSimpleFinBridge(effectiveToken, cutover)
        if (res.isSuccess) {
            lastFailedLocalSimpleFinInput = null
            if (repository.storageMode.value == StorageMode.SELF_HOSTED) {
                repository.sync(force = true)
            }
            val afterAcctCount = repository.accounts.value.size
            val afterTxCount = repository.transactions.value.size
            val addedTx = (afterTxCount - beforeTxCount).coerceAtLeast(0)
            val summary = if (afterAcctCount > 0) {
                "Connected · $afterAcctCount account${if (afterAcctCount == 1) "" else "s"} synced" +
                    if (addedTx > 0) " · $addedTx transaction${if (addedTx == 1) "" else "s"} imported" else ""
            } else {
                "Connected to SimpleFIN Bridge"
            }
            _uiState.update {
                it.copy(
                    isConnectingSimpleFin = false,
                    simpleFinConnected = true,
                    simpleFinSummaryText = summary,
                    simpleFinToken = "",
                    simpleFinError = null
                )
            }
        } else {
            if (repository.storageMode.value == StorageMode.LOCAL && !repository.preferences.value.simpleFinToken.isNullOrBlank()) {
                lastFailedLocalSimpleFinInput = token
            }
            _uiState.update {
                it.copy(
                    isConnectingSimpleFin = false,
                    simpleFinError = res.exceptionOrNull()?.message ?: "Could not connect to SimpleFIN Bridge."
                )
            }
        }
    }
}

internal fun OnboardingViewModel.saveManualAccountImpl() {
    val draft = _uiState.value.manualAccountDraft
    val trimmedName = draft.name.trim()
    if (trimmedName.isBlank()) {
        _uiState.update { it.copy(manualAccountDraft = draft.copy(error = "Enter an account name.")) }
        return
    }
    _uiState.update { it.copy(manualAccountDraft = draft.copy(isSubmitting = true, error = null)) }
    val signedBalanceCents = if (draft.type.isLiability) -abs(draft.balanceCents) else draft.balanceCents
    scope.launch {
        repository.saveAccount(
            Account(
                id = 0L,
                name = trimmedName,
                type = draft.type,
                currentBalanceCents = signedBalanceCents,
                estimatedValueCents = draft.estimatedValueCents.takeIf { it > 0L },
                isManual = true
            )
        )
        _uiState.update { it.copy(manualAccountDraft = ManualAccountDraft()) }
    }
}

internal fun OnboardingViewModel.saveHouseholdMemberImpl() {
    val draft = _uiState.value.memberDraft
    val trimmedName = draft.name.trim()
    if (trimmedName.isBlank()) {
        _uiState.update { it.copy(memberDraft = draft.copy(error = "Enter a name to add.")) }
        return
    }
    val currentYear = LocalDateUtils.todayLocalDate().year
    val birthDate = draft.birthYearText.toIntOrNull()?.takeIf { it in 1920..currentYear }?.let { "$it-01-01" }
    val isAdult = draft.role == "adult"
    val contribPct = if (isAdult) draft.retirementContribPercentText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0 else 0.0
    _uiState.update { it.copy(memberDraft = draft.copy(isSubmitting = true, error = null)) }
    scope.launch {
        repository.saveHouseholdMember(
            HouseholdMember(
                id = 0L,
                name = trimmedName,
                role = draft.role,
                birthDate = birthDate,
                grossIncomeAnnualCents = if (isAdult) draft.annualGrossCents else 0L,
                employeeContributionPercent = contribPct
            )
        )
        _uiState.update { it.copy(memberDraft = HouseholdMemberDraft()) }
    }
}

internal suspend fun OnboardingViewModel.persistFeatureChoicesImpl(state: OnboardingUiState) {
    val current = repository.preferences.value
    val mhaOn = "mha" in state.enabledFeatures
    val allOptionalKeys = OnboardingStepResolver.ALL_OPTIONAL_FEATURES.map { it.routeName }.filter { it != "mha" }
    val hidden = allOptionalKeys.filter { it !in state.enabledFeatures }
    val widgets = if (!state.isReplay) {
        current.dashboardWidgets.map { w ->
            when (w.widgetId) {
                "subscriptions" -> w.copy(isVisible = "upcoming" in state.enabledFeatures)
                "retirement" -> w.copy(isVisible = "retirement_calculator" in state.enabledFeatures)
                else -> w
            }
        }
    } else current.dashboardWidgets

    repository.updatePreferences(
        current.copy(
            hiddenRouteNames = hidden,
            mhaTrackerEnabled = mhaOn,
            dashboardWidgets = widgets
        )
    )
    if (repository.storageMode.value == StorageMode.SELF_HOSTED) {
        repository.createApiClient().putMhaSettings(mhaOn)
    }
}

internal suspend fun OnboardingViewModel.persistRetirementChoicesImpl(state: OnboardingUiState) {
    repository.updatePreferences(
        repository.preferences.value.copy(
            dashboardRetirementAge = state.retirementAge,
            dashboardRetirementPreset = state.retirementPreset.name
        )
    )
}

internal suspend fun OnboardingViewModel.persistTaxChoicesImpl(state: OnboardingUiState) {
    val existing = repository.snapshot.value.mhaTaxProfile
    val hasIncome = repository.snapshot.value.members.any { it.grossIncomeAnnualCents > 0L }
    val isMha = state.taxTrackingMode == TaxTrackingMode.MHA
    val updated = existing.copy(
        trackingMode = state.taxTrackingMode,
        mode = if (hasIncome) "household" else existing.mode,
        filingStatus = state.taxFilingStatus,
        state = state.taxStateCode.take(2).uppercase().ifBlank { "IL" },
        designatedCapCents = if (isMha) state.taxCapCents else null,
        fairMarketRentalValueCents = if (isMha) state.taxFmrvCents else null
    )
    repository.updateMhaTaxProfile(updated)
}
