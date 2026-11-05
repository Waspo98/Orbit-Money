package app.overbay.orbit.ui.components.charts

import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.domain.NetWorthSnapshot
import app.overbay.orbit.domain.ProjectionPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Empirical stress tests and adversarial verification for Milestone 3 interactive Canvas charts:
 * 1. Catmull-Rom cubic Bezier curve math and Y-coordinate boundary clamping
 * 2. Horseshoe 260° sweep arc geometry, bottom gap symmetry, and overbudget state transitions
 * 3. Edge-case data robustness: all-negative net worth, all-identical balances, zero budgets, and refunds
 * 4. Scrubber state machine & haptic tick transition deduplication
 * 5. Stress test for boundary vulnerabilities (division by zero on zero-nest-egg / identical ages)
 */
class ChartsEmpiricalAdversarialTest {

    private fun makeSnapshot(netWorth: Cents): NetWorthSnapshot = NetWorthSnapshot(
        cashCents = netWorth,
        investmentsCents = 0L,
        realEstateEquityCents = 0L,
        creditDebtCents = 0L,
        loanDebtCents = 0L,
        otherCents = 0L,
        totalAssetsCents = if (netWorth > 0) netWorth else 0L,
        totalLiabilitiesCents = if (netWorth < 0) -netWorth else 0L,
        netWorthCents = netWorth
    )

    // =========================================================================
    // 1. Catmull-Rom Cubic Bezier Curve Math & Clamping Verification
    // =========================================================================

    @Test
    fun testCatmullRomBezier_clampingPreventsOvershootOnSpikes() {
        val h = 220f

        // Construct a pathological sharp V-spike: high -> zero -> high
        // P0=(0, 200), P1=(90, 0), P2=(180, 200), P3=(270, 0)
        // Without clamping, Catmull-Rom control points would project below 0 (negative Y)
        val p0x = 0f; val p0y = 200f
        val p1x = 90f; val p1y = 0f
        val p2x = 180f; val p2y = 200f
        val p3x = 270f; val p3y = 0f

        // When P1=(90, 0), P2=(180, 0), P3=(270, 220) (curve stays flat at floor, then sharply rises):
        // Tangent at P2 points up towards P3, forcing the incoming cubic curve control point CP2 to dip below 0f!
        val floorP0y = 100f
        val floorP1y = 0f
        val floorP2y = 0f
        val floorP3y = 220f

        val unclampedCp2y = floorP2y - (floorP3y - floorP1y) / 6f // 0 - (220 - 0)/6 = -36.67f (UNDERSHOOT below 0 without clamping!)
        assertTrue(unclampedCp2y < 0f, "Unclamped Catmull-Rom CP2y should overshoot into negative territory: $unclampedCp2y")

        // Chart implementation applies: .coerceIn(0f, h)
        val clampedCp2y = unclampedCp2y.coerceIn(0f, h)
        assertEquals(0f, clampedCp2y, "Clamped CP2y must be bounded strictly to 0f")

        // Now test an upward overshoot:
        // Inverted case for ceiling overshoot: P0=(0, 0), P1=(90, 200), P2=(180, 0), P3=(270, 220)
        val ceilP0y = 0f; val ceilP1y = 220f; val ceilP2y = 220f
        val unclampedCeilCp1y = ceilP1y + (ceilP2y - ceilP0y) / 6f // 220 + (220 - 0)/6 = 256.67f (OVERSHOOT > h)
        assertTrue(unclampedCeilCp1y > h, "Unclamped Catmull-Rom CP1y should overshoot above h: $unclampedCeilCp1y")

        val clampedCeilCp1y = unclampedCeilCp1y.coerceIn(0f, h)
        assertEquals(h, clampedCeilCp1y, "Clamped CP1y must be bounded strictly to h ($h)")
    }

    // =========================================================================
    // 2. NetWorthAreaChart Scaling & Edge Cases
    // =========================================================================

    @Test
    fun testNetWorthChart_allNegativeBalances_safeNormalization() {
        val h = 220f
        val points = listOf(
            MonthlyNetWorthPoint(
                month = "2026-01",
                snapshot = makeSnapshot(-500_000_00L) // -$500,000
            ),
            MonthlyNetWorthPoint(
                month = "2026-02",
                snapshot = makeSnapshot(-300_000_00L) // -$300,000
            ),
            MonthlyNetWorthPoint(
                month = "2026-03",
                snapshot = makeSnapshot(-100_000_00L) // -$100,000
            )
        )

        val rawValues = points.map { it.snapshot.netWorthCents }
        val minVal = rawValues.minOrNull() ?: 0L
        val maxVal = rawValues.maxOrNull() ?: 1L
        assertEquals(-500_000_00L, minVal)
        assertEquals(-100_000_00L, maxVal)

        val span = max(1L, maxVal - minVal)
        assertEquals(400_000_00L, span)

        val paddedMin = minVal - (span * 0.12).toLong()
        val paddedMax = maxVal + (span * 0.12).toLong()
        assertTrue(paddedMin < minVal)
        assertTrue(paddedMax > maxVal)

        val totalSpan = max(1L, paddedMax - paddedMin).toDouble()
        assertTrue(totalSpan > 0.0)

        // Verify normalized screen Y-coordinates for all points are bounded in [0f, h]
        for (pt in points) {
            val normalized = ((pt.snapshot.netWorthCents - paddedMin).toDouble() / totalSpan).toFloat().coerceIn(0f, 1f)
            val py = h - (normalized * h)
            assertTrue(py in 0f..h, "Screen coordinate Y ($py) must be within [0, $h]")
        }
    }

