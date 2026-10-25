package app.overbay.orbit.domain

import app.overbay.orbit.data.model.RetirementPreset
import app.overbay.orbit.data.model.RetirementTargetMode
import app.overbay.orbit.data.model.TargetNestEggMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

data class ProjectionPoint(
    val age: Double,
    val balanceCents: Cents
)

data class DrawdownSimulationResult(
    val points: List<ProjectionPoint>,
    val depletionAge: Double?,
    val finalBalanceCents: Cents
)

data class RetirementScenarioModel(
    val realAnnualReturn: Double,
    val effectiveMonthlyRate: Double,
    val monthsToRetirement: Int,
    val targetNestEggCents: Cents,
    val projectedBalanceCents: Cents,
    val fundingGapCents: Cents,
    val readinessPercent: Double,
    val requiredMonthlyContributionCents: Cents,
    val monthlySavingsGapCents: Cents,
    val coastFireTargetCents: Cents,
    val isCoastFireReached: Boolean,
    val earliestRetirementAge: Double?,
    val accumulationPoints: List<ProjectionPoint>,
    val drawdownPoints: List<ProjectionPoint>,
    val depletionAge: Double?
)

object RetirementCalculatorEngine {

    const val DEFAULT_WITHDRAWAL_RATE = 0.04

    fun computeRealReturn(nominalReturn: Double, inflation: Double): Double {
        return ((1.0 + nominalReturn) / (1.0 + inflation)) - 1.0
    }

    fun computeEffectiveMonthlyRate(annualRate: Double): Double {
        if (annualRate <= -1.0) return -1.0
        return (1.0 + annualRate).pow(1.0 / 12.0) - 1.0
    }

    fun futureValue(
        currentBalanceCents: Cents,
        monthlyContributionCents: Cents,
        annualReturn: Double,
        months: Int
    ): Cents {
        val p = currentBalanceCents.toDouble()
        val c = monthlyContributionCents.toDouble()
        val rm = computeEffectiveMonthlyRate(annualReturn)
        if (months <= 0) return currentBalanceCents
        if (abs(rm) < 0.000001) {
            return (p + c * months).roundToLong()
        }
        val compound = (1.0 + rm).pow(months.toDouble())
        val future = p * compound + c * ((compound - 1.0) / rm)
        return max(0.0, future).roundToLong()
    }

    fun requiredMonthlyContribution(
        currentBalanceCents: Cents,
        targetNestEggCents: Cents,
        annualReturn: Double,
        months: Int
    ): Cents {
        val p = currentBalanceCents.toDouble()
        val goal = targetNestEggCents.toDouble()
        val rm = computeEffectiveMonthlyRate(annualReturn)
        if (months <= 0) return 0L
        if (abs(rm) < 0.000001) {
            return max(0.0, (goal - p) / months).roundToLong()
        }
        val compound = (1.0 + rm).pow(months.toDouble())
        val grownP = p * compound
        val factor = (compound - 1.0) / rm
        return max(0.0, (goal - grownP) / factor).roundToLong()
    }

    fun requiredRetirementBalance(
        annualSpendingCents: Cents,
        legacyAmountCents: Cents,
        retirementAge: Double,
        runwayEndAge: Double,
        realReturn: Double
    ): Cents {
        val months = max(0, ((runwayEndAge - retirementAge) * 12.0).roundToLong().toInt())
        val monthlySpending = annualSpendingCents.toDouble() / 12.0
        val finalBal = legacyAmountCents.toDouble()
        val rm = computeEffectiveMonthlyRate(realReturn)
        if (months <= 0) return legacyAmountCents
        if (abs(rm) < 0.000001) {
            return (monthlySpending * months + finalBal).roundToLong()
        }
        val discount = (1.0 + rm).pow(months.toDouble())
        val spendingNeed = monthlySpending * ((1.0 - (1.0 / discount)) / rm)
        val finalNeed = finalBal / discount
        return max(0.0, spendingNeed + finalNeed).roundToLong()
    }

    fun computeTargetNestEgg(
        mode: TargetNestEggMode,
        annualSpendingCents: Cents,
        customTargetCents: Cents? = null,
        legacyAmountCents: Cents = 1_000_000_00L,
        retirementAge: Double = 67.0,
        runwayEndAge: Double = 95.0,
        realReturn: Double = 0.0439
    ): Cents {
        return when (mode) {
            TargetNestEggMode.WITHDRAWAL_4_PERCENT -> ((annualSpendingCents.toDouble()) / DEFAULT_WITHDRAWAL_RATE).roundToLong()
            TargetNestEggMode.NEST_EGG -> {
                val growth = max(0.001, realReturn)
                ((annualSpendingCents.toDouble()) / growth).roundToLong()
            }
            TargetNestEggMode.LEAVE_LEGACY -> requiredRetirementBalance(
                annualSpendingCents = annualSpendingCents,
                legacyAmountCents = legacyAmountCents,
                retirementAge = retirementAge,
                runwayEndAge = runwayEndAge,
                realReturn = realReturn
            )
            TargetNestEggMode.CUSTOM -> customTargetCents ?: ((annualSpendingCents.toDouble()) / DEFAULT_WITHDRAWAL_RATE).roundToLong()
        }
    }

