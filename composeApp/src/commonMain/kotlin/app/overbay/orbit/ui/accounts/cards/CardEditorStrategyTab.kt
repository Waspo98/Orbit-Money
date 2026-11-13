package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.util.LocalOrbitHaptics

private val COOLDOWN_OPTIONS = listOf(
    null to "None",
    24 to "24 Months",
    48 to "48 Months",
    999 to "Lifetime"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditorStrategyTab(
    draft: CardEditorDraft,
    controller: CardEditorController,
    transactions: List<Transaction>,
    rewardsToolsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val historySuggestions = remember(draft.accountId, transactions, draft.annualFeeCents) {
        CreditCardStrategyEngine.detectHistorySuggestions(
            accountId = draft.accountId,
            transactions = transactions,
            templateAnnualFeeCents = draft.annualFeeCents?.takeIf { it > 0L }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (rewardsToolsEnabled) {
            OrbitSectionCard(
                title = "Card Timeline & 5/24 Status",
                subtitle = "Account age, Chase 5/24 eligibility & bonus cooldown",
                showHeaderDivider = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrbitAccordionVisibility(
                        visible = historySuggestions.suggestedOpenedDate != null &&
                            draft.openedDate != historySuggestions.suggestedOpenedDate
                    ) {
                        historySuggestions.suggestedOpenedDate?.let { opDate ->
                            AssistChip(
                                onClick = {
                                    haptics.confirm()
                                    controller.setOpenedDate(opDate)
                                },
                                label = {
                                    Text("Earliest activity ${CardFormat.formatCardDate(opDate)} · Set as Opened Date")
                                }
                            )
                        }
                    }

                    DateFieldButton(
                        date = draft.openedDate?.let { LocalDateUtils.parseIsoDate(it) },
                        onDateSelected = { dt -> controller.setOpenedDate(LocalDateUtils.formatIsoDate(dt)) },
                        onClear = { controller.setOpenedDate(null) },
                        label = "Card Opened Date",
                        placeholder = "Not Set",
                        isError = draft.errors.containsKey("openedDate")
                    )

                    OrbitSwitchRow(
                        title = "Business Card (5/24 Exempt)",
                        subtitle = "Exclude this card from personal Chase 5/24 count",
                        checked = draft.isBusinessCard,
                        onCheckedChange = { b -> controller.updateDraft { it.copy(isBusinessCard = b) } }
                    )

                    Column {
                        OrbitFieldLabel("Issuer Cooldown Rule")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            COOLDOWN_OPTIONS.forEach { (months, lbl) ->
                                val selected = draft.subCooldownMonths == months
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptics.tick()
                                        controller.updateDraft { it.copy(subCooldownMonths = months) }
                                    },
                                    label = { Text(lbl, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            OrbitSectionCard(
                title = "Welcome Bonus Tracker",
                subtitle = "Sign-up bonus minimum spend & completion date",
                showHeaderDivider = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrbitSegmentedTabRow(
                        items = WelcomeBonusMode.entries,
                        selected = draft.welcomeBonusMode,
                        onSelect = { controller.setWelcomeBonusMode(it) },
                        label = { mode ->
                            when (mode) {
                                WelcomeBonusMode.NONE -> "None"
                                WelcomeBonusMode.WORKING -> "In Progress"
                                WelcomeBonusMode.EARNED -> "Earned"
                            }
                        }
                    )

                    AnimatedContent(
                        targetState = draft.welcomeBonusMode,
                        transitionSpec = { orbitMorphContentTransform(clip = false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { bonusMode ->
                        when (bonusMode) {
                            WelcomeBonusMode.WORKING -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OrbitTextField(
                                        value = draft.subRewardText.orEmpty(),
                                        onValueChange = { t -> controller.updateDraft { it.copy(subRewardText = t.ifBlank { null }) } },
                                        label = { Text("Bonus Offer") },
                                        placeholder = { Text("e.g. 75,000 Ultimate Rewards pts") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CurrencyTextField(
                                            cents = draft.targetSpendCents ?: 0L,
                                            onCentsChange = { c ->
                                                controller.updateDraft { it.copy(targetSpendCents = c.takeIf { it > 0L }) }
                                            },
                                            label = "Min Spend",
                                            modifier = Modifier.weight(1f)
                                        )
                                        DateFieldButton(
                                            date = draft.targetDeadlineDate?.let { LocalDateUtils.parseIsoDate(it) },
                                            onDateSelected = { dt ->
                                                controller.updateDraft { it.copy(targetDeadlineDate = LocalDateUtils.formatIsoDate(dt)) }
                                            },
                                            onClear = {
                                                controller.updateDraft { it.copy(targetDeadlineDate = null) }
                                            },
                                            label = "Spend Deadline",
                                            placeholder = "Not Set",
                                            isError = draft.errors.containsKey("targetDeadlineDate"),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            WelcomeBonusMode.EARNED -> {
                                DateFieldButton(
                                    date = draft.subReceivedDate?.let { LocalDateUtils.parseIsoDate(it) },
                                    onDateSelected = { dt ->
                                        controller.updateDraft { it.copy(subReceivedDate = LocalDateUtils.formatIsoDate(dt)) }
                                    },
                                    onClear = {
                                        controller.updateDraft { it.copy(subReceivedDate = null) }
                                    },
                                    label = "Bonus Received Date",
                                    placeholder = "Not Set"
                                )
                            }
                            WelcomeBonusMode.NONE -> Unit
                        }
                    }
                }
            }
        }

        OrbitSectionCard(
            title = "Notes & Memo",
            subtitle = "Retention offers, product-change history & private notes",
            showHeaderDivider = false
        ) {
            OrbitTextField(
                value = draft.notes.orEmpty(),
                onValueChange = { t -> controller.updateDraft { it.copy(notes = t.ifBlank { null }) } },
                placeholder = { Text("Add retention offers, downgrade paths, or card notes…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