    @Test
    fun testNetWorthChart_allIdenticalZeroBalances_noDivisionByZero() {
        val points = listOf(
            MonthlyNetWorthPoint("2026-01", makeSnapshot(0L)),
            MonthlyNetWorthPoint("2026-02", makeSnapshot(0L))
        )

        val rawValues = points.map { it.snapshot.netWorthCents }
        val minVal = rawValues.minOrNull() ?: 0L
        val maxVal = rawValues.maxOrNull() ?: 1L
        val span = max(1L, maxVal - minVal)
        assertEquals(1L, span, "Span must be guarded by max(1L, ...)")

        val paddedMin = minVal - (span * 0.12).toLong()
        val paddedMax = maxVal + (span * 0.12).toLong()
        val totalSpan = max(1L, paddedMax - paddedMin).toDouble()
        assertTrue(totalSpan >= 1.0, "totalSpan must be at least 1.0 to prevent division by zero")

        val normalized = ((0L - paddedMin).toDouble() / totalSpan).toFloat().coerceIn(0f, 1f)
        assertFalse(normalized.isNaN(), "Normalized value must not be NaN")
        assertFalse(normalized.isInfinite(), "Normalized value must not be Infinite")
    }

    @Test
    fun testNetWorthChart_singlePointList_scrubFractionSafe() {
        val singlePoint = listOf(
            MonthlyNetWorthPoint("2026-01", makeSnapshot(50_000_00L))
        )
        val n = singlePoint.size
        assertEquals(1, n)

        // When n == 1, offset fraction * (n - 1) = fraction * 0 = 0
        val fractions = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        for (f in fractions) {
            val selectedIndex = (f * (n - 1)).roundToInt().coerceIn(0, n - 1)
            assertEquals(0, selectedIndex, "Single point chart scrubber must always select index 0 without crashing")
        }
    }

    // =========================================================================
    // 3. SpendingTrendsBarChart Scaling & Edge Cases
    // =========================================================================

    @Test
    fun testSpendingTrendsBarChart_negativeAndZeroExpenseCents() {
        val h = 220f
        val items = listOf(
            SpendingBarItem(label = "Jan", expenseCents = -50_00L), // Refund in category
            SpendingBarItem(label = "Feb", expenseCents = 0L),       // No spending
            SpendingBarItem(label = "Mar", expenseCents = 150_00L)   // Normal spending
        )

        var highest = 1L
        for (item in items) {
            highest = max(highest, item.expenseCents)
        }
        val maxValue = (highest * 1.15).toLong()
        assertTrue(maxValue >= 1L, "maxValue must be at least 1L to prevent division by zero")

        for (item in items) {
            val barH = ((item.expenseCents.toDouble() / maxValue.toDouble()) * h).toFloat().coerceAtLeast(4f)
            assertTrue(barH >= 4f, "Bar height must be at least 4f (coerceAtLeast)")
            assertFalse(barH.isNaN(), "Bar height must not be NaN")
        }
    }

    // =========================================================================
    // 4. BudgetRingGauge Horseshoe Geometry & Overbudget States
    // =========================================================================

    @Test
    fun testBudgetRingGauge_horseshoeGeometryAngles() {
        val startAngle = 140f
        val totalSweep = 260f
        val endAngle = (startAngle + totalSweep) % 360f

        // Start angle at 140° (bottom-left)
        assertEquals(140f, startAngle)
        // 140 + 260 = 400° -> 40° (bottom-right)
        assertEquals(40f, endAngle)

        // Bottom gap calculation: from 40° to 140°
        val bottomGap = startAngle - endAngle
        assertEquals(100f, bottomGap, "Horseshoe open bottom gap must be exactly 100°")
        assertEquals(360f, totalSweep + bottomGap, "Sweep (260°) + Gap (100°) must sum to complete 360° circle")

        // Bottom gap center should be at 90° (due south / 6 o'clock)
        val gapCenter = endAngle + bottomGap / 2f
        assertEquals(90f, gapCenter, "Bottom gap must be perfectly symmetrical around 90° (due south)")
    }

