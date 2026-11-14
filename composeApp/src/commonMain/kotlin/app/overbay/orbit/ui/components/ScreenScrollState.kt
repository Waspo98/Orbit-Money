package app.overbay.orbit.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Per-back-stack-entry scroll coordinator shared across [rememberHeroScrollState]
 * and [rememberOrbitLazyListState]. Keeps list and canopy offsets preserved while an entry
 * remains on the back stack, and supports explicit scroll-to-top actions from navigation tabs.
 */
@Stable
class ScreenScrollState {
    internal var savedHeroOffsetPx: Float = 0f
    internal var savedHeroMaxHeightPx: Float = 0f
    internal var savedHeroFabVisible: Boolean = true
    internal val listSnapshots = HashMap<Any, Pair<Int, Int>>()

    var scrollToTopRequestId by mutableIntStateOf(0)
        private set

    fun requestScrollToTop() {
        scrollToTopRequestId++
    }

    fun resetSavedScroll() {
        savedHeroOffsetPx = 0f
        savedHeroFabVisible = true
        listSnapshots.clear()
    }
}

val LocalScreenScrollState = compositionLocalOf<ScreenScrollState?> { null }

/**
 * Drop-in replacement for `rememberLazyListState` that automatically restores its scroll position
 * across back navigation via [LocalScreenScrollState] and animates back to the top when requested
 * by a navigation tab re-tap.
 */
@Composable
fun rememberOrbitLazyListState(slotKey: Any = "default"): LazyListState {
    val ownerScroll = LocalScreenScrollState.current
    val initial = ownerScroll?.listSnapshots?.get(slotKey)
    val listState = remember(ownerScroll, slotKey) {
        LazyListState(
            firstVisibleItemIndex = initial?.first ?: 0,
            firstVisibleItemScrollOffset = initial?.second ?: 0
        )
    }

    if (ownerScroll != null) {
        var handledScrollToTopId by remember(ownerScroll, slotKey) {
            mutableIntStateOf(ownerScroll.scrollToTopRequestId)
        }

        LaunchedEffect(ownerScroll, slotKey, listState) {
            snapshotFlow {
                Triple(
                    listState.layoutInfo.totalItemsCount,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                )
            }.collect { (count, index, offset) ->
                // Guard against pre-measure empty frames overwriting a restored scroll position.
                if (count > 0) {
                    ownerScroll.listSnapshots[slotKey] = index to offset
                }
            }
        }

        LaunchedEffect(ownerScroll, slotKey, ownerScroll.scrollToTopRequestId) {
            val reqId = ownerScroll.scrollToTopRequestId
            if (reqId > handledScrollToTopId) {
                handledScrollToTopId = reqId
                listState.animateScrollToItem(0)
            }
        }

        DisposableEffect(ownerScroll, slotKey, listState) {
            onDispose {
                if (listState.layoutInfo.totalItemsCount > 0) {
                    ownerScroll.listSnapshots[slotKey] =
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }
            }
        }
    }

    return listState
}
