package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AnnualFeeCardItem
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
internal fun AnnualFeesAheadBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(title = "Annual Fees Ahead", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uiState.feesAhead.isEmpty()) {
                Text(
                    text = "No annual fees due in the next 90 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                uiState.feesAhead.forEach { item ->
                    AnnualFeeRow(item = item, onClick = { actions.onInspectAccount(item.status.accountId) })
                }
            }
        }
    }
}

@Composable
private fun AnnualFeeRow(item: AnnualFeeCardItem, onClick: () -> Unit) {
    val cycle = item.status
    val feeStr = MoneyUtils.formatCents(cycle.annualFeeCents, showCents = false)
    val dateStr = cycle.nextAnnualFeeDate?.let {
        "${LocalDateUtils.formatShortDate(it)} (${CardFormat.formatDaysUntil(cycle.daysUntilAnnualFee ?: 0)})"
    } ?: "Upcoming"
    val captured = MoneyUtils.formatCents(item.ledger.creditsCapturedCents, showCents = false)
    val avail = MoneyUtils.formatCents(item.ledger.creditsAvailableCents, showCents = false)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = cycle.cardTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(text = "Annual fee $feeStr · Due $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Credits used: $captured of $avail this card-year", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun PaymentsDueBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val dueCycles = remember(uiState.cardCycles) {
        uiState.cardCycles.filter { it.daysUntilDue != null && it.daysUntilDue in -7..35 }.sortedBy { it.daysUntilDue }
    }

    OrbitSectionCard(title = "Billing & Payments Due", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dueCycles.isEmpty()) {
                Text(
                    text = "No payments due soon · All accounts current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                dueCycles.forEach { cycle ->
                    PaymentDueRow(cycle = cycle, onClick = { actions.onInspectAccount(cycle.accountId) })
                }
            }
        }
    }
}

@Composable
private fun PaymentDueRow(cycle: CardCycleStatus, onClick: () -> Unit) {
    val days = cycle.daysUntilDue ?: 0
    val (dueLabel, dueTone) = when {
        days < 0 -> "Overdue" to CardStatusTone.CRITICAL
        days in 0..3 -> CardFormat.formatDaysUntil(days) to CardStatusTone.CRITICAL
        days in 4..7 -> CardFormat.formatDaysUntil(days) to CardStatusTone.WARNING
        else -> CardFormat.formatDaysUntil(days) to CardStatusTone.NEUTRAL
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = cycle.cardTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val amountStr = cycle.estimatedStatementBalanceCents?.let { "Est. ${MoneyUtils.formatCents(it)}" }
                        ?: "Owed ${MoneyUtils.formatCents(cycle.owedNowCents)}"
                    Text(text = amountStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CardStatusPill(text = dueLabel, tone = dueTone)
            }
            if (cycle.daysUntilPromoEnd != null && cycle.daysUntilPromoEnd in 0..60) {
                CardStatusPill(text = "0% Promo APR ends in ${cycle.daysUntilPromoEnd}d", tone = CardStatusTone.WARNING)
            }
        }
    }
}