    fun simulateDrawdown(
        retirementAge: Double,
        runwayEndAge: Double,
        startingBalanceCents: Cents,
        annualSpendingCents: Cents,
        realReturn: Double
    ): DrawdownSimulationResult {
        val rm = computeEffectiveMonthlyRate(realReturn)
        var balance = startingBalanceCents.toDouble()
        val monthlySpending = annualSpendingCents.toDouble() / 12.0
        val points = mutableListOf<ProjectionPoint>()
        var depletionAge: Double? = null

        val startAge = retirementAge.toInt()
        val endAge = runwayEndAge.toInt()

        for (age in startAge..endAge) {
            points.add(ProjectionPoint(age.toDouble(), balance.roundToLong()))
            for (month in 0 until 12) {
                balance = max(0.0, balance * (1.0 + rm) - monthlySpending)
                if (balance <= 0.0 && depletionAge == null) {
                    depletionAge = age.toDouble() + ((month + 1).toDouble() / 12.0)
                }
            }
        }

        return DrawdownSimulationResult(
            points = points,
            depletionAge = depletionAge,
            finalBalanceCents = points.lastOrNull()?.balanceCents ?: 0L
        )
    }

    fun calculateScenario(
        currentAge: Double,
        retirementAge: Double,
        runwayEndAge: Double = 95.0,
        currentBalanceCents: Cents,
        monthlySavingsCents: Cents,
        annualSpendingCents: Cents,
        preset: RetirementPreset = RetirementPreset.BALANCED,
        targetMode: TargetNestEggMode = TargetNestEggMode.WITHDRAWAL_4_PERCENT,
        customTargetCents: Cents? = null,
        legacyAmountCents: Cents = 1_000_000_00L
    ): RetirementScenarioModel {
        val realReturn = computeRealReturn(preset.nominalAnnualReturn, preset.inflationRate)
        val rm = computeEffectiveMonthlyRate(realReturn)
        val months = max(0, ((retirementAge - currentAge) * 12.0).roundToLong().toInt())

        val targetNestEgg = computeTargetNestEgg(
            mode = targetMode,
            annualSpendingCents = annualSpendingCents,
            customTargetCents = customTargetCents,
            legacyAmountCents = legacyAmountCents,
            retirementAge = retirementAge,
            runwayEndAge = runwayEndAge,
            realReturn = realReturn
        )

        val projectedBalance = futureValue(currentBalanceCents, monthlySavingsCents, realReturn, months)
        val fundingGap = max(0L, targetNestEgg - projectedBalance)
        val readiness = if (targetNestEgg > 0L) min(150.0, (projectedBalance.toDouble() / targetNestEgg.toDouble()) * 100.0) else 0.0

        val requiredMonthly = requiredMonthlyContribution(currentBalanceCents, targetNestEgg, realReturn, months)
        val savingsGap = if (months > 0) max(0L, requiredMonthly - monthlySavingsCents) else fundingGap

        // Coast FIRE: current balance required today to grow to target with ZERO future contributions
        val yearsToRetire = max(0.0, retirementAge - currentAge)
        val coastDiscount = (1.0 + realReturn).pow(yearsToRetire)
        val coastTarget = (targetNestEgg.toDouble() / coastDiscount).roundToLong()
        val isCoastReached = currentBalanceCents >= coastTarget

        // Accumulation points
        val accumPoints = mutableListOf<ProjectionPoint>()
        for (age in currentAge.toInt()..retirementAge.toInt()) {
            val m = ((age - currentAge) * 12.0).roundToLong().toInt()
            accumPoints.add(ProjectionPoint(age.toDouble(), futureValue(currentBalanceCents, monthlySavingsCents, realReturn, m)))
        }

        // Find earliest retirement age if savings continue
        val longRangePoints = (currentAge.toInt()..runwayEndAge.toInt()).map { age ->
            val m = ((age - currentAge) * 12.0).roundToLong().toInt()
            ProjectionPoint(age.toDouble(), futureValue(currentBalanceCents, monthlySavingsCents, realReturn, m))
        }
        val earliestAge = longRangePoints.find { it.balanceCents >= targetNestEgg }?.age

        // Drawdown simulation
        val drawdown = simulateDrawdown(
            retirementAge = retirementAge,
            runwayEndAge = runwayEndAge,
            startingBalanceCents = projectedBalance,
            annualSpendingCents = annualSpendingCents,
            realReturn = realReturn
        )

        return RetirementScenarioModel(
            realAnnualReturn = realReturn,
            effectiveMonthlyRate = rm,
            monthsToRetirement = months,
            targetNestEggCents = targetNestEgg,
            projectedBalanceCents = projectedBalance,
            fundingGapCents = fundingGap,
            readinessPercent = readiness,
            requiredMonthlyContributionCents = requiredMonthly,
            monthlySavingsGapCents = savingsGap,
            coastFireTargetCents = coastTarget,
            isCoastFireReached = isCoastReached,
            earliestRetirementAge = earliestAge,
            accumulationPoints = accumPoints,
            drawdownPoints = drawdown.points,
            depletionAge = drawdown.depletionAge
        )
    }
}
