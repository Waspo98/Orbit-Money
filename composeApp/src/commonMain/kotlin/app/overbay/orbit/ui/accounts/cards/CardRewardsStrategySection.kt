package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import app.overbay.orbit.domain.CategoryBestCardPick
import app.overbay.orbit.domain.Chase524PersonSummary
import app.overbay.orbit.domain.Chase524SlotItem
import app.overbay.orbit.domain.CreditCardPortfolioSummary
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.SubProgressSummary
import app.overbay.orbit.domain.SubTrackerStatus
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardRewardsStrategySection(
    summary: CreditCardPortfolioSummary,
    showWhichCardWhere: Boolean,
    onSelectPlayer: (String) -> Unit,
    onInspectAccount: (Long) -> Unit,
    onToggleWhichCardWhere: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (summary.availablePlayers.size > 1) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val allSelected = summary.selectedPlayer == CreditCardStrategyEngine.ALL_PLAYERS
                FilterChip(
                    selected = allSelected,
                    onClick = { onSelectPlayer(CreditCardStrategyEngine.ALL_PLAYERS) },
                    label = { Text("All Household") }
                )
                summary.availablePlayers.forEach { (key, label) ->
                    FilterChip(
                        selected = summary.selectedPlayer == key,
                        onClick = { onSelectPlayer(key) },
                        label = { Text(label) }
                    )
                }
            }
        }

        summary.chase524ByPerson.forEach { person ->
            Chase524PersonCard(person = person, onInspectAccount = onInspectAccount)
        }

        if (summary.activeSubs.isNotEmpty()) {
            WelcomeBonusesCard(subs = summary.activeSubs, onInspectAccount = onInspectAccount)
        }

        if (summary.bestCardsByCategory.isNotEmpty()) {
            WhichCardWhereCard(
                picks = summary.bestCardsByCategory,
                isExpanded = showWhichCardWhere,
                onToggleExpand = onToggleWhichCardWhere,
                onInspectAccount = onInspectAccount
            )
        }
    }
}

@Composable
fun Chase524PersonCard(
    person: Chase524PersonSummary,
    onInspectAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = person.summary.count
    val missing = person.summary.missingOpenDateCount
    val (statusText, tone) = when {
        missing > 0 -> "$count/5 · $missing Missing" to CardStatusTone.NEUTRAL
        count < 4 -> "$count/5 · Eligible" to CardStatusTone.POSITIVE
        count == 4 -> "4/5 · 1 Slot Left" to CardStatusTone.WARNING
        else -> "$count/5 · Over 5/24" to CardStatusTone.CRITICAL
    }

    OrbitSectionCard(
        title = "${person.holderLabel} · Chase 5/24",
        action = { CardStatusPill(text = statusText, tone = tone) },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 5) {
                    val barColor = when {
                        i >= count -> MaterialTheme.colorScheme.surfaceContainerHighest
                        count >= 5 -> OrbitTheme.colors.expense
                        count == 4 -> OrbitTheme.colors.warning
                        else -> OrbitTheme.colors.income
                    }
                    Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                }
            }

            if (person.summary.nextSlotFreeDate != null && person.summary.daysUntilNextSlotFree != null) {
                Text(
                    text = "Next slot opens ${CardFormat.formatCardDate(person.summary.nextSlotFreeDate)} (${CardFormat.formatDaysUntil(person.summary.daysUntilNextSlotFree)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (missing > 0) {
                Text(
                    text = "$missing cards missing opening date; 5/24 status may be incomplete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (person.summary.slots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                person.summary.slots.forEach { slot ->
                    Chase524SlotRow(slot = slot, onClick = { onInspectAccount(slot.accountId) })
                }
            }
        }
    }
}

