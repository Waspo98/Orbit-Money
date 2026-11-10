package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.CreditCardCycleEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Compact banking & billing summary card shown inside the Accounts Inspector for credit cards.
 * Keeps Recent Transactions above the fold while providing a 1-tap bridge to the dedicated
 * Card Perks & Rewards destination.
 */
@Composable
fun CreditAccountBillingCard(
    account: Account,
    transactions: List<Transaction>,
    onOpenCardPerks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val today = remember { LocalDateUtils.todayLocalDate() }
    val profile = account.creditCardProfile
    val cycleStatus = remember(account, transactions, today) {
        CreditCardCycleEngine.status(account, transactions, today)
    }
    val upgradedProfile = remember(profile, today) {
        profile?.let { CreditCardBenefitEngine.upgradeProfile(it, today) }
    }
    val ledger = remember(upgradedProfile, today) {
        upgradedProfile?.let { CreditCardBenefitEngine.ledger(it, today) }
    }
    val openedDate = remember(profile?.strategy?.openedDate) {
        profile?.strategy?.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
    }
    val unusedPerksCents = remember(upgradedProfile, openedDate, today) {
        upgradedProfile?.benefits?.sumOf { b ->
            val period = CreditCardBenefitEngine.currentPeriod(b, openedDate, today)
            val used = CreditCardBenefitEngine.usedCents(b, period)
            val value = CreditCardBenefitEngine.periodValueCents(b)
            (value - used).coerceAtLeast(0L)
        } ?: 0L
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Billing & Statement Cycle",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                cycleStatus?.daysUntilDue?.let { days ->
                    val tone = when {
                        days <= 3 -> CardStatusTone.CRITICAL
                        days in 4..7 -> CardStatusTone.WARNING
                        else -> CardStatusTone.NEUTRAL
                    }
                    CardStatusPill(text = "Due ${CardFormat.formatDaysUntil(days)}", tone = tone)
                }
            }

            // Credit Limit & Utilization Bar
            profile?.creditLimitCents?.takeIf { it > 0L }?.let { limit ->
                val ratio = profile.creditUtilizationRatio(account.currentBalanceCents) ?: 0.0
                val pct = (ratio * 100.0).roundToInt()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Credit Utilization ($pct%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Limit ${MoneyUtils.formatCents(limit, showCents = false)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OrbitPillProgressBar(
                        percent = ratio.toFloat().coerceIn(0f, 1f),
                        height = 6.dp
                    )
                }
            }

            // Compact 2-column billing facts
            val closeStr = cycleStatus?.nextCloseDate?.let { CardFormat.formatCardDate(it.toString()) }
                ?: profile?.statementClosingDay?.let { "Day $it" }
                ?: "Not Set"
            val dueStr = cycleStatus?.dueDate?.let { CardFormat.formatCardDate(it.toString()) }
                ?: profile?.paymentDueDay?.let { "Day $it" }
                ?: "Not Set"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BillingFactItem(label = "Statement Closes", value = closeStr)
                BillingFactItem(label = "Payment Due", value = dueStr)
                val aprLabel = when {
                    profile?.promoAprBps != null -> "Promo ${CardFormat.formatBps(profile.promoAprBps)}"
                    profile?.aprBps != null -> CardFormat.formatBps(profile.aprBps)
                    else -> "—"
                }
                BillingFactItem(label = "APR", value = aprLabel)
            }

            // 1-Tap Bridge to Card Perks & Rewards Page
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptics.tick()
                        onOpenCardPerks()
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = OrbitIcons.CardPerks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Credit Card Tracker & Rewards Strategy",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val perksSummary = when {
                                unusedPerksCents > 0L -> "${MoneyUtils.formatCents(unusedPerksCents, showCents = false)} in unused credits this period"
                                ledger != null && ledger.creditsAvailableCents > 0L -> "${MoneyUtils.formatCents(ledger.creditsCapturedCents, showCents = false)} of ${MoneyUtils.formatCents(ledger.creditsAvailableCents, showCents = false)} credits captured"
                                else -> "Manage statement credits, multipliers, bonuses & 5/24"
                            }
                            Text(
                                text = perksSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Credit Card Tracker",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BillingFactItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
