package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.CategoryBestCardPick
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.SubProgressSummary
import app.overbay.orbit.domain.SubTrackerStatus
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

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
                    text = "No active welcome bonuses \u00B7 Set a spend target in any card's Strategy tab to track progress.",
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
    val haptics = LocalOrbitHaptics.current
    val pct = (sub.progressRatio * 100f).roundToInt()
    val (statusLabel, statusTone) = when (sub.status) {
        SubTrackerStatus.RECEIVED -> "Bonus Received" to CardStatusTone.POSITIVE
        SubTrackerStatus.SPEND_MET -> "Spend Met: Awaiting Bonus" to CardStatusTone.POSITIVE
        SubTrackerStatus.EXPIRED -> "Expired" to CardStatusTone.NEUTRAL
        SubTrackerStatus.AT_RISK -> "$pct% At Risk" to CardStatusTone.CRITICAL
        SubTrackerStatus.ON_TRACK -> "$pct%" to CardStatusTone.POSITIVE
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
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val pace = sub.dailyPaceNeededCents?.let { "${MoneyUtils.formatCents(it)}/d \u00B7 " }.orEmpty()
                val deadlineText = if (sub.daysRemaining != null && sub.daysRemaining >= 0) {
                    "${pace}${sub.daysRemaining}d left"
                } else if (!sub.deadlineDate.isNullOrBlank()) {
                    "Due ${CardFormat.formatCardDate(sub.deadlineDate)}"
                } else "${MoneyUtils.formatCents(sub.remainingSpendCents)} left"
                val displayText = if (sub.deadlineIsEstimated) "$deadlineText (Est.)" else deadlineText
                Text(
                    text = displayText,
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = if (sub.status == SubTrackerStatus.AT_RISK) OrbitTheme.colors.danger else MaterialTheme.colorScheme.outline
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
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    alwaysExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val filteredPicks = remember(picks, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) picks else picks.filter { pick ->
            pick.categoryName.lowercase().contains(q) ||
                pick.cardTitle.lowercase().contains(q) ||
                (pick.notes?.lowercase()?.contains(q) == true)
        }
    }

    OrbitSectionCard(
        title = "Which Card Where",
        subtitle = "Best multiplier card per spending category",
        actionText = if (alwaysExpanded || picks.isEmpty()) null else if (isExpanded) "Hide" else "Show",
        onActionClick = if (alwaysExpanded || picks.isEmpty()) null else onToggleExpand,
        modifier = modifier
    ) {
        if (picks.isEmpty()) {
            Text(
                text = "No category multipliers configured yet \u00B7 Link a card template or add multipliers to build your checkout cheat sheet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else if (alwaysExpanded || isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picks.size > 6) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search category, card, or cap notes") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear Search", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filteredPicks.isEmpty()) {
                    Text(
                        text = "No matching categories for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    filteredPicks.forEach { pick ->
                        CategoryPickRow(pick = pick, onClick = { onInspectAccount(pick.accountId) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickRow(pick: CategoryBestCardPick, onClick: () -> Unit) {
    val haptics = LocalOrbitHaptics.current
    val rateText = remember(pick.multiplier, pick.rateType) {
        CardFormat.formatEarnRate(
            RewardCategory(
                category = pick.categoryName,
                multiplier = pick.multiplier,
                rateType = pick.rateType
            )
        )
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = pick.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    if (pick.isRotating) {
                        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                            CardStatusPill(text = "Rotating", tone = CardStatusTone.WARNING)
                        }
                    }
                }
                val cardSubtitle = listOfNotNull(
                    pick.cardTitle,
                    pick.cardholder?.takeIf { it.isNotBlank() }
                ).joinToString(" \u00B7 ")
                Text(
                    text = cardSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                pick.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = rateText,
                    style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
