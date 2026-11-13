package app.overbay.orbit.ui.cardperks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardPerkRow
import app.overbay.orbit.ui.accounts.cards.CardVisualPlate
import app.overbay.orbit.ui.accounts.cards.Chase524PersonCard
import app.overbay.orbit.ui.accounts.cards.WelcomeBonusesCard
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.accounts.cards.WhichCardWhereCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard

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
private fun CardWalletRoiBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val creditAccounts = remember(uiState.accounts, uiState.selectedCardPlayer, uiState.householdMembers) {
        uiState.accounts.filter { acct ->
            acct.type == AccountType.CREDIT &&
                !acct.isArchived &&
                CardFormat.matchesSelectedPlayer(acct, uiState.selectedCardPlayer, uiState.householdMembers)
        }
    }

    OrbitSectionCard(
        title = "Card Wallet (${creditAccounts.size})",
        subtitle = "Tap card for more details",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.animateContentSize(animationSpec = OrbitSprings.IntSizeAccordionFast),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            creditAccounts.forEach { account ->
                val profile = account.creditCardProfile
                val isSelected = uiState.selectedAccountId == account.id
                val displayName = profile?.cardName?.takeIf { it.isNotBlank() } ?: account.name
                val institution = account.institution?.takeIf { it.isNotBlank() }
                    ?: profile?.issuerName?.takeIf { it.isNotBlank() }
                val metaSubtitle = listOfNotNull(
                    institution,
                    account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }
                ).joinToString(" · ")
                val holderName = CardFormat.resolveCardholderName(profile, uiState.householdMembers)
                val auNames = CardFormat.resolveAuthorizedUsers(profile, uiState.householdMembers)
                val network = profile?.network?.takeIf { it.isNotBlank() }

                val cardBg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceContainerLowest,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "cardWalletSelectedBg"
                )
                val cardBorderColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "cardWalletSelectedBorder"
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { actions.onInspectAccount(account.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardVisualPlate(
                            name = displayName,
                            issuer = institution,
                            artKey = profile?.imageUrl,
                            artworkUrl = profile?.artworkUrl,
                            last4 = account.accountNumberLast4,
                            modifier = Modifier
                                .fillMaxWidth(0.46f)
                                .widthIn(max = 176.dp)
                                .aspectRatio(1.586f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (metaSubtitle.isNotEmpty()) {
                                Text(
                                    text = metaSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (network != null || holderName != null || auNames.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (network != null) {
                                        OrbitBadge(
                                            text = network,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    if (holderName != null) {
                                        OrbitBadge(
                                            text = holderName,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    auNames.forEach { au ->
                                        OrbitBadge(
                                            text = "AU · $au",
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Inspect Card",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerksThisPeriodBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val perks = uiState.cardPortfolioSummary.currentPeriodPerks
    val activePerks = perks.filter { !it.isFullyUsed }
    val usedPerks = perks.filter { it.isFullyUsed }
    val totalRemaining = activePerks.sumOf { it.remainingCents }
    val subtitle = if (perks.isNotEmpty()) "${MoneyUtils.formatCents(totalRemaining)} left to use this period" else null

    OrbitSectionCard(title = "Perks This Period", subtitle = subtitle, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (perks.isEmpty()) {
                Text(
                    text = "No perks set up · Tap any card in your wallet to pick a template or add monthly/annual credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                activePerks.forEach { item ->
                    CardPerkRow(
                        title = "${item.cardTitle} · ${item.benefit.title}",
                        subtitle = item.periodLabel,
                        valueCents = item.periodValueCents,
                        usedCents = item.usedCents,
                        daysUntilReset = item.daysUntilReset,
                        onToggle = { actions.onToggleBenefit(item.accountId, item.benefitId) },
                        onEditAmount = { actions.onOpenPerkUsage(item.accountId, item.benefitId) }
                    )
                }

                if (usedPerks.isNotEmpty()) {
                    TextButton(onClick = { actions.onToggleShowUsedPerks() }, modifier = Modifier.align(Alignment.Start)) {
                        Text(if (uiState.showUsedPerks) "Hide ${usedPerks.size} Used" else "Show ${usedPerks.size} Used")
                    }

                    OrbitAccordionVisibility(visible = uiState.showUsedPerks) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            usedPerks.forEach { item ->
                                CardPerkRow(
                                    title = "${item.cardTitle} · ${item.benefit.title}",
                                    subtitle = item.periodLabel,
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
    val people = uiState.cardPortfolioSummary.chase524ByPerson
    if (people.isEmpty()) {
        OrbitSectionCard(title = "Chase 5/24 Status", modifier = modifier) {
            Text(
                text = "Add card opening dates in your credit card tracker settings to track personal 5/24 eligibility.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            people.forEach { person ->
                Chase524PersonCard(person = person, onInspectAccount = actions.onInspectAccount)
            }
        }
    }
}
