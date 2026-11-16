package app.overbay.orbit.ui.mha

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.MhaYearTaxRecord
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MhaTaxEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class MhaTrackerViewModel(
    private val repository: OrbitRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(LocalDateUtils.todayLocalDate().year)
    private val _activeScreenTab = MutableStateFlow(MhaScreenTab.TRACKER)
    private val _searchQuery = MutableStateFlow("")
    var searchQueryInput by mutableStateOf("")
        private set
    private val _showConfigSheet = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _showExcludedInList = MutableStateFlow(false)
    private val _inspectingTransactionId = MutableStateFlow<Long?>(null)
    private var lastAutoRecordedFingerprint: String? = null

    val uiState: StateFlow<MhaTrackerUiState> = combine(
        combine(
            repository.snapshot,
            repository.transactions,
            repository.accounts,
            repository.categories,
            _selectedYear
        ) { snapshot, txns, accounts, cats, year ->
            SnapshotData(snapshot, txns, accounts, cats, year)
        },
        combine(
            _activeScreenTab,
            _searchQuery,
            _showConfigSheet,
            _errorMessage
        ) { screenTab, search, showConfig, err ->
            ViewFilterData(screenTab, search, showConfig, err)
        },
        combine(
            _selectedCategoryId,
            _showExcludedInList,
            _inspectingTransactionId
        ) { categoryId, showExcluded, inspectingId ->
            ListFilterData(categoryId, showExcluded, inspectingId)
        }
    ) { snapData, filterData, listFilter ->
        buildUiState(snapData, filterData, listFilter)
    }.flowOn(computeDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MhaTrackerUiState(isLoading = true)
    )

    private fun buildUiState(
        snapData: SnapshotData,
        filterData: ViewFilterData,
        listFilter: ListFilterData
    ): MhaTrackerUiState {
        val snapshot = snapData.snapshot
        val transactions = snapData.transactions
        val accounts = snapData.accounts
        val categories = snapData.categories
        val year = snapData.year
        val profile = snapshot.mhaTaxProfile

        val classified = MhaTrackerTransform.classifyYearTransactions(year, transactions, accounts, categories)
        val sortedAll = classified.allOutflows
        val sortedEligible = classified.eligible
        val actualSpentCents = classified.actualSpentCents

        val today = LocalDateUtils.todayLocalDate()
        val currentYear = today.year
        val yearRecord = profile.yearlyRecords[year.toString()]
        val designatedCap = if (yearRecord?.capsConfigured == true) yearRecord.designatedCapCents else profile.designatedCapCents
        val fmrv = if (yearRecord?.capsConfigured == true) yearRecord.fairMarketRentalValueCents else profile.fairMarketRentalValueCents

        val excludableCap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = actualSpentCents,
            designatedCapCents = designatedCap,
            fairMarketRentalValueCents = fmrv
        )
        val bindingLimit = MhaTrackerTransform.resolveLimitingFactor(actualSpentCents, designatedCap, fmrv)
        val taxCalc = MhaTaxEngine.calculateMhaSavingsForYear(
            year = year,
            currentYear = currentYear,
            actualEligibleCents = actualSpentCents,
            profile = profile,
            householdMembers = snapshot.members,
            designatedCapCents = designatedCap,
            fairMarketRentalValueCents = fmrv
        )

        val projectedSavings = if (year == currentYear) {
            val dayOfYear = today.dayOfYear.toDouble()
            val daysInYear = if (LocalDateUtils.isLeapYear(today.year)) 366.0 else 365.0
            if (dayOfYear > 0.0) ((taxCalc.totalSavingsCents.toDouble() / dayOfYear) * daysInYear).toLong()
            else taxCalc.totalSavingsCents
        } else {
            taxCalc.totalSavingsCents
        }

        val isStoredRateActive = yearRecord != null && !yearRecord.useLiveProfile && (year < currentYear || yearRecord.isLocked)
        val isHistoricalUnset = year < currentYear && (yearRecord == null || yearRecord.useLiveProfile)
        val taxRateSourceLabel = when {
            isStoredRateActive -> "Saved $year Rate"
            isHistoricalUnset -> "Estimated"
            profile.mode.equals("simple", ignoreCase = true) -> "Live · Simple"
            else -> "Live · Household"
        }

        val categoryBreakdown = MhaTrackerTransform.buildCategoryBreakdown(sortedEligible)
        val validCatFilter = listFilter.selectedCategoryId?.takeIf { catId ->
            categoryBreakdown.any { it.categoryId == catId } || listFilter.showExcludedInList
        }
        val visibleItems = MhaTrackerTransform.filterVisibleTransactions(
            source = if (listFilter.showExcludedInList) sortedAll else sortedEligible,
            categoryId = validCatFilter,
            searchQuery = filterData.searchQuery
        )
        val groupedMonths = MhaTrackerTransform.groupTransactionsByMonth(visibleItems)
        val visibleTotalCents = visibleItems.filter { it.isEligible }.sumOf { abs(it.amountCents) }

        val availableYears = (transactions.mapNotNull {
            it.effectiveDate.take(4).toIntOrNull()
        } + currentYear + year).distinct().sortedDescending()

        return MhaTrackerUiState(
            selectedYear = year,
            currentYear = currentYear,
            availableYears = availableYears,
            actualEligibleHousingSpentCents = actualSpentCents,
            designatedCapCents = designatedCap,
            fairMarketRentalValueCents = fmrv,
            excludableAllowanceCents = excludableCap,
            limitingFactor = bindingLimit,
            taxCalculationResult = taxCalc,
            projectedAnnualSavingsCents = projectedSavings,
            taxProfile = profile,
            activeYearTaxRecord = yearRecord,
            isUsingStoredYearRate = isStoredRateActive,
            isHistoricalYearWithoutSavedRate = isHistoricalUnset,
            taxRateSourceLabel = taxRateSourceLabel,
            eligibleTransactions = sortedEligible,
            allYearOutflows = sortedAll,
            visibleTransactions = visibleItems,
            groupedVisibleMonths = groupedMonths,
            visibleTotalCents = visibleTotalCents,
            categoryBreakdown = categoryBreakdown,
            selectedCategoryId = validCatFilter,
            showExcludedInList = listFilter.showExcludedInList,
            inspectingTransaction = listFilter.inspectingTransactionId?.let { id -> sortedAll.find { it.id == id } },
            accounts = accounts,
            categories = categories,
            searchQuery = filterData.searchQuery,
            showConfigSheet = filterData.showConfig,
            isLoading = false,
            errorMessage = filterData.error,
            mhaEnabled = snapshot.preferences.mhaTrackerEnabled,
            isReadOnlySession = snapshot.preferences.isReadOnlySession,
            activeScreenTab = filterData.screenTab
        )
    }

    fun setYear(year: Int) {
        _selectedYear.value = year
        _selectedCategoryId.value = null
        _inspectingTransactionId.value = null
    }

    fun setSelectedCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun setShowExcludedInList(show: Boolean) {
        if (show && uiState.value.selectedCategoryId == null) {
            _selectedCategoryId.value = null
        }
        _showExcludedInList.value = show
    }

    fun openTransactionActionSheet(item: MhaTransactionItem?) {
        _inspectingTransactionId.value = item?.id
    }

    fun setActiveScreenTab(tab: MhaScreenTab) {
        _activeScreenTab.value = tab
    }

    fun setSearchQuery(query: String) {
        searchQueryInput = query
        _searchQuery.value = query
    }

    fun openConfigSheet() {
        _showConfigSheet.value = true
    }

    fun closeConfigSheet() {
        _showConfigSheet.value = false
    }

    fun dismissConfigSheet() = closeConfigSheet()

    fun setMhaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMhaEnabled(enabled)
        }
    }

    fun setMhaAccountDefault(accountId: Long, eligible: Boolean) {
        viewModelScope.launch {
            repository.setMhaAccountDefault(accountId, eligible)
        }
    }

    fun setMhaCategoryDefault(categoryId: Long, eligible: Boolean) {
        viewModelScope.launch {
            repository.setMhaCategoryDefault(categoryId, eligible)
        }
    }

    fun setMhaCategoryIgnoreDefault(categoryId: Long, ignored: Boolean) {
        viewModelScope.launch {
            repository.setMhaCategoryIgnoreDefault(categoryId, ignored)
        }
    }

    fun toggleTransactionEligibility(transactionId: Long, currentEligible: Boolean? = null) {
        viewModelScope.launch {
            val target = uiState.value.allYearOutflows.find { it.id == transactionId }
            val newEligible = if (currentEligible != null) !currentEligible else !(target?.isEligible ?: false)
            repository.batchSetMhaEligible(setOf(transactionId), newEligible)
        }
    }

    fun resetTransactionEligibility(transactionId: Long) {
        viewModelScope.launch {
            repository.resetTransaction(transactionId)
        }
    }

    fun resetTransactionMhaOverride(transactionId: Long) {
        viewModelScope.launch {
            repository.resetTransactionFields(transactionId, listOf("mha_eligible"))
        }
    }

    fun setTransactionIgnored(transactionId: Long, ignored: Boolean) {
        viewModelScope.launch {
            repository.setTransactionIgnored(transactionId, ignored)
        }
    }

    fun batchSetMhaEligible(ids: Set<Long>, eligible: Boolean) {
        viewModelScope.launch {
            repository.batchSetMhaEligible(ids, eligible)
        }
    }

    fun recordCurrentYearRateIfChanged() {
        val state = uiState.value
        if (state.isLoading || state.isReadOnlySession) return
        if (state.selectedYear != state.currentYear) return
        if (state.actualEligibleHousingSpentCents <= 0L) return
        val existing = state.activeYearTaxRecord
        if (existing?.isLocked == true) return
        val calc = state.taxCalculationResult ?: return

        val resolvedCapsConfigured = existing?.capsConfigured
            ?: (state.designatedCapCents != null || state.fairMarketRentalValueCents != null)
        val computedRecord = MhaTaxEngine.toYearRecord(
            year = state.currentYear,
            result = calc,
            profile = state.taxProfile,
            designatedCapCents = state.designatedCapCents,
            fairMarketRentalValueCents = state.fairMarketRentalValueCents,
            capsConfigured = resolvedCapsConfigured,
            isLocked = false,
            useLiveProfile = false,
            updatedAt = LocalDateUtils.todayIsoDate()
        )
        val unchanged = existing != null &&
            !existing.useLiveProfile &&
            existing.capsConfigured == computedRecord.capsConfigured &&
            abs(existing.effectiveRatePercent - computedRecord.effectiveRatePercent) < 0.01 &&
            existing.federalRatePercent == computedRecord.federalRatePercent &&
            existing.stateRatePercent == computedRecord.stateRatePercent &&
            existing.designatedCapCents == computedRecord.designatedCapCents &&
            existing.fairMarketRentalValueCents == computedRecord.fairMarketRentalValueCents &&
            existing.mode == computedRecord.mode
        if (unchanged) return

        val fingerprint = "${computedRecord.year}:${computedRecord.effectiveRatePercent}:${computedRecord.federalRatePercent}:${computedRecord.stateRatePercent}:${computedRecord.designatedCapCents}:${computedRecord.fairMarketRentalValueCents}:${computedRecord.capsConfigured}:${computedRecord.mode}"
        if (lastAutoRecordedFingerprint == fingerprint) return
        lastAutoRecordedFingerprint = fingerprint

        val updatedProfile = state.taxProfile.copy(
            yearlyRecords = state.taxProfile.yearlyRecords + (state.currentYear.toString() to computedRecord)
        )
        viewModelScope.launch {
            repository.updateMhaTaxProfile(updatedProfile)
        }
    }

    fun updateMhaConfig(
        designationCapCents: Cents? = null,
        fairMarketRentalValueCents: Cents? = null,
        profile: MhaTaxProfile = repository.snapshot.value.mhaTaxProfile
    ) {
        saveYearConfig(
            year = _selectedYear.value,
            updatedProfile = profile,
            designationCapCents = designationCapCents,
            fairMarketRentalValueCents = fairMarketRentalValueCents,
            customEffectiveRatePercent = null,
            lockYearRate = false
        )
    }

    fun saveYearConfig(
        year: Int,
        updatedProfile: MhaTaxProfile,
        designationCapCents: Cents?,
        fairMarketRentalValueCents: Cents?,
        customEffectiveRatePercent: Double?,
        lockYearRate: Boolean
    ) {
        val currentYear = LocalDateUtils.todayLocalDate().year
        val snap = repository.snapshot.value
        val yearKey = year.toString()
        val isCurrentYear = year == currentYear
        val baseProfile = if (isCurrentYear) {
            updatedProfile.copy(
                designatedCapCents = designationCapCents,
                fairMarketRentalValueCents = fairMarketRentalValueCents
            )
        } else {
            updatedProfile
        }

        val updatedRecords = baseProfile.yearlyRecords.toMutableMap()
        val yearSpent = MhaTrackerTransform.classifyYearTransactions(
            year = year,
            transactions = repository.transactions.value,
            accounts = repository.accounts.value,
            categories = repository.categories.value
        ).actualSpentCents

        if (lockYearRate && customEffectiveRatePercent != null) {
            val clamped = MhaTaxEngine.roundPercentTwoDecimals(customEffectiveRatePercent.coerceIn(0.0, 100.0))
            val existing = updatedRecords[yearKey]
            val matchesExistingRates = existing != null &&
                abs(existing.effectiveRatePercent - clamped) < 0.01
            updatedRecords[yearKey] = MhaYearTaxRecord(
                year = year,
                effectiveRatePercent = clamped,
                federalRatePercent = if (matchesExistingRates) existing?.federalRatePercent else null,
                stateRatePercent = if (matchesExistingRates) existing?.stateRatePercent else null,
                mode = if (matchesExistingRates) (existing?.mode ?: "manual_effective") else "manual_effective",
                designatedCapCents = designationCapCents,
                fairMarketRentalValueCents = fairMarketRentalValueCents,
                capsConfigured = true,
                isLocked = true,
                useLiveProfile = false,
                updatedAt = LocalDateUtils.todayIsoDate()
            )
        } else if (isCurrentYear) {
            val liveCalc = MhaTaxEngine.calculateMhaSavings(
                actualEligibleCents = yearSpent,
                profile = baseProfile,
                householdMembers = snap.members,
                designatedCapCents = designationCapCents,
                fairMarketRentalValueCents = fairMarketRentalValueCents
            )
            updatedRecords[yearKey] = MhaTaxEngine.toYearRecord(
                year = year,
                result = liveCalc,
                profile = baseProfile,
                designatedCapCents = designationCapCents,
                fairMarketRentalValueCents = fairMarketRentalValueCents,
                capsConfigured = true,
                isLocked = false,
                useLiveProfile = false,
                updatedAt = LocalDateUtils.todayIsoDate()
            )
        } else {
            // Historical year using live profile calculation; keep record only if caps exist or override global caps
            val hasYearCaps = designationCapCents != null || fairMarketRentalValueCents != null
            val overridesGlobalCaps = baseProfile.designatedCapCents != null || baseProfile.fairMarketRentalValueCents != null
            if (hasYearCaps || overridesGlobalCaps) {
                val liveCalc = MhaTaxEngine.calculateMhaSavings(
                    actualEligibleCents = yearSpent,
                    profile = baseProfile,
                    householdMembers = snap.members,
                    designatedCapCents = designationCapCents,
                    fairMarketRentalValueCents = fairMarketRentalValueCents
                )
                updatedRecords[yearKey] = MhaTaxEngine.toYearRecord(
                    year = year,
                    result = liveCalc,
                    profile = baseProfile,
                    designatedCapCents = designationCapCents,
                    fairMarketRentalValueCents = fairMarketRentalValueCents,
                    capsConfigured = true,
                    isLocked = false,
                    useLiveProfile = true,
                    updatedAt = LocalDateUtils.todayIsoDate()
                )
            } else {
                updatedRecords.remove(yearKey)
            }
        }

        val finalProfile = baseProfile.copy(yearlyRecords = updatedRecords)
        _showConfigSheet.value = false
        viewModelScope.launch {
            repository.updateMhaTaxProfile(finalProfile)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private data class SnapshotData(
        val snapshot: app.overbay.orbit.data.model.OrbitWorkspaceSnapshot,
        val transactions: List<Transaction>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val year: Int
    )

    private data class ViewFilterData(
        val screenTab: MhaScreenTab,
        val searchQuery: String,
        val showConfig: Boolean,
        val error: String?
    )

    private data class ListFilterData(
        val selectedCategoryId: Long?,
        val showExcludedInList: Boolean,
        val inspectingTransactionId: Long?
    )
}
