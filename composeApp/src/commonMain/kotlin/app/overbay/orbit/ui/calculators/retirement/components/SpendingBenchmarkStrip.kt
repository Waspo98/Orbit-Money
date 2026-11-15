package app.overbay.orbit.ui.calculators.retirement.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Displays the 55%–80% gross-income retirement spending guideline band and quick-snap chips.
 */
@Composable
internal fun SpendingBenchmarkStrip(
    grossIncomeAnnualCents: Cents,
    annualSpendingCents: Cents,
    minSpendingCents: Cents,
    maxSpendingCents: Cents,
    onApplyFraction: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val pct55 = (grossIncomeAnnualCents.toDouble() * 0.55).roundToLong()
    val pct80 = (grossIncomeAnnualCents.toDouble() * 0.80).roundToLong()
    val is55Active = abs(annualSpendingCents - pct55) <= 2_500_00L
    val is80Active = abs(annualSpendingCents - pct80) <= 2_500_00L
    val span = (maxSpendingCents - minSpendingCents).coerceAtLeast(1L).toDouble()
    val f55 = ((pct55 - minSpendingCents) / span).toFloat().coerceIn(0f, 1f)
    val f80 = ((pct80 - minSpendingCents) / span).toFloat().coerceIn(f55, 1f)
    val fCur = ((annualSpendingCents - minSpendingCents) / span).toFloat().coerceIn(0f, 1f)

    Spacer(modifier = Modifier.height(6.dp))
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val trackW = maxWidth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(999.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = trackW * f55)
                .width((trackW * (f80 - f55)).coerceAtLeast(4.dp))
                .height(4.dp)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = (trackW * fCur - 4.dp).coerceIn(0.dp, trackW - 8.dp))
                .size(8.dp)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BenchmarkSnapChip(
            label = "55% · ${MoneyUtils.formatCompactCents(pct55)}",
            selected = is55Active,
            onClick = { onApplyFraction(0.55) },
            modifier = Modifier.weight(1f)
        )
        BenchmarkSnapChip(
            label = "80% · ${MoneyUtils.formatCompactCents(pct80)}",
            selected = is80Active,
            onClick = { onApplyFraction(0.80) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun BenchmarkSnapChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else OrbitTheme.colors.cardBorder
        ),
        modifier = modifier
            .clip(shape)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
