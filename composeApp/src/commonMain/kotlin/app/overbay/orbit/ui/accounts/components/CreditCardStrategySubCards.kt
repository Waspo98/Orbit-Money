package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.SubProgressSummary
import app.overbay.orbit.domain.SubTrackerStatus
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import kotlin.math.roundToInt

/**
 * Legacy support components retained for CreditCardPerksPanel until Phase U3 card detail rewrite.
 */
@Deprecated("Removed in Phase U3 along with CreditCardPerksPanel")
@Composable
fun StrategyPillBadge(
    text: String,
    isAccent: Boolean = false,
    isPositive: Boolean = false
) {
    val bg = when {
        isPositive -> OrbitColorTokens.Green.copy(alpha = 0.16f)
        isAccent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        isPositive -> OrbitColorTokens.Green
        isAccent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Deprecated("Removed in Phase U3 along with CreditCardPerksPanel")
@Composable
fun SubTrackerMiniCard(sub: SubProgressSummary) {
    val pct = (sub.progressRatio * 100f).roundToInt()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "SUB: ${sub.rewardText}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val (badgeText, badgeColor) = when (sub.status) {
                    SubTrackerStatus.RECEIVED -> "Bonus Received" to OrbitColorTokens.Green
                    SubTrackerStatus.SPEND_MET -> "Spend Met · Awaiting Bonus" to OrbitColorTokens.Green
                    SubTrackerStatus.EXPIRED -> "Expired" to MaterialTheme.colorScheme.outline
                    SubTrackerStatus.AT_RISK -> "$pct%" to MaterialTheme.colorScheme.error
                    SubTrackerStatus.ON_TRACK -> "$pct%" to MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            OrbitPillProgressBar(percent = sub.progressRatio, overBudget = false, height = 6.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${MoneyUtils.formatCents(sub.currentSpendCents)} / ${MoneyUtils.formatCents(sub.targetSpendCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val rightMeta = when (sub.status) {
                    SubTrackerStatus.RECEIVED -> "Bonus Received"
                    SubTrackerStatus.SPEND_MET -> "Spend Met"
                    SubTrackerStatus.EXPIRED -> "Expired"
                    SubTrackerStatus.AT_RISK, SubTrackerStatus.ON_TRACK -> {
                        if (sub.daysRemaining != null && sub.daysRemaining >= 0) {
                            val pace = sub.dailyPaceNeededCents?.let { "${MoneyUtils.formatCents(it)}/d • " }.orEmpty()
                            "${pace}${sub.daysRemaining}d left"
                        } else if (sub.deadlineDate != null) {
                            "Due ${sub.deadlineDate}"
                        } else "${MoneyUtils.formatCents(sub.remainingSpendCents)} to go"
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sub.status == SubTrackerStatus.AT_RISK) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "At Risk",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = rightMeta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sub.status == SubTrackerStatus.AT_RISK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Deprecated("Removed in Phase U3 along with CreditCardPerksPanel")
@Composable
fun CardBenefitChecklistRow(benefit: CardBenefit, onToggle: () -> Unit) {
    val period = CreditCardBenefitEngine.currentPeriod(benefit, null, LocalDateUtils.todayLocalDate())
    val isUsed = CreditCardBenefitEngine.usedCents(benefit, period) > 0L ||
        (benefit.usedPeriods.isNotEmpty() && period.key in benefit.usedPeriods) ||
        (benefit.usedPeriods.isEmpty() && benefit.usedThisYear)
    val periodLabel = period.label
    val perPeriodCents = CreditCardBenefitEngine.periodValueCents(benefit).takeIf { it > 0 }
        ?: benefit.annualCreditValueCents?.let {
            val periodsInYear = (12 / benefit.cadence.periodMonths).coerceAtLeast(1)
            it / periodsInYear
        }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isUsed, onCheckedChange = { onToggle() }, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = benefit.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isUsed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(periodLabel, benefit.description?.takeIf { it.isNotBlank() }).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            perPeriodCents?.let { creditVal ->
                Text(
                    text = MoneyUtils.formatCents(creditVal),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUsed) OrbitColorTokens.Green else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
