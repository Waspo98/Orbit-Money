package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.domain.ActivePerkItem
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.PerkPeriodFilter
import app.overbay.orbit.ui.accounts.cards.CardEditorTab
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardPerkRow
import app.overbay.orbit.ui.accounts.cards.Chase524SectionCard
import app.overbay.orbit.ui.accounts.cards.WelcomeBonusesCard
import app.overbay.orbit.ui.accounts.cards.WhichCardWhereCard
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CardPerksBentoItem(
    cardType: CardPerksCardType,
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    when (cardType) {
        CardPerksCardType.PERKS_THIS_PERIOD -> PerksThisPeriodBentoCard(uiState, actions, modifier)
        CardPerksCardType.CARD_WALLET_ROI -> CardWalletRoiBentoCard(uiState, actions, modifier)
        CardPerksCardType.WHICH_CARD_WHERE -> WhichCardWhereCard(
            picks = uiState.cardPortfolioSummary.bestCardsByCategory,
            isExpanded = true,
            onToggleExpand = {},
            onInspectAccount = actions.onInspectAccount,
            searchQuery = uiState.whichCardQuery,
            onSearchQueryChange = actions.onWhichCardQueryChange,
            alwaysExpanded = true,
            modifier = modifier
        )
        CardPerksCardType.WELCOME_BONUSES -> WelcomeBonusesCard(
            subs = uiState.cardPortfolioSummary.activeSubs,
            onInspectAccount = actions.onInspectAccount,
            modifier = modifier
        )
        CardPerksCardType.CHASE_524 -> Chase524BentoSection(uiState, actions, modifier)
        CardPerksCardType.ANNUAL_FEES_AHEAD -> AnnualFeesAheadBentoCard(uiState, actions, modifier)
        CardPerksCardType.PAYMENTS_DUE -> PaymentsDueBentoCard(uiState, actions, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerksThisPeriodBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val perks = uiState.cardPortfolioSummary.currentPeriodPerks
    val activePerks = remember(perks) {
        perks.filter { !it.isFullyUsed }.sortedWith(
            compareBy<ActivePerkItem> { it.daysUntilReset ?: Int.MAX_VALUE }
                .thenByDescending { it.remainingCents }
        )
    }
    val usedPerks = remember(perks) { perks.filter { it.isFullyUsed } }
    val expiringCount = remember(activePerks) { activePerks.count { (it.daysUntilReset ?: Int.MAX_VALUE) <= 7 } }
    val filteredActive = remember(activePerks, uiState.perkPeriodFilter) {
        when (uiState.perkPeriodFilter) {
            PerkPeriodFilter.ALL -> activePerks
            PerkPeriodFilter.EXPIRING_SOON -> activePerks.filter { (it.daysUntilReset ?: Int.MAX_VALUE) <= 7 }
            PerkPeriodFilter.MONTHLY -> activePerks.filter { it.benefit.cadence == BenefitCadence.MONTHLY }
            PerkPeriodFilter.ANNUAL_PLUS -> activePerks.filter { it.benefit.cadence != BenefitCadence.MONTHLY }
        }
    }
    val totalRemaining = activePerks.sumOf { it.remainingCents }
    val subtitle = if (perks.isNotEmpty()) {
        "${CardFormat.formatAdaptiveCents(totalRemaining)} remaining across ${activePerks.size} perk${if (activePerks.size == 1) "" else "s"}"
    } else null

    OrbitSectionCard(
        title = "Perks This Period",
        subtitle = subtitle,
        actionText = when {
            usedPerks.isEmpty() -> null
            uiState.showUsedPerks -> "Hide Used"
            else -> "Show ${usedPerks.size} Used"
        },
        onActionClick = if (usedPerks.isEmpty()) null else {
            {
                haptics.tick()
                actions.onToggleShowUsedPerks()
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (perks.isEmpty()) {
                Text(
                    text = "No perks set up \u00B7 Link a card template in Card Wallet to track monthly, quarterly, or annual credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                if (activePerks.size > 4 || uiState.perkPeriodFilter != PerkPeriodFilter.ALL) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PerkPeriodFilter.entries.forEach { filterOption ->
                            if (filterOption == PerkPeriodFilter.EXPIRING_SOON && expiringCount == 0 && uiState.perkPeriodFilter != filterOption) {
                                return@forEach
                            }
                            val isSelected = uiState.perkPeriodFilter == filterOption
                            val chipLabel = if (filterOption == PerkPeriodFilter.EXPIRING_SOON && expiringCount > 0) {
                                "Expiring \u22647d ($expiringCount)"
                            } else {
                                filterOption.label
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        haptics.tick()
                                        actions.onSelectPerkFilter(filterOption)
                                    }
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (filteredActive.isEmpty() && activePerks.isNotEmpty()) {
                    Text(
                        text = "No perks match \"${uiState.perkPeriodFilter.label}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    filteredActive.forEach { item ->
                        CardPerkRow(
                            title = item.benefit.title,
                            subtitle = "${item.cardTitle} \u00B7 ${item.periodLabel}",
                            valueCents = item.periodValueCents,
                            usedCents = item.usedCents,
                            daysUntilReset = item.daysUntilReset,
                            onToggle = { actions.onToggleBenefit(item.accountId, item.benefitId) },
                            onEditAmount = { actions.onOpenPerkUsage(item.accountId, item.benefitId) }
                        )
                    }
                }

                if (usedPerks.isNotEmpty()) {
                    OrbitAccordionVisibility(visible = uiState.showUsedPerks) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            usedPerks.forEach { item ->
                                CardPerkRow(
                                    title = item.benefit.title,
                                    subtitle = "${item.cardTitle} \u00B7 ${item.periodLabel}",
                                    valueCents = item.periodValueCents,
                                    usedCents = item.usedCents,
                                    daysUntilReset = item.daysUntilReset,
                                    onToggle = { actions.onToggleBenefit(item.accountId, item.benefitId) },
                                    onEditAmount = { actions.onOpenPerkUsage(item.accountId, item.benefitId) }
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
private fun Chase524BentoSection(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    Chase524SectionCard(
        people = uiState.cardPortfolioSummary.chase524ByPerson,
        selectedHolderKey = uiState.chase524SelectedHolderKey,
        onSelectHolder = actions.onSelectChase524Holder,
        onInspectAccount = actions.onInspectAccount,
        onFixMissingOpenDate = { accountId ->
            actions.onOpenCardSetup(accountId, CardEditorTab.STRATEGY, false)
        },
        modifier = modifier
    )
}
