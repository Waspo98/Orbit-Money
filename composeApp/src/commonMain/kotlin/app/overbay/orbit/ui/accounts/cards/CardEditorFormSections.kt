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
private val COOLDOWN_OPTIONS = listOf(null to "None", 24 to "24 Mo", 48 to "48 Mo", 999 to "Lifetime")

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
        OrbitSectionCard(title = "1. Card Identity & Wallet Look") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrbitTextField(
                    value = draft.accountName, onValueChange = { controller.updateDraft { d -> d.copy(accountName = it) } },
                    label = { Text("Account Nickname") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OrbitTextField(
                    value = draft.issuer, onValueChange = { controller.updateDraft { d -> d.copy(issuer = it) } },
                    label = { Text("Issuer (e.g. Chase)") }, singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            OrbitTextField(
                value = draft.cardName, onValueChange = { controller.updateDraft { d -> d.copy(cardName = it) } },
                label = { Text("Card Product Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
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
                    label = { Text(draft.cardholder?.takeIf { h -> householdMembers.none { it.name.equals(h, ignoreCase = true) } } ?: "Other...") }
                )
            }
        }

        // Section 2: Billing & Cycle
        OrbitSectionCard(title = "2. Billing Cycle & Interest") {
            CurrencyTextField(
                cents = draft.creditLimitCents ?: 0L,
                onCentsChange = { c -> controller.updateDraft { it.copy(creditLimitCents = c.takeIf { it > 0L }) } },
                label = "Credit Limit", modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrbitTextField(
                    value = draft.statementCloseDayText,
                    onValueChange = { t -> controller.updateDraft { it.copy(statementCloseDayText = t) } },
                    label = { Text("Statement Close Day (1-31)") },
                    supportingText = { draft.errors["statementCloseDay"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } ?: Text("Find on statement") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
                OrbitTextField(
                    value = draft.paymentDueDayText,
                    onValueChange = { t -> controller.updateDraft { it.copy(paymentDueDayText = t) } },
                    label = { Text("Payment Due Day (1-31)") },
                    supportingText = { draft.errors["paymentDueDay"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } ?: Text("Find on statement") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
            }
            OrbitTextField(
                value = draft.aprText,
                onValueChange = { t -> controller.updateDraft { it.copy(aprText = t) } },
                label = { Text("Purchase APR (e.g. 24.99%)") },
                supportingText = { draft.errors["apr"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            OrbitSwitchRow(
                title = "Has Promotional / Intro APR", checked = draft.hasPromo,
                onCheckedChange = { chk -> controller.updateDraft { it.copy(hasPromo = chk) } }
            )
            if (draft.hasPromo) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitTextField(
                        value = draft.promoAprText, onValueChange = { t -> controller.updateDraft { it.copy(promoAprText = t) } },
                        label = { Text("Promo APR (e.g. 0.0%)") }, supportingText = { draft.errors["promoApr"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        DateFieldButton(
                            date = draft.promoEndDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                            onDateSelected = { dt -> controller.updateDraft { it.copy(promoEndDate = LocalDateUtils.formatIsoDate(dt)) } },
                            label = draft.promoEndDate?.let { "Promo Ends ($it)" } ?: "Select End Date"
                        )
                    }
                }
            }
        }

        // Section 3: Annual Fee
        OrbitSectionCard(title = "3. Annual Fee") {
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
                        label = { Text("Use detected fee: ${feeCents?.let { MoneyUtils.formatCents(it) } ?: ""} on $feeDate") }
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
                            label = draft.annualFeePostDate?.let { "Posts ($it)" } ?: "Select Fee Date"
                        )
                    }
                }
            }
        }

        // Section 4: Statement Credits & Perks
        OrbitSectionCard(title = "4. Statement Credits & Perks (${draft.benefits.size})") {
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
                Text("+ Add Perk")
            }
        }

        // Section 5: Reward Multipliers
        OrbitSectionCard(title = "5. Reward Multipliers (${draft.rewardCategories.size})") {
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
                Text("+ Add Multiplier")
            }
        }

        // Section 6: Rewards Strategy (only if rewardsToolsEnabled)
        if (rewardsToolsEnabled) {
            OrbitSectionCard(title = "6. Welcome Bonus & Strategy") {
                historySuggestions.suggestedOpenedDate?.let { opDate ->
                    AssistChip(
                        onClick = { controller.updateDraft { it.copy(openedDate = opDate) } },
                        label = { Text("Earliest activity: $opDate. Use as open date?") }
                    )
                }
                DateFieldButton(
                    date = draft.openedDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                    onDateSelected = { dt -> controller.updateDraft { it.copy(openedDate = LocalDateUtils.formatIsoDate(dt)) } },
                    label = draft.openedDate?.let { "Card Opened ($it)" } ?: "Set Card Opened Date"
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
                                label = draft.targetDeadlineDate?.let { "Deadline ($it)" } ?: "Spend Deadline"
                            )
                        }
                    }
                } else if (draft.welcomeBonusMode == WelcomeBonusMode.EARNED) {
                    DateFieldButton(
                        date = draft.subReceivedDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                        onDateSelected = { dt -> controller.updateDraft { it.copy(subReceivedDate = LocalDateUtils.formatIsoDate(dt)) } },
                        label = draft.subReceivedDate?.let { "Bonus Received ($it)" } ?: "Set Date Received"
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

        // Section 7: Notes
        OrbitSectionCard(title = "7. Notes & Memo") {
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
