package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.domain.CardHistorySuggestions
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardTemplate
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

private val PLAYER_OPTIONS = listOf("Player 1", "Player 2", "Shared")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardStep1CardAndLook(
    accountName: String,
    onAccountNameChange: (String) -> Unit,
    cardName: String,
    onCardNameChange: (String) -> Unit,
    issuerName: String,
    onIssuerNameChange: (String) -> Unit,
    network: String,
    onNetworkChange: (String) -> Unit,
    imageUrl: String,
    onImageUrlChange: (String) -> Unit,
    onApplyTemplate: (CreditCardTemplate) -> Unit,
    onClearToCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WizardStepHeader(
            title = "1. Link a Popular Card or Start Fresh",
            subtitle = "Choose a card template to auto-fill annual fees, statement credits, and reward multipliers."
        )

        WizardSectionContainer(
            title = "Popular Cards",
            subtitle = "Tap a card to load its perks and issuer rules, or tap 'Start from Scratch'."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CreditCardTemplates.ALL.forEach { tpl ->
                    val isSelected = cardName.equals(tpl.cardName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.confirm()
                            onApplyTemplate(tpl)
                        },
                        label = {
                            Text(
                                text = tpl.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
                val isCustom = CreditCardTemplates.ALL.none { it.cardName.equals(cardName, ignoreCase = true) } && cardName.isNotBlank()
                FilterChip(
                    selected = isCustom,
                    onClick = {
                        haptics.tick()
                        onClearToCustom()
                    },
                    label = { Text("Start from Scratch", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        WizardSectionContainer(
            title = "Card Name & Artwork",
            subtitle = "Customize how this card appears in your wallet."
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrbitTextField(
                    value = cardName,
                    onValueChange = onCardNameChange,
                    label = { Text("Card Product Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OrbitTextField(
                    value = issuerName,
                    onValueChange = onIssuerNameChange,
                    label = { Text("Issuer (e.g. Chase)") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }
            OrbitTextField(
                value = accountName,
                onValueChange = onAccountNameChange,
                label = { Text("Account Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            CardArtworkAndNetworkSection(
                network = network,
                onNetworkChange = onNetworkChange,
                imageUrl = imageUrl,
                onImageUrlChange = onImageUrlChange,
                cardName = cardName,
                issuerName = issuerName
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardStep2DatesAndFee(
    annualFeeCents: Cents,
    onAnnualFeeChange: (Cents) -> Unit,
    annualFeeDate: LocalDate,
    hasAnnualFeeDate: Boolean,
    onAnnualFeeDateSelected: (LocalDate) -> Unit,
    strategy: CreditCardStrategy,
    onStrategyChange: (CreditCardStrategy) -> Unit,
    suggestions: CardHistorySuggestions,
    modifier: Modifier = Modifier
) {
    val today = LocalDateUtils.todayLocalDate()
    val openedLocal = LocalDateUtils.parseIsoDate(strategy.openedDate) ?: today

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WizardStepHeader(
            title = "2. Dates, Owner & Annual Fee",
            subtitle = "Track your Chase 5/24 velocity and when your annual fee renews."
        )

        SmartHistorySuggestionsBanner(
            suggestions = suggestions,
            currentOpenedDate = strategy.openedDate,
            currentAnnualFeeDate = if (hasAnnualFeeDate) LocalDateUtils.formatIsoDate(annualFeeDate) else null,
            onApplyOpenedDate = { onStrategyChange(strategy.copy(openedDate = it)) },
            onApplyFeeAndDate = { feeCents, dateLocal ->
                if (feeCents != null) onAnnualFeeChange(feeCents)
                onAnnualFeeDateSelected(dateLocal)
            }
        )

        WizardSectionContainer(
            title = "Household Cardholder & 5/24 Status",
            subtitle = "Assign to a household member for separate 5/24 tracking."
        ) {
            CardholderAndBusinessFields(
                strategy = strategy,
                onStrategyChange = onStrategyChange
            )
        }

        WizardSectionContainer(
            title = "Sign-Up Date & Annual Fee",
            subtitle = "When did you open this card, and when does the annual fee post?"
        ) {
            DateFieldButton(
                date = openedLocal,
                onDateSelected = { selected ->
                    val iso = LocalDateUtils.formatIsoDate(selected)
                    onStrategyChange(strategy.copy(openedDate = iso))
                },
                label = if (strategy.openedDate != null) "Sign-Up Date (${strategy.openedDate})" else "Select Sign-Up / Opened Date"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CurrencyTextField(
                        cents = annualFeeCents,
                        onCentsChange = onAnnualFeeChange,
                        label = "Annual Fee"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DateFieldButton(
                        date = annualFeeDate,
                        onDateSelected = onAnnualFeeDateSelected,
                        label = if (hasAnnualFeeDate) "Fee Posts (${LocalDateUtils.formatIsoDate(annualFeeDate)})" else "Fee Post Date"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardholderAndBusinessFields(
    strategy: CreditCardStrategy,
    onStrategyChange: (CreditCardStrategy) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PLAYER_OPTIONS.forEach { p ->
            val selected = strategy.cardholder.equals(p, ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    onStrategyChange(strategy.copy(cardholder = if (selected) null else p))
                },
                label = { Text(p, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Business Card (5/24 Exempt)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Excludes this card from your personal Chase 5/24 count.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(
            checked = strategy.isBusinessCard,
            onCheckedChange = {
                haptics.tick()
                onStrategyChange(strategy.copy(isBusinessCard = it))
            }
        )
    }
}

@Composable
fun SmartHistorySuggestionsBanner(
    suggestions: CardHistorySuggestions,
    currentOpenedDate: String?,
    currentAnnualFeeDate: String?,
    onApplyOpenedDate: (String) -> Unit,
    onApplyFeeAndDate: (Cents?, LocalDate) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val showOpened = suggestions.suggestedOpenedDate != null && currentOpenedDate != suggestions.suggestedOpenedDate
    val showFee = suggestions.suggestedAnnualFeeDate != null && currentAnnualFeeDate != suggestions.suggestedAnnualFeeDate
    if (!showOpened && !showFee) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = OrbitColorTokens.Green.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, OrbitColorTokens.Green.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SMART SUGGESTIONS FROM STATEMENT HISTORY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = OrbitColorTokens.Green
            )
            if (showOpened) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Earliest Activity: ${suggestions.suggestedOpenedDate}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        suggestions.earliestTxnMerchant?.let {
                            Text(text = "First txn: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    TextButton(onClick = {
                        haptics.confirm()
                        onApplyOpenedDate(suggestions.suggestedOpenedDate!!)
                    }) {
                        Text("Use Sign-Up Date", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (showFee) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val feeStr = suggestions.detectedAnnualFeeCents?.let { MoneyUtils.formatCents(it) } ?: "Annual Fee"
                        Text(
                            text = "Detected $feeStr on ${suggestions.suggestedAnnualFeeDate}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        suggestions.detectedFeeMerchant?.let {
                            Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    TextButton(onClick = {
                        haptics.confirm()
                        LocalDateUtils.parseIsoDate(suggestions.suggestedAnnualFeeDate)?.let {
                            onApplyFeeAndDate(suggestions.detectedAnnualFeeCents, it)
                        }
                    }) {
                        Text("Use Fee Date", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WizardStepHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
