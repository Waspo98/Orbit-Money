package app.overbay.orbit.ui.cardperks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.accounts.cards.CardEditorSheetContent
import app.overbay.orbit.ui.accounts.cards.CreditCardDetailSection
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class CardPerksInspectorMode { DETAIL, EDIT_PERKS }

/**
 * Right-hand morphing inspector pane (and phone modal sheet content) for the Card Perks screen.
 * Focuses strictly on a card's Perks Checklist, Annual Fee ROI Ledger, Multipliers, and SUB/5/24 strategy.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    val mode = if (state.cardEditorDraft != null) {
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
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxWidth(),
        label = "cardPerksInspectorTransition"
    ) { currentMode ->
        when (currentMode) {
            CardPerksInspectorMode.EDIT_PERKS -> {
                val draft = state.cardEditorDraft
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
                    val profile = inspecting.creditCardProfile ?: CreditCardProfile(accountId = inspecting.id)
                    val subProgress = remember(inspecting, state.selectedAccountTransactions) {
                        CreditCardStrategyEngine.computeSubProgress(inspecting, state.selectedAccountTransactions)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.cardName?.takeIf { it.isNotBlank() } ?: inspecting.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitleParts = listOfNotNull(
                                        inspecting.institution?.takeIf { it.isNotBlank() },
                                        inspecting.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                                        profile.strategy.cardholder?.takeIf { it.isNotBlank() }
                                    )
                                    if (subtitleParts.isNotEmpty()) {
                                        Text(
                                            text = subtitleParts.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { haptics.tick(); onClosePane() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Details",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        item {
                            CreditCardDetailSection(
                                account = inspecting,
                                profile = profile,
                                transactions = state.selectedAccountTransactions,
                                subProgress = subProgress,
                                rewardsToolsEnabled = state.rewardsToolsEnabled,
                                showBillingDetails = false,
                                onEditCard = { viewModel.openCreditCardProfile(inspecting) },
                                onToggleBenefit = { bId -> viewModel.toggleCardBenefit(inspecting.id, bId) },
                                onOpenPerkUsage = { bId -> viewModel.openPerkUsage(inspecting.id, bId) }
                            )
                        }

                        item {
                            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OrbitActionChip(
                                    label = "Edit Card",
                                    onClick = { haptics.tick(); viewModel.openCreditCardProfile(inspecting) }
                                )
                                OrbitActionChip(
                                    label = "View Account & Billing",
                                    onClick = {
                                        haptics.tick()
                                        onNavigate(OrbitNavKey.Accounts(accountId = inspecting.id))
                                    }
                                )
                                OrbitActionChip(
                                    label = "See Transactions",
                                    onClick = {
                                        haptics.tick()
                                        onNavigate(OrbitNavKey.Transactions(accountId = inspecting.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
