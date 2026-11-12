package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Key-stable drag-and-drop reorder state holder for both [androidx.compose.foundation.lazy.LazyColumn]
 * and standard [androidx.compose.foundation.layout.Column] lists.
 * Uses each neighbor's measured height when crossing slot boundaries so cards of varying
 * heights swap with smooth spring displacement, pickup lift, and release settle.
 */
@Stable
class OrbitDragReorderState<K : Any>(
    private val getKeys: () -> List<K>,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onTick: () -> Unit,
    private val spacingPx: Float,
    private val scope: CoroutineScope? = null
) {
    var draggingKey by mutableStateOf<K?>(null)
        private set
    var settlingKey by mutableStateOf<K?>(null)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set
    var pickupProgress by mutableFloatStateOf(0f)
        private set

    private val measuredHeights = mutableMapOf<K, Int>()
    private val neighborOffsets = mutableStateMapOf<K, Float>()
    private val neighborAnimatables = mutableMapOf<K, Animatable<Float, AnimationVector1D>>()
    private val pickupAnim = Animatable(0f)
    private var settleJob: Job? = null
    private var liftJob: Job? = null
    private var fallbackHeightPx = 140f

    fun onItemMeasured(key: K, heightPx: Int) {
        if (heightPx > 0) {
            measuredHeights[key] = heightPx
            fallbackHeightPx = heightPx.toFloat()
        }
    }

    fun isDragging(key: K): Boolean = draggingKey == key

    fun isElevated(key: K): Boolean = draggingKey == key || settlingKey == key

    fun hasNeighborOffset(key: K): Boolean = (neighborOffsets[key] ?: 0f) != 0f

    fun liftProgressFor(key: K): Float = if (isElevated(key)) pickupProgress else 0f

    fun offsetFor(key: K): Float = when (key) {
        draggingKey, settlingKey -> dragOffsetY
        else -> neighborOffsets[key] ?: 0f
    }

    fun startDrag(key: K) {
        settleJob?.cancel()
        settleJob = null
        settlingKey = null
        neighborOffsets.remove(key)
        onTick()
        draggingKey = key
        dragOffsetY = 0f

        val cs = scope
        if (cs != null) {
            liftJob?.cancel()
            liftJob = cs.launch {
                pickupAnim.animateTo(1f, OrbitSprings.FloatSpatialFast) {
                    pickupProgress = value
                }
            }
        } else {
            pickupProgress = 1f
        }
    }

    fun dragBy(key: K, dy: Float) {
        if (draggingKey != key) return
        val keys = getKeys()
        val currentIndex = keys.indexOf(key)
        if (currentIndex == -1) return

        dragOffsetY += dy
        val draggedHeight = (measuredHeights[key]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)

        if (dragOffsetY > 0f && currentIndex < keys.lastIndex) {
            val nextKey = keys[currentIndex + 1]
            val nextHeight = (measuredHeights[nextKey]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)
            val step = nextHeight + spacingPx
            if (dragOffsetY > step * 0.55f) {
                onTick()
                onMove(currentIndex, currentIndex + 1)
                dragOffsetY -= step
                animateNeighborDisplacement(nextKey, (draggedHeight + spacingPx) + (neighborOffsets[nextKey] ?: 0f))
            }
        } else if (dragOffsetY < 0f && currentIndex > 0) {
            val prevKey = keys[currentIndex - 1]
            val prevHeight = (measuredHeights[prevKey]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)
            val step = prevHeight + spacingPx
            if (dragOffsetY < -step * 0.55f) {
                onTick()
                onMove(currentIndex, currentIndex - 1)
                dragOffsetY += step
                animateNeighborDisplacement(prevKey, -(draggedHeight + spacingPx) + (neighborOffsets[prevKey] ?: 0f))
            }
        }
    }

    fun endDrag() {
        val releasedKey = draggingKey ?: return
        val releaseOffset = dragOffsetY
        draggingKey = null

        val cs = scope
        if (cs == null) {
            settlingKey = null
            dragOffsetY = 0f
            pickupProgress = 0f
            return
        }

        settlingKey = releasedKey
        liftJob?.cancel()
        liftJob = cs.launch {
            pickupAnim.animateTo(0f, OrbitSprings.FloatSpatialDefault) {
                pickupProgress = value
            }
        }

        settleJob?.cancel()
        settleJob = cs.launch {
            if (abs(releaseOffset) >= 0.5f) {
                val anim = Animatable(releaseOffset)
                try {
                    anim.animateTo(0f, OrbitSprings.FloatSpatialDefault) {
                        if (settlingKey == releasedKey) {
                            dragOffsetY = value
                        }
                    }
                } finally {
                    if (settlingKey == releasedKey) {
                        settlingKey = null
                        dragOffsetY = 0f
                    }
                }
            } else {
                liftJob?.join()
                if (settlingKey == releasedKey) {
                    settlingKey = null
                    dragOffsetY = 0f
                }
            }
        }
    }

    private fun animateNeighborDisplacement(neighborKey: K, initialOffsetPx: Float) {
        val cs = scope ?: return
        neighborOffsets[neighborKey] = initialOffsetPx
        cs.launch {
            val anim = neighborAnimatables.getOrPut(neighborKey) { Animatable(initialOffsetPx) }
            anim.snapTo(initialOffsetPx)
            try {
                anim.animateTo(0f, OrbitSprings.FloatSpatialDefault) {
                    neighborOffsets[neighborKey] = value
                }
            } finally {
                if (abs(anim.value) < 0.5f) {
                    neighborOffsets.remove(neighborKey)
                }
            }
        }
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
    val scope = rememberCoroutineScope()
    val spacingPx = with(density) { itemSpacingDp.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentKeySelector by rememberUpdatedState(keySelector)
    val currentOnMove by rememberUpdatedState(onMove)

    return remember(spacingPx, scope) {
        OrbitDragReorderState(
            getKeys = { currentItems.map(currentKeySelector) },
            onMove = { from, to -> currentOnMove(from, to) },
            onTick = { haptics.tick() },
            spacingPx = spacingPx,
            scope = scope
        )
    }
}

/**
 * Modifier applied to a reorderable card or row container to track its height and
 * translate/elevate it smoothly on the GPU during pickup, neighbor swaps, and release settle.
 */
fun <K : Any> Modifier.dragReorderItem(
    state: OrbitDragReorderState<K>?,
    key: K
): Modifier {
    if (state == null) return this
    val isElevated = state.isElevated(key)
    return this
        .zIndex(if (isElevated) 10f else if (state.hasNeighborOffset(key)) 2f else 0f)
        .onGloballyPositioned { state.onItemMeasured(key, it.size.height) }
        .graphicsLayer {
            translationY = state.offsetFor(key)
            val lift = state.liftProgressFor(key)
            if (lift > 0f) {
                val scale = 1f + 0.02f * lift
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.06f * lift
                shadowElevation = 8.dp.toPx() * lift
            }
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
