package app.overbay.orbit.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.min
import kotlinx.coroutines.isActive

/**
 * Frame-synced edge auto-scroll for vertical scrollable containers during an active drag.
 * Smoothly glides [scrollState] when the pointer enters the top or bottom edge zone and
 * feeds consumed scroll pixels into [state] so the dragged card stays pixel-locked under the finger.
 */
@Composable
fun <K : Any> DragReorderAutoScrollEffect(
    state: OrbitDragReorderState<K>,
    scrollState: ScrollState,
    viewportTopRootYProvider: () -> Float,
    viewportBottomRootYProvider: () -> Float,
    edgeZoneDp: Dp = 64.dp,
    maxSpeedDpPerSec: Dp = 520.dp
) {
    val density = LocalDensity.current
    val edgeZoneMaxPx = with(density) { edgeZoneDp.toPx() }
    val maxSpeedPxPerSec = with(density) { maxSpeedDpPerSec.toPx() }
    val activeKey = state.draggingKey

    LaunchedEffect(activeKey, scrollState) {
        if (activeKey == null) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive && state.draggingKey == activeKey) {
            val frameNanos = withFrameNanos { it }
            val dtSec = if (lastFrameNanos == 0L) 0f
            else ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = frameNanos

            val pointerY = state.activePointerRootY
            val topY = viewportTopRootYProvider()
            val bottomY = viewportBottomRootYProvider()
            val viewportHeight = bottomY - topY
            if (dtSec <= 0f || pointerY.isNaN() || viewportHeight <= 1f) continue

            val edgeZonePx = min(edgeZoneMaxPx, viewportHeight * 0.25f)
            val delta = when {
                pointerY < topY + edgeZonePx && scrollState.canScrollBackward -> {
                    val t = ((topY + edgeZonePx - pointerY) / edgeZonePx).coerceIn(0f, 1f)
                    -(maxSpeedPxPerSec * t * t * dtSec)
                }
                pointerY > bottomY - edgeZonePx && scrollState.canScrollForward -> {
                    val t = ((pointerY - (bottomY - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                    maxSpeedPxPerSec * t * t * dtSec
                }
                else -> 0f
            }

            if (delta != 0f) {
                val consumed = scrollState.scrollBy(delta)
                if (consumed != 0f) {
                    state.dragBy(activeKey, consumed)
                }
            }
        }
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
    val isNeighborMoving = state.hasNeighborOffset(key)
    return this
        .zIndex(if (isElevated) 10f else if (isNeighborMoving) 2f else 0f)
        .onGloballyPositioned { state.onItemMeasured(key, it.size.height) }
        .graphicsLayer {
            translationY = state.offsetFor(key)
            val lift = state.liftProgressFor(key)
            if (lift > 0f) {
                val scale = 1f + 0.02f * lift
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.04f * lift
                shadowElevation = 8.dp.toPx() * lift
            }
        }
}

/**
 * Left-side tactile drag handle (`⋮⋮`) used across all reorderable cards and rows.
 * Picks up immediately on pointer down, tracks finger delta in root coordinates (immune to
 * graphicsLayer scale/translation and layout reordering), and consumes events so parent
 * scrollables or modal sheets cannot hijack the drag.
 */
@Composable
fun <K : Any> OrbitDragHandle(
    state: OrbitDragReorderState<K>,
    key: K,
    modifier: Modifier = Modifier
) {
    val isDragging = state.isDragging(key)
    val currentKey by rememberUpdatedState(key)
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .size(width = 40.dp, height = 44.dp)
            .onGloballyPositioned { handleCoords = it }
            .pointerInput(state, key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startCoords = handleCoords?.takeIf { it.isAttached }
                    var lastRootY = startCoords?.localToRoot(down.position)?.y ?: down.position.y
                    down.consume()
                    state.startDrag(currentKey, pointerRootY = lastRootY)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val coords = handleCoords?.takeIf { it.isAttached }
                            val dy = if (coords != null) {
                                val currentRootY = coords.localToRoot(change.position).y
                                val delta = currentRootY - lastRootY
                                lastRootY = currentRootY
                                delta
                            } else {
                                val delta = change.positionChange().y
                                lastRootY += delta
                                delta
                            }
                            change.consume()
                            if (dy != 0f) {
                                state.dragBy(currentKey, dy, pointerRootY = lastRootY)
                            } else {
                                state.updatePointerRootY(lastRootY)
                            }
                        }
                    } finally {
                        state.endDrag()
                    }
                }
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
