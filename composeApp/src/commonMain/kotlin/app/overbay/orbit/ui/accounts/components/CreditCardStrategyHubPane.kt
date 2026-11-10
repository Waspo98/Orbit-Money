package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import app.overbay.orbit.domain.Chase524PersonSummary
import app.overbay.orbit.domain.Chase524Summary
import app.overbay.orbit.domain.CreditCardPortfolioSummary
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.SubProgressSummary
import app.overbay.orbit.domain.SubTrackerStatus
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditCardStrategyHubPane(
    summary: CreditCardPortfolioSummary,
    onSelectPlayer: (String) -> Unit,
    onInspectAccount: (Long) -> Unit,
    onToggleBenefit: (accountId: Long, benefitId: String) -> Unit,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val startPad = if (isWideScreen) 20.dp else 16.dp
    val endPad = if (isWideScreen) 0.dp else 16.dp

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = startPad, end = endPad, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Player Filter Chips (Household Player 1 / Player 2)
        if (summary.availablePlayers.isNotEmpty()) {
            item(key = "player_filter") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val allSelected = summary.selectedPlayer == CreditCardStrategyEngine.ALL_PLAYERS
                    FilterChip(
                        selected = allSelected,
                        onClick = { haptics.tick(); onSelectPlayer(CreditCardStrategyEngine.ALL_PLAYERS) },
                        label = { Text("All Household", style = MaterialTheme.typography.labelMedium, fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium) }
                    )
                    summary.availablePlayers.forEach { (playerKey, playerLabel) ->
                        val selected = summary.selectedPlayer == playerKey
                        FilterChip(
                            selected = selected,
                            onClick = { haptics.tick(); onSelectPlayer(playerKey) },
                            label = { Text(playerLabel, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
                        )
                    }
                }
            }
        }

        // 2. Chase 5/24 & Velocity Bento Cards per Person
        items(
            items = summary.chase524ByPerson,
            key = { "chase_524_${it.holderKey}" }
        ) { personSummary ->
            Chase524HeroCard(
                personSummary = personSummary,
                onInspectAccount = onInspectAccount
            )
        }

        // 3. Active Sign-Up Bonus (SUB) Minimum Spend Trackers
        if (summary.activeSubs.isNotEmpty()) {
            item(key = "active_subs") {
                ActiveSubsSectionCard(
                    subs = summary.activeSubs,
                    onInspectAccount = onInspectAccount
                )
            }
        }

        // 4. Annual Fee Keeper ROI & Current-Period Statement Credits Radar
        item(key = "fee_and_perks_radar") {
            PerksAndFeeRoiCard(
                summary = summary,
                onToggleBenefit = onToggleBenefit,
                onInspectAccount = onInspectAccount
            )
        }

        // 5. Best Card by Category Cheat Sheet
        if (summary.bestCardsByCategory.isNotEmpty()) {
            item(key = "best_cards_matrix") {
                BestCardsByCategoryCard(
                    picks = summary.bestCardsByCategory,
                    onInspectAccount = onInspectAccount
                )
            }
        }
    }
}

@Composable
private fun Chase524HeroCard(
    personSummary: Chase524PersonSummary,
    onInspectAccount: (Long) -> Unit
) {
    val chase524 = personSummary.summary
    val haptics = LocalOrbitHaptics.current
    val hasMissingDates = chase524.count == 0 && chase524.missingOpenDateCount > 0
    val (statusColor, statusText) = when {
        hasMissingDates -> MaterialTheme.colorScheme.outline to "Add Opened Dates"
        chase524.count < 4 -> OrbitColorTokens.Green to "Under 5/24 • Eligible"
        chase524.count == 4 -> OrbitColorTokens.Amber to "4/24 • 1 Slot Left"
        else -> MaterialTheme.colorScheme.error to "At/Over 5/24 • Locked"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${personSummary.holderLabel.uppercase()} · CHASE 5/24",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${chase524.count} / 5",
                            style = TabularNumStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "personal cards in 24 mos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 5-Slot Visual Meter
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (slotIdx in 0 until 5) {
                    val filled = slotIdx < chase524.count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) statusColor else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                }
            }

            // Drop-off timeline summary
            val dropSubtitle = when {
                !chase524.isUnder524 && chase524.under524DropDate != null ->
                    "Drops back under 5/24 (to 4/24) on ${chase524.under524DropDate}"
                chase524.nextSlotFreeDate != null ->
                    "Next slot frees up on ${chase524.nextSlotFreeDate} (${chase524.daysUntilNextSlotFree} days)"
                else -> "Set 'Opened Date' on your credit cards to track 5/24 drop-off dates."
            }
            Text(
                text = buildString {
                    append(dropSubtitle)
                    if (chase524.excludedBusinessCount > 0) {
                        append(" • ${chase524.excludedBusinessCount} Business card(s) excluded")
                    }
                    if (chase524.missingOpenDateCount > 0) {
                        val cardWord = if (chase524.missingOpenDateCount == 1) "1 card" else "${chase524.missingOpenDateCount} cards"
                        append(" • $cardWord missing opened date")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Active 24-month slots list
            if (chase524.slots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chase524.slots.forEachIndexed { idx, slot ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.tick()
                                    onInspectAccount(slot.accountId)
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${slot.cardTitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(slot.cardholder, "Opened ${slot.openedDate}").joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = "Drops ${slot.dropOffDate} (${slot.daysUntilDropOff}d)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSubsSectionCard(
    subs: List<SubProgressSummary>,
    onInspectAccount: (Long) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SIGN-UP BONUS (SUB) SPEND TRACKERS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold
            )
            subs.forEach { sub ->
                val pct = (sub.progressRatio * 100f).roundToInt()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.tick()
                            onInspectAccount(sub.accountId)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.cardTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = listOfNotNull(sub.cardholder, sub.rewardText).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val (badgeText, badgeColor) = when (sub.status) {
                                SubTrackerStatus.RECEIVED -> "Bonus Received" to OrbitColorTokens.Green
                                SubTrackerStatus.SPEND_MET -> "Spend Met · Awaiting Bonus" to OrbitColorTokens.Green
                                SubTrackerStatus.EXPIRED -> "Expired" to MaterialTheme.colorScheme.outline
                                SubTrackerStatus.AT_RISK -> "$pct%" to MaterialTheme.colorScheme.error
                                SubTrackerStatus.ON_TRACK -> "$pct%" to MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                        OrbitPillProgressBar(percent = sub.progressRatio, overBudget = false, height = 7.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${MoneyUtils.formatCents(sub.currentSpendCents)} of ${MoneyUtils.formatCents(sub.targetSpendCents)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val paceLabel = when (sub.status) {
                                SubTrackerStatus.RECEIVED -> "Bonus credited to account"
                                SubTrackerStatus.SPEND_MET -> "Target spend met"
                                SubTrackerStatus.EXPIRED -> "Deadline passed"
                                SubTrackerStatus.AT_RISK, SubTrackerStatus.ON_TRACK -> {
                                    if (sub.daysRemaining != null && sub.daysRemaining >= 0) {
                                        val daily = sub.dailyPaceNeededCents?.let { "${MoneyUtils.formatCents(it)}/day • " }.orEmpty()
                                        "${daily}${sub.daysRemaining}d left"
                                    } else "${MoneyUtils.formatCents(sub.remainingSpendCents)} left"
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (sub.status == SubTrackerStatus.AT_RISK) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "At Risk",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = paceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sub.status == SubTrackerStatus.AT_RISK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
