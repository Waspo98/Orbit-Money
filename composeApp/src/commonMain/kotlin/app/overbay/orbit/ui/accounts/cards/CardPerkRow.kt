package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CardPerkRow(
    title: String,
    subtitle: String?,
    valueCents: Cents,
    usedCents: Cents,
    daysUntilReset: Int?,
    onToggle: () -> Unit,
    onEditAmount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isFullyUsed = if (valueCents > 0L) usedCents >= valueCents else usedCents > 0L
    val isPartial = valueCents > 0L && usedCents > 0L && usedCents < valueCents

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 48dp leading IconToggleButton with haptics
            IconToggleButton(
                checked = isFullyUsed,
                onCheckedChange = { checked ->
                    if (checked) haptics.confirm() else haptics.tick()
                    onToggle()
                },
                modifier = Modifier.size(48.dp)
            ) {
                if (isFullyUsed) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Perk Used",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Body clickable to edit partial amount
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEditAmount() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isFullyUsed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isPartial) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${MoneyUtils.formatCents(usedCents)} of ${MoneyUtils.formatCents(valueCents)} used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OrbitPillProgressBar(
                        percent = (usedCents.toFloat() / valueCents.toFloat()).coerceIn(0f, 1f),
                        height = 6.dp
                    )
                } else if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (daysUntilReset != null) {
                    val isSoon = !isFullyUsed && daysUntilReset <= 7
                    val resetColor = if (isSoon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    Text(
                        text = "Resets ${CardFormat.formatDaysUntil(daysUntilReset)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = if (isSoon) FontWeight.Bold else FontWeight.Normal,
                        color = resetColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing benefit amount
            if (valueCents > 0L) {
                Text(
                    text = MoneyUtils.formatCents(valueCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFullyUsed) OrbitTheme.colors.income else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
