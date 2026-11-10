package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CardEditorRewardsTab(
    draft: CardEditorDraft,
    controller: CardEditorController,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OrbitSectionCard(
            title = "Statement Credits & Perks",
            subtitle = if (draft.benefits.isEmpty()) {
                "Monthly, quarterly & annual statement credits"
            } else {
                "${draft.benefits.size} active credit${if (draft.benefits.size == 1) "" else "s"} · tap any row to edit"
            },
            showHeaderDivider = false,
            action = {
                OrbitActionChip(
                    label = "Add Perk",
                    leadingIcon = Icons.Default.Add,
                    onClick = { controller.updateDraft { it.copy(editingPerkId = "") } }
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.lastDeletedBenefit?.let { deleted ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Removed \"${deleted.title}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    haptics.tick()
                                    controller.undoRemoveBenefit()
                                }
                            ) {
                                Text("Undo", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (draft.benefits.isEmpty()) {
                    EmptyRewardsListHint(
                        message = "No statement credits configured yet. Add credits like Uber Cash, Dining, or Airline incidentals."
                    )
                } else {
                    draft.benefits.forEach { b ->
                        val subtitle = buildString {
                            append(b.cadence.displayName)
                            if (b.anchor == BenefitAnchor.CARDMEMBER_YEAR) append(" · Cardmember Year")
                            b.description?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        }
                        val valueCents = b.periodValueCents ?: b.annualCreditValueCents ?: 0L
                        OrbitCompactListRow(
                            title = b.title,
                            subtitle = subtitle,
                            showTopDivider = false,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            borderColor = OrbitTheme.colors.cardBorder,
                            leadingContent = {
                                RateOrCreditPill(text = MoneyUtils.formatCents(valueCents, showCents = false))
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        haptics.reject()
                                        controller.removeBenefit(b.id)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Perk",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            },
                            onClick = {
                                haptics.tick()
                                controller.updateDraft { it.copy(editingPerkId = b.id) }
                            }
                        )
                    }
                }
            }
        }

        OrbitSectionCard(
            title = "Reward Multipliers",
            subtitle = if (draft.rewardCategories.isEmpty()) {
                "Category bonus multipliers & base earn rate"
            } else {
                "${draft.rewardCategories.size} earning tier${if (draft.rewardCategories.size == 1) "" else "s"} · tap any row to edit"
            },
            showHeaderDivider = false,
            action = {
                OrbitActionChip(
                    label = "Add Rate",
                    leadingIcon = Icons.Default.Add,
                    onClick = { controller.updateDraft { it.copy(editingRewardIndex = -1) } }
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.rewardCategories.isEmpty()) {
                    EmptyRewardsListHint(
                        message = "No earning multipliers configured. Add category rates like 3× Dining or 2% Everything Else."
                    )
                } else {
                    draft.rewardCategories.forEachIndexed { idx, r ->
                        val metaParts = listOfNotNull(
                            if (r.isRotating) "Rotating Quarterly" else null,
                            r.capCents?.takeIf { it > 0L }?.let { "Cap ${MoneyUtils.formatCents(it, showCents = false)}" },
                            r.notes?.takeIf { it.isNotBlank() }
                        )
                        OrbitCompactListRow(
                            title = r.label.ifBlank { r.category },
                            subtitle = metaParts.joinToString(" · ").takeIf { it.isNotBlank() },
                            showTopDivider = false,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            borderColor = OrbitTheme.colors.cardBorder,
                            leadingContent = {
                                RateOrCreditPill(text = CardFormat.formatEarnRate(r))
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        haptics.reject()
                                        controller.removeReward(idx)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Multiplier",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            },
                            onClick = {
                                haptics.tick()
                                controller.updateDraft { it.copy(editingRewardIndex = idx) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RateOrCreditPill(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyRewardsListHint(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
