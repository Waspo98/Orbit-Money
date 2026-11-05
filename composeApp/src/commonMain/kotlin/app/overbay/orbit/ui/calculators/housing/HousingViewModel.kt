package app.overbay.orbit.ui.calculators.housing

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.AmortizationMonth
import app.overbay.orbit.data.model.BuyingScenarioResult
import app.overbay.orbit.data.model.SellingProceedsResult
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.HousingCalculatorEngine
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HousingViewModel(
    private val repository: OrbitRepository,
    scope: CoroutineScope? = null
) : ViewModel() {
    private val vmScope: CoroutineScope = scope ?: (viewModelScope + Dispatchers.Default)
    private val _uiState = MutableStateFlow(HousingUiState())
    val uiState: StateFlow<HousingUiState> = _uiState.asStateFlow()

    init {
        vmScope.launch {
            recalculate()
            repository.accounts.collect { accounts ->
                val mortgages = accounts.filter { !it.isArchived && it.type == AccountType.MORTGAGE }
                _uiState.update { current ->
                    current.copy(availableMortgageAccounts = mortgages)
                }
            }
        }
    }

    fun selectMortgage(accountId: Long?) {
        val mortgage = _uiState.value.availableMortgageAccounts.find { it.id == accountId }
        if (mortgage == null) {
            _uiState.update { it.copy(selectedMortgageAccountId = null) }
            recalculate()
            return
        }

        val remainingBal = abs(mortgage.currentBalanceCents)
        val estimatedSale = mortgage.estimatedValueCents

        _uiState.update { current ->
            current.copy(
                selectedMortgageAccountId = accountId,
                remainingMortgageCents = remainingBal,
                salePriceCents = estimatedSale ?: current.salePriceCents
            )
        }
        recalculate()

        // If net proceeds are positive, set down payment to net proceeds
        val currentProceeds = _uiState.value.sellingProceeds?.netProceedsCents
        if (currentProceeds != null && currentProceeds > 0L) {
            _uiState.update { it.copy(downPaymentCents = currentProceeds) }
            recalculate()
        }
    }

    fun setSalePrice(cents: Cents?) {
        _uiState.update { it.copy(salePriceCents = cents) }
        recalculate()
    }

    fun setRemainingMortgage(cents: Cents?) {
        _uiState.update { it.copy(remainingMortgageCents = cents) }
        recalculate()
    }

    fun setAgentCommissionPercent(percent: Double) {
        _uiState.update { it.copy(agentCommissionPercent = max(0.0, percent)) }
        recalculate()
    }

    fun setSellerClosingPercent(percent: Double) {
        _uiState.update { it.copy(sellerClosingPercent = max(0.0, percent)) }
        recalculate()
    }

    fun setProratedPropertyTax(cents: Cents) {
        _uiState.update { it.copy(proratedPropertyTaxCents = max(0L, cents)) }
        recalculate()
    }

    fun setMiscRepairs(cents: Cents) {
        _uiState.update { it.copy(miscRepairsCents = max(0L, cents)) }
        recalculate()
    }

    fun useNetProceedsAsDownPayment() {
        val proceeds = _uiState.value.sellingProceeds?.netProceedsCents ?: return
        if (proceeds > 0L) {
            _uiState.update { it.copy(downPaymentCents = proceeds) }
            recalculate()
        }
    }

    fun setPurchasePrice(cents: Cents) {
        _uiState.update { it.copy(purchasePriceCents = max(0L, cents)) }
        recalculate()
    }

    fun setDownPayment(cents: Cents) {
        _uiState.update { it.copy(downPaymentCents = max(0L, cents)) }
        recalculate()
    }

    fun setInterestRate(ratePercent: Double) {
        _uiState.update { it.copy(interestRatePercent = max(0.0, ratePercent)) }
        recalculate()
    }

    fun setLoanTermYears(years: Int) {
        _uiState.update { it.copy(loanTermYears = max(1, years)) }
        recalculate()
    }

    fun setAnnualPropertyTaxes(cents: Cents) {
        _uiState.update { it.copy(annualPropertyTaxesCents = max(0L, cents)) }
        recalculate()
    }

    fun setAnnualHomeInsurance(cents: Cents) {
        _uiState.update { it.copy(annualHomeInsuranceCents = max(0L, cents)) }
        recalculate()
    }

    fun setMonthlyHoa(cents: Cents) {
        _uiState.update { it.copy(monthlyHoaCents = max(0L, cents)) }
        recalculate()
    }

    fun setBuyerClosingPercent(percent: Double) {
        _uiState.update { it.copy(buyerClosingPercent = max(0.0, percent)) }
        recalculate()
    }

    fun setExtraMonthlyPrincipal(cents: Cents) {
        _uiState.update { it.copy(extraMonthlyPrincipalCents = max(0L, cents)) }
        recalculate()
    }

    fun reset() {
        _uiState.value = HousingUiState(
            availableMortgageAccounts = _uiState.value.availableMortgageAccounts
        )
        recalculate()
    }

    private fun recalculate() {
        val state = _uiState.value

        // 1. Selling proceeds
        val sellingProceeds = if (state.salePriceCents != null && state.remainingMortgageCents != null) {
            HousingCalculatorEngine.calculateSelling(
                salePriceCents = state.salePriceCents,
                remainingMortgageCents = state.remainingMortgageCents,
                agentCommissionPercent = state.agentCommissionPercent,
                sellerClosingPercent = state.sellerClosingPercent,
                proratedPropertyTaxCents = state.proratedPropertyTaxCents,
                miscRepairsCents = state.miscRepairsCents
            )
        } else null

        // 2. Buying result
        val buyingResult = HousingCalculatorEngine.calculateBuying(
            purchasePriceCents = state.purchasePriceCents,
            downPaymentCents = state.downPaymentCents,
            interestRatePercent = state.interestRatePercent,
            loanTermYears = state.loanTermYears,
            annualPropertyTaxesCents = state.annualPropertyTaxesCents,
            annualHomeInsuranceCents = state.annualHomeInsuranceCents,
            monthlyHoaCents = state.monthlyHoaCents,
            buyerClosingPercent = state.buyerClosingPercent
        )

        // 3. Baseline amortization schedule
        val schedule = HousingCalculatorEngine.generateAmortizationSchedule(
            principalCents = buyingResult.loanAmountCents,
            annualRatePercent = state.interestRatePercent,
            loanTermYears = state.loanTermYears
        )
        val totalInterestPaid = schedule.sumOf { it.interestPaidCents }

        // 4. Extra principal calculation
        val (monthsSaved, interestSaved) = if (state.extraMonthlyPrincipalCents > 0L && buyingResult.loanAmountCents > 0L) {
            computeExtraPrincipalSavings(
                principalCents = buyingResult.loanAmountCents,
                annualRatePercent = state.interestRatePercent,
                loanTermYears = state.loanTermYears,
                baseMonthlyPayment = buyingResult.monthlyPrincipalInterestCents,
                extraMonthlyPrincipal = state.extraMonthlyPrincipalCents,
                baselineMonths = schedule.size,
                baselineInterest = totalInterestPaid
            )
        } else 0 to 0L

        _uiState.update {
            it.copy(
                sellingProceeds = sellingProceeds,
                buyingResult = buyingResult,
                amortizationSchedule = schedule,
                totalInterestPaidCents = totalInterestPaid,
                monthsSavedWithExtraPrincipal = monthsSaved,
                interestSavedWithExtraPrincipalCents = interestSaved
            )
        }
    }

    private fun computeExtraPrincipalSavings(
        principalCents: Cents,
        annualRatePercent: Double,
        loanTermYears: Int,
        baseMonthlyPayment: Cents,
        extraMonthlyPrincipal: Cents,
        baselineMonths: Int,
        baselineInterest: Cents
    ): Pair<Int, Cents> {
        val totalMonths = loanTermYears * 12
        val monthlyRate = annualRatePercent / 100.0 / 12.0
        var balance = principalCents
        var acceleratedMonths = 0
        var acceleratedInterest = 0L

        for (m in 1..totalMonths) {
            val interest = (balance.toDouble() * monthlyRate).toLong()
            val principal = min(balance, baseMonthlyPayment - interest + extraMonthlyPrincipal)
            acceleratedInterest += interest
            balance = max(0L, balance - principal)
            acceleratedMonths = m
            if (balance <= 0L) break
        }

        val monthsSaved = max(0, baselineMonths - acceleratedMonths)
        val interestSaved = max(0L, baselineInterest - acceleratedInterest)
        return monthsSaved to interestSaved
    }
}
