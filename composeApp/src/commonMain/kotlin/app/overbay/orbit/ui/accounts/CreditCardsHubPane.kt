package app.overbay.orbit.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardPerkRow
import app.overbay.orbit.ui.accounts.cards.CardRewardsStrategySection
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents

data class CreditCardsHubActions(
    val onInspectAccount: (Long) -> Unit,
    val onOpenCardEditor: (Account) -> Unit,
    val onToggleBenefit: (accountId: Long, benefitId: String) -> Unit,
    val onOpenPerkUsage: (accountId: Long, benefitId: String) -> Unit,
    val onToggleShowUsedPerks: () -> Unit,
    val onToggleShowWhichCardWhere: () -> Unit,
    val onSelectRewardCategory: (String?) -> Unit,
    val onSelectPlayer: (String) -> Unit
)

@Composable
fun CreditCardsHubPane(
    uiState: AccountsUiState,
    actions: CreditCardsHubActions,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val creditAccounts = remember(uiState.accounts) {
        uiState.accounts.filter { it.type == AccountType.CREDIT && !it.isArchived }
    }
    val startPad = if (isWideScreen) 20.dp else 16.dp
    val endPad = if (isWideScreen) 0.dp else 16.dp

    if (creditAccounts.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(horizontal = startPad, vertical = 24.dp)) {
            EmptyStateCard(
                title = "No Credit Cards",
                description = "Add a credit card account to track statement balances, payment due dates, and perks.",
                actionButtonText = "Add Account",
                onActionClick = {}
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = startPad, end = endPad, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "summary_strip") { CardHubSummaryStrip(uiState = uiState) }
        item(key = "payments_due") { PaymentsDueSection(uiState = uiState, actions = actions) }
        item(key = "perks_this_period") { PerksThisPeriodSection(uiState = uiState, actions = actions) }
        item(key = "annual_fees_ahead") { AnnualFeesAheadSection(uiState = uiState, actions = actions) }

        if (uiState.rewardsToolsEnabled) {
            item(key = "rewards_strategy") {
                CardRewardsStrategySection(
                    summary = uiState.cardPortfolioSummary,
                    showWhichCardWhere = uiState.showWhichCardWhere,
                    onSelectPlayer = actions.onSelectPlayer,
                    onInspectAccount = actions.onInspectAccount,
                    onToggleWhichCardWhere = actions.onToggleShowWhichCardWhere
                )
            }
        }
    }
}

@Composable
private fun CardHubSummaryStrip(uiState: AccountsUiState) {
    val owed = uiState.householdCredit?.totalOwedCents ?: 0L
    val totalLimit = uiState.householdCredit?.totalLimitCents ?: 0L
    val utilBps = uiState.householdCredit?.utilizationBps

    OrbitSectionCard(shape = RoundedCornerShape(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "Total Credit Balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    RollingTickerCents(cents = owed, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                if (totalLimit > 0L && utilBps != null) {
                    val tone = when {
                        utilBps >= 5000 -> CardStatusTone.CRITICAL
                        utilBps >= 3000 -> CardStatusTone.WARNING
                        else -> CardStatusTone.POSITIVE
                    }
                    val utilStr = CardFormat.formatBps(utilBps)
                    val limitStr = MoneyUtils.formatCents(totalLimit, showCents = false)
                    CardStatusPill(text = "$utilStr of $limitStr limit", tone = tone)
                }
            }

            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val nextDue = uiState.householdCredit?.nextDue
                    val nextDueText = if (nextDue?.dueDate != null && nextDue.daysUntilDue != null) {
                        "${LocalDateUtils.formatShortDate(nextDue.dueDate)} (${CardFormat.formatDaysUntil(nextDue.daysUntilDue)})"
                    } else "None due"
                    HubMetricCell(label = "Next Due", value = nextDueText)
                    HubMetricCell(label = "Annual Fees", value = "${MoneyUtils.formatCents(uiState.cardPortfolioSummary.totalAnnualFeesCents, showCents = false)}/yr")
                    HubMetricCell(label = "Welcome Bonuses", value = "${uiState.cardPortfolioSummary.activeSubs.size} active")
                }
            }
        }
    }
}

@Composable
private fun HubMetricCell(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaymentsDueSection(uiState: AccountsUiState, actions: CreditCardsHubActions) {
    val dueCycles = remember(uiState.cardCycles) {
        uiState.cardCycles.filter { it.daysUntilDue != null && it.daysUntilDue in -7..35 }.sortedBy { it.daysUntilDue }
    }

    OrbitSectionCard(title = "Payments Due") {
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

            if (uiState.missingBillingDatesCount > 0) {
                val firstMissing = uiState.accounts.find {
                    it.type == AccountType.CREDIT && !it.isArchived &&
                        (it.creditCardProfile?.statementClosingDay == null || it.creditCardProfile?.paymentDueDay == null)
                }
                if (firstMissing != null) {
                    TextButton(onClick = { actions.onOpenCardEditor(firstMissing) }, modifier = Modifier.align(Alignment.Start)) {
                        Text("Add Billing Dates for ${uiState.missingBillingDatesCount} Cards")
                    }
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

@Composable
private fun PerksThisPeriodSection(uiState: AccountsUiState, actions: CreditCardsHubActions) {
    val perks = uiState.cardPortfolioSummary.currentPeriodPerks
    val activePerks = perks.filter { !it.isFullyUsed }
    val usedPerks = perks.filter { it.isFullyUsed }
    val totalRemaining = activePerks.sumOf { it.remainingCents }
    val subtitle = if (perks.isNotEmpty()) "${MoneyUtils.formatCents(totalRemaining)} left to use" else null

    OrbitSectionCard(title = "Perks This Period", subtitle = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (perks.isEmpty()) {
                Text(
                    text = "No perks set up · Add perks in card details to track monthly and annual statement credits.",
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
private fun AnnualFeesAheadSection(uiState: AccountsUiState, actions: CreditCardsHubActions) {
    OrbitSectionCard(title = "Annual Fees Ahead") {
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
