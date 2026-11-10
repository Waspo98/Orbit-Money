package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.*
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditCardDetailSection(
    account: Account,
    profile: CreditCardProfile,
    transactions: List<Transaction> = emptyList(),
    subProgress: SubProgressSummary? = null,
    householdMembers: List<HouseholdMember> = emptyList(),
    rewardsToolsEnabled: Boolean = true,
    showBillingDetails: Boolean = true,
    onToggleBenefit: (benefitId: String) -> Unit,
    onOpenPerkUsage: (benefitId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDateUtils.todayLocalDate() }
    val openedDate = remember(profile.strategy.openedDate) {
        profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
    }
    val upgradedProfile = remember(profile, today) {
        CreditCardBenefitEngine.upgradeProfile(profile, today)
    }
    val cycleStatus = remember(account, transactions, today) {
        CreditCardCycleEngine.status(account, transactions, today)
    }
    val ledger = remember(upgradedProfile, today) {
        CreditCardBenefitEngine.ledger(upgradedProfile, today)
    }
    val subEligibility = remember(profile) {
        CreditCardStrategyEngine.computeSubEligibility(profile)
    }
    val holderName = remember(profile, householdMembers, cycleStatus) {
        CardFormat.resolveCardholderName(profile, householdMembers)
            ?: cycleStatus?.holderLabel?.takeIf { it.isNotBlank() && it != "Unassigned" }
    }
    val auNames = remember(profile, householdMembers) {
        CardFormat.resolveAuthorizedUsers(profile, householdMembers)
    }
    val network = profile.network?.takeIf { it.isNotBlank() }
    val balanceColor = if (account.currentBalanceCents != 0L) OrbitTheme.colors.expense else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 1. Fixed-width Card Plate + Balance & Cardholder/AU Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CardVisualPlate(
                    name = profile.cardName ?: account.name,
                    issuer = profile.issuerName ?: account.institution,
                    artKey = profile.imageUrl,
                    artworkUrl = profile.artworkUrl,
                    last4 = account.accountNumberLast4,
                    modifier = Modifier
                        .width(132.dp)
                        .aspectRatio(1.586f)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Current Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyUtils.formatCents(account.currentBalanceCents),
                        style = TabularNumStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = balanceColor
                    )
                    if (network != null || holderName != null || auNames.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (network != null) {
                                OrbitBadge(
                                    text = network,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (holderName != null) {
                                OrbitBadge(
                                    text = holderName,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            auNames.forEach { au ->
                                OrbitBadge(
                                    text = "AU · $au",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // 2. Stats Row
            CardDetailStatsRow(account, profile, cycleStatus)

            // 3. Billing & Payment Details
            if (showBillingDetails) {
                CardDetailBillingDetails(profile, cycleStatus)
            }

            // 4. Perks & Credits Checklist
            if (upgradedProfile.benefits.isNotEmpty()) {
                CardDetailPerksList(upgradedProfile.benefits, openedDate, today, onToggleBenefit, onOpenPerkUsage)
            }

            // 5. Annual Fee & Value Ledger
            if (profile.annualFeeCents != null || upgradedProfile.benefits.isNotEmpty()) {
                CardDetailLedgerSection(ledger, cycleStatus?.nextAnnualFeeDate, cycleStatus?.daysUntilAnnualFee)
            }

            // 6. Rewards Strategy & Multipliers
            if (rewardsToolsEnabled || profile.rewardCategories.isNotEmpty() || subProgress != null) {
                CardDetailStrategySection(profile, subProgress, subEligibility, rewardsToolsEnabled, today)
            }

            // 7. Notes
            profile.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetailStatsRow(
    account: Account,
    profile: CreditCardProfile,
    cycle: CardCycleStatus?
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cycle?.daysUntilDue?.let { days ->
            val tone = when {
                days <= 3 -> CardStatusTone.CRITICAL
                days in 4..7 -> CardStatusTone.WARNING
                else -> CardStatusTone.NEUTRAL
            }
            CardStatusPill("Due ${CardFormat.formatDaysUntil(days)}", tone)
        }
        profile.creditLimitCents?.let { limit ->
            val ratio = profile.creditUtilizationRatio(account.currentBalanceCents)
            val pct = if (ratio != null) (ratio * 100.0).roundToInt() else 0
            val tone = if (pct > 30) CardStatusTone.CRITICAL else if (pct > 10) CardStatusTone.WARNING else CardStatusTone.POSITIVE
            CardStatusPill("$pct% Util of ${MoneyUtils.formatCents(limit)}", tone)
        }
        if (profile.promoAprBps != null && (cycle?.daysUntilPromoEnd == null || cycle.daysUntilPromoEnd >= 0)) {
            CardStatusPill("Promo APR ${CardFormat.formatBps(profile.promoAprBps)}", CardStatusTone.POSITIVE)
        } else if (profile.aprBps != null) {
            CardStatusPill("APR ${CardFormat.formatBps(profile.aprBps)}", CardStatusTone.NEUTRAL)
        }
    }
}

@Composable
private fun CardDetailBillingDetails(profile: CreditCardProfile, cycle: CardCycleStatus?) {
    val rows = buildList {
        add("Statement Closes" to (cycle?.nextCloseDate?.let { CardFormat.formatCardDate(it.toString()) } ?: profile.statementClosingDay?.let { "Day $it" } ?: "Not Set"))
        add("Payment Due" to (cycle?.dueDate?.let { CardFormat.formatCardDate(it.toString()) } ?: profile.paymentDueDay?.let { "Day $it" } ?: "Not Set"))
        cycle?.estimatedStatementBalanceCents?.let { add("Est. Statement Balance" to MoneyUtils.formatCents(it)) }
        if (profile.promoAprBps != null) {
            val endText = profile.promoEndDate?.let { " (ends ${CardFormat.formatCardDate(it)})" } ?: ""
            add("Promo APR" to "${CardFormat.formatBps(profile.promoAprBps)}$endText")
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Billing & Payment Details", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CardDetailPerksList(
    benefits: List<CardBenefit>,
    openedDate: LocalDate?,
    today: LocalDate,
    onToggle: (String) -> Unit,
    onOpenUsage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Statement Credits & Perks", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        benefits.forEach { b ->
            val period = remember(b, openedDate, today) { CreditCardBenefitEngine.currentPeriod(b, openedDate, today) }
            val used = remember(b, period) { CreditCardBenefitEngine.usedCents(b, period) }
            val value = remember(b) { CreditCardBenefitEngine.periodValueCents(b) }
            val days = remember(period, today) { CreditCardBenefitEngine.daysUntilReset(period, today) }
            CardPerkRow(
                title = b.title,
                subtitle = b.description ?: period.label,
                valueCents = value,
                usedCents = used,
                daysUntilReset = days,
                onToggle = { onToggle(b.id) },
                onEditAmount = { onOpenUsage(b.id) }
            )
        }
    }
}

@Composable
private fun CardDetailLedgerSection(ledger: CardValueLedger, nextFeeDate: LocalDate?, daysUntilFee: Int?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OrbitColorTokens.Purple.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, OrbitColorTokens.Purple.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Annual Fee & Value Ledger", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OrbitColorTokens.Purple)
                if (nextFeeDate != null) {
                    val daysSuffix = daysUntilFee?.let { " (${CardFormat.formatDaysUntil(it)})" } ?: ""
                    Text("Renews ${CardFormat.formatCardDate(nextFeeDate.toString())}$daysSuffix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text("Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(MoneyUtils.formatCents(ledger.annualFeeCents), style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("Credits Captured", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("${MoneyUtils.formatCents(ledger.creditsCapturedCents)} of ${MoneyUtils.formatCents(ledger.creditsAvailableCents)}", style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OrbitColorTokens.Green))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Fee After Credits (This Card-Year)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    val feeColor = if (ledger.feeAfterCreditsCents <= 0L) OrbitColorTokens.Green else MaterialTheme.colorScheme.onSurface
                    Text(MoneyUtils.formatCents(ledger.feeAfterCreditsCents), style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = feeColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetailStrategySection(
    profile: CreditCardProfile,
    subProgress: SubProgressSummary?,
    subEligibility: SubEligibilityInfo?,
    rewardsToolsEnabled: Boolean,
    today: LocalDate
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rewards Strategy & Multipliers", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (profile.rewardCategories.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                profile.rewardCategories.forEach { cat ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(CardFormat.formatEarnRate(cat), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(cat.label.ifBlank { cat.category }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (subProgress != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pct = (subProgress.progressRatio * 100f).roundToInt()
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Welcome Bonus Tracker", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        val (label, tone) = when (subProgress.status) {
                            SubTrackerStatus.RECEIVED -> "Bonus Received" to CardStatusTone.POSITIVE
                            SubTrackerStatus.SPEND_MET -> "Spend Met: Awaiting Bonus" to CardStatusTone.POSITIVE
                            SubTrackerStatus.EXPIRED -> "Expired" to CardStatusTone.NEUTRAL
                            SubTrackerStatus.AT_RISK -> "$pct% At Risk" to CardStatusTone.CRITICAL
                            SubTrackerStatus.ON_TRACK -> "$pct%" to CardStatusTone.POSITIVE
                        }
                        CardStatusPill(label, tone)
                    }
                    OrbitPillProgressBar(percent = subProgress.progressRatio, height = 6.dp)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("${MoneyUtils.formatCents(subProgress.currentSpendCents)} of ${MoneyUtils.formatCents(subProgress.targetSpendCents)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        val deadlineText = subProgress.daysRemaining?.let { "${it}d left" } ?: "${MoneyUtils.formatCents(subProgress.remainingSpendCents)} left"
                        Text(deadlineText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (rewardsToolsEnabled) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.strategy.isBusinessCard) {
                    CardStatusPill("5/24 Exempt (Biz)", CardStatusTone.POSITIVE)
                } else if (profile.strategy.openedDate != null) {
                    val opened = LocalDateUtils.parseIsoDate(profile.strategy.openedDate)
                    val dropOff = opened?.let { LocalDateUtils.addMonthsClamped(it, 24) }
                    val text = if (dropOff != null && dropOff > today) "5/24 until ${CardFormat.formatCardDate(LocalDateUtils.formatIsoDate(dropOff))}" else "5/24 Clear"
                    val tone = if (dropOff != null && dropOff > today) CardStatusTone.WARNING else CardStatusTone.POSITIVE
                    CardStatusPill(text, tone)
                }
                subEligibility?.let { elig ->
                    CardStatusPill(elig.statusLabel, if (elig.isEligibleNow) CardStatusTone.POSITIVE else CardStatusTone.NEUTRAL)
                }
            }
        }
    }
}
