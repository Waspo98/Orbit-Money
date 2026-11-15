package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AnnualFeeCardItem
import app.overbay.orbit.ui.accounts.cards.CardEditorTab
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
internal fun AnnualFeesAheadBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val scopedFees = if (uiState.playerScopedHub.walletAccountIds.isNotEmpty()) {
        uiState.playerScopedHub.feesAhead
    } else {
        uiState.feesAhead
    }

    OrbitSectionCard(title = "Annual Fees Ahead", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (scopedFees.isEmpty()) {
                Text(
                    text = "No annual fees due in the next 90 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                scopedFees.forEach { item ->
                    AnnualFeeRow(item = item, onClick = { actions.onInspectAccount(item.status.accountId) })
                }
            }
        }
    }
}

@Composable
private fun AnnualFeeRow(item: AnnualFeeCardItem, onClick: () -> Unit) {
    val haptics = LocalOrbitHaptics.current
    val cycle = item.status
    val feeStr = CardFormat.formatAdaptiveCents(cycle.annualFeeCents)
    val dateStr = cycle.nextAnnualFeeDate?.let {
        "${LocalDateUtils.formatShortDate(it)} (${CardFormat.formatDaysUntil(cycle.daysUntilAnnualFee ?: 0)})"
    } ?: "Upcoming"
    val captured = CardFormat.formatAdaptiveCents(item.ledger.creditsCapturedCents)
    val avail = CardFormat.formatAdaptiveCents(item.ledger.creditsAvailableCents)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cycle.cardTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = feeStr,
                        style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Due $dateStr",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Credits captured: $captured of $avail this card-year",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = OrbitTheme.colors.income
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 6.dp).size(16.dp)
            )
        }
    }
}

@Composable
internal fun PaymentsDueBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val scopedCycles = if (uiState.playerScopedHub.walletAccountIds.isNotEmpty() || uiState.playerScopedHub.cardCycles.isNotEmpty()) {
        uiState.playerScopedHub.cardCycles
    } else {
        uiState.cardCycles
    }
    val dueCycles = remember(scopedCycles) {
        scopedCycles.filter { it.daysUntilDue != null && it.daysUntilDue in -7..35 }.sortedBy { it.daysUntilDue }
    }
    val missingCount = uiState.playerScopedHub.missingBillingDatesCount
    val firstMissingId = uiState.playerScopedHub.firstMissingBillingAccountId

    OrbitSectionCard(title = "Billing & Payments Due", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dueCycles.isEmpty()) {
                Text(
                    text = "No payments due soon \u00B7 All accounts current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                dueCycles.forEach { cycle ->
                    PaymentDueRow(cycle = cycle, onClick = { actions.onInspectAccount(cycle.accountId) })
                }
            }

            if (missingCount > 0 && firstMissingId != null) {
                OrbitActionChip(
                    label = "$missingCount card${if (missingCount == 1) "" else "s"} missing billing dates \u2014 Set now",
                    onClick = { actions.onOpenCardSetup(firstMissingId, CardEditorTab.BILLING, false) }
                )
            }
        }
    }
}

@Composable
private fun PaymentDueRow(cycle: CardCycleStatus, onClick: () -> Unit) {
    val haptics = LocalOrbitHaptics.current
    val days = cycle.daysUntilDue ?: 0
    val (dueLabel, dueTone) = when {
        days < 0 -> "Overdue" to CardStatusTone.CRITICAL
        days in 0..3 -> CardFormat.formatDaysUntil(days) to CardStatusTone.CRITICAL
        days in 4..7 -> CardFormat.formatDaysUntil(days) to CardStatusTone.WARNING
        else -> CardFormat.formatDaysUntil(days) to CardStatusTone.NEUTRAL
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cycle.cardTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val amountStr = cycle.estimatedStatementBalanceCents?.let { "Est. ${MoneyUtils.formatCents(it)}" }
                        ?: "Owed ${MoneyUtils.formatCents(cycle.owedNowCents)}"
                    Text(
                        text = amountStr,
                        style = TabularNumStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CardStatusPill(text = dueLabel, tone = dueTone)
            }
            if (cycle.daysUntilPromoEnd != null && cycle.daysUntilPromoEnd in 0..60) {
                CardStatusPill(text = "0% Promo APR ends in ${cycle.daysUntilPromoEnd}d", tone = CardStatusTone.WARNING)
            }
        }
    }
}
