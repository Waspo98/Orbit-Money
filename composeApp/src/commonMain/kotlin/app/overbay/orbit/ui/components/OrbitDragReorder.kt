package app.overbay.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

private val NeighborGlideSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Key-stable drag-and-drop reorder state holder for both [androidx.compose.foundation.lazy.LazyColumn]
 * and standard [androidx.compose.foundation.layout.Column] lists.
 *
 * Maintains an authoritative in-drag key list so fast multi-slot drags never read stale
 * pre-recomposition keys, keeps per-frame translation/scale strictly in GPU [androidx.compose.ui.graphics.graphicsLayer]
 * reads, cancels superseded neighbor animations safely, and emits a single [onDragEnd] commit.
 */
@Stable
class OrbitDragReorderState<K : Any>(
    private val getKeys: () -> List<K>,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onTick: () -> Unit,
    private val spacingPx: Float,
    private val scope: CoroutineScope? = null,
    private val onDragEnd: ((orderedKeys: List<K>) -> Unit)? = null,
    private val maxOverscrollPx: Float = 36f
) {
    var draggingKey by mutableStateOf<K?>(null)
        private set
    var settlingKey by mutableStateOf<K?>(null)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set
    var pickupProgress by mutableFloatStateOf(0f)
        private set
    var activePointerRootY by mutableFloatStateOf(Float.NaN)
        private set

    private val measuredHeights = mutableMapOf<K, Int>()
    private val neighborOffsets = mutableStateMapOf<K, Float>()
    private val activeNeighbors = mutableStateMapOf<K, Boolean>()
    private val neighborAnimatables = mutableMapOf<K, Animatable<Float, AnimationVector1D>>()
    private val neighborJobs = mutableMapOf<K, Job>()
    private val pickupAnim = Animatable(0f)
    private var settleJob: Job? = null
    private var liftJob: Job? = null
    private var fallbackHeightPx = 140f
    private var initialDragKeys: List<K>? = null
    private var activeKeys: MutableList<K>? = null

    fun onItemMeasured(key: K, heightPx: Int) {
        if (heightPx > 0) {
            measuredHeights[key] = heightPx
            fallbackHeightPx = heightPx.toFloat()
        }
    }

    fun isDragging(key: K): Boolean = draggingKey == key

    fun isElevated(key: K): Boolean = draggingKey == key || settlingKey == key

    fun hasNeighborOffset(key: K): Boolean = activeNeighbors[key] == true

    fun liftProgressFor(key: K): Float = if (isElevated(key)) pickupProgress else 0f

    fun offsetFor(key: K): Float = when (key) {
        draggingKey, settlingKey -> dragOffsetY
        else -> neighborOffsets[key] ?: 0f
    }

    fun startDrag(key: K, pointerRootY: Float = Float.NaN) {
        val snapshotKeys = getKeys()
        if (key !in snapshotKeys) return

        settleJob?.cancel()
        settleJob = null
        settlingKey = null
        neighborJobs.remove(key)?.cancel()
        neighborOffsets.remove(key)
        activeNeighbors.remove(key)

        initialDragKeys = snapshotKeys
        activeKeys = snapshotKeys.toMutableList()
        activePointerRootY = pointerRootY
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

    fun updatePointerRootY(pointerRootY: Float) {
        if (draggingKey != null && !pointerRootY.isNaN()) {
            activePointerRootY = pointerRootY
        }
    }

    fun dragBy(key: K, dy: Float, pointerRootY: Float = Float.NaN) {
        if (draggingKey != key) return
        if (!pointerRootY.isNaN()) {
            activePointerRootY = pointerRootY
        }
        val keys = activeKeys ?: getKeys().toMutableList().also { activeKeys = it }
        if (keys.indexOf(key) == -1) return

        dragOffsetY += dy
        val draggedHeight = (measuredHeights[key]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)

        var swaps = 0
        while (swaps++ < 16) {
            val currentIndex = keys.indexOf(key)
            if (currentIndex == -1) break

            if (dragOffsetY > 0f && currentIndex < keys.lastIndex) {
                val nextKey = keys[currentIndex + 1]
                val nextHeight = (measuredHeights[nextKey]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)
                val step = nextHeight + spacingPx
                if (dragOffsetY > step * 0.52f) {
                    keys.removeAt(currentIndex)
                    keys.add(currentIndex + 1, key)
                    onTick()
                    onMove(currentIndex, currentIndex + 1)
                    dragOffsetY -= step
                    animateNeighborDisplacement(nextKey, (draggedHeight + spacingPx) + (neighborOffsets[nextKey] ?: 0f))
                    continue
                }
            } else if (dragOffsetY < 0f && currentIndex > 0) {
                val prevKey = keys[currentIndex - 1]
                val prevHeight = (measuredHeights[prevKey]?.toFloat() ?: fallbackHeightPx).coerceAtLeast(48f)
                val step = prevHeight + spacingPx
                if (dragOffsetY < -step * 0.52f) {
                    keys.removeAt(currentIndex)
                    keys.add(currentIndex - 1, key)
                    onTick()
                    onMove(currentIndex, currentIndex - 1)
                    dragOffsetY += step
                    animateNeighborDisplacement(prevKey, -(draggedHeight + spacingPx) + (neighborOffsets[prevKey] ?: 0f))
                    continue
                }
            }
            break
        }

        val finalIndex = keys.indexOf(key)
        if (finalIndex == 0 && dragOffsetY < -maxOverscrollPx) {
            dragOffsetY = -maxOverscrollPx
        } else if (finalIndex == keys.lastIndex && dragOffsetY > maxOverscrollPx) {
            dragOffsetY = maxOverscrollPx
        }
    }

    fun endDrag() {
        val releasedKey = draggingKey ?: return
        val releaseOffset = dragOffsetY
        val finalOrder = activeKeys?.toList()
        val startOrder = initialDragKeys

        draggingKey = null
        activeKeys = null
        initialDragKeys = null
        activePointerRootY = Float.NaN

        if (finalOrder != null && startOrder != null && finalOrder != startOrder) {
            onDragEnd?.invoke(finalOrder)
        }

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
        neighborJobs.remove(neighborKey)?.cancel()
        neighborOffsets[neighborKey] = initialOffsetPx
        activeNeighbors[neighborKey] = true
        val job = cs.launch {
            val myJob = currentCoroutineContext()[Job]
            val anim = neighborAnimatables.getOrPut(neighborKey) { Animatable(initialOffsetPx) }
            anim.snapTo(initialOffsetPx)
            try {
                anim.animateTo(0f, NeighborGlideSpring) {
                    neighborOffsets[neighborKey] = value
                }
            } finally {
                if (neighborJobs[neighborKey] === myJob) {
                    neighborJobs.remove(neighborKey)
                    neighborOffsets.remove(neighborKey)
                    activeNeighbors.remove(neighborKey)
                }
            }
        }
        neighborJobs[neighborKey] = job
    }
}

@Composable
fun <T, K : Any> rememberDragReorderState(
    items: List<T>,
    keySelector: (T) -> K,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    itemSpacingDp: Dp = 12.dp,
    onDragEnd: ((orderedKeys: List<K>) -> Unit)? = null
): OrbitDragReorderState<K> {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spacingPx = with(density) { itemSpacingDp.toPx() }
    val maxOverscrollPx = with(density) { 12.dp.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentKeySelector by rememberUpdatedState(keySelector)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    return remember(spacingPx, maxOverscrollPx, scope) {
        OrbitDragReorderState(
            getKeys = { currentItems.map(currentKeySelector) },
            onMove = { from, to -> currentOnMove(from, to) },
            onTick = { haptics.tick() },
            spacingPx = spacingPx,
            scope = scope,
            onDragEnd = { ordered -> currentOnDragEnd?.invoke(ordered) },
            maxOverscrollPx = maxOverscrollPx
        )
    }
}
