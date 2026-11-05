package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Connected M3E capsule period navigator: `< April 2026 >`.
 * Matches the crisp surface + subtle border aesthetic of `CanopySurfacePill`
 * and smoothly crossfades between [label] and [compactLabel] when collapsed.
 */
@Composable
fun PeriodNavigatorPill(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    compactLabel: String = label,
    isCompact: Boolean = false,
    canGoNext: Boolean = true,
    onClickLabel: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val buttonSize = if (isCompact) 34.dp else 38.dp

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    haptics.tick()
                    onPrevious()
                },
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Period",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedContent(
                targetState = isCompact,
                transitionSpec = {
                    fadeIn(OrbitSprings.effectsDefault()) togetherWith
                        fadeOut(OrbitSprings.effectsDefault())
                },
                contentAlignment = Alignment.Center,
                label = "periodNavigatorLabelTransition"
            ) { compact ->
                Text(
                    text = if (compact) compactLabel else label,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (onClickLabel != null) {
                                Modifier.clickable {
                                    haptics.tick()
                                    onClickLabel()
                                }
                            } else Modifier
                        )
                        .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = {
                    if (canGoNext) {
                        haptics.tick()
                        onNext()
                    }
                },
                enabled = canGoNext,
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Period",
                    tint = if (canGoNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
