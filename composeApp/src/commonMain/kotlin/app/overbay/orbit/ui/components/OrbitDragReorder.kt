package app.overbay.orbit.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Key-stable drag-and-drop reorder state holder for both [androidx.compose.foundation.lazy.LazyColumn]
 * and standard [androidx.compose.foundation.layout.Column] lists.
 * Uses each neighbor's measured height when crossing slot boundaries so cards of varying
 * heights (such as expanded vs. collapsed account groups) swap with zero visual jump.
 */
@Stable
class OrbitDragReorderState<K : Any>(
    private val getKeys: () -> List<K>,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onTick: () -> Unit,
    private val spacingPx: Float
) {
    var draggingKey by mutableStateOf<K?>(null)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    private val measuredHeights = mutableMapOf<K, Int>()
    private var fallbackHeightPx = 140f

    fun onItemMeasured(key: K, heightPx: Int) {
        if (heightPx > 0) {
            measuredHeights[key] = heightPx
            fallbackHeightPx = heightPx.toFloat()
        }
    }

    fun isDragging(key: K): Boolean = draggingKey == key

    fun offsetFor(key: K): Float = if (draggingKey == key) dragOffsetY else 0f

    fun startDrag(key: K) {
        onTick()
        draggingKey = key
        dragOffsetY = 0f
    }

    fun dragBy(key: K, dy: Float) {
        if (draggingKey != key) return
        val keys = getKeys()
        val currentIndex = keys.indexOf(key)
        if (currentIndex == -1) return

        dragOffsetY += dy

        if (dragOffsetY > 0f && currentIndex < keys.lastIndex) {
            val nextKey = keys[currentIndex + 1]
            val nextHeight = (measuredHeights[nextKey]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)
            val step = nextHeight + spacingPx
            if (dragOffsetY > step * 0.55f) {
                onTick()
                onMove(currentIndex, currentIndex + 1)
                dragOffsetY -= step
            }
        } else if (dragOffsetY < 0f && currentIndex > 0) {
            val prevKey = keys[currentIndex - 1]
            val prevHeight = (measuredHeights[prevKey]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)
            val step = prevHeight + spacingPx
            if (dragOffsetY < -step * 0.55f) {
                onTick()
                onMove(currentIndex, currentIndex - 1)
                dragOffsetY += step
            }
        }
    }

    fun endDrag() {
        draggingKey = null
        dragOffsetY = 0f
    }
}

@Composable
fun <T, K : Any> rememberDragReorderState(
    items: List<T>,
    keySelector: (T) -> K,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    itemSpacingDp: Dp = 12.dp
): OrbitDragReorderState<K> {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val spacingPx = with(density) { itemSpacingDp.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentKeySelector by rememberUpdatedState(keySelector)
    val currentOnMove by rememberUpdatedState(onMove)

    return remember(spacingPx) {
        OrbitDragReorderState(
            getKeys = { currentItems.map(currentKeySelector) },
            onMove = { from, to -> currentOnMove(from, to) },
            onTick = { haptics.tick() },
            spacingPx = spacingPx
        )
    }
}

/**
 * Modifier applied to a reorderable card or row container to track its height and
 * translate it smoothly while being dragged.
 */
fun <K : Any> Modifier.dragReorderItem(
    state: OrbitDragReorderState<K>?,
    key: K
): Modifier {
    if (state == null) return this
    val isDragging = state.isDragging(key)
    return this
        .zIndex(if (isDragging) 10f else 0f)
        .onGloballyPositioned { state.onItemMeasured(key, it.size.height) }
        .graphicsLayer {
            translationY = state.offsetFor(key)
            alpha = if (isDragging) 0.9f else 1f
            shadowElevation = if (isDragging) 12f else 0f
        }
}

/**
 * Left-side tactile drag handle (`⋮⋮`) used across all reorderable cards and rows.
 */
@Composable
fun <K : Any> OrbitDragHandle(
    state: OrbitDragReorderState<K>,
    key: K,
    modifier: Modifier = Modifier
) {
    val isDragging = state.isDragging(key)
    Box(
        modifier = modifier
            .size(width = 28.dp, height = 36.dp)
            .pointerInput(state, key) {
                detectDragGestures(
                    onDragStart = { state.startDrag(key) },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.dragBy(key, dragAmount.y)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⋮⋮",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDragging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
