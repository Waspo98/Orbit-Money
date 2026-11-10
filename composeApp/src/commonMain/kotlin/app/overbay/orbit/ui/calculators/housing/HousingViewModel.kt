package app.overbay.orbit.ui.calculators.housing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.HousingCalculatorEngine
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
import kotlin.math.roundToLong

class HousingViewModel(
    private val repository: OrbitRepository,
    scope: CoroutineScope? = null
) : ViewModel() {
    private val vmScope: CoroutineScope = scope ?: (viewModelScope + Dispatchers.Default)
    private val _uiState = MutableStateFlow(HousingUiState())
    val uiState: StateFlow<HousingUiState> = _uiState.asStateFlow()

    init {
        vmScope.launch {
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

        // If net proceeds are positive, set down payment to net proceeds (matching old-pwa)
        val currentProceeds = _uiState.value.netProceedsCents
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

    fun setAgentCommissionPercent(percent: Double?) {
        _uiState.update { it.copy(agentCommissionPercent = percent) }
        recalculate()
    }

    fun setSellerClosingPercent(percent: Double?) {
        _uiState.update { it.copy(sellerClosingPercent = percent) }
        recalculate()
    }

    fun setProratedPropertyTax(cents: Cents?) {
        _uiState.update { it.copy(proratedPropertyTaxCents = cents) }
        recalculate()
    }

    fun setMiscRepairs(cents: Cents?) {
        _uiState.update { it.copy(miscRepairsCents = cents) }
        recalculate()
    }

    fun useNetProceedsAsDownPayment() {
        val proceeds = _uiState.value.netProceedsCents ?: return
        if (proceeds > 0L) {
            _uiState.update { it.copy(downPaymentCents = proceeds) }
            recalculate()
        }
    }

    fun setPurchasePrice(cents: Cents?) {
        _uiState.update { it.copy(purchasePriceCents = cents) }
        recalculate()
    }

    fun setDownPayment(cents: Cents?) {
        _uiState.update { it.copy(downPaymentCents = cents) }
        recalculate()
    }

    fun setInterestRate(ratePercent: Double?) {
        _uiState.update { it.copy(interestRatePercent = ratePercent) }
        recalculate()
    }

    fun setLoanTermYears(years: Int?) {
        _uiState.update { it.copy(loanTermYears = years) }
        recalculate()
    }

    fun setAnnualPropertyTaxes(cents: Cents?) {
        _uiState.update { it.copy(annualPropertyTaxesCents = cents) }
        recalculate()
    }

    fun setAnnualHomeInsurance(cents: Cents?) {
        _uiState.update { it.copy(annualHomeInsuranceCents = cents) }
        recalculate()
    }

    fun setMonthlyHoa(cents: Cents?) {
        _uiState.update { it.copy(monthlyHoaCents = cents) }
        recalculate()
    }

    fun setBuyerClosingPercent(percent: Double?) {
        _uiState.update { it.copy(buyerClosingPercent = percent) }
        recalculate()
    }

    fun requestReset() {
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    fun cancelReset() {
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    fun confirmReset() {
        _uiState.value = HousingUiState(
            availableMortgageAccounts = _uiState.value.availableMortgageAccounts
        )
        recalculate()
    }

    private fun recalculate() {
        val state = _uiState.value

        // Selling calculations matching old-pwa calculateSelling
        val salePrice = state.salePriceCents
        val remainingMortgage = state.remainingMortgageCents
        val agentPct = state.agentCommissionPercent ?: 5.0
        val sellerClosingPct = state.sellerClosingPercent ?: 2.0
        val proratedTax = state.proratedPropertyTaxCents ?: 2000_00L
        val miscRepairs = state.miscRepairsCents ?: 1000_00L

        val agentComm = if (salePrice != null) {
            (salePrice.toDouble() * (agentPct / 100.0)).roundToLong()
        } else null

        val sellerClosing = if (salePrice != null) {
            (salePrice.toDouble() * (sellerClosingPct / 100.0)).roundToLong()
        } else null

        val totalFees = if (agentComm != null && sellerClosing != null) {
            agentComm + sellerClosing + proratedTax + miscRepairs
        } else null

        val netProceeds = if (salePrice != null && remainingMortgage != null && totalFees != null) {
            salePrice - remainingMortgage - totalFees
        } else null

        // Buying calculations matching old-pwa calculateBuying
        val purchasePrice = state.purchasePriceCents
        val downPayment = state.downPaymentCents
        val interestRate = state.interestRatePercent ?: 6.0
        val loanTermYears = state.loanTermYears ?: 30
        val annualTaxes = state.annualPropertyTaxesCents ?: 8000_00L
        val annualInsurance = state.annualHomeInsuranceCents ?: 1500_00L
        val monthlyHoa = state.monthlyHoaCents ?: 23_00L
        val buyerClosingPct = state.buyerClosingPercent ?: 3.0

        val buyerClosingCosts = if (purchasePrice != null) {
            (purchasePrice.toDouble() * (buyerClosingPct / 100.0)).roundToLong()
        } else null

        val loanAmount = if (purchasePrice != null && downPayment != null) {
            max(0L, purchasePrice - downPayment)
        } else null

        val cashToClose = if (downPayment != null && buyerClosingCosts != null) {
            downPayment + buyerClosingCosts
        } else null

        val pi = if (loanAmount != null && loanTermYears > 0) {
            HousingCalculatorEngine.calculateMonthlyMortgagePayment(
                principalCents = loanAmount,
                annualRatePercent = interestRate,
                loanTermYears = loanTermYears
            )
        } else null

        val monthlyTax = if (purchasePrice != null || state.annualPropertyTaxesCents != null) {
            (annualTaxes.toDouble() / 12.0).roundToLong()
        } else null

        val monthlyIns = if (purchasePrice != null || state.annualHomeInsuranceCents != null) {
            (annualInsurance.toDouble() / 12.0).roundToLong()
        } else null

        val effectiveMonthlyHoa = if (purchasePrice != null || state.monthlyHoaCents != null) monthlyHoa else null

        val totalMonthly = if (pi != null && monthlyTax != null && monthlyIns != null && effectiveMonthlyHoa != null) {
            pi + monthlyTax + monthlyIns + effectiveMonthlyHoa
        } else null

        _uiState.update {
            it.copy(
                agentCommissionCents = agentComm,
                sellerClosingCents = sellerClosing,
                totalSellingFeesCents = totalFees,
                netProceedsCents = netProceeds,
                buyerClosingCostsCents = buyerClosingCosts,
                loanAmountCents = loanAmount,
                cashToCloseCents = cashToClose,
                monthlyPrincipalInterestCents = pi,
                monthlyPropertyTaxCents = monthlyTax,
                monthlyHomeInsuranceCents = monthlyIns,
                monthlyHoaPaymentCents = effectiveMonthlyHoa,
                totalMonthlyPaymentCents = totalMonthly
            )
        }
    }
}
