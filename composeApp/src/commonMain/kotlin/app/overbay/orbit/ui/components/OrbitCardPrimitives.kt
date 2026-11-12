package app.overbay.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Recreates `.dash-mini-stat-row` and `.dash-balance-row` with muted left label
 * (plus optional detail) and right tabular bold value, plus optional 1dp top border.
 */
@Composable
fun OrbitMiniStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    showTopDivider: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detail != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = valueColor
            )
        }
    }
}

/**
 * Recreates `.dash-compact-row` for lists inside cards (recent activity, uncategorized items, bills).
 */
@Composable
fun OrbitCompactListRow(
    title: String,
    amount: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    amountDetail: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    showTopDivider: Boolean = true,
    containerColor: Color? = null,
    borderColor: Color? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val rowBody: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showTopDivider && containerColor == null) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = OrbitTheme.colors.cardBorder
                )
            }

            val rowModifier = if (onClick != null) {
                Modifier
                    .fillMaxWidth()
                    .orbitTactilePress()
                    .clickable { onClick() }
                    .padding(horizontal = if (containerColor != null) 12.dp else 0.dp, vertical = 10.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (containerColor != null) 12.dp else 0.dp, vertical = 10.dp)
            }

            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingContent != null) {
                    leadingContent()
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (amount != null || amountDetail != null) {
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        if (amount != null) {
                            Text(
                                text = amount,
                                style = TabularNumStyle.copy(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = amountColor
                            )
                        }
                        if (amountDetail != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = amountDetail,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val trailing = trailingContent ?: trailingAction
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailing()
                }
            }
        }
    }

    if (containerColor != null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            border = borderColor?.let { BorderStroke(1.dp, it) },
            modifier = modifier.fillMaxWidth()
        ) {
            rowBody()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            rowBody()
        }
    }
}

/**
 * Standard Material 3 Badge primitive for status tags, counts, and metadata pills.
 */
@Composable
fun OrbitBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Badge(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Shared boxed metric tile for 2x2 and 3-column summary grids across cards and dialogs.
 */
@Composable
fun OrbitStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subText: String? = subtitle,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    subTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = TabularNumStyle.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Standard Material 3 AssistChip wrapper for compact pill actions in rows, headers, and inspectors.
 */
@Composable
fun OrbitActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingIcon: ImageVector? = icon,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    containerColor: Color = if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
    contentColor: Color = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
) {
    val haptics = LocalOrbitHaptics.current
    AssistChip(
        onClick = {
            haptics.tick()
            onClick()
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        },
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else null,
        enabled = enabled,
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = contentColor,
            leadingIconContentColor = contentColor
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = if (isDestructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            } else {
                OrbitTheme.colors.cardBorder
            }
        ),
        modifier = modifier.orbitTactilePress(enabled = enabled)
    )
}
