package app.overbay.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle

/**
 * Slim pill-shaped progress bar for budget pulse, goal funding, and category limits.
 */
@Composable
fun OrbitPillProgressBar(
    percent: Float,
    modifier: Modifier = Modifier,
    overBudget: Boolean = false,
    height: Dp = 10.dp,
    customFillColor: Color? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val clampedPercent = percent.coerceIn(0f, 1f)
    val fillColor = customFillColor ?: if (overBudget) {
        OrbitTheme.colors.expense
    } else if (percent >= 0.85f) {
        OrbitTheme.colors.warning
    } else {
        MaterialTheme.colorScheme.primary
    }

    LinearProgressIndicator(
        progress = { clampedPercent },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape),
        color = fillColor,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/**
 * Recreates `.dash-mini-stat-row` with left label (plus optional detail) and right tabular bold value.
 */
@Composable
fun OrbitMiniStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            style = TabularNumStyle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor
        )
    }
}

/**
 * Recreates `.dash-compact-row` for lists inside cards (recent activity, uncategorized items, bills).
 */
@Composable
fun OrbitCompactListRow(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    amountDetail: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    showTopDivider: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = OrbitTheme.colors.cardBorder
            )
        }

        val rowModifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 9.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp)
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amount,
                    style = TabularNumStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
                if (amountDetail != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = amountDetail,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trailingAction != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingAction()
            }
        }
    }
}
