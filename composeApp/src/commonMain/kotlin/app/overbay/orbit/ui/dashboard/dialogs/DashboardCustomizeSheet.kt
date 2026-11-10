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
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitDragReorderState
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.ui.dashboard.DashboardAction
import app.overbay.orbit.ui.dashboard.DashboardCardConfigItem
import app.overbay.orbit.ui.dashboard.DashboardCardType
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Centered Customize Dashboard modal matching the main branch design:
 * - '⋮⋮' drag-handle reordering
 * - Animated 2-button column width picker (standard left/right placement vs full-width span) on wide screens
 * - Tactile visibility toggle switch
 *
 */
data class OrbitBentoCustomizeItem<K : Any>(
    val key: K,
    val title: String,
    val description: String,
    val isVisible: Boolean,
    val wideSpan: Int = 1
)

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
    val items = remember(cards) {
        cards.map { OrbitBentoCustomizeItem(it.type, it.title, it.description, it.isVisible, it.wideSpan) }
    }
    OrbitBentoCustomizeSheet(
        title = "Customize Dashboard",
        items = items,
        onToggleVisibility = onToggleVisibility,
        onSetWideSpan = onSetWideSpan,
        onMoveItem = onMoveCard,
        onReset = onReset,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun <K : Any> OrbitBentoCustomizeSheet(
    title: String,
    items: List<OrbitBentoCustomizeItem<K>>,
    onToggleVisibility: (K) -> Unit,
    onSetWideSpan: (K, Int) -> Unit = { _, _ -> },
    onMoveItem: (Int, Int) -> Unit = { _, _ -> },
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val showWidthPicker = true
    val standardPlacements = remember(items) { computeStandardPlacements(items) }
    val reorderState = rememberDragReorderState(
        items = items,
        keySelector = { it.key },
        onMove = onMoveItem,
        itemSpacingDp = 8.dp
    )

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            OrbitSheetHeader(
                title = title,
                onDismiss = onDismiss,
                trailingActions = {
                    OrbitActionChip(
                        label = "Reset",
                        onClick = { haptics.tick(); onReset() }
                    )
                }
            )

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
                items.forEach { config ->
                    key(config.key) {
                        CustomizeCardRow(
                            config = config,
                            reorderState = reorderState,
                            showWidthPicker = showWidthPicker,
                            isRightPlacement = standardPlacements[config.key] == true,
                            onToggle = { haptics.tap(); onToggleVisibility(config.key) },
                            onWideSpanChange = { span -> haptics.tick(); onSetWideSpan(config.key, span) }
                        )
                    }
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
private fun <K : Any> CustomizeCardRow(
    config: OrbitBentoCustomizeItem<K>,
    reorderState: OrbitDragReorderState<K>,
    showWidthPicker: Boolean,
    isRightPlacement: Boolean,
    onToggle: () -> Unit,
    onWideSpanChange: (Int) -> Unit
) {
    val isDragging = reorderState.isDragging(config.key)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dragReorderItem(reorderState, config.key),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (isDragging) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitDragHandle(state = reorderState, key = config.key)

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

            val supportsTwoPane = LocalDevicePosture.current.supportsTwoPane
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (supportsTwoPane && showWidthPicker) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardColumnWidthPicker(
    wideSpan: Int,
    isRightPlacement: Boolean,
    onWideSpanChange: (Int) -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    SingleChoiceSegmentedButtonRow(modifier = Modifier.height(34.dp)) {
        SegmentedButton(
            selected = wideSpan == 1,
            onClick = { onWideSpanChange(1) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {},
            label = {
                DashboardWidthIcon(
                    isWide = false,
                    isRightPlacement = isRightPlacement,
                    tint = if (wideSpan == 1) activeColor else inactiveColor
                )
            }
        )
        SegmentedButton(
            selected = wideSpan == 2,
            onClick = { onWideSpanChange(2) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {},
            label = {
                DashboardWidthIcon(
                    isWide = true,
                    isRightPlacement = false,
                    tint = if (wideSpan == 2) activeColor else inactiveColor
                )
            }
        )
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

private fun <K : Any> computeStandardPlacements(cards: List<OrbitBentoCustomizeItem<K>>): Map<K, Boolean> {
    val placements = mutableMapOf<K, Boolean>()
    var filledColumns = 0
    for (item in cards) {
        val isRight = filledColumns != 0
        placements[item.key] = isRight
        if (!item.isVisible) continue
        filledColumns = if (item.wideSpan == 2) 0 else if (!isRight) 1 else 0
    }
    return placements
}
