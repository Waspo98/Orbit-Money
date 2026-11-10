package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import app.overbay.orbit.domain.ActivePerkItem
import app.overbay.orbit.domain.CategoryBestCardPick
import app.overbay.orbit.domain.CreditCardPortfolioSummary
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.SubProgressSummary
import app.overbay.orbit.domain.SubTrackerStatus
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
                Text(
                    text = if (sub.status == SubTrackerStatus.COMPLETED) "Earned!" else "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (sub.status == SubTrackerStatus.COMPLETED) OrbitColorTokens.Green else MaterialTheme.colorScheme.onSurface
                )
            }
            OrbitPillProgressBar(percent = sub.progressRatio, overBudget = false, height = 6.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${MoneyUtils.formatCents(sub.currentSpendCents)} / ${MoneyUtils.formatCents(sub.targetSpendCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val rightMeta = when {
                    sub.status == SubTrackerStatus.COMPLETED -> "Bonus Unlocked"
                    sub.daysRemaining != null && sub.daysRemaining >= 0 -> {
                        val pace = sub.dailyPaceNeededCents?.let { "${MoneyUtils.formatCents(it)}/d • " }.orEmpty()
                        "${pace}${sub.daysRemaining}d left"
                    }
                    sub.deadlineDate != null -> "Due ${sub.deadlineDate}"
                    else -> "${MoneyUtils.formatCents(sub.remainingSpendCents)} to go"
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

@Composable
fun CardBenefitChecklistRow(benefit: CardBenefit, onToggle: () -> Unit) {
    val isUsed = CreditCardStrategyEngine.isBenefitUsedInCurrentPeriod(benefit)
    val periodLabel = CreditCardStrategyEngine.currentPeriodDisplayLabel(benefit.cadence)
    val perPeriodCents = benefit.annualCreditValueCents?.let {
        (it.toDouble() / benefit.cadence.periodsPerYear.toDouble()).roundToLong()
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

@Composable
fun PerksAndFeeRoiCard(
    summary: CreditCardPortfolioSummary,
    onToggleBenefit: (accountId: Long, benefitIndex: Int) -> Unit,
    onInspectAccount: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "ANNUAL FEE ROI & STATEMENT CREDITS RADAR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoiStatBox(
                    label = "Annual Fees",
                    value = MoneyUtils.formatCents(summary.totalAnnualFeesCents, showCents = false),
                    modifier = Modifier.weight(1f)
                )
                RoiStatBox(
                    label = "Credits Used",
                    value = MoneyUtils.formatCents(summary.capturedCreditsYtdCents, showCents = false),
                    isPositive = summary.capturedCreditsYtdCents > 0L,
                    modifier = Modifier.weight(1f)
                )
                RoiStatBox(
                    label = "Net Cost",
                    value = MoneyUtils.formatCents(summary.netEffectiveAnnualFeeCents, showCents = false),
                    isPositive = summary.netEffectiveAnnualFeeCents <= 0L,
                    modifier = Modifier.weight(1f)
                )
            }

            if (summary.currentPeriodPerks.isEmpty()) {
                Text(
                    text = "Tap any credit card and open 'Edit Perks' to add or 1-tap autofill monthly/annual credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.currentPeriodPerks.forEach { item ->
                        HubPerkChecklistRow(
                            item = item,
                            onToggle = { onToggleBenefit(item.accountId, item.benefitIndex) },
                            onInspect = { onInspectAccount(item.accountId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoiStatBox(
    label: String,
    value: String,
    isPositive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = if (isPositive) OrbitColorTokens.Green else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HubPerkChecklistRow(
    item: ActivePerkItem,
    onToggle: () -> Unit,
    onInspect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable { onInspect() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isUsedInCurrentPeriod,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.benefit.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isUsedInCurrentPeriod) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(item.cardTitle, item.cardholder, item.periodLabel).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (item.periodValueCents > 0L) {
                Text(
                    text = MoneyUtils.formatCents(item.periodValueCents, showCents = false),
                    style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = if (item.isUsedInCurrentPeriod) OrbitColorTokens.Green else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun BestCardsByCategoryCard(
    picks: List<CategoryBestCardPick>,
    onInspectAccount: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "BEST CARD TO USE BY CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold
            )
            picks.forEach { pick ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().clickable { onInspectAccount(pick.accountId) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = pick.categoryName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = listOfNotNull(pick.cardTitle, pick.cardholder).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        }
                        val multStr = if (pick.multiplier == pick.multiplier.toLong().toDouble()) {
                            "${pick.multiplier.toLong()}x"
                        } else "${pick.multiplier}x"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = multStr,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
