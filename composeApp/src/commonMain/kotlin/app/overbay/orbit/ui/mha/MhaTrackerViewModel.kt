package app.overbay.orbit.ui.mha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MhaTaxEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class MhaTrackerViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(LocalDateUtils.todayLocalDate().year)
    private val _selectedTabFilter = MutableStateFlow(MhaTransactionTabFilter.ELIGIBLE)
    private val _activeScreenTab = MutableStateFlow(MhaScreenTab.TRACKER)
    private val _searchQuery = MutableStateFlow("")
    private val _showConfigSheet = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _designatedCapCents = MutableStateFlow<Cents?>(repository.snapshot.value.mhaTaxProfile.designatedCapCents)
    private val _fairMarketRentalValueCents = MutableStateFlow<Cents?>(repository.snapshot.value.mhaTaxProfile.fairMarketRentalValueCents)
    private val _taxProfile = MutableStateFlow(repository.snapshot.value.mhaTaxProfile)

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
            _selectedTabFilter,
            _activeScreenTab,
            _searchQuery,
            _showConfigSheet,
            _errorMessage
        ) { tab, screenTab, search, showConfig, err ->
            ViewFilterData(tab, screenTab, search, showConfig, err)
        },
        combine(
            _designatedCapCents,
            _fairMarketRentalValueCents,
            _taxProfile
        ) { cap, fmrv, profile ->
            ConfigData(cap, fmrv, profile)
        }
    ) { snapData, filterData, configData ->
        val snapshot = snapData.snapshot
        val transactions = snapData.transactions
        val accounts = snapData.accounts
        val categories = snapData.categories
        val year = snapData.year

        val tabFilter = filterData.tabFilter
        val screenTab = filterData.screenTab
        val search = filterData.searchQuery
        val showConfig = filterData.showConfig
        val error = filterData.error

        val designatedCap = configData.designatedCap
        val fmrv = configData.fmrv
        val profile = configData.profile

        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        val yearPrefix = "$year-"
        val yearTxns = transactions.filter { it.effectiveDate.startsWith(yearPrefix) }

        val accountMhaSet = accounts.filter { it.mhaDefaultEligible }.map { it.id }.toSet()
        val catMhaSet = categories.filter { it.mhaDefaultEligible }.map { it.id }.toSet()
        val catIgnoredSet = categories.filter { it.mhaDefaultIgnored }.map { it.id }.toSet()

        val allItems = mutableListOf<MhaTransactionItem>()
        val eligibleItems = mutableListOf<MhaTransactionItem>()
        val reviewQueueItems = mutableListOf<MhaTransactionItem>()

        var actualSpentCents = 0L

        for (txn in yearTxns) {
            val amount = txn.effectiveAmountCents
            val isOutflow = amount < 0L
            val isTransfer = txn.effectiveIsTransfer || (txn.effectiveCategoryId?.let { categoryMap[it]?.isTransfer } == true)
            val isIgnored = txn.effectiveIsIgnored

            if (!isOutflow || isTransfer || isIgnored) continue

            val catId = txn.effectiveCategoryId
            val accId = txn.accountId

            val isEligible = txn.editedMhaEligible ?: run {
                if (catId != null && catId in catIgnoredSet) false
                else (accId in accountMhaSet) || (catId != null && catId in catMhaSet)
            }

            val item = MhaTransactionItem(
                transaction = txn,
                category = catId?.let { categoryMap[it] },
                account = accountMap[accId],
                isEligible = isEligible,
                isExplicitOverride = txn.editedMhaEligible != null,
                isOverriddenByUser = txn.editedMhaEligibleSource == "user"
            )

            allItems.add(item)
            if (isEligible) {
                eligibleItems.add(item)
                actualSpentCents += abs(amount)
            } else {
                reviewQueueItems.add(item)
            }
        }

        val excludableCap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = actualSpentCents,
            designatedCapCents = designatedCap,
            fairMarketRentalValueCents = fmrv
        )

        val bindingLimit = when {
            designatedCap == null && fmrv == null -> MhaLimitingFactor.UNCAPPED_ACTUAL
            designatedCap != null && fmrv != null -> {
                val minCap = minOf(designatedCap, fmrv)
                when {
                    actualSpentCents <= minCap -> MhaLimitingFactor.ACTUAL_EXPENSES
                    designatedCap <= fmrv -> MhaLimitingFactor.DESIGNATION_CAP
                    else -> MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE
                }
            }
            designatedCap != null -> {
                if (actualSpentCents <= designatedCap) MhaLimitingFactor.ACTUAL_EXPENSES
                else MhaLimitingFactor.DESIGNATION_CAP
            }
            else -> {
                if (actualSpentCents <= fmrv!!) MhaLimitingFactor.ACTUAL_EXPENSES
                else MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE
            }
        }

        val taxCalc = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = actualSpentCents,
            profile = profile,
            householdMembers = snapshot.members,
            designatedCapCents = designatedCap,
            fairMarketRentalValueCents = fmrv
        )

        val today = LocalDateUtils.todayLocalDate()
        val currentYear = today.year
        val projectedSavings = if (year == currentYear) {
            val dayOfYear = today.dayOfYear.toDouble()
            val daysInYear = if (LocalDateUtils.isLeapYear(today.year)) 366.0 else 365.0
            if (dayOfYear > 0.0) ((taxCalc.totalSavingsCents.toDouble() / dayOfYear) * daysInYear).toLong()
            else taxCalc.totalSavingsCents
        } else {
            taxCalc.totalSavingsCents
        }

        val availableYears = (transactions.mapNotNull {
            it.effectiveDate.take(4).toIntOrNull()
        } + currentYear + year).distinct().sortedDescending()

        MhaTrackerUiState(
            selectedYear = year,
            availableYears = availableYears,
            actualEligibleHousingSpentCents = actualSpentCents,
            designatedCapCents = designatedCap,
            fairMarketRentalValueCents = fmrv,
            excludableAllowanceCents = excludableCap,
            limitingFactor = bindingLimit,
            taxCalculationResult = taxCalc,
            projectedAnnualSavingsCents = projectedSavings,
            taxProfile = profile,
            eligibleTransactions = eligibleItems,
            reviewQueueTransactions = reviewQueueItems,
            allYearOutflows = allItems,
            selectedTabFilter = tabFilter,
            accounts = accounts,
            categories = categories,
            searchQuery = search,
            showConfigSheet = showConfig,
            isLoading = false,
            errorMessage = error,
            mhaEnabled = snapshot.preferences.mhaTrackerEnabled,
            activeScreenTab = screenTab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MhaTrackerUiState(isLoading = true)
    )

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun setTabFilter(filter: MhaTransactionTabFilter) {
        _selectedTabFilter.value = filter
    }

    fun setActiveScreenTab(tab: MhaScreenTab) {
        _activeScreenTab.value = tab
    }

    fun setSearchQuery(query: String) {
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

    fun updateMhaConfig(
        designationCapCents: Cents? = null,
        fairMarketRentalValueCents: Cents? = null,
        profile: MhaTaxProfile = _taxProfile.value
    ) {
        val updated = profile.copy(
            designatedCapCents = designationCapCents,
            fairMarketRentalValueCents = fairMarketRentalValueCents
        )
        _designatedCapCents.value = designationCapCents
        _fairMarketRentalValueCents.value = fairMarketRentalValueCents
        _taxProfile.value = updated
        _showConfigSheet.value = false
        viewModelScope.launch {
            repository.updateMhaTaxProfile(updated)
        }
    }

    fun saveMhaConfig(updatedProfile: MhaTaxProfile) {
        updateMhaConfig(
            designationCapCents = _designatedCapCents.value,
            fairMarketRentalValueCents = _fairMarketRentalValueCents.value,
            profile = updatedProfile
        )
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
        val tabFilter: MhaTransactionTabFilter,
        val screenTab: MhaScreenTab,
        val searchQuery: String,
        val showConfig: Boolean,
        val error: String?
    )

    private data class ConfigData(
        val designatedCap: Cents?,
        val fmrv: Cents?,
        val profile: MhaTaxProfile
    )
}
