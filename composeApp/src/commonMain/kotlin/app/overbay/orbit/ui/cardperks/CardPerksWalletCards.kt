package app.overbay.orbit.ui.cardperks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.ActivePerkItem
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.cards.CardEditorTab
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.accounts.cards.CardVisualPlate
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
internal fun CardWalletRoiBentoCard(
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    modifier: Modifier = Modifier
) {
    val creditAccounts = remember(uiState.accounts, uiState.selectedCardPlayer, uiState.householdMembers, uiState.playerScopedHub.walletAccountIds) {
        val walletIds = uiState.playerScopedHub.walletAccountIds
        uiState.accounts.filter { acct ->
            acct.type == AccountType.CREDIT &&
                !acct.isArchived &&
                (if (walletIds.isNotEmpty()) acct.id in walletIds else CardFormat.matchesSelectedPlayer(acct, uiState.selectedCardPlayer, uiState.householdMembers))
        }
    }
    val cycleByAccount = remember(uiState.playerScopedHub.cardCycles, uiState.cardCycles) {
        uiState.playerScopedHub.cardCycles.ifEmpty { uiState.cardCycles }.associateBy { it.accountId }
    }
    val unusedPerksByAccount = remember(uiState.cardPortfolioSummary.currentPeriodPerks) {
        uiState.cardPortfolioSummary.currentPeriodPerks.filter { !it.isFullyUsed }.groupBy { it.accountId }
    }
    val unconfiguredCount = remember(creditAccounts) {
        creditAccounts.count { acct ->
            val p = acct.creditCardProfile
            p == null || (p.benefits.isEmpty() && p.rewardCategories.isEmpty() && p.annualFeeCents == null)
        }
    }

    OrbitSectionCard(
        title = "Card Wallet (${creditAccounts.size})",
        subtitle = if (unconfiguredCount > 0) {
            "$unconfiguredCount card${if (unconfiguredCount == 1) "" else "s"} ready for template setup"
        } else {
            "Tap any card for statement credits, billing & recent activity"
        },
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = OrbitSprings.IntSizeAccordionFast)
        ) {
            val useTwoColumnGrid = maxWidth >= 600.dp && creditAccounts.size > 1
            if (useTwoColumnGrid) {
                val rows = creditAccounts.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            WalletCardTile(
                                account = pair[0],
                                cycle = cycleByAccount[pair[0].id],
                                unusedPerks = unusedPerksByAccount[pair[0].id].orEmpty(),
                                uiState = uiState,
                                actions = actions,
                                isCompactTile = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (pair.size > 1) {
                                WalletCardTile(
                                    account = pair[1],
                                    cycle = cycleByAccount[pair[1].id],
                                    unusedPerks = unusedPerksByAccount[pair[1].id].orEmpty(),
                                    uiState = uiState,
                                    actions = actions,
                                    isCompactTile = true,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    creditAccounts.forEach { account ->
                        WalletCardTile(
                            account = account,
                            cycle = cycleByAccount[account.id],
                            unusedPerks = unusedPerksByAccount[account.id].orEmpty(),
                            uiState = uiState,
                            actions = actions,
                            isCompactTile = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletCardTile(
    account: Account,
    cycle: CardCycleStatus?,
    unusedPerks: List<ActivePerkItem>,
    uiState: AccountsUiState,
    actions: CardPerksBentoActions,
    isCompactTile: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val profile = account.creditCardProfile
    val isSelected = uiState.selectedAccountId == account.id
    val displayName = profile?.cardName?.takeIf { it.isNotBlank() } ?: account.name
    val institution = account.institution?.takeIf { it.isNotBlank() } ?: profile?.issuerName?.takeIf { it.isNotBlank() }
    val metaSubtitle = listOfNotNull(
        institution,
        account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "\u2022\u2022\u2022\u2022 $it" }
    ).joinToString(" \u00B7 ")
    val holderName = CardFormat.resolveCardholderName(profile, uiState.householdMembers)
    val auNames = CardFormat.resolveAuthorizedUsers(profile, uiState.householdMembers)
    val network = profile?.network?.takeIf { it.isNotBlank() }

    val owedCents = cycle?.owedNowCents ?: abs(account.currentBalanceCents)
    val utilBps = cycle?.utilizationBps
    val unusedCents = unusedPerks.sumOf { it.remainingCents }
    val annualFee = profile?.annualFeeCents ?: 0L
    val needsTemplate = profile == null || (profile.benefits.isEmpty() && profile.rewardCategories.isEmpty() && profile.annualFeeCents == null)
    val needsBillingDates = !needsTemplate && (profile?.statementClosingDay == null || profile.paymentDueDay == null)

    val cardBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "cardWalletSelectedBg"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "cardWalletSelectedBorder"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = modifier
            .orbitTactilePress()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptics.tick()
                actions.onInspectAccount(account.id)
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CardVisualPlate(
                    name = displayName,
                    issuer = institution,
                    artKey = profile?.imageUrl,
                    artworkUrl = profile?.artworkUrl,
                    last4 = account.accountNumberLast4,
                    modifier = Modifier
                        .width(if (isCompactTile) 82.dp else 94.dp)
                        .aspectRatio(1.586f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Inspect Card",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (metaSubtitle.isNotEmpty()) {
                        Text(
                            text = metaSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = MoneyUtils.formatCents(owedCents),
                            style = TabularNumStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (utilBps != null) {
                            val utilTone = when {
                                utilBps >= 7500 -> CardStatusTone.CRITICAL
                                utilBps >= 3000 -> CardStatusTone.WARNING
                                utilBps < 1000 -> CardStatusTone.POSITIVE
                                else -> CardStatusTone.NEUTRAL
                            }
                            CardStatusPill(text = "${CardFormat.formatBps(utilBps)} util", tone = utilTone)
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (unusedPerks.isNotEmpty()) {
                    CardStatusPill(
                        text = "${unusedPerks.size} perk${if (unusedPerks.size == 1) "" else "s"} \u00B7 ${CardFormat.formatAdaptiveCents(unusedCents)}",
                        tone = CardStatusTone.POSITIVE
                    )
                }
                if (annualFee > 0L) {
                    OrbitBadge(
                        text = "${CardFormat.formatAdaptiveCents(annualFee)}/yr",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (holderName != null) {
                    OrbitBadge(
                        text = holderName,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (network != null) {
                    OrbitBadge(
                        text = network,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                auNames.forEach { au ->
                    OrbitBadge(
                        text = "AU \u00B7 $au",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                if (needsTemplate) {
                    OrbitActionChip(
                        label = "Link Template",
                        onClick = {
                            haptics.tick()
                            actions.onOpenCardSetup(account.id, CardEditorTab.CARD, true)
                        }
                    )
                } else if (needsBillingDates) {
                    OrbitActionChip(
                        label = "Set Billing Dates",
                        onClick = {
                            haptics.tick()
                            actions.onOpenCardSetup(account.id, CardEditorTab.BILLING, false)
                        }
                    )
                }
            }
        }
    }
}
