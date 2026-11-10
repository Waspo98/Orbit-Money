package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.CardArtPalette
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.OrbitTextInputDialog
import app.overbay.orbit.util.LocalOrbitHaptics

private val NETWORKS = listOf("Visa", "Mastercard", "Amex", "Discover")
private val COOLDOWN_OPTIONS = listOf(null to "None", 24 to "24 Months", 48 to "48 Months", 999 to "Once per Lifetime")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditorFormSections(
    draft: CardEditorDraft,
    controller: CardEditorController,
    householdMembers: List<HouseholdMember>,
    transactions: List<Transaction>,
    rewardsToolsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showCustomHolderDialog by remember { mutableStateOf(false) }

    val historySuggestions = remember(draft.accountId, transactions, draft.annualFeeCents) {
        CreditCardStrategyEngine.detectHistorySuggestions(
            accountId = draft.accountId, transactions = transactions,
            templateAnnualFeeCents = draft.annualFeeCents?.takeIf { it > 0L }
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section 1: Card Identity & Styling
        OrbitSectionCard(title = "1. Card Product & Wallet Look") {
            OrbitTextField(
                value = draft.cardName, onValueChange = { controller.updateDraft { d -> d.copy(cardName = it) } },
                label = { Text("Card Product Name (e.g. Sapphire Preferred)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Text("Payment Network", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NETWORKS.forEach { net ->
                    val sel = draft.network.equals(net, ignoreCase = true)
                    FilterChip(selected = sel, onClick = { haptics.tick(); controller.updateDraft { it.copy(network = net) } }, label = { Text(net, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) })
                }
            }

            // CRITICAL D4: Preset Card Art Chips ONLY (NO artworkUrl field)
            Text("Card Artwork Gradient", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardArtPalette.ALL.forEach { preset ->
                    val sel = draft.artKey == preset.key
                    FilterChip(selected = sel, onClick = {
                        haptics.tick()
                        controller.updateDraft { d -> d.copy(artKey = if (sel) null else preset.key, network = if (d.network.isBlank()) preset.defaultNetwork else d.network) }
                    }, label = { Text(preset.label, fontSize = 11.5.sp) })
                }
            }

            Text("Cardholder / Owner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                householdMembers.forEach { m ->
                    val sel = draft.cardholderMemberId == m.id || draft.cardholder.equals(m.name, ignoreCase = true)
                    FilterChip(selected = sel, onClick = {
                        haptics.tick()
                        controller.updateDraft { d -> if (sel) d.copy(cardholderMemberId = null, cardholder = null) else d.copy(cardholderMemberId = m.id, cardholder = m.name) }
                    }, label = { Text(m.name) })
                }
                FilterChip(
                    selected = draft.cardholderMemberId == null && !draft.cardholder.isNullOrBlank() && householdMembers.none { it.name.equals(draft.cardholder, ignoreCase = true) },
                    onClick = { showCustomHolderDialog = true },
                    label = { Text(draft.cardholder?.takeIf { h -> householdMembers.none { it.name.equals(h, ignoreCase = true) } } ?: "Someone Else") }
                )
            }
        }

        // Section 2: Statement Credits, Perks & Annual Fee
        OrbitSectionCard(title = "2. Statement Credits & Perks (${draft.benefits.size})") {
            draft.benefits.forEach { b ->
                val subtitle = buildString {
                    append(b.cadence.displayName)
                    b.periodValueCents?.let { append(" · ${MoneyUtils.formatCents(it)}") }
                    if (b.anchor == BenefitAnchor.CARDMEMBER_YEAR) append(" · Cardmember Year")
                }
                OrbitCompactListRow(
                    title = b.title, subtitle = subtitle,
                    trailingContent = {
                        IconButton(onClick = { haptics.reject(); controller.removeBenefit(b.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.clickable { controller.updateDraft { it.copy(editingPerkId = b.id) } }
                )
            }
            TextButton(onClick = { controller.updateDraft { it.copy(editingPerkId = "") } }, modifier = Modifier.align(Alignment.End)) {
                Text("Add Perk")
            }

            OrbitSwitchRow(
                title = "Has Annual Fee", checked = draft.hasAnnualFee,
                onCheckedChange = { chk -> controller.updateDraft { it.copy(hasAnnualFee = chk) } }
            )
            if (draft.hasAnnualFee) {
                historySuggestions.suggestedAnnualFeeDate?.let { feeDate ->
                    val feeCents = historySuggestions.detectedAnnualFeeCents
                    AssistChip(
                        onClick = {
                            haptics.confirm()
                            controller.updateDraft { it.copy(annualFeeCents = feeCents ?: it.annualFeeCents, annualFeePostDate = feeDate) }
                        },
                        label = { Text("Use detected fee: ${feeCents?.let { MoneyUtils.formatCents(it) } ?: ""} on ${CardFormat.formatCardDate(feeDate)}") }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CurrencyTextField(
                            cents = draft.annualFeeCents ?: 0L,
                            onCentsChange = { c -> controller.updateDraft { it.copy(annualFeeCents = c.takeIf { it > 0L }) } },
                            label = "Annual Fee"
                        )
                    }
                    Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        DateFieldButton(
                            date = draft.annualFeePostDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                            onDateSelected = { dt -> controller.updateDraft { it.copy(annualFeePostDate = LocalDateUtils.formatIsoDate(dt)) } },
                            label = draft.annualFeePostDate?.let { "Posts (${CardFormat.formatCardDate(it)})" } ?: "Select Fee Date"
                        )
                    }
                }
            }
        }

        // Section 3: Reward Multipliers
        OrbitSectionCard(title = "3. Reward Multipliers (${draft.rewardCategories.size})") {
            draft.rewardCategories.forEachIndexed { idx, r ->
                val typeLabel = if (r.rateType == "percent") "% Cash" else "x Points"
                OrbitCompactListRow(
                    title = "${r.multiplier}$typeLabel · ${r.label.ifBlank { r.category }}",
                    subtitle = listOfNotNull(r.notes, if (r.isRotating) "Rotating" else null).joinToString(" · ").takeIf { it.isNotBlank() },
                    trailingContent = {
                        IconButton(onClick = { haptics.reject(); controller.removeReward(idx) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.clickable { controller.updateDraft { it.copy(editingRewardIndex = idx) } }
                )
            }
            TextButton(onClick = { controller.updateDraft { it.copy(editingRewardIndex = -1) } }, modifier = Modifier.align(Alignment.End)) {
                Text("Add Multiplier")
            }
        }

        // Section 4: Rewards Strategy (only if rewardsToolsEnabled)
        if (rewardsToolsEnabled) {
            OrbitSectionCard(title = "4. Welcome Bonus & 5/24 Strategy") {
                historySuggestions.suggestedOpenedDate?.let { opDate ->
                    AssistChip(
                        onClick = { controller.updateDraft { it.copy(openedDate = opDate) } },
                        label = { Text("Earliest activity: ${CardFormat.formatCardDate(opDate)}. Use as Opened Date?") }
                    )
                }
                DateFieldButton(
                    date = draft.openedDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                    onDateSelected = { dt -> controller.updateDraft { it.copy(openedDate = LocalDateUtils.formatIsoDate(dt)) } },
                    label = draft.openedDate?.let { "Card Opened (${CardFormat.formatCardDate(it)})" } ?: "Set Opened Date"
                )
                OrbitSwitchRow(
                    title = "Business Card (5/24 Exempt)", subtitle = "Excludes this card from your personal Chase 5/24 count.",
                    checked = draft.isBusinessCard, onCheckedChange = { b -> controller.updateDraft { it.copy(isBusinessCard = b) } }
                )

                Text("Welcome Bonus Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WelcomeBonusMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = draft.welcomeBonusMode == mode,
                            onClick = { haptics.tick(); controller.setWelcomeBonusMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = WelcomeBonusMode.entries.size)
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                if (draft.welcomeBonusMode == WelcomeBonusMode.WORKING) {
                    OrbitTextField(
                        value = draft.subRewardText.orEmpty(), onValueChange = { t -> controller.updateDraft { it.copy(subRewardText = t.ifBlank { null }) } },
                        label = { Text("Bonus (e.g. 75,000 pts)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CurrencyTextField(cents = draft.targetSpendCents ?: 0L, onCentsChange = { c -> controller.updateDraft { it.copy(targetSpendCents = c.takeIf { it > 0L }) } }, label = "Min Spend ($)")
                        }
                        Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            DateFieldButton(
                                date = draft.targetDeadlineDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                                onDateSelected = { dt -> controller.updateDraft { it.copy(targetDeadlineDate = LocalDateUtils.formatIsoDate(dt)) } },
                                label = draft.targetDeadlineDate?.let { "Deadline (${CardFormat.formatCardDate(it)})" } ?: "Spend Deadline"
                            )
                        }
                    }
                } else if (draft.welcomeBonusMode == WelcomeBonusMode.EARNED) {
                    DateFieldButton(
                        date = draft.subReceivedDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                        onDateSelected = { dt -> controller.updateDraft { it.copy(subReceivedDate = LocalDateUtils.formatIsoDate(dt)) } },
                        label = draft.subReceivedDate?.let { "Bonus Received (${CardFormat.formatCardDate(it)})" } ?: "Set Date Received"
                    )
                }

                Text("Issuer Cooldown Rule", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COOLDOWN_OPTIONS.forEach { (m, lbl) ->
                        FilterChip(selected = draft.subCooldownMonths == m, onClick = { controller.updateDraft { it.copy(subCooldownMonths = m) } }, label = { Text(lbl) })
                    }
                }
            }
        }

        // Section 5: Notes
        OrbitSectionCard(title = "5. Notes & Memo") {
            OrbitTextField(
                value = draft.notes.orEmpty(),
                onValueChange = { t -> controller.updateDraft { it.copy(notes = t.ifBlank { null }) } },
                label = { Text("Private card notes, retention offers, retention notes") },
                minLines = 2, modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showCustomHolderDialog) {
        OrbitTextInputDialog(
            title = "Cardholder Name", initialValue = draft.cardholder.orEmpty(),
            onConfirm = { name ->
                showCustomHolderDialog = false
                controller.updateDraft { it.copy(cardholderMemberId = null, cardholder = name.trim().ifBlank { null }) }
            },
            onDismiss = { showCustomHolderDialog = false }
        )
    }
}
