package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Segmented capsule bar for period selection (Spending Trends, Net Worth, Cash Flow)
 * with a sliding M3E spring-animated active thumb capsule.
 */
@Composable
fun <T> CanopySegmentedBar(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val count = items.size.coerceAtLeast(1)
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val spacing = 2.dp

    val totalWidthDp = with(density) { trackSize.width.toDp() }
    val totalHeightDp = with(density) { trackSize.height.toDp() }
    val itemWidthDp = if (trackSize.width > 0) {
        ((totalWidthDp - spacing * (count - 1)) / count).coerceAtLeast(0.dp)
    } else 0.dp
    val targetThumbOffsetDp = (itemWidthDp + spacing) * selectedIndex
    val thumbOffsetDp by animateDpAsState(
        targetValue = targetThumbOffsetDp,
        animationSpec = OrbitSprings.DpSpatialFast,
        label = "canopySegmentedThumbOffset"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .onSizeChanged { trackSize = it }
        ) {
            if (trackSize.width > 0 && trackSize.height > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetDp.roundToPx(), 0) }
                        .size(width = itemWidthDp, height = totalHeightDp)
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                items.forEach { item ->
                    val selected = item == selectedItem
                    val textColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = OrbitSprings.ColorEffectsFast,
                        label = "canopySegmentTextColor"
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (selected && trackSize.width == 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable {
                                if (!selected) {
                                    haptics.tick()
                                    onSelect(item)
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = labelProvider(item),
                                fontSize = if (isCompact) 11.sp else 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
