package app.overbay.orbit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import app.overbay.orbit.ui.components.ScreenScrollState

/**
 * Unique navigation stack frame pairing an immutable route [key] with an identity [id]
 * so repeated visits to the same route maintain independent scroll histories.
 */
data class OrbitBackStackEntry(
    val id: Long,
    val key: OrbitNavKey
)

/**
 * Coordinates forward navigation, tab resets, back pops, and per-entry [ScreenScrollState]
 * lifetimes so bottom-tab touches always start at the top while system Back restores the prior
 * page's saved scroll offset.
 */
@Stable
class OrbitBackStackController(initialKey: OrbitNavKey = OrbitNavKey.Dashboard) {
    private var nextEntryId: Long = 1L
    val entries = mutableStateListOf(OrbitBackStackEntry(id = 0L, key = initialKey))
    private val entryScrollStates = HashMap<Long, ScreenScrollState>()
    private val entryViewStates = HashMap<Long, Any>()

    val currentEntry: OrbitBackStackEntry
        get() = entries.lastOrNull() ?: OrbitBackStackEntry(id = 0L, key = OrbitNavKey.Dashboard)

    val currentKey: OrbitNavKey
        get() = currentEntry.key

    val canPopBack: Boolean
        get() = entries.size > 1

    fun scrollStateFor(entryId: Long): ScreenScrollState =
        entryScrollStates.getOrPut(entryId) { ScreenScrollState() }

    internal fun saveEntryState(entryId: Long, state: Any) {
        entryViewStates[entryId] = state
    }

    internal fun entryStateFor(entryId: Long): Any? = entryViewStates[entryId]

    internal fun clearEntryState(entryId: Long) {
        entryViewStates.remove(entryId)
    }

    fun navigate(destination: OrbitNavKey, fromTab: Boolean = false) {
        val top = entries.lastOrNull()
        if (top == null) {
            resetTo(destination)
            return
        }

        if (destination == top.key) {
            if (fromTab) {
                scrollStateFor(top.id).requestScrollToTop()
            }
            return
        }

        val samePrimaryTab =
            destination.primaryTabIndex != null &&
                destination.primaryTabIndex == top.key.primaryTabIndex
        if (samePrimaryTab) {
            entries[entries.lastIndex] = top.copy(key = destination)
            if (fromTab) {
                val entryScroll = scrollStateFor(top.id)
                entryScroll.resetSavedScroll()
                entryScroll.requestScrollToTop()
            }
        } else {
            entries.add(OrbitBackStackEntry(id = nextEntryId++, key = destination))
        }
    }

    fun popBack(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        pruneRemovedScrollStates()
        return true
    }

    fun resetTo(key: OrbitNavKey) {
        entries.clear()
        entries.add(OrbitBackStackEntry(id = nextEntryId++, key = key))
        pruneRemovedScrollStates()
    }

    internal fun trackedScrollEntryCount(): Int = entryScrollStates.size

    internal fun trackedViewStateEntryCount(): Int = entryViewStates.size

    private fun pruneRemovedScrollStates() {
        val activeIds = entries.mapTo(HashSet(entries.size)) { it.id }
        entryScrollStates.keys.retainAll(activeIds)
        entryViewStates.keys.retainAll(activeIds)
    }
}
