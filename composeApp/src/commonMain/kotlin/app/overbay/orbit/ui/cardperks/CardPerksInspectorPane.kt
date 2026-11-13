package app.overbay.orbit.ui.cardperks

import androidx.compose.animation.AnimatedContent
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.accounts.cards.CardEditorSheetContent
import app.overbay.orbit.ui.accounts.cards.CreditCardInspectorDetailContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class CardPerksInspectorMode { DETAIL, EDIT_PERKS }

/**
 * Right-hand morphing inspector pane (and phone modal sheet content) for the Credit Card Tracker screen.
 */
@Composable
fun CardPerksInspectorPane(
    state: AccountsUiState,
    viewModel: AccountsViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var cachedAccount by remember { mutableStateOf<Account?>(null) }
    val activeAccount = state.selectedAccount
    if (activeAccount != null) {
        cachedAccount = activeAccount
    }
    val inspecting = activeAccount ?: cachedAccount

    val liveCardDraft = viewModel.cardEditor.currentDraft ?: state.cardEditorDraft
    val mode = if (liveCardDraft != null) {
        CardPerksInspectorMode.EDIT_PERKS
    } else {
        CardPerksInspectorMode.DETAIL
    }

    val canBackFromEdit = mode == CardPerksInspectorMode.EDIT_PERKS && state.selectedAccount != null
    BackHandler(enabled = canBackFromEdit) {
        haptics.tick()
        viewModel.cardEditor.requestClose()
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = { orbitMorphContentTransform(clip = false) },
        modifier = modifier.fillMaxWidth(),
        label = "cardPerksInspectorTransition"
    ) { currentMode ->
        when (currentMode) {
            CardPerksInspectorMode.EDIT_PERKS -> {
                val draft = viewModel.cardEditor.currentDraft ?: state.cardEditorDraft
                if (draft != null) {
                    CardEditorSheetContent(
                        draft = draft,
                        controller = viewModel.cardEditor,
                        householdMembers = state.householdMembers,
                        transactions = state.selectedAccountTransactions,
                        rewardsToolsEnabled = state.rewardsToolsEnabled,
                        onDismiss = {
                            if (state.selectedAccount != null) viewModel.cardEditor.requestClose() else onClosePane()
                        }
                    )
                }
            }
            CardPerksInspectorMode.DETAIL -> {
                if (inspecting != null) {
                    CreditCardInspectorDetailContent(
                        account = inspecting,
                        transactions = state.selectedAccountTransactions,
                        householdMembers = state.householdMembers,
                        rewardsToolsEnabled = state.rewardsToolsEnabled,
                        categoryMap = state.categoryMap,
                        onEditCard = { viewModel.openCreditCardProfile(inspecting) },
                        onToggleBenefit = { bId -> viewModel.toggleCardBenefit(inspecting.id, bId) },
                        onOpenPerkUsage = { bId -> viewModel.openPerkUsage(inspecting.id, bId) },
                        onSeeFullTransactions = { onNavigate(OrbitNavKey.Transactions(accountId = inspecting.id)) },
                        onClosePane = onClosePane
                    )
                }
            }
        }
    }
}
