package app.overbay.orbit.ui.mha.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.mha.MhaLimitingFactor

@Composable
fun MhaThreeLimitHeroCard(
    actualSpentCents: Cents,
    designatedCapCents: Cents?,
    fairMarketRentalValueCents: Cents?,
    excludableCapCents: Cents,
    bindingLimit: MhaLimitingFactor,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "IRS § 107 Three-Limit Test",
        subtitle = "Least of Expenses, Church Designation, or Fair Rental Value",
        action = {
            FilledTonalButton(
                onClick = onConfigureClick,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Edit Caps", style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EXCLUDABLE HOUSING ALLOWANCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            RollingTickerCents(
                cents = excludableCapCents,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Binding Limit Status Banner
            BindingLimitBadge(bindingLimit)

            Spacer(modifier = Modifier.height(20.dp))

            // Comparison Rows for the 3 Limits
            val referenceDenominator = maxOf(
                actualSpentCents,
                designatedCapCents ?: 0L,
                fairMarketRentalValueCents ?: 0L,
                1L
            ).toFloat()

            LimitProgressRow(
                label = "1. Actual Expenses Paid",
                amountCents = actualSpentCents,
                progress = (actualSpentCents / referenceDenominator).coerceIn(0f, 1f),
                isBinding = bindingLimit == MhaLimitingFactor.ACTUAL_EXPENSES || bindingLimit == MhaLimitingFactor.UNCAPPED_ACTUAL,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            LimitProgressRow(
                label = "2. Church Designation Cap",
                amountCents = designatedCapCents,
                progress = designatedCapCents?.let { (it / referenceDenominator).coerceIn(0f, 1f) } ?: 0f,
                isBinding = bindingLimit == MhaLimitingFactor.DESIGNATION_CAP,
                tint = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            LimitProgressRow(
                label = "3. Fair Rental Value (FMRV)",
                amountCents = fairMarketRentalValueCents,
                progress = fairMarketRentalValueCents?.let { (it / referenceDenominator).coerceIn(0f, 1f) } ?: 0f,
                isBinding = bindingLimit == MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE,
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun BindingLimitBadge(bindingLimit: MhaLimitingFactor) {
    val (bgColor, textColor) = when (bindingLimit) {
        MhaLimitingFactor.ACTUAL_EXPENSES -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        MhaLimitingFactor.DESIGNATION_CAP -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MhaLimitingFactor.FAIR_MARKET_RENTAL_VALUE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MhaLimitingFactor.UNCAPPED_ACTUAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = bindingLimit.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = bindingLimit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun LimitProgressRow(
    label: String,
    amountCents: Cents?,
    progress: Float,
    isBinding: Boolean,
    tint: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "LimitProgressAnimation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBinding) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tint)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isBinding) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = amountCents?.let { MoneyUtils.formatCents(it) } ?: "Not Set",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (amountCents == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
