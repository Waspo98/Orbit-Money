package app.overbay.orbit.domain

import app.overbay.orbit.data.model.MhaTaxProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MhaAndCalculatorsTest {

    // -------------------------------------------------------------------------
    // MhaTaxEngine Unit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testMhaTaxEngine_parsonage3LimitRule_actualExpensesLowest() {
        // Limit: min(actual, designated, fairMarketRentalValue)
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 24_000_00L,
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 36_000_00L
        )
        assertEquals(24_000_00L, cap)
    }

    @Test
    fun testMhaTaxEngine_parsonage3LimitRule_designatedCapLowest() {
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 32_000_00L,
            designatedCapCents = 25_000_00L,
            fairMarketRentalValueCents = 30_000_00L
        )
        assertEquals(25_000_00L, cap)
    }

    @Test
    fun testMhaTaxEngine_parsonage3LimitRule_fmrvLowest() {
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 35_000_00L,
            designatedCapCents = 35_000_00L,
            fairMarketRentalValueCents = 28_000_00L
        )
        assertEquals(28_000_00L, cap)
    }

    @Test
    fun testMhaTaxEngine_parsonage3LimitRule_zeroDesignationCap() {
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 25_000_00L,
            designatedCapCents = 0L,
            fairMarketRentalValueCents = 30_000_00L
        )
        assertEquals(0L, cap)
    }

    @Test
    fun testMhaTaxEngine_parsonage3LimitRule_zeroFmrv() {
        val cap = MhaTaxEngine.calculateParsonageCap(
            actualExpensesCents = 25_000_00L,
            designatedCapCents = 30_000_00L,
            fairMarketRentalValueCents = 0L
        )
        assertEquals(0L, cap)
    }

    @Test
    fun testMhaTaxEngine_simpleModeTaxSavings() {
        val profile = MhaTaxProfile(
            mode = "simple",
            simpleFederalRatePercent = 22.0,
            simpleStateRatePercent = 4.95
        )

        // $20,000 excludable housing spent
        val result = MhaTaxEngine.calculateMhaSavings(
            actualEligibleCents = 20_000_00L,
            profile = profile,
            designatedCapCents = 25_000_00L,
            fairMarketRentalValueCents = 25_000_00L
        )

        // Fed savings: $20,000 * 22% = $4,400
        assertEquals(4400_00L, result.federalSavingsCents)
        // State savings: $20,000 * 4.95% = $990
        assertEquals(990_00L, result.stateSavingsCents)
        // Total savings = $5,390
        assertEquals(5390_00L, result.totalSavingsCents)
        // SECA estimate: $20,000 * 15.3% = $3,060
        assertEquals(3060_00L, result.secaEstimateCents)
    }

    // -------------------------------------------------------------------------
    // HousingCalculatorEngine Unit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testHousingCalculator_sellingProceeds() {
        // Home sold for $400,000 with $250,000 remaining mortgage
        val proceeds = HousingCalculatorEngine.calculateSelling(
            salePriceCents = 400_000_00L,
            remainingMortgageCents = 250_000_00L,
            agentCommissionPercent = 5.0, // $20,000
            sellerClosingPercent = 2.0,   // $8,000
            proratedPropertyTaxCents = 2000_00L,
            miscRepairsCents = 1000_00L
        )

        assertEquals(20_000_00L, proceeds.agentCommissionCents)
        assertEquals(8_000_00L, proceeds.sellerClosingCents)
        assertEquals(31_000_00L, proceeds.totalSellingFeesCents)
        // Net: 400k - 250k - 31k = $119,000
        assertEquals(119_000_00L, proceeds.netProceedsCents)
    }

    @Test
    fun testHousingCalculator_buyingPitiAmortization() {
        // $300,000 loan at 6.0% for 30 years
        val monthlyPI = HousingCalculatorEngine.calculateMonthlyMortgagePayment(
            loanAmountCents = 300_000_00L,
            interestRatePercent = 6.0,
            loanTermYears = 30
        )

        // Formula P * (r*(1+r)^n)/((1+r)^n - 1) -> ~$1,798.65
        assertTrue(monthlyPI in 1798_00L..1800_00L)
    }

    // -------------------------------------------------------------------------
    // RetirementCalculatorEngine Unit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testRetirementCalculator_futureValue_compoundGrowth() {
        // Starting balance $100,000, adding $1,000/month, 7% annual return for 10 years (120 months)
        val fv = RetirementCalculatorEngine.futureValue(
            currentBalanceCents = 100_000_00L,
            monthlyContributionCents = 1000_00L,
            annualReturn = 0.07,
            months = 120
        )

        // Expected future value is ~$365,000 - $375,000 depending on compounding frequency
        assertTrue(fv in 365_000_00L..375_000_00L)
    }

    @Test
    fun testRetirementCalculator_safeWithdrawalRule() {
        // $1,500,000 nest egg at 4% rule
        val nestEgg = 1_500_000_00L
        val annualWithdrawal = (nestEgg * 0.04).toLong()
        val monthlyWithdrawal = annualWithdrawal / 12

        assertEquals(60_000_00L, annualWithdrawal) // $60,000/year
        assertEquals(5_000_00L, monthlyWithdrawal)  // $5,000/month
    }
}
