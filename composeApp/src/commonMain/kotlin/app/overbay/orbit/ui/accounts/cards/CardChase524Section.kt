package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
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
import app.overbay.orbit.domain.Chase524SlotItem
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chase524SectionCard(
    people: List<Chase524PersonSummary>,
    selectedHolderKey: String? = null,
    onSelectHolder: (String) -> Unit = {},
    onInspectAccount: (Long) -> Unit,
    onFixMissingOpenDate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (people.isEmpty()) {
        OrbitSectionCard(title = "Chase 5/24 Status", modifier = modifier) {
            Text(
                text = "Add card opening dates in any card's Strategy tab to track personal 5/24 eligibility.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        return
    }

    val activePerson = people.firstOrNull { it.holderKey == selectedHolderKey } ?: people.first()
    val count = activePerson.summary.count
    val missing = activePerson.summary.missingOpenDateCount
    val (statusText, tone) = when {
        missing > 0 -> "$count/5 \u00B7 $missing Missing" to CardStatusTone.NEUTRAL
        count < 4 -> "$count/5 \u00B7 Eligible" to CardStatusTone.POSITIVE
        count == 4 -> "4/5 \u00B7 1 Slot Left" to CardStatusTone.WARNING
        else -> "$count/5 \u00B7 Over 5/24" to CardStatusTone.CRITICAL
    }

    OrbitSectionCard(
        title = if (people.size == 1) "${activePerson.holderLabel} \u00B7 Chase 5/24" else "Chase 5/24 Status",
        action = { CardStatusPill(text = statusText, tone = tone) },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (people.size > 1) {
                val haptics = LocalOrbitHaptics.current
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    people.forEach { person ->
                        val isSelected = person.holderKey == activePerson.holderKey
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    haptics.tick()
                                    onSelectHolder(person.holderKey)
                                }
                        ) {
                            Text(
                                text = "${person.holderLabel} \u00B7 ${person.summary.count}/5",
                                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = activePerson,
                transitionSpec = { orbitFadeContentTransform() },
                label = "chase524PersonSwitch"
            ) { person ->
                val personCount = person.summary.count
                val personMissing = person.summary.missingOpenDateCount
                val firstMissingId = person.summary.missingOpenDateAccountIds.firstOrNull()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until 5) {
                            val barColor = when {
                                i >= personCount -> MaterialTheme.colorScheme.surfaceContainerHighest
                                personCount >= 5 -> OrbitTheme.colors.expense
                                personCount == 4 -> OrbitTheme.colors.warning
                                else -> OrbitTheme.colors.income
                            }
                            Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                        }
                    }

                    if (person.summary.nextSlotFreeDate != null && person.summary.daysUntilNextSlotFree != null) {
                        Text(
                            text = "Next slot opens ${CardFormat.formatCardDate(person.summary.nextSlotFreeDate)} (${CardFormat.formatDaysUntil(person.summary.daysUntilNextSlotFree)})",
                            style = TabularNumStyle.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (personMissing > 0) {
                        val haptics = LocalOrbitHaptics.current
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OrbitTheme.colors.dangerContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, OrbitTheme.colors.danger.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (firstMissingId != null) {
                                        Modifier.clickable {
                                            haptics.tick()
                                            onFixMissingOpenDate(firstMissingId)
                                        }
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$personMissing card${if (personMissing == 1) "" else "s"} missing opening date \u2014 Tap to add",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = OrbitTheme.colors.onDangerContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (firstMissingId != null) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Add opening date",
                                        tint = OrbitTheme.colors.onDangerContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
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
    }
}

@Composable
private fun Chase524SlotRow(slot: Chase524SlotItem, onClick: () -> Unit) {
    val haptics = LocalOrbitHaptics.current
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = slot.cardTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (slot.isAuthorizedUser) CardStatusPill(text = "AU", tone = CardStatusTone.NEUTRAL)
                }
                Text(
                    text = "Opened ${CardFormat.formatCardDate(slot.openedDate)} \u00B7 Drops ${CardFormat.formatCardDate(slot.dropOffDate)}",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