@Composable
private fun Chase524SlotRow(slot: Chase524SlotItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = slot.cardTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (slot.isAuthorizedUser) CardStatusPill(text = "AU", tone = CardStatusTone.NEUTRAL)
                }
                Text(
                    text = "Opened ${CardFormat.formatCardDate(slot.openedDate)} · Frees up ${CardFormat.formatCardDate(slot.dropOffDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun WelcomeBonusesCard(
    subs: List<SubProgressSummary>,
    onInspectAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Welcome Bonuses",
        subtitle = if (subs.isNotEmpty()) "${subs.size} active minimum-spend tracker${if (subs.size == 1) "" else "s"}" else null,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (subs.isEmpty()) {
                Text(
                    text = "No active welcome bonuses · Set a spend target in any card's perks editor to track progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                subs.forEach { sub -> WelcomeBonusRow(sub = sub, onClick = { onInspectAccount(sub.accountId) }) }
            }
        }
    }
}

@Composable
private fun WelcomeBonusRow(sub: SubProgressSummary, onClick: () -> Unit) {
    val pct = (sub.progressRatio * 100f).roundToInt()
    val (statusLabel, statusTone) = when (sub.status) {
        SubTrackerStatus.RECEIVED -> "Bonus Received" to CardStatusTone.POSITIVE
        SubTrackerStatus.SPEND_MET -> "Spend Met: Awaiting Bonus" to CardStatusTone.POSITIVE
        SubTrackerStatus.EXPIRED -> "Expired" to CardStatusTone.NEUTRAL
        SubTrackerStatus.AT_RISK -> "$pct% At Risk" to CardStatusTone.CRITICAL
        SubTrackerStatus.ON_TRACK -> "$pct%" to CardStatusTone.POSITIVE
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sub.cardTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(text = "Bonus: ${sub.rewardText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                CardStatusPill(text = statusLabel, tone = statusTone)
            }
            OrbitPillProgressBar(percent = sub.progressRatio, height = 6.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${MoneyUtils.formatCents(sub.currentSpendCents)} of ${MoneyUtils.formatCents(sub.targetSpendCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val pace = sub.dailyPaceNeededCents?.let { "${MoneyUtils.formatCents(it)}/d · " }.orEmpty()
                val deadlineText = if (sub.daysRemaining != null && sub.daysRemaining >= 0) {
                    "${pace}${sub.daysRemaining}d left"
                } else if (!sub.deadlineDate.isNullOrBlank()) {
                    "Due ${CardFormat.formatCardDate(sub.deadlineDate)}"
                } else "${MoneyUtils.formatCents(sub.remainingSpendCents)} left"
                val displayText = if (sub.deadlineIsEstimated) "$deadlineText (Est.)" else deadlineText
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sub.status == SubTrackerStatus.AT_RISK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun WhichCardWhereCard(
    picks: List<CategoryBestCardPick>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onInspectAccount: (Long) -> Unit,
    alwaysExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Which Card Where",
        subtitle = "Best card for each spending category",
        actionText = if (alwaysExpanded || picks.isEmpty()) null else if (isExpanded) "Hide" else "Show",
        onActionClick = if (alwaysExpanded || picks.isEmpty()) null else onToggleExpand,
        modifier = modifier
    ) {
        if (picks.isEmpty()) {
            Text(
                text = "No category multipliers configured yet · Pick a card template or add multipliers to build your cheat sheet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else if (alwaysExpanded || isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                picks.forEach { pick -> CategoryPickRow(pick = pick, onClick = { onInspectAccount(pick.accountId) }) }
            }
        }
    }
}

@Composable
private fun CategoryPickRow(pick: CategoryBestCardPick, onClick: () -> Unit) {
    val mult = pick.multiplier
    val numStr = if (mult == mult.toLong().toDouble()) mult.toLong().toString() else mult.toString()
    val rateText = if (pick.rateType.equals("percent", ignoreCase = true)) "$numStr% cash" else "$numStr\u00D7 pts"

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pick.categoryName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                val cardSubtitle = listOfNotNull(pick.cardTitle, pick.cardholder?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(text = cardSubtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = rateText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
