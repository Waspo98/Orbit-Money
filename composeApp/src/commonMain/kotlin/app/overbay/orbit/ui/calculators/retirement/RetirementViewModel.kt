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
import kotlin.math.min
import kotlin.math.roundToLong

class RetirementViewModel(
    private val repository: OrbitRepository,
    scope: CoroutineScope? = null
) : ViewModel() {
    private val vmScope: CoroutineScope = scope ?: (viewModelScope + Dispatchers.Default)
    private val _uiState = MutableStateFlow(RetirementUiState())
    val uiState: StateFlow<RetirementUiState> = _uiState.asStateFlow()

    private val currentYearString: String by lazy { LocalDateUtils.todayLocalDate().year.toString() }
    private var cachedHistoryAge: Double = Double.NaN
    private var cachedBaseHistoryPoints: List<ProjectionPoint> = emptyList()

    init {
        vmScope.launch {
            combine(
                repository.accounts,
                repository.snapshot
                    .map { Triple(it.members, it.retirementAccountLinks, it.household) }
                    .distinctUntilChanged(),
                repository.goals
            ) { accounts, (members, links, _), goals ->
                Triple(accounts, members to links, goals)
            }.collect { (accounts, membersAndLinks, goals) ->
                val (members, links) = membersAndLinks
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
                val plannedAnnualSavings = adultMembers.sumOf { calculateMemberRetirementAnnual(it) }
                val plannedMonthly = if (plannedAnnualSavings > 0L) {
                    (plannedAnnualSavings.toDouble() / 12.0).roundToLong()
                } else 1000_00L

                // 4. Resolve connected retirement accounts & precomputed member breakdowns
                val investments = accounts.filter { !it.isArchived && it.type == AccountType.INVESTMENT }
                val accountMap = accounts.associateBy { it.id }
                val linkedDetails = buildLinkedAccountDetails(
                    members = members,
                    accounts = accounts,
                    investments = investments,
                    goals = goals
                )
                val memberSummaries = buildMemberSummaries(members, accountMap)
                val connectedBalance = linkedDetails.sumOf { it.balanceCents }

                _uiState.update { current ->
                    val nextCurrentAge = derivedAge
                    val nextRetirementAge = max(nextCurrentAge + 1.0, current.retirementAge)
                    val nextRunwayEndAge = max(nextRetirementAge + 1.0, current.runwayEndAge)

                    val nextCurrentBalance = if (!current.currentBalanceOverridden && connectedBalance > 0L) {
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
                        investmentAccounts = investments,
                        allAccounts = accounts,
                        accountById = accountMap,
                        householdMembers = members,
                        linkedAccounts = linkedDetails,
                        memberSummaries = memberSummaries
                    )
                }
                recalculate()
            }
        }

        vmScope.launch {
            val history = repository.getRetirementHistory(1200)
            _uiState.update { current ->
                cachedHistoryAge = current.currentAge
                cachedBaseHistoryPoints = buildActualHistoryProjectionPoints(
                    history = history,
                    currentAge = current.currentAge,
                    currentBalanceCents = current.currentBalanceCents
                )
                current.copy(
                    historyPoints = history,
                    filteredHistoryPoints = filterHistoryPoints(history, current.selectedHorizon, currentYearString),
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
                filteredHistoryPoints = filterHistoryPoints(current.historyPoints, horizon, currentYearString)
            )
        }
    }

    fun openAssumptionsSheet() {
        _uiState.update { it.copy(showAssumptionsSheet = true) }
    }

    fun dismissAssumptionsSheet() {
        _uiState.update { it.copy(showAssumptionsSheet = false) }
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
        if (cachedHistoryAge != currentAge) {
            cachedHistoryAge = currentAge
            cachedBaseHistoryPoints = buildActualHistoryProjectionPoints(
                history = history,
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

    companion object {
        private data class MemberContributionBreakdown(
            val employeeSavingsCents: Cents,
            val employerMatchCents: Cents,
            val matchCapReached: Boolean,
            val hsaCents: Cents,
            val totalAnnualCents: Cents
        )

        private fun computeMemberBreakdown(member: HouseholdMember): MemberContributionBreakdown {
            val gross = member.grossIncomeAnnualCents
            val empPct = member.employeeContributionPercent ?: 0.0
            val empContribCents = if (member.employeeContributionAnnualCents > 0L) {
                member.employeeContributionAnnualCents
            } else if (empPct > 0.0 && gross > 0L) {
                ((gross.toDouble() * empPct) / 100.0).roundToLong()
            } else 0L

            val matchPct = member.employerMatchPercent
            val matchLimitPct = member.employerMatchLimitPercent
            val matchCap = member.employerMatchAnnualCapCents
            val eligiblePct = if (matchLimitPct > 0.0) min(empPct, matchLimitPct) else empPct
            val matchRate = if (matchPct > 0.0) matchPct / 100.0 else 0.0
            val uncappedMatchCents = if (eligiblePct > 0.0 && gross > 0L) {
                ((gross.toDouble() * eligiblePct / 100.0) * matchRate).roundToLong()
            } else 0L
            val matchCapReached = matchCap > 0L && uncappedMatchCents > matchCap
            val matchCents = if (matchCap > 0L) min(matchCap, uncappedMatchCents) else uncappedMatchCents
            val hsaCents = member.hsaContributionAnnualCents
            return MemberContributionBreakdown(
                employeeSavingsCents = empContribCents,
                employerMatchCents = matchCents,
                matchCapReached = matchCapReached,
                hsaCents = hsaCents,
                totalAnnualCents = empContribCents + matchCents + hsaCents
            )
        }

        fun calculateMemberRetirementAnnual(member: HouseholdMember): Cents =
            computeMemberBreakdown(member).totalAnnualCents

        fun buildMemberSummaries(
            members: List<HouseholdMember>,
            accountMap: Map<Long, Account>
        ): List<MemberRetirementSummary> {
            return members.map { member ->
                val breakdown = computeMemberBreakdown(member)
                val payPeriods = if (member.payPeriodsPerYear > 0.0) member.payPeriodsPerYear else 26.0
                val perPaycheck = (breakdown.totalAnnualCents.toDouble() / payPeriods).roundToLong()
                val chips = member.retirementAccounts.map { link ->
                    val acct = accountMap[link.accountId]
                    val labelSource = link.accountKind.ifBlank { acct?.name ?: "" }
                    LinkedAccountChip(
                        id = link.accountId,
                        displayKind = classifyPlanKind(labelSource),
                        name = acct?.name ?: "Account #${link.accountId}"
                    )
                }
                MemberRetirementSummary(
                    memberId = member.id,
                    name = member.name,
                    employer = member.employer,
                    payFrequencyLabel = member.payFrequency.replaceFirstChar { it.uppercase() },
                    perPaycheckCents = perPaycheck,
                    annualEmployeeSavingsCents = breakdown.employeeSavingsCents,
                    annualEmployerMatchCents = breakdown.employerMatchCents,
                    matchCapReached = breakdown.matchCapReached,
                    annualHsaCents = breakdown.hsaCents,
                    annualTotalCents = breakdown.totalAnnualCents,
                    linkedAccounts = chips
                )
            }
        }

        fun filterHistoryPoints(
            history: List<RetirementHistoryPoint>,
            horizon: String,
            currentYear: String
        ): List<RetirementHistoryPoint> {
            return when (horizon) {
                "YTD" -> history.filter { it.month.startsWith(currentYear) }.ifEmpty { history.takeLast(12) }
                "3Y" -> history.takeLast(36)
                "5Y" -> history.takeLast(60)
                "10Y" -> history.takeLast(120)
                else -> history
            }
        }

        fun classifyPlanKind(nameOrKind: String): String =
            RetirementCalculatorEngine.formatPlanKindLabel(nameOrKind)

        fun buildLinkedAccountDetails(
            members: List<HouseholdMember>,
            accounts: List<Account>,
            investments: List<Account>,
            goals: List<app.overbay.orbit.data.model.Goal>
        ): List<LinkedRetirementAccountDetail> {
            val accountMap = accounts.associateBy { it.id }
            val list = mutableListOf<LinkedRetirementAccountDetail>()
            var colorIndex = 0

            // 1. Check member retirement accounts
            members.forEach { member ->
                member.retirementAccounts.forEach { ra ->
                    val acct = accountMap[ra.accountId]
                    val planKind = classifyPlanKind(ra.accountKind.ifBlank { acct?.name ?: "" })
                    val balance = acct?.currentBalanceCents ?: 0L
                    list.add(
                        LinkedRetirementAccountDetail(
                            id = ra.accountId,
                            accountId = ra.accountId,
                            name = acct?.name ?: "Retirement Account #${ra.accountId}",
                            ownerName = member.name,
                            planKind = planKind,
                            institution = acct?.institution,
                            balanceCents = max(0L, balance),
                            colorIndex = colorIndex++
                        )
                    )
                }
            }
            if (list.isNotEmpty()) return list

            // 2. Check general investment accounts
            if (investments.isNotEmpty()) {
                investments.forEach { acct ->
                    list.add(
                        LinkedRetirementAccountDetail(
                            id = acct.id,
                            accountId = acct.id,
                            name = acct.name,
                            ownerName = null,
                            planKind = classifyPlanKind(acct.name),
                            institution = acct.institution,
                            balanceCents = max(0L, acct.currentBalanceCents),
                            colorIndex = colorIndex++
                        )
                    )
                }
                return list
            }

            // 3. Fallback to goal named "Retirement"
            val retirementGoal = goals.find { it.name.trim().equals("retirement", ignoreCase = true) }
            if (retirementGoal != null) {
                if (retirementGoal.allocations.isNotEmpty()) {
                    retirementGoal.allocations.forEach { alloc ->
                        val acct = accountMap[alloc.accountId]
                        list.add(
                            LinkedRetirementAccountDetail(
                                id = alloc.id,
                                accountId = alloc.accountId,
                                name = alloc.accountName.ifBlank { acct?.name ?: "Retirement Allocation" },
                                ownerName = null,
                                planKind = classifyPlanKind(alloc.accountName),
                                institution = acct?.institution,
                                balanceCents = max(0L, alloc.currentAmountCents),
                                colorIndex = colorIndex++
                            )
                        )
                    }
                } else if (retirementGoal.currentAmountCents > 0L) {
                    list.add(
                        LinkedRetirementAccountDetail(
                            id = retirementGoal.id,
                            accountId = retirementGoal.linkedAccountId,
                            name = retirementGoal.name,
                            ownerName = null,
                            planKind = "Retirement Goal",
                            institution = null,
                            balanceCents = retirementGoal.currentAmountCents,
                            colorIndex = colorIndex
                        )
                    )
                }
            }

            return list
        }

        fun buildActualHistoryProjectionPoints(
            history: List<RetirementHistoryPoint>,
            currentAge: Double,
            currentBalanceCents: Cents
        ): List<ProjectionPoint> {
            val today = LocalDateUtils.todayLocalDate()
            val result = mutableListOf<ProjectionPoint>()

            history.forEach { pt ->
                val year = pt.month.take(4).toIntOrNull()
                val month = pt.month.drop(5).take(2).toIntOrNull()
                if (year != null && month != null) {
                    val monthDiff = (today.year - year) * 12 + (today.monthNumber - month)
                    val age = currentAge - (monthDiff.toDouble() / 12.0)
                    if (age <= currentAge + 0.05) {
                        result.add(ProjectionPoint(age, pt.balanceCents))
                    }
                }
            }

            result.sortBy { it.age }

            // Ensure today point is present
            if (result.none { kotlin.math.abs(it.age - currentAge) < 0.08 }) {
                result.add(ProjectionPoint(currentAge, currentBalanceCents))
                result.sortBy { it.age }
            }

            return result
        }
    }
}
