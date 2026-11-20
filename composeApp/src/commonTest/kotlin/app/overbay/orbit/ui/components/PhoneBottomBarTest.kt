package app.overbay.orbit.ui.components

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import app.overbay.orbit.OrbitNavKey
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PhoneBottomBarTest {

    @Test
    fun isTabSelected_matchesExactAndParameterizedNavKeys() {
        assertTrue(isTabSelected(OrbitNavKey.Dashboard, OrbitNavKey.Dashboard))
        assertFalse(isTabSelected(OrbitNavKey.Dashboard, OrbitNavKey.Budgets))

        assertTrue(
            isTabSelected(
                OrbitNavKey.Transactions(),
                OrbitNavKey.Transactions(transactionId = 42L, uncategorizedOnly = true)
            )
        )
        assertFalse(isTabSelected(OrbitNavKey.Transactions(), OrbitNavKey.Dashboard))

        assertTrue(isTabSelected(OrbitNavKey.Accounts(), OrbitNavKey.Accounts(accountId = 7L)))
        assertFalse(isTabSelected(OrbitNavKey.Accounts(), OrbitNavKey.CardPerks()))

        assertTrue(isTabSelected(OrbitNavKey.CardPerks(), OrbitNavKey.CardPerks(accountId = 9L)))
        assertFalse(isTabSelected(OrbitNavKey.CardPerks(), OrbitNavKey.Accounts()))
    }

    @Test
    fun cappedNavLabelSp_allowsModestScalingAndCapsLargeAccessibilityScales() {
        assertClose(10f, cappedNavLabelSp(10f, fontScale = 0.85f).value)
        assertClose(10f, cappedNavLabelSp(10f, fontScale = 1.0f).value)
        assertClose(10f, cappedNavLabelSp(10f, fontScale = 1.15f).value)

        val scale15 = cappedNavLabelSp(10f, fontScale = 1.5f).value
        assertClose(11.5f, scale15 * 1.5f)

        val scale20 = cappedNavLabelSp(10f, fontScale = 2.0f).value
        assertClose(11.5f, scale20 * 2.0f)

        assertClose(10f, cappedNavLabelSp(10f, fontScale = 0f).value)
        assertClose(10f, cappedNavLabelSp(10f, fontScale = -1f).value)
    }

    @Test
    fun mappedInteractionSource_offsetsPressCoordinatesAndPairsReleaseCancel() = runTest {
        val base = MutableInteractionSource()
        val mapped = MappedInteractionSource(base) { Offset(8f, 18f) }
        val collected = mutableListOf<Interaction>()
        val job = launch {
            mapped.interactions.take(4).toList(collected)
        }
        testScheduler.advanceUntilIdle()

        val press1 = PressInteraction.Press(Offset(50f, 60f))
        val press2 = PressInteraction.Press(Offset(20f, 25f))
        val hover = HoverInteraction.Enter()
        base.emit(press1)
        base.emit(PressInteraction.Release(press1))
        base.emit(press2)
        base.emit(PressInteraction.Cancel(press2))
        testScheduler.advanceUntilIdle()
        job.join()

        val mappedPress1 = assertIs<PressInteraction.Press>(collected[0])
        assertEquals(Offset(42f, 42f), mappedPress1.pressPosition)

        val mappedRelease = assertIs<PressInteraction.Release>(collected[1])
        assertEquals(mappedPress1, mappedRelease.press)

        val mappedPress2 = assertIs<PressInteraction.Press>(collected[2])
        assertEquals(Offset(12f, 7f), mappedPress2.pressPosition)

        val mappedCancel = assertIs<PressInteraction.Cancel>(collected[3])
        assertEquals(mappedPress2, mappedCancel.press)

        // Non-press interactions pass through unchanged
        val passthrough = mutableListOf<Interaction>()
        val hoverJob = launch {
            mapped.interactions.take(1).toList(passthrough)
        }
        testScheduler.advanceUntilIdle()
        base.emit(hover)
        testScheduler.advanceUntilIdle()
        hoverJob.join()
        assertEquals(hover, passthrough.single())
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.001f) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected $expected ± $tolerance but was $actual"
        )
    }
}
