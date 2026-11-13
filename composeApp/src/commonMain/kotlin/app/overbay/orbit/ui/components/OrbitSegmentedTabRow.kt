package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Recessed M3E capsule tab switcher with a true sliding spring-animated active thumb,
 * optional count pills, and error dots.
 */
@Composable
fun <T> OrbitSegmentedTabRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    badgeCount: ((T) -> Int)? = null,
    hasError: ((T) -> Boolean)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val count = items.size.coerceAtLeast(1)
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)
    val spacing = 3.dp

    val totalWidthDp = with(density) { trackSize.width.toDp() }
    val totalHeightDp = with(density) { trackSize.height.toDp() }
    val tabWidthDp = if (trackSize.width > 0) {
        ((totalWidthDp - spacing * (count - 1)) / count).coerceAtLeast(0.dp)
    } else 0.dp
    val targetOffsetDp = (tabWidthDp + spacing) * selectedIndex
    val thumbOffsetDp by animateDpAsState(
        targetValue = targetOffsetDp,
        animationSpec = OrbitSprings.DpSpatialFast,
        label = "segTabThumbOffset"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
                .onSizeChanged { trackSize = it }
        ) {
            if (trackSize.width > 0 && trackSize.height > 0) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetDp.roundToPx(), 0) }
                        .size(width = tabWidthDp, height = totalHeightDp)
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = item == selected
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = OrbitSprings.ColorEffectsFast,
                        label = "segTabText"
                    )
                    val itemCount = badgeCount?.invoke(item) ?: 0
                    val showError = hasError?.invoke(item) == true

                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSelected && trackSize.width == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (isSelected && trackSize.width == 0) BorderStroke(1.dp, OrbitTheme.colors.cardBorder) else null,
                        shadowElevation = if (isSelected && trackSize.width == 0) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable {
                                if (!isSelected) {
                                    haptics.tick()
                                    onSelect(item)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label(item),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            OrbitHorizontalReveal(visible = itemCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ) {
                                        Text(
                                            text = itemCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            OrbitHorizontalReveal(visible = showError) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
