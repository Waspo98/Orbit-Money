package app.overbay.orbit.ui.transactions.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

internal val RuleBoltIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RuleBolt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M13 2L4.5 13.5H11.5L10.5 22L19.5 10.5H12.5L13 2Z").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

internal sealed interface FieldEditProvenance {
    data object None : FieldEditProvenance
    data object Manual : FieldEditProvenance
    data class AutoRule(val ruleId: Long?) : FieldEditProvenance
}

internal fun parseFieldProvenance(isEdited: Boolean, rawSource: String?): FieldEditProvenance {
    if (!isEdited) return FieldEditProvenance.None
    val src = rawSource?.trim()?.lowercase().orEmpty()
    return if (src.startsWith("rule:")) {
        FieldEditProvenance.AutoRule(src.removePrefix("rule:").toLongOrNull())
    } else {
        FieldEditProvenance.Manual
    }
}

/**
 * Interactive 1-tap Category pill used inside both the expanded accordion and wide-screen Inspector.
 */
@Composable
fun TransactionCategoryChip(
    categoryLabel: String,
    categoryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    Surface(
        shape = CircleShape,
        color = categoryColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.45f)),
        modifier = modifier
            .orbitTactilePress()
            .clip(CircleShape)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = categoryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Change Category",
                tint = categoryColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
internal fun FieldLabelWithProvenance(
    label: String,
    provenance: FieldEditProvenance,
    onReset: () -> Unit,
    onViewRule: (Long) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (provenance) {
            FieldEditProvenance.None -> {}
            FieldEditProvenance.Manual -> {
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edited",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { haptics.tick(); onReset() }
                )
            }
            is FieldEditProvenance.AutoRule -> {
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = RuleBoltIcon,
                    contentDescription = "Auto-Rule",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(12.dp)
                )
                if (provenance.ruleId != null) {
                    Text(
                        text = "View Rule",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .clickable { haptics.tick(); onViewRule(provenance.ruleId) }
                    )
                } else {
                    Text(
                        text = "Rule",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatusProvenancePill(
    text: String,
    provenance: FieldEditProvenance,
    onReset: () -> Unit,
    onViewRule: (Long) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (provenance) {
                FieldEditProvenance.None -> {}
                FieldEditProvenance.Manual -> Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edited",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp)
                )
                is FieldEditProvenance.AutoRule -> Icon(
                    imageVector = RuleBoltIcon,
                    contentDescription = "Auto-Rule",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            when (provenance) {
                FieldEditProvenance.None -> {}
                FieldEditProvenance.Manual -> Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { haptics.tick(); onReset() }
                )
                is FieldEditProvenance.AutoRule -> if (provenance.ruleId != null) {
                    Text(
                        text = "View Rule",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable { haptics.tick(); onViewRule(provenance.ruleId) }
                    )
                }
            }
        }
    }
}

@Composable
fun InspectorMetaItem(
    label: String,
    value: String,
    isMono: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitle: String? = null,
    onReset: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onReset != null) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).clickable { haptics.tick(); onReset() }
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (isMono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
