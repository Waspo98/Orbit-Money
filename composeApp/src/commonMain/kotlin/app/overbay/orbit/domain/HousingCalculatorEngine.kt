package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AmortizationMonth
import app.overbay.orbit.data.model.BuyingScenarioResult
import app.overbay.orbit.data.model.SellingProceedsResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

object HousingCalculatorEngine {

    fun calculateSelling(
        salePriceCents: Cents,
        remainingMortgageCents: Cents,
        agentCommissionPercent: Double = 5.0,
        sellerClosingPercent: Double = 2.0,
        proratedPropertyTaxCents: Cents = 2000_00L,
        miscRepairsCents: Cents = 1000_00L
    ): SellingProceedsResult {
        val commission = (salePriceCents.toDouble() * (agentCommissionPercent / 100.0)).roundToLong()
        val closing = (salePriceCents.toDouble() * (sellerClosingPercent / 100.0)).roundToLong()
        val totalFees = commission + closing + proratedPropertyTaxCents + miscRepairsCents
        val net = salePriceCents - remainingMortgageCents - totalFees

        return SellingProceedsResult(
            salePriceCents = salePriceCents,
            agentCommissionCents = commission,
            sellerClosingCents = closing,
            totalSellingFeesCents = totalFees,
            remainingMortgageCents = remainingMortgageCents,
            netProceedsCents = net,
            proratedPropertyTaxCents = proratedPropertyTaxCents,
            miscRepairsCents = miscRepairsCents
        )
    }

    fun calculateBuying(
        purchasePriceCents: Cents,
        downPaymentCents: Cents,
        interestRatePercent: Double,
        loanTermYears: Int = 30,
        annualPropertyTaxesCents: Cents = 8000_00L,
        annualHomeInsuranceCents: Cents = 1500_00L,
        monthlyHoaCents: Cents = 23_00L,
        buyerClosingPercent: Double = 3.0
    ): BuyingScenarioResult {
        val closingCosts = (purchasePriceCents.toDouble() * (buyerClosingPercent / 100.0)).roundToLong()
        val loanAmount = max(0L, purchasePriceCents - downPaymentCents)
        val cashToClose = downPaymentCents + closingCosts

        val pi = calculateMonthlyMortgagePayment(
            principalCents = loanAmount,
            annualRatePercent = interestRatePercent,
            loanTermYears = loanTermYears
        )
        val monthlyTax = annualPropertyTaxesCents / 12
        val monthlyIns = annualHomeInsuranceCents / 12
        val totalMonthly = pi + monthlyTax + monthlyIns + monthlyHoaCents

        return BuyingScenarioResult(
            purchasePriceCents = purchasePriceCents,
            downPaymentCents = downPaymentCents,
            loanAmountCents = loanAmount,
            buyerClosingCostsCents = closingCosts,
            cashToCloseCents = cashToClose,
            monthlyPrincipalInterestCents = pi,
            monthlyPropertyTaxCents = monthlyTax,
            monthlyHomeInsuranceCents = monthlyIns,
            monthlyHoaCents = monthlyHoaCents,
            totalMonthlyPaymentCents = totalMonthly
        )
    }

    fun calculateMonthlyMortgagePayment(
        principalCents: Cents = 0L,
        annualRatePercent: Double = 0.0,
        loanTermYears: Int = 30,
        loanAmountCents: Cents = principalCents,
        interestRatePercent: Double = annualRatePercent
    ): Cents {
        val principal = if (loanAmountCents != 0L) loanAmountCents else principalCents
        val rate = if (interestRatePercent != 0.0) interestRatePercent else annualRatePercent
        if (principal <= 0L || loanTermYears <= 0) return 0L
        val months = loanTermYears * 12
        val monthlyRate = rate / 100.0 / 12.0
        if (monthlyRate == 0.0) return principal / months
        val factor = (1.0 + monthlyRate).pow(months.toDouble())
        return ((principal.toDouble() * monthlyRate * factor) / (factor - 1.0)).roundToLong()
    }

    fun generateAmortizationSchedule(
        principalCents: Cents,
        annualRatePercent: Double,
        loanTermYears: Int
    ): List<AmortizationMonth> {
        val totalMonths = loanTermYears * 12
        val monthlyPayment = calculateMonthlyMortgagePayment(principalCents, annualRatePercent, loanTermYears)
        val monthlyRate = annualRatePercent / 100.0 / 12.0

        var balance = principalCents
        val schedule = mutableListOf<AmortizationMonth>()

        for (m in 1..totalMonths) {
            val interest = (balance.toDouble() * monthlyRate).roundToLong()
            val principal = min(balance, monthlyPayment - interest)
            balance = max(0L, balance - principal)
            schedule.add(
                AmortizationMonth(
                    monthNumber = m,
                    paymentCents = monthlyPayment,
                    principalPaidCents = principal,
                    interestPaidCents = interest,
                    remainingBalanceCents = balance
                )
            )
            if (balance <= 0L) break
        }
        return schedule
    }
}
