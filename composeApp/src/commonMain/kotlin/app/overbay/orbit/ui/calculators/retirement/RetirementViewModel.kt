package app.overbay.orbit.ui.calculators.retirement

import app.overbay.orbit.data.*
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.RetirementCalculatorEngine
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong

class RetirementViewModel(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val _uiState = MutableStateFlow(RetirementUiState())
    val uiState: StateFlow<RetirementUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            repository.accounts.collect { accounts ->
                val investments = accounts.filter { !it.isArchived && it.type == AccountType.INVESTMENT }
                _uiState.update { current ->
                    val next = current.copy(
                        investmentAccounts = investments,
                        allAccounts = accounts
                    )
                    if (current.useConnectedAccounts && investments.isNotEmpty()) {
                        val totalBal = investments.sumOf { it.currentBalanceCents }
                        next.copy(currentBalanceCents = max(0L, totalBal))
                    } else {
                        next
                    }
                }
                recalculate()
            }
        }

        scope.launch {
            repository.snapshot.collect { snapshot ->
                val currentYear = LocalDateUtils.todayLocalDate().year
                val adultBirthYears = snapshot.members
                    .filter { it.role.equals("adult", ignoreCase = true) }
                    .mapNotNull { it.birthDate?.take(4)?.toIntOrNull() }
                val derivedAge = if (adultBirthYears.isNotEmpty()) {
                    (currentYear - adultBirthYears.minOrNull()!!).toDouble().coerceIn(18.0, 80.0)
                } else 35.0

                val totalGross = snapshot.members.sumOf { it.grossIncomeAnnualCents }
                val baselineSpending = if (totalGross > 0L) ((totalGross.toDouble() * 0.80).roundToLong()) else 60_000_00L

                _uiState.update { current ->
                    current.copy(
                        householdMembers = snapshot.members,
                        currentAge = if (!current.currentAgeOverridden) derivedAge else current.currentAge,
                        annualSpendingCents = if (!current.annualSpendingOverridden) baselineSpending else current.annualSpendingCents
                    )
                }
                recalculate()
            }
        }

        scope.launch {
            val history = repository.getRetirementHistory()
            _uiState.update { it.copy(historyPoints = history) }
        }

        recalculate()
    }

    fun setCurrentAge(age: Double) {
        val clampedAge = age.coerceIn(18.0, _uiState.value.retirementAge - 1.0)
        _uiState.update { it.copy(currentAge = clampedAge, currentAgeOverridden = true) }
        recalculate()
    }

    fun setRetirementAge(age: Double) {
        val clampedAge = age.coerceIn(_uiState.value.currentAge + 1.0, _uiState.value.runwayEndAge - 1.0)
        _uiState.update { it.copy(retirementAge = clampedAge) }
        recalculate()
    }

    fun setRunwayEndAge(age: Double) {
        val clampedAge = age.coerceIn(_uiState.value.retirementAge + 1.0, 110.0)
        _uiState.update { it.copy(runwayEndAge = clampedAge) }
        recalculate()
    }

    fun setPreset(preset: RetirementScenarioPreset) {
        _uiState.update { it.copy(preset = preset) }
        recalculate()
    }

    fun setTargetMode(mode: RetirementTargetMode) {
        _uiState.update { it.copy(targetMode = mode) }
        recalculate()
    }

    fun setCurrentBalance(cents: Cents) {
        _uiState.update { it.copy(currentBalanceCents = max(0L, cents), useConnectedAccounts = false) }
        recalculate()
    }

    fun setMonthlySavings(cents: Cents) {
        _uiState.update { it.copy(monthlySavingsCents = max(0L, cents)) }
        recalculate()
    }

    fun setAnnualSpending(cents: Cents) {
        _uiState.update { it.copy(annualSpendingCents = max(0L, cents), annualSpendingOverridden = true) }
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

    fun setUseConnectedAccounts(enabled: Boolean) {
        _uiState.update { current ->
            val nextBal = if (enabled && current.investmentAccounts.isNotEmpty()) {
                max(0L, current.investmentAccounts.sumOf { it.currentBalanceCents })
            } else current.currentBalanceCents
            current.copy(useConnectedAccounts = enabled, currentBalanceCents = nextBal)
        }
        recalculate()
    }

    fun setSelectedHorizon(horizon: String) {
        _uiState.update { it.copy(selectedHorizon = horizon) }
    }

    fun openTargetSettings() {
        _uiState.update { it.copy(showTargetSettingsSheet = true) }
    }

    fun closeTargetSettings() {
        _uiState.update { it.copy(showTargetSettingsSheet = false) }
    }

    fun saveTargetSettings(
        mode: RetirementTargetMode,
        customTarget: Cents?,
        legacyAmount: Cents,
        runwayEndAge: Double
    ) {
        _uiState.update {
            it.copy(
                targetMode = mode,
                customTargetCents = customTarget,
                legacyAmountCents = legacyAmount,
                runwayEndAge = runwayEndAge,
                showTargetSettingsSheet = false
            )
        }
        recalculate()
    }

    fun onAgeScrubbed(age: Double?, balance: Cents?) {
        _uiState.update {
            it.copy(
                scrubbedAge = age,
                scrubbedBalanceCents = balance
            )
        }
    }

    fun reset() {
        _uiState.value = RetirementUiState(
            investmentAccounts = _uiState.value.investmentAccounts,
            householdMembers = _uiState.value.householdMembers,
            allAccounts = _uiState.value.allAccounts,
            historyPoints = _uiState.value.historyPoints
        )
        recalculate()
    }

    private fun recalculate() {
        val state = _uiState.value

        // Separate HSA vs Non-HSA for pre-65 bridge analysis
        val hsaLinks = repository.snapshot.value.retirementAccountLinks
            .filter { it.accountKind.equals("hsa", ignoreCase = true) }
            .map { it.accountId }
            .toSet()

        val hsaBalance = if (state.useConnectedAccounts) {
            state.allAccounts.filter { it.id in hsaLinks }.sumOf { it.currentBalanceCents }
        } else 0L

        val nonHsaBalance = max(0L, state.currentBalanceCents - hsaBalance)

        val hsaAnnualContrib = state.householdMembers.sumOf { it.hsaContributionAnnualCents }
        val hsaMonthlySavings = (hsaAnnualContrib.toDouble() / 12.0).roundToLong()
        val nonHsaMonthlySavings = max(0L, state.monthlySavingsCents - hsaMonthlySavings)

        val scenario = RetirementCalculatorEngine.calculateScenario(
            currentAge = state.currentAge,
            retirementAge = state.retirementAge,
            runwayEndAge = state.runwayEndAge,
            currentBalanceCents = state.currentBalanceCents,
            monthlySavingsCents = state.monthlySavingsCents,
            annualSpendingCents = state.annualSpendingCents,
            preset = state.preset,
            targetMode = state.targetMode,
            customTargetCents = state.customTargetCents,
            legacyAmountCents = state.legacyAmountCents,
            nonHsaCurrentBalanceCents = nonHsaBalance,
            nonHsaMonthlySavingsCents = nonHsaMonthlySavings,
            hsaCurrentBalanceCents = hsaBalance,
            hsaMonthlySavingsCents = hsaMonthlySavings
        )

        _uiState.update {
            it.copy(
                scenario = scenario,
                nonHsaCurrentBalanceCents = nonHsaBalance,
                nonHsaMonthlySavingsCents = nonHsaMonthlySavings,
                hsaCurrentBalanceCents = hsaBalance,
                hsaMonthlySavingsCents = hsaMonthlySavings
            )
        }
    }
}
