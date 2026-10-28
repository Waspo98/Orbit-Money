package app.overbay.orbit.ui.dashboard.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.dashboard.DashboardAction
import app.overbay.orbit.ui.dashboard.DashboardCardConfigItem
import app.overbay.orbit.ui.dashboard.DashboardCardType
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Centered Customize Dashboard modal matching the main branch design:
 * - '⋮⋮' drag-handle reordering
 * - Animated 2-button column width picker (standard left/right placement vs full-width span) on wide screens
 * - Tactile visibility toggle switch
 * Strictly under the 400-line ceiling.
 */
@Composable
fun DashboardCustomizeSheet(
    cards: List<DashboardCardConfigItem>,
    onAction: (DashboardAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCustomizeSheet(
        cards = cards,
        onToggleVisibility = { onAction(DashboardAction.ToggleCardVisibility(it)) },
        onSetWideSpan = { type, span -> onAction(DashboardAction.SetCardWideSpan(type, span)) },
        onMoveCard = { from, to -> onAction(DashboardAction.MoveCard(from, to)) },
        onReset = { onAction(DashboardAction.ResetCardsToDefault) },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun DashboardCustomizeSheet(
    cards: List<DashboardCardConfigItem>,
    onToggleVisibility: (DashboardCardType) -> Unit,
    onSetWideSpan: (DashboardCardType, Int) -> Unit = { _, _ -> },
    onMoveCard: (Int, Int) -> Unit = { _, _ -> },
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val posture = LocalDevicePosture.current
    val showWidthPicker = posture.supportsTwoPane
    val standardPlacements = remember(cards) { computeStandardPlacements(cards) }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(160f) }
    val currentCards by rememberUpdatedState(cards)

    app.overbay.orbit.ui.components.OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customize Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { haptics.tick(); onReset() }) {
                        Text("Reset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { haptics.tick(); onDismiss() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cards.forEachIndexed { index, config ->
                    val isDragging = draggingIndex == index
                    CustomizeCardRow(
                        config = config,
                        showWidthPicker = showWidthPicker,
                        isRightPlacement = standardPlacements[config.type] == true,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        onMeasuredHeight = { h -> if (h > 0) rowHeightPx = h.toFloat() },
                        onDragStart = {
                            haptics.tick()
                            draggingIndex = index
                            dragOffsetY = 0f
                        },
                        onDragDelta = { dy ->
                            if (draggingIndex in currentCards.indices) {
                                dragOffsetY += dy
                                val step = (rowHeightPx + 16f).coerceAtLeast(80f)
                                val indexShift = (dragOffsetY / step).roundToInt()
                                val targetIndex = (draggingIndex + indexShift).coerceIn(0, currentCards.lastIndex)
                                if (targetIndex != draggingIndex) {
                                    haptics.tick()
                                    onMoveCard(draggingIndex, targetIndex)
                                    dragOffsetY -= (targetIndex - draggingIndex) * step
                                    draggingIndex = targetIndex
                                }
                            }
                        },
                        onDragEnd = {
                            draggingIndex = -1
                            dragOffsetY = 0f
                        },
                        onToggle = { haptics.tap(); onToggleVisibility(config.type) },
                        onWideSpanChange = { span -> haptics.tick(); onSetWideSpan(config.type, span) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { haptics.confirm(); onDismiss() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CustomizeCardRow(
    config: DashboardCardConfigItem,
    showWidthPicker: Boolean,
    isRightPlacement: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onMeasuredHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: () -> Unit,
    onWideSpanChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 0f)
            .onGloballyPositioned { onMeasuredHeight(it.size.height) }
            .graphicsLayer {
                translationY = dragOffsetY
                alpha = if (isDragging) 0.85f else 1f
                shadowElevation = if (isDragging) 12f else 0f
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (isDragging) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 38.dp)
                    .pointerInput(config.type) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⋮⋮", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (config.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (config.description.isNotBlank()) {
                    Text(
                        text = config.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (config.isVisible) 0.85f else 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showWidthPicker) {
                    DashboardColumnWidthPicker(
                        wideSpan = config.wideSpan,
                        isRightPlacement = isRightPlacement,
                        onWideSpanChange = onWideSpanChange
                    )
                }
                Switch(checked = config.isVisible, onCheckedChange = { onToggle() })
            }
        }
    }
}

@Composable
private fun DashboardColumnWidthPicker(
    wideSpan: Int,
    isRightPlacement: Boolean,
    onWideSpanChange: (Int) -> Unit
) {
    val activeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, OrbitTheme.colors.cardBorder, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .background(if (wideSpan == 1) activeBg else Color.Transparent)
                .clickable { onWideSpanChange(1) },
            contentAlignment = Alignment.Center
        ) {
            DashboardWidthIcon(isWide = false, isRightPlacement = isRightPlacement, tint = if (wideSpan == 1) activeColor else inactiveColor)
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(OrbitTheme.colors.cardBorder))
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .background(if (wideSpan == 2) activeBg else Color.Transparent)
                .clickable { onWideSpanChange(2) },
            contentAlignment = Alignment.Center
        ) {
            DashboardWidthIcon(isWide = true, isRightPlacement = false, tint = if (wideSpan == 2) activeColor else inactiveColor)
        }
    }
}

@Composable
private fun DashboardWidthIcon(isWide: Boolean, isRightPlacement: Boolean, tint: Color) {
    val pillOffsetX by animateDpAsState(
        targetValue = if (!isWide && isRightPlacement) 16.dp else 3.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "dashboardWidthIconOffset"
    )
    val pillWidth = if (isWide) 22.dp else 9.dp

    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 18.dp)
            .border(1.5.dp, tint.copy(alpha = 0.92f), RoundedCornerShape(5.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .alpha(0.36f)
                .background(tint)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(pillOffsetX.roundToPx(), 3.dp.roundToPx()) }
                .size(width = pillWidth, height = 12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint)
        )
    }
}

private fun computeStandardPlacements(cards: List<DashboardCardConfigItem>): Map<DashboardCardType, Boolean> {
    val placements = mutableMapOf<DashboardCardType, Boolean>()
    var filledColumns = 0
    for (item in cards) {
        val isRight = filledColumns != 0
        placements[item.type] = isRight
        if (!item.isVisible) continue
        filledColumns = if (item.wideSpan == 2) 0 else if (!isRight) 1 else 0
    }
    return placements
}
