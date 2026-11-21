package app.overbay.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Standard section card for Orbit Money with 16dp rounded corners, subtle border,
 * clean title header, optional badge/action link, and header divider.
 */
@Composable
fun OrbitSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerBadge: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    showHeaderDivider: Boolean = true,
    edgeToEdgeContent: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val layoutDirection = LocalLayoutDirection.current
    val cardInteractionSource = remember { MutableInteractionSource() }
    val interactiveModifier = if (onCardClick != null) {
        modifier
            .fillMaxWidth()
            .orbitTactilePress(pressedScale = 0.985f, interactionSource = cardInteractionSource)
            .clip(shape)
            .clickable(
                interactionSource = cardInteractionSource,
                indication = LocalIndication.current
            ) {
                haptics.tick()
                onCardClick()
            }
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = interactiveModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (edgeToEdgeContent) {
                        Modifier.clip(shape)
                    } else {
                        Modifier.padding(contentPadding)
                    }
                )
        ) {
            val hasHeader = title != null || action != null || actionText != null || headerBadge != null
            if (hasHeader) {
                val headerModifier = if (edgeToEdgeContent) {
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                            top = contentPadding.calculateTopPadding()
                        )
                } else {
                    Modifier.fillMaxWidth()
                }
                Row(
                    modifier = headerModifier,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null || headerBadge != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (title != null) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                if (headerBadge != null) {
                                    headerBadge()
                                }
                            }
                        }
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (actionText != null && onActionClick != null) {
                        val actionInteractionSource = remember { MutableInteractionSource() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .orbitTactilePress(interactionSource = actionInteractionSource)
                                .clickable(
                                    interactionSource = actionInteractionSource,
                                    indication = LocalIndication.current
                                ) {
                                    haptics.tick()
                                    onActionClick()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = actionText,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(1.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (action != null) {
                        action()
                    }
                }

                if (showHeaderDivider) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = OrbitTheme.colors.cardBorder,
                        modifier = if (edgeToEdgeContent) {
                            Modifier.padding(top = 10.dp)
                        } else {
                            Modifier.padding(top = 10.dp, bottom = 12.dp)
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            content()
        }
    }
}
