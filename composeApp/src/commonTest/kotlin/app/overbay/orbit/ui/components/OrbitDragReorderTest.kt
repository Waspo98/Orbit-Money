package app.overbay.orbit.ui.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrbitDragReorderTest {

    @Test
    fun fastMultiSlotDragInSingleEvent_stepsAcrossMultipleItemsWithoutWaitingForRecomposition() {
        val list = mutableListOf("a", "b", "c", "d")
        val moves = mutableListOf<Pair<Int, Int>>()
        // Simulates rememberUpdatedState lag: getKeys() still returns the pre-recomposition snapshot
        // until Compose recomposes after dragBy returns.
        val staleSnapshot = list.toList()
        val state = OrbitDragReorderState(
            getKeys = { staleSnapshot },
            onMove = { from, to ->
                moves.add(from to to)
                val item = list.removeAt(from)
                list.add(to, item)
            },
            onTick = {},
            spacingPx = 10f,
            scope = null
        )
        listOf("a", "b", "c", "d").forEach { state.onItemMeasured(it, 100) }

        state.startDrag("a")
        // Drag down 2.2 slots (242px, where each step is 110px) in one frame
        state.dragBy("a", 242f)

        assertEquals(listOf("b", "c", "a", "d"), list)
        assertEquals(listOf(0 to 1, 1 to 2), moves)
    }

    @Test
    fun draggingPastTopAndBottomBounds_clampsOverscrollOffset() {
        val list = mutableListOf("a", "b")
        val state = OrbitDragReorderState(
            getKeys = { list.toList() },
            onMove = { from, to ->
                val item = list.removeAt(from)
                list.add(to, item)
            },
            onTick = {},
            spacingPx = 8f,
            scope = null
        )
        state.onItemMeasured("a", 100)
        state.onItemMeasured("b", 100)

        state.startDrag("a")
        state.dragBy("a", -400f)
        assertTrue(state.dragOffsetY >= -60f, "Top overscroll should be clamped, was ${state.dragOffsetY}")
        state.endDrag()

        state.startDrag("b")
        state.dragBy("b", 400f)
        assertTrue(state.dragOffsetY <= 60f, "Bottom overscroll should be clamped, was ${state.dragOffsetY}")
        state.endDrag()
    }

    @Test
    fun variableHeightCards_crossingDownAndUp_conservesOffsetWithoutOscillation() {
        val list = mutableListOf("short", "tall", "medium")
        val moves = mutableListOf<Pair<Int, Int>>()
        val state = OrbitDragReorderState(
            getKeys = { list.toList() },
            onMove = { from, to ->
                moves.add(from to to)
                val item = list.removeAt(from)
                list.add(to, item)
            },
            onTick = {},
            spacingPx = 8f,
            scope = null
        )
        state.onItemMeasured("short", 72)
        state.onItemMeasured("tall", 150)
        state.onItemMeasured("medium", 104)

        state.startDrag("short")
        // tall step = 158, threshold = 82.16 -> cross down at +85
        state.dragBy("short", 85f)
        assertEquals(listOf("tall", "short", "medium"), list)
        assertEquals(1, moves.size)
        // Visual offset after swap should be 85 - 158 = -73, well inside -82.16 so tiny jitter won't bounce back
        assertTrue(abs(state.dragOffsetY - (-73f)) < 0.01f)
        state.dragBy("short", -5f)
        assertEquals(1, moves.size, "Small upward jitter must not trigger reverse oscillation")

        // Continuing upward past -82.16 cleanly swaps back
        state.dragBy("short", -15f)
        assertEquals(listOf("short", "tall", "medium"), list)
        assertEquals(2, moves.size)
        state.endDrag()
    }

    @Test
    fun onDragEnd_firesOnceWhenOrderChanges_andSkipsWhenUnchanged() {
        val list = mutableListOf("a", "b", "c")
        val commits = mutableListOf<List<String>>()
        val state = OrbitDragReorderState(
            getKeys = { list.toList() },
            onMove = { from, to ->
                val item = list.removeAt(from)
                list.add(to, item)
            },
            onTick = {},
            spacingPx = 8f,
            scope = null,
            onDragEnd = { commits.add(it) }
        )
        list.forEach { state.onItemMeasured(it, 100) }

        // No-op pickup and release must not emit a commit
        state.startDrag("a")
        state.dragBy("a", 12f)
        state.endDrag()
        assertTrue(commits.isEmpty(), "No-op drag must not trigger onDragEnd commit")

        // Drag across two slots and release -> single commit with final order
        state.startDrag("a")
        state.dragBy("a", 230f)
        state.endDrag()
        assertEquals(listOf(listOf("b", "c", "a")), commits)
    }
}
