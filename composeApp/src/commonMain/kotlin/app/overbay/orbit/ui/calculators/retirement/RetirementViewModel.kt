package app.overbay.orbit.ui.calculators.retirement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.getRetirementHistory
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementCalculatorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.math.max
import kotlin.math.roundToLong

private data class RetirementInputs(
    val accounts: List<Account>,
    val members: List<HouseholdMember>,
    val goals: List<app.overbay.orbit.data.model.Goal>,
    val customIncludedIds: Set<Long>?
)

class RetirementViewModel(
    private val repository: OrbitRepository,
    scope: CoroutineScope? = null
) : ViewModel() {
    private val vmScope: CoroutineScope = scope ?: (viewModelScope + Dispatchers.Default)
    private val _uiState = MutableStateFlow(RetirementUiState())
    val uiState: StateFlow<RetirementUiState> = _uiState.asStateFlow()

    private val currentYearString: String by lazy { LocalDateUtils.todayLocalDate().year.toString() }
    private var cachedHistoryAge: Double = Double.NaN
    private var cachedHistoryCustomIds: Set<Long>? = null
    private var cachedBaseHistoryPoints: List<ProjectionPoint> = emptyList()
    private var lastObservedCustomIds: Set<Long>? = null
    private var hasObservedCustomIds: Boolean = false

    init {
        vmScope.launch {
            combine(
                repository.accounts,
                repository.snapshot
                    .map { Triple(it.members, it.retirementAccountLinks, it.household) }
                    .distinctUntilChanged(),
                repository.goals,
                repository.preferences
                    .map { it.retirementIncludedAccountIds }
                    .distinctUntilChanged()
            ) { accounts, (members, _, _), goals, customIncludedList ->
                RetirementInputs(
                    accounts = accounts,
                    members = members,
                    goals = goals,
                    customIncludedIds = customIncludedList?.toSet()
                )
            }.collect { inputs ->
                val accounts = inputs.accounts
                val members = inputs.members
                val goals = inputs.goals
                val customIds = inputs.customIncludedIds
                val customChanged = hasObservedCustomIds && customIds != lastObservedCustomIds
                hasObservedCustomIds = true
                lastObservedCustomIds = customIds

                val adultMembers = members.filter { it.role.equals("adult", ignoreCase = true) }

                // 1. Inferred current age from oldest adult household member
                val adultAges = adultMembers.mapNotNull { LocalDateUtils.ageFromBirthDate(it.birthDate) }
                val hasAgeSource = adultAges.isNotEmpty()
                val derivedAge = if (hasAgeSource) {
                    adultAges.maxOrNull()!!.toDouble().coerceIn(18.0, 80.0)
                } else 35.0

                // 2. Household gross income & baseline 80% spending
                val totalGross = members.sumOf { it.grossIncomeAnnualCents }
                val baselineSpending = if (totalGross > 0L) {
                    ((totalGross.toDouble() * 0.80).roundToLong())
                } else 60_000_00L

                // 3. Planned monthly retirement savings from household member contributions
                val plannedAnnualSavings = adultMembers.sumOf {
                    RetirementAccountResolver.calculateMemberRetirementAnnual(it)
                }
                val plannedMonthly = if (plannedAnnualSavings > 0L) {
                    (plannedAnnualSavings.toDouble() / 12.0).roundToLong()
                } else 1000_00L

                // 4. Resolve included retirement accounts & precomputed member breakdowns
                val investments = accounts.filter { !it.isArchived && it.type == AccountType.INVESTMENT }
                val accountMap = accounts.associateBy { it.id }
                val linkedDetails = RetirementAccountResolver.buildLinkedAccountDetails(
                    members = members,
                    accounts = accounts,
                    accountMap = accountMap,
                    investments = investments,
                    goals = goals,
                    customIncludedIds = customIds
                )
                val memberSummaries = RetirementAccountResolver.buildMemberSummaries(members, accountMap)
                val defaultIds = RetirementAccountResolver.resolveDefaultIncludedAccountIds(members, investments, goals)
                val connectedBalance = linkedDetails.sumOf { it.balanceCents }
                val includedIds = linkedDetails.mapNotNull { it.accountId }.toSet()
                val hasCustomSelection = customIds != null
                val hasSources = linkedDetails.isNotEmpty() || hasCustomSelection

                _uiState.update { current ->
                    val nextCurrentAge = derivedAge
                    val nextRetirementAge = max(nextCurrentAge + 1.0, current.retirementAge)
                    val nextRunwayEndAge = max(nextRetirementAge + 1.0, current.runwayEndAge)

                    val clearBalanceOverride = customChanged || !current.currentBalanceOverridden
                    val nextCurrentBalance = if (clearBalanceOverride && hasSources) {
                        connectedBalance
                    } else current.currentBalanceCents

                    val nextMonthlySavings = if (!current.monthlySavingsOverridden) {
                        plannedMonthly
                    } else current.monthlySavingsCents

                    val nextAnnualSpending = if (!current.annualSpendingOverridden) {
                        baselineSpending
                    } else current.annualSpendingCents

                    current.copy(
                        currentAge = nextCurrentAge,
                        hasAgeSource = hasAgeSource,
                        retirementAge = nextRetirementAge,
                        runwayEndAge = nextRunwayEndAge,
                        grossIncomeAnnualCents = totalGross,
                        baselineSpendingCents = baselineSpending,
                        annualSpendingCents = nextAnnualSpending,
                        plannedMonthlySavingsCents = plannedMonthly,
                        monthlySavingsCents = nextMonthlySavings,
                        connectedBalanceCents = connectedBalance,
                        currentBalanceCents = nextCurrentBalance,
                        currentBalanceOverridden = if (customChanged) false else current.currentBalanceOverridden,
                        investmentAccounts = investments,
                        allAccounts = accounts,
                        accountById = accountMap,
                        householdMembers = members,
                        linkedAccounts = linkedDetails,
                        defaultIncludedAccountIds = defaultIds,
                        hasCustomAccountSelection = hasCustomSelection,
                        includedAccountIds = includedIds,
                        memberSummaries = memberSummaries,
                        filteredHistoryPoints = RetirementAccountResolver.filterHistoryPoints(
                            history = current.historyPoints,
                            horizon = current.selectedHorizon,
                            currentYear = currentYearString,
                            customIncludedIds = customIds
                        )
                    )
                }
                recalculate()
            }
        }

        vmScope.launch {
            val initialCustomIds = repository.preferences.value.retirementIncludedAccountIds?.toSet()
            val history = repository.getRetirementHistory(1200, initialCustomIds)
            _uiState.update { current ->
                val activeCustomIds = lastObservedCustomIds ?: initialCustomIds
                cachedHistoryAge = current.currentAge
                cachedHistoryCustomIds = activeCustomIds
                cachedBaseHistoryPoints = RetirementAccountResolver.buildActualHistoryProjectionPoints(
                    history = RetirementAccountResolver.filterHistoryByAccounts(history, activeCustomIds),
                    currentAge = current.currentAge,
                    currentBalanceCents = current.currentBalanceCents
                )
                current.copy(
                    historyPoints = history,
                    filteredHistoryPoints = RetirementAccountResolver.filterHistoryPoints(
                        history = history,
                        horizon = current.selectedHorizon,
                        currentYear = currentYearString,
                        customIncludedIds = activeCustomIds
                    ),
                    actualHistoryPoints = cachedBaseHistoryPoints
                )
            }
        }
    }

    fun selectTab(tab: RetirementWorkspaceTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setRetirementAge(age: Double) {
        val current = _uiState.value
        val clampedAge = age.coerceIn(current.currentAge + 1.0, current.runwayEndAge - 1.0)
        if (clampedAge == current.retirementAge) return
        _uiState.update { it.copy(retirementAge = clampedAge) }
        recalculate()
    }

    fun setRunwayEndAge(age: Double) {
        val current = _uiState.value
        val clampedAge = age.coerceIn(current.retirementAge + 1.0, 110.0)
        if (clampedAge == current.runwayEndAge) return
        _uiState.update { it.copy(runwayEndAge = clampedAge) }
        recalculate()
    }

    fun setPreset(preset: RetirementScenarioPreset) {
        if (_uiState.value.preset == preset) return
        _uiState.update { current ->
            current.copy(
                preset = preset,
                scenario = current.scenarios[preset] ?: current.scenario
            )
        }
    }

    fun setTargetMode(mode: RetirementTargetMode) {
        if (_uiState.value.targetMode == mode) return
        _uiState.update { it.copy(targetMode = mode) }
        recalculate()
    }

    fun setCurrentBalance(cents: Cents) {
        val safeCents = max(0L, cents)
        _uiState.update { it.copy(currentBalanceCents = safeCents, currentBalanceOverridden = true) }
        recalculate()
    }

    fun resetCurrentBalanceToConnectedAccounts() {
        _uiState.update { current ->
            current.copy(
                currentBalanceCents = max(0L, current.connectedBalanceCents),
                currentBalanceOverridden = false
            )
        }
        recalculate()
    }

    fun setMonthlySavings(cents: Cents) {
        val safeCents = max(0L, cents)
        if (_uiState.value.monthlySavingsCents == safeCents && _uiState.value.monthlySavingsOverridden) return
        _uiState.update { it.copy(monthlySavingsCents = safeCents, monthlySavingsOverridden = true) }
        recalculate()
    }

    fun resetMonthlySavingsToHouseholdPace() {
        _uiState.update { current ->
            current.copy(
                monthlySavingsCents = max(0L, current.plannedMonthlySavingsCents),
                monthlySavingsOverridden = false
            )
        }
        recalculate()
    }

    fun setAnnualSpending(cents: Cents) {
        val safeCents = max(0L, cents)
        if (_uiState.value.annualSpendingCents == safeCents && _uiState.value.annualSpendingOverridden) return
        _uiState.update { it.copy(annualSpendingCents = safeCents, annualSpendingOverridden = true) }
        recalculate()
    }

    fun applySpendingIncomeFraction(fraction: Double) {
        val gross = _uiState.value.grossIncomeAnnualCents
        val base = if (gross > 0L) (gross.toDouble() * fraction).roundToLong() else _uiState.value.baselineSpendingCents
        setAnnualSpending(base)
    }

    fun resetAnnualSpendingToGuideline() {
        _uiState.update { current ->
            current.copy(
                annualSpendingCents = max(0L, current.baselineSpendingCents),
                annualSpendingOverridden = false
            )
        }
        recalculate()
    }

    fun setCustomTarget(cents: Cents?) {
        _uiState.update { it.copy(customTargetCents = cents?.let { c -> max(0L, c) }) }
        recalculate()
    }

    fun setLegacyAmount(cents: Cents) {
        _uiState.update { it.copy(legacyAmountCents = max(0L, cents)) }
        recalculate()
    }

    fun setSelectedHorizon(horizon: String) {
        if (_uiState.value.selectedHorizon == horizon) return
        _uiState.update { current ->
            current.copy(
                selectedHorizon = horizon,
                filteredHistoryPoints = RetirementAccountResolver.filterHistoryPoints(
                    history = current.historyPoints,
                    horizon = horizon,
                    currentYear = currentYearString,
                    customIncludedIds = lastObservedCustomIds
                )
            )
        }
    }

    fun openAssumptionsSheet() {
        _uiState.update { it.copy(showAssumptionsSheet = true) }
    }

    fun dismissAssumptionsSheet() {
        _uiState.update { it.copy(showAssumptionsSheet = false) }
    }

    fun openIncludedAccountsSheet() {
        _uiState.update { it.copy(showIncludedAccountsSheet = true) }
    }

    fun dismissIncludedAccountsSheet() {
        _uiState.update { it.copy(showIncludedAccountsSheet = false) }
    }

    fun setIncludedAccounts(accountIds: List<Long>?) {
        val normalized = accountIds?.distinct()?.sorted()
        _uiState.update { it.copy(showIncludedAccountsSheet = false, currentBalanceOverridden = false) }
        vmScope.launch {
            val currentPrefs = repository.preferences.value
            if (currentPrefs.retirementIncludedAccountIds != normalized) {
                repository.updatePreferences(currentPrefs.copy(retirementIncludedAccountIds = normalized))
            }
            if (repository.storageMode.value != StorageMode.SELF_HOSTED) {
                val customSet = normalized?.toSet()
                val refreshed = repository.getRetirementHistory(1200, customSet)
                cachedHistoryAge = Double.NaN
                _uiState.update { cur ->
                    val filtered = RetirementAccountResolver.filterHistoryPoints(
                        history = refreshed,
                        horizon = cur.selectedHorizon,
                        currentYear = currentYearString,
                        customIncludedIds = customSet
                    )
                    cur.copy(
                        historyPoints = refreshed,
                        filteredHistoryPoints = filtered
                    )
                }
                recalculate()
            }
        }
    }

    fun openAmountEditor(field: RetirementAmountField) {
        _uiState.update { it.copy(activeAmountField = field) }
    }

    fun dismissAmountEditor() {
        _uiState.update { it.copy(activeAmountField = null) }
    }

    fun commitAmountEditor(field: RetirementAmountField, cents: Cents) {
        _uiState.update { it.copy(activeAmountField = null) }
        when (field) {
            RetirementAmountField.MONTHLY_SAVINGS -> setMonthlySavings(cents)
            RetirementAmountField.ANNUAL_SPENDING -> setAnnualSpending(cents)
            RetirementAmountField.STARTING_BALANCE -> setCurrentBalance(cents)
        }
    }

    private fun recalculate() {
        val state = _uiState.value

        val hsaBalance = state.linkedAccounts
            .filter { it.planKind.equals("hsa", ignoreCase = true) }
            .sumOf { it.balanceCents }

        val nonHsaBalance = max(0L, state.currentBalanceCents - hsaBalance)

        val hsaAnnualContrib = state.householdMembers.sumOf { it.hsaContributionAnnualCents }
        val hsaMonthlySavings = (hsaAnnualContrib.toDouble() / 12.0).roundToLong()
        val nonHsaMonthlySavings = max(0L, state.monthlySavingsCents - hsaMonthlySavings)

        val allScenarios = RetirementCalculatorEngine.calculateAllScenarios(
            currentAge = state.currentAge,
            retirementAge = state.retirementAge,
            runwayEndAge = state.runwayEndAge,
            currentBalanceCents = state.currentBalanceCents,
            monthlySavingsCents = state.monthlySavingsCents,
            annualSpendingCents = state.annualSpendingCents,
            targetMode = state.targetMode,
            customTargetCents = state.customTargetCents,
            legacyAmountCents = state.legacyAmountCents,
            nonHsaCurrentBalanceCents = nonHsaBalance,
            nonHsaMonthlySavingsCents = nonHsaMonthlySavings,
            hsaCurrentBalanceCents = hsaBalance,
            hsaMonthlySavingsCents = hsaMonthlySavings
        )

        val activeScenario = allScenarios[state.preset] ?: allScenarios.values.first()

        val actualPoints = resolveActualHistoryProjection(
            history = state.historyPoints,
            currentAge = state.currentAge,
            currentBalanceCents = state.currentBalanceCents
        )

        _uiState.update {
            it.copy(
                scenarios = allScenarios,
                scenario = activeScenario,
                nonHsaCurrentBalanceCents = nonHsaBalance,
                nonHsaMonthlySavingsCents = nonHsaMonthlySavings,
                hsaCurrentBalanceCents = hsaBalance,
                hsaMonthlySavingsCents = hsaMonthlySavings,
                actualHistoryPoints = actualPoints
            )
        }
    }

    private fun resolveActualHistoryProjection(
        history: List<RetirementHistoryPoint>,
        currentAge: Double,
        currentBalanceCents: Cents
    ): List<ProjectionPoint> {
        if (history.isEmpty() && cachedBaseHistoryPoints.isEmpty()) {
            return listOf(ProjectionPoint(currentAge, currentBalanceCents))
        }
        if (cachedHistoryAge != currentAge || cachedHistoryCustomIds != lastObservedCustomIds) {
            cachedHistoryAge = currentAge
            cachedHistoryCustomIds = lastObservedCustomIds
            cachedBaseHistoryPoints = RetirementAccountResolver.buildActualHistoryProjectionPoints(
                history = RetirementAccountResolver.filterHistoryByAccounts(history, lastObservedCustomIds),
                currentAge = currentAge,
                currentBalanceCents = currentBalanceCents
            )
            return cachedBaseHistoryPoints
        }
        // Fast path during slider drags: reuse parsed dates & update only today's endpoint balance
        val updated = cachedBaseHistoryPoints.toMutableList()
        val lastIdx = updated.indexOfLast { kotlin.math.abs(it.age - currentAge) < 0.08 }
        if (lastIdx >= 0) {
            updated[lastIdx] = ProjectionPoint(currentAge, currentBalanceCents)
        } else {
            updated.add(ProjectionPoint(currentAge, currentBalanceCents))
        }
        return updated
    }
}
