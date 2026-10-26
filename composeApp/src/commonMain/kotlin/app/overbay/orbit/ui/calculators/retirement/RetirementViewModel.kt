package app.overbay.orbit.ui.calculators.retirement

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.domain.Cents
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
                    val next = current.copy(investmentAccounts = investments)
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
        recalculate()
    }

    fun setCurrentAge(age: Double) {
        val clampedAge = age.coerceIn(18.0, _uiState.value.retirementAge - 1.0)
        _uiState.update { it.copy(currentAge = clampedAge) }
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
        _uiState.update { it.copy(annualSpendingCents = max(0L, cents)) }
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
            investmentAccounts = _uiState.value.investmentAccounts
        )
        recalculate()
    }

    private fun recalculate() {
        val state = _uiState.value
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
            legacyAmountCents = state.legacyAmountCents
        )
        _uiState.update { it.copy(scenario = scenario) }
    }
}
