package app.overbay.orbit.ui.cardperks

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AnnualFeeCardItem
import app.overbay.orbit.ui.accounts.cards.CardFace
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardPerkRow
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.accounts.cards.Chase524PersonCard
import app.overbay.orbit.ui.accounts.cards.WelcomeBonusesCard
import app.overbay.orbit.ui.accounts.cards.WhichCardWhereCard
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun CardPerksBentoItem(
    cardType: CardPerksCardType,
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    when (cardType) {
        CardPerksCardType.PERKS_THIS_PERIOD -> PerksThisPeriodBentoCard(uiState, actions, modifier)
        CardPerksCardType.CARD_WALLET_ROI -> CardWalletRoiBentoCard(uiState, actions, modifier)
        CardPerksCardType.WHICH_CARD_WHERE -> WhichCardWhereCard(
            picks = uiState.cardPortfolioSummary.bestCardsByCategory,
            isExpanded = true,
            onToggleExpand = {},
            onInspectAccount = actions.onInspectAccount,
            alwaysExpanded = true,
            modifier = modifier
        )
        CardPerksCardType.WELCOME_BONUSES -> WelcomeBonusesCard(
            subs = uiState.cardPortfolioSummary.activeSubs,
            onInspectAccount = actions.onInspectAccount,
            modifier = modifier
        )
        CardPerksCardType.CHASE_524 -> Chase524BentoSection(uiState, actions, modifier)
        CardPerksCardType.ANNUAL_FEES_AHEAD -> AnnualFeesAheadBentoCard(uiState, actions, modifier)
        CardPerksCardType.PAYMENTS_DUE -> PaymentsDueBentoCard(uiState, actions, modifier)
    }
}

@Composable
private fun CardWalletRoiBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDateUtils.todayLocalDate() }
    val creditAccounts = remember(uiState.accounts) {
        uiState.accounts.filter { it.type == AccountType.CREDIT && !it.isArchived }
    }

    OrbitSectionCard(
        title = "Card Wallet (${creditAccounts.size})",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            creditAccounts.forEach { account ->
                val profile = account.creditCardProfile
                val upgraded = remember(profile, today) {
                    profile?.let { CreditCardBenefitEngine.upgradeProfile(it, today) }
                }
                val ledger = remember(upgraded, today) {
                    upgraded?.let { CreditCardBenefitEngine.ledger(it, today) }
                }
                val isSelected = uiState.selectedAccountId == account.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { actions.onInspectAccount(account.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CardFace(
                            name = profile?.cardName ?: account.name,
                            issuer = profile?.issuerName ?: account.institution,
                            network = profile?.network,
                            artKey = profile?.imageUrl,
                            holderLabel = profile?.strategy?.cardholder,
                            compact = true,
                            artworkUrl = profile?.artworkUrl,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            val fee = ledger?.annualFeeCents ?: 0L
                            val cap = ledger?.creditsCapturedCents ?: 0L
                            val avail = ledger?.creditsAvailableCents ?: 0L
                            if (fee > 0L || avail > 0L) {
                                Text(
                                    text = "${MoneyUtils.formatCents(cap, showCents = false)} / ${MoneyUtils.formatCents(avail, showCents = false)} used",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OrbitColorTokens.Green
                                )
                                Text(
                                    text = if (fee > 0L) "${MoneyUtils.formatCents(fee, showCents = false)}/yr fee" else "No annual fee",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Text(
                                    text = "Edit Card",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Inspect Card",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerksThisPeriodBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val perks = uiState.cardPortfolioSummary.currentPeriodPerks
    val activePerks = perks.filter { !it.isFullyUsed }
    val usedPerks = perks.filter { it.isFullyUsed }
    val totalRemaining = activePerks.sumOf { it.remainingCents }
    val subtitle = if (perks.isNotEmpty()) "${MoneyUtils.formatCents(totalRemaining)} left to use this period" else null

    OrbitSectionCard(title = "Perks This Period", subtitle = subtitle, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (perks.isEmpty()) {
                Text(
                    text = "No perks set up · Tap any card in your wallet to pick a template or add monthly/annual credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                activePerks.forEach { item ->
                    CardPerkRow(
                        title = "${item.cardTitle} · ${item.benefit.title}",
                        subtitle = item.periodLabel,
                        valueCents = item.periodValueCents,
                        usedCents = item.usedCents,
                        daysUntilReset = item.daysUntilReset,
                        onToggle = { actions.onToggleBenefit(item.accountId, item.benefitId) },
                        onEditAmount = { actions.onOpenPerkUsage(item.accountId, item.benefitId) }
                    )
                }

                if (usedPerks.isNotEmpty()) {
                    TextButton(onClick = { actions.onToggleShowUsedPerks() }, modifier = Modifier.align(Alignment.Start)) {
                        Text(if (uiState.showUsedPerks) "Hide ${usedPerks.size} Used" else "Show ${usedPerks.size} Used")
                    }

                    if (uiState.showUsedPerks) {
                        usedPerks.forEach { item ->
                            CardPerkRow(
                                title = "${item.cardTitle} · ${item.benefit.title}",
                                subtitle = item.periodLabel,
                                valueCents = item.periodValueCents,
                                usedCents = item.usedCents,
                                daysUntilReset = item.daysUntilReset,
                                onToggle = { actions.onToggleBenefit(item.accountId, item.benefitId) },
                                onEditAmount = { actions.onOpenPerkUsage(item.accountId, item.benefitId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chase524BentoSection(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val people = uiState.cardPortfolioSummary.chase524ByPerson
    if (people.isEmpty()) {
        OrbitSectionCard(title = "Chase 5/24 Status", modifier = modifier) {
            Text(
                text = "Add card opening dates in your credit card tracker settings to track personal 5/24 eligibility.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            people.forEach { person ->
                Chase524PersonCard(person = person, onInspectAccount = actions.onInspectAccount)
            }
        }
    }
}

@Composable
private fun AnnualFeesAheadBentoCard(
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
private fun PaymentsDueBentoCard(
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
