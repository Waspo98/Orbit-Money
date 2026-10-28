package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Primary surface capsule used inside [MorphingPageHero]'s 2-tier `canopyBar`.
 * Matches the crisp surface + subtle border aesthetic of `TransactionsSearchBar`
 * and smoothly crossfades between [expandedText] and [compactText] on scroll.
 */
@Composable
fun CanopySurfacePill(
    expandedText: String,
    modifier: Modifier = Modifier,
    compactText: String = expandedText,
    isCompact: Boolean = false,
    leadingIcon: ImageVector? = null,
    accentText: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val horizontalPad = if (isCompact) 12.dp else 16.dp
        val iconSize = if (isCompact) 16.dp else 18.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(if (isCompact) 6.dp else 8.dp))
                }

                AnimatedContent(
                    targetState = isCompact,
                    transitionSpec = {
                        fadeIn(OrbitSprings.effectsDefault()) togetherWith
                            fadeOut(OrbitSprings.effectsDefault())
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "canopyPillTextTransition"
                ) { compact ->
                    Text(
                        text = if (compact) compactText else expandedText,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (accentText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = accentText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Circular action button positioned at the trailing edge of [MorphingPageHero]'s `canopyBar`.
 * Shrinks in lockstep with the main capsule from 46dp (expanded) to 38dp (collapsed).
 */
@Composable
fun CanopyIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    badgeCount: Int = 0,
    enabled: Boolean = true
) {
    val haptics = LocalOrbitHaptics.current
    val iconSize = if (buttonSize < 42.dp) 18.dp else 20.dp

    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier.size(buttonSize)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(badgeCount.toString())
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (!enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * Compact pill button with icon + label for canopy actions (e.g., "Run Rules").
 */
@Composable
fun CanopyActionPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    compactLabel: String = label,
    isCompact: Boolean = false,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    val haptics = LocalOrbitHaptics.current
    val iconSize = if (buttonSize < 42.dp) 16.dp else 18.dp

    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier.height(buttonSize)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (isCompact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = if (isCompact) compactLabel else label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
        }
    }
}

/**
 * Segmented capsule bar for period selection (Spending Trends, Net Worth) inside `canopyBar`.
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

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEach { item ->
                val selected = item == selectedItem
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable {
                            haptics.tick()
                            onSelect(item)
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = labelProvider(item),
                            fontSize = if (isCompact) 11.sp else 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
