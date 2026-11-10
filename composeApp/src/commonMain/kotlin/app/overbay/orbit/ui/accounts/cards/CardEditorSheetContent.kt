package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.launch

@Composable
fun CardEditorSheetContent(
    draft: CardEditorDraft,
    controller: CardEditorController,
    householdMembers: List<HouseholdMember> = emptyList(),
    transactions: List<Transaction> = emptyList(),
    rewardsToolsEnabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val scope = rememberCoroutineScope()
    val catalog by CreditCardTemplates.catalogFlow.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (draft.step == CardEditorStep.PICK_TEMPLATE) "Choose a Card Template" else "Card Settings & Perks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (draft.step == CardEditorStep.FORM) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            controller.updateDraft { it.copy(step = CardEditorStep.PICK_TEMPLATE) }
                        }
                    ) {
                        Text("Link to Real Card", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Single LazyColumn scroll container (NO nested verticalScroll, NO heightIn caps)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (draft.step == CardEditorStep.PICK_TEMPLATE) {
                    cardTemplatePickerItems(
                        catalog = catalog,
                        query = draft.templateQuery,
                        selectedFilter = draft.templateCategoryFilter,
                        onQueryChange = { controller.updateDraft { d -> d.copy(templateQuery = it) } },
                        onFilterChange = { filter ->
                            haptics.tick()
                            controller.updateDraft { d -> d.copy(templateCategoryFilter = filter) }
                        },
                        onSelectTemplate = { tpl ->
                            haptics.confirm()
                            controller.applyTemplate(tpl)
                        },
                        onSkip = {
                            haptics.tick()
                            controller.skipTemplate()
                        }
                    )
                } else {
                    // Pinned live CardFace preview (compact)
                    item(key = "card_preview") {
                        CardFace(
                            name = draft.accountName.ifBlank { draft.cardName.ifBlank { "Credit Card" } },
                            issuer = draft.issuer.ifBlank { null },
                            network = draft.network.ifBlank { null },
                            artKey = draft.artKey,
                            holderLabel = draft.cardholder,
                            compact = true,
                            artworkUrl = draft.artworkUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Form sections
                    item(key = "card_form_sections") {
                        CardEditorFormSections(
                            draft = draft,
                            controller = controller,
                            householdMembers = householdMembers,
                            transactions = transactions,
                            rewardsToolsEnabled = rewardsToolsEnabled
                        )
                    }
                }
            }
        }

        // Sticky bottom action bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        controller.requestClose()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !draft.isSaving
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        scope.launch {
                            controller.save()
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    enabled = draft.isValid && !draft.isSaving && draft.step == CardEditorStep.FORM
                ) {
                    if (draft.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(if (draft.isSaving) "Saving..." else "Save Card", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Discard Confirmation Dialog
    if (draft.showDiscardConfirm) {
        OrbitConfirmationDialog(
            title = "Discard Changes?",
            message = "You have unsaved changes to this card. Discard them?",
            confirmText = "Discard",
            dismissText = "Keep Editing",
            isDestructive = true,
            onConfirm = {
                controller.confirmDiscard()
                onDismiss()
            },
            onDismiss = { controller.dismissDiscardConfirm() }
        )
    }

    // Perk Editor Dialog
    if (draft.editingPerkId != null) {
        val existingBenefit = draft.benefits.find { it.id == draft.editingPerkId }
        val targetBenefit = existingBenefit ?: CardBenefit(id = draft.editingPerkId.takeIf { it.isNotBlank() } ?: "")
        PerkEditorDialog(
            benefit = targetBenefit,
            isNew = existingBenefit == null,
            onSave = { updated ->
                controller.upsertBenefit(updated)
                controller.updateDraft { it.copy(editingPerkId = null) }
            },
            onDismiss = { controller.updateDraft { it.copy(editingPerkId = null) } }
        )
    }

    // Reward Editor Dialog
    if (draft.editingRewardIndex != null) {
        val idx = draft.editingRewardIndex
        val targetReward = if (idx >= 0 && idx in draft.rewardCategories.indices) {
            draft.rewardCategories[idx]
        } else {
            RewardCategory()
        }
        RewardEditorDialog(
            reward = targetReward,
            isNew = idx < 0,
            onSave = { updated ->
                controller.upsertReward(if (idx >= 0) idx else null, updated)
                controller.updateDraft { it.copy(editingRewardIndex = null) }
            },
            onDismiss = { controller.updateDraft { it.copy(editingRewardIndex = null) } }
        )
    }
}

@Composable
fun CardEditorModalSheet(
    draft: CardEditorDraft,
    controller: CardEditorController,
    householdMembers: List<HouseholdMember> = emptyList(),
    transactions: List<Transaction> = emptyList(),
    rewardsToolsEnabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        CardEditorSheetContent(
            draft = draft,
            controller = controller,
            householdMembers = householdMembers,
            transactions = transactions,
            rewardsToolsEnabled = rewardsToolsEnabled,
            onDismiss = onDismiss
        )
    }
}