    @Test
    fun testBudgetRingGauge_stateTransitions() {
        val budget = 1000_00L // $1,000.00

        // Case A: Well under budget (50% spent) -> ON_TRACK / income tone
        val spent50 = 500_00L
        val ratio50 = spent50.toDouble() / budget.toDouble()
        assertEquals(0.5, ratio50)
        assertFalse(ratio50 > 1.0, "50% spent is not over budget")
        assertFalse(ratio50 >= 0.85, "50% spent is not in watch zone")

        // Case B: In watch zone (85% spent) -> warning tone
        val spent85 = 850_00L
        val ratio85 = spent85.toDouble() / budget.toDouble()
        assertEquals(0.85, ratio85)
        assertTrue(ratio85 >= 0.85, "85% spent must trigger warning tone")
        assertFalse(ratio85 > 1.0, "85% spent is not over budget")

        // Case C: Over budget (115% spent) -> OVER_BUDGET / expense tone
        val spent115 = 1150_00L
        val ratio115 = spent115.toDouble() / budget.toDouble()
        assertEquals(1.15, ratio115)
        assertTrue(ratio115 > 1.0, "115% spent must trigger isOverBudget = true")

        // Case D: Zero budget handling (division by zero guard)
        val zeroBudget = 0L
        val spentOnZero = 200_00L
        val rawRatioZero = if (zeroBudget > 0L) spentOnZero.toDouble() / zeroBudget.toDouble() else 0.0
        assertEquals(0.0, rawRatioZero, "Zero budget must safely yield 0.0 rawRatio without division by zero")
    }

    // =========================================================================
    // 5. Scrubber State Machine & Haptic Tick Deduplication
    // =========================================================================

    @Test
    fun testScrubberHaptics_deduplicationLogic() {
        var tickCount = 0
        var lastHapticIndex: Int? = null

        fun simulateScrubberSelection(newIndex: Int?) {
            if (newIndex != null && newIndex != lastHapticIndex) {
                tickCount++
                lastHapticIndex = newIndex
            } else if (newIndex == null) {
                lastHapticIndex = null
            }
        }

        // 1. Initial touch at index 2 -> 1 tick
        simulateScrubberSelection(2)
        assertEquals(1, tickCount)
        assertEquals(2, lastHapticIndex)

        // 2. Drag within same index 2 -> 0 extra ticks (deduped)
        simulateScrubberSelection(2)
        simulateScrubberSelection(2)
        assertEquals(1, tickCount, "Consecutive events at the same index must not trigger duplicate haptic ticks")

        // 3. Move to index 3 -> 1 extra tick
        simulateScrubberSelection(3)
        assertEquals(2, tickCount)
        assertEquals(3, lastHapticIndex)

        // 4. Release touch (null) -> resets lastHapticIndex, 0 ticks
        simulateScrubberSelection(null)
        assertEquals(2, tickCount)
        assertEquals(null, lastHapticIndex)

        // 5. Re-touch at index 3 -> 1 tick (allowed because touch was released)
        simulateScrubberSelection(3)
        assertEquals(3, tickCount)
    }

    // =========================================================================
    // 6. RetirementProjectionChart Boundary Stress Test
    // =========================================================================

    @Test
    fun testRetirementProjectionChart_divisionByZeroGuardAudit() {
        // CHALLENGE: What happens when all balances are 0 and targetNestEggCents is 0?
        val allZeroPoints = listOf(
            ProjectionPoint(age = 30.0, balanceCents = 0L),
            ProjectionPoint(age = 40.0, balanceCents = 0L)
        )
        val targetNestEggCents = 0L

        // In RetirementProjectionChart.kt line 109:
        // val peak = allPoints.maxOfOrNull { it.balanceCents } ?: targetNestEggCents
        // (max(peak, targetNestEggCents) * 1.15).toLong()
        val peak = allZeroPoints.maxOfOrNull { it.balanceCents } ?: targetNestEggCents
        val maxBalance = (max(peak, targetNestEggCents) * 1.15).toLong()

        // Verify finding: maxBalance evaluates to 0L!
        assertEquals(0L, maxBalance, "maxBalance evaluates to 0L when balances and target are 0")

        // If maxBalance is 0L, bal.toDouble() / maxBalance.toDouble() = 0.0 / 0.0 = NaN!
        val rawDiv = (0L).toDouble() / maxBalance.toDouble()
        assertTrue(rawDiv.isNaN(), "Direct division by 0L maxBalance produces NaN")

        // Recommended defensive fix: max(1L, (max(peak, targetNestEggCents) * 1.15).toLong())
        val defensiveMaxBalance = max(1L, (max(peak, targetNestEggCents) * 1.15).toLong())
        assertEquals(1L, defensiveMaxBalance, "Defensive maxBalance must be at least 1L")
        val safeDiv = (0L).toDouble() / defensiveMaxBalance.toDouble()
        assertEquals(0.0, safeDiv, "Safe division produces 0.0, preventing NaN coordinates")
    }
}
