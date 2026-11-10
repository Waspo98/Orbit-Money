package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.CreditCardTemplate
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun CreditCardProfileEditorSheet(
    profile: CreditCardProfile,
    initialAccountName: String = "",
    initialInstitution: String = "",
    transactions: List<Transaction> = emptyList(),
    onSave: (CreditCardProfile, accountName: String, accountInstitution: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        CreditCardProfileEditorContent(
            profile = profile,
            initialAccountName = initialAccountName,
            initialInstitution = initialInstitution,
            transactions = transactions,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun CreditCardProfileEditorContent(
    profile: CreditCardProfile,
    initialAccountName: String = "",
    initialInstitution: String = "",
    transactions: List<Transaction> = emptyList(),
    onSave: (CreditCardProfile, accountName: String, accountInstitution: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val scope = rememberCoroutineScope()

    val isNewCardSetup = remember(profile) {
        profile.cardName.isNullOrBlank() && profile.benefits.isEmpty() && profile.rewardCategories.isEmpty()
    }
    var isWizardMode by remember(profile) { mutableStateOf(isNewCardSetup) }
    var expandedSection by remember { mutableStateOf(0) }

    var accountName by remember(profile) { mutableStateOf(initialAccountName) }
    var accountInstitution by remember(profile) { mutableStateOf(initialInstitution) }
    var cardName by remember(profile) { mutableStateOf(profile.cardName ?: "") }
    var issuerName by remember(profile) { mutableStateOf(profile.issuerName ?: "") }
    var network by remember(profile) { mutableStateOf(profile.network ?: "Visa") }
    var imageUrl by remember(profile) { mutableStateOf(profile.imageUrl ?: "") }
    var annualFeeCents by remember(profile) { mutableStateOf(profile.annualFeeCents ?: 0L) }
    var annualFeeDate by remember(profile) {
        val parsed = profile.annualFeePostDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed ?: LocalDateUtils.todayLocalDate())
    }
    var hasAnnualFeeDate by remember(profile) { mutableStateOf(profile.annualFeePostDate != null) }
    var creditLimitCents by remember(profile) { mutableStateOf(profile.creditLimitCents ?: 0L) }
    var statementClosingDayText by remember(profile) { mutableStateOf(profile.statementClosingDay?.toString() ?: "") }
    var paymentDueDayText by remember(profile) { mutableStateOf(profile.paymentDueDay?.toString() ?: "") }
    var authorizedUsers by remember(profile) { mutableStateOf(profile.authorizedUsers) }
    var rewardList by remember(profile) { mutableStateOf(profile.rewardCategories) }
    var benefitList by remember(profile) { mutableStateOf(profile.benefits) }
    var strategy by remember(profile) { mutableStateOf(profile.strategy) }
    var notes by remember(profile) { mutableStateOf(profile.notes ?: "") }

    val historySuggestions = remember(profile.accountId, transactions, annualFeeCents) {
        CreditCardStrategyEngine.detectHistorySuggestions(
            accountId = profile.accountId,
            transactions = transactions,
            templateAnnualFeeCents = annualFeeCents.takeIf { it > 0L }
        )
    }

    val applyTemplate: (CreditCardTemplate) -> Unit = { tpl ->
        val applied = tpl.applyToProfile(
            profile.copy(
                cardName = cardName,
                issuerName = issuerName,
                network = network,
                imageUrl = imageUrl,
                annualFeeCents = annualFeeCents,
                rewardCategories = rewardList,
                benefits = benefitList,
                strategy = strategy
            )
        )
        cardName = applied.cardName ?: ""
        issuerName = applied.issuerName ?: ""
        if (accountName.isBlank()) accountName = applied.cardName ?: ""
        if (accountInstitution.isBlank()) accountInstitution = applied.issuerName ?: ""
        network = applied.network ?: "Visa"
        imageUrl = applied.imageUrl ?: ""
        annualFeeCents = applied.annualFeeCents ?: 0L
        rewardList = applied.rewardCategories
        benefitList = applied.benefits
        strategy = applied.strategy
    }

    val commitSave: () -> Unit = {
        haptics.confirm()
        val feeCents = if (annualFeeCents > 0L) annualFeeCents else null
        val limitCents = if (creditLimitCents > 0L) creditLimitCents else null
        val feePostDate = if (hasAnnualFeeDate) LocalDateUtils.formatIsoDate(annualFeeDate) else null

        val updated = profile.copy(
            cardName = cardName.trim().ifBlank { null },
            issuerName = issuerName.trim().ifBlank { null },
            network = network.trim().ifBlank { null },
            imageUrl = imageUrl.trim().ifBlank { null },
            annualFeeCents = feeCents,
            annualFeePostDate = feePostDate,
            creditLimitCents = limitCents,
            statementClosingDay = statementClosingDayText.toIntOrNull()?.takeIf { it in 1..31 },
            paymentDueDay = paymentDueDayText.toIntOrNull()?.takeIf { it in 1..31 },
            authorizedUsers = authorizedUsers,
            rewardCategories = rewardList,
            benefits = benefitList,
            strategy = strategy,
            notes = notes.trim().ifBlank { null }
        )
        onSave(updated, accountName.trim(), accountInstitution.trim())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header with Mode Toggle (Guided Wizard vs. Categorized Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isWizardMode) "Card Setup Wizard" else "Card Settings & Perks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = {
                    haptics.tick()
                    isWizardMode = !isWizardMode
                }
            ) {
                Text(
                    text = if (isWizardMode) "All Settings" else "Setup Wizard",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Pinned Live Morphing Credit Card Preview
        CreditCardLivePreviewCard(
            accountName = accountName,
            cardName = cardName,
            issuerName = issuerName,
            network = network,
            imageUrl = imageUrl,
            annualFeeCents = annualFeeCents,
            strategy = strategy
        )

        if (isWizardMode) {
            val pagerState = rememberPagerState(pageCount = { 4 })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 420.dp)
            ) { page ->
                when (page) {
                    0 -> WizardStep1CardAndLook(
                        accountName = accountName, onAccountNameChange = { accountName = it },
                        cardName = cardName, onCardNameChange = { cardName = it },
                        issuerName = issuerName, onIssuerNameChange = { issuerName = it },
                        network = network, onNetworkChange = { network = it },
                        imageUrl = imageUrl, onImageUrlChange = { imageUrl = it },
                        onApplyTemplate = applyTemplate,
                        onClearToCustom = {
                            cardName = ""; issuerName = ""; imageUrl = ""; annualFeeCents = 0L
                            rewardList = emptyList(); benefitList = emptyList()
                        }
                    )
                    1 -> WizardStep2DatesAndFee(
                        annualFeeCents = annualFeeCents, onAnnualFeeChange = { annualFeeCents = it },
                        annualFeeDate = annualFeeDate, hasAnnualFeeDate = hasAnnualFeeDate,
                        onAnnualFeeDateSelected = { annualFeeDate = it; hasAnnualFeeDate = true },
                        strategy = strategy, onStrategyChange = { strategy = it },
                        suggestions = historySuggestions
                    )
                    2 -> WizardStep3WelcomeBonus(strategy = strategy, onStrategyChange = { strategy = it })
                    3 -> WizardStep4PerksAndMultipliers(
                        rewards = rewardList, onRewardsChange = { rewardList = it },
                        benefits = benefitList, onBenefitsChange = { benefitList = it }
                    )
                }
            }

            LumaWizardBottomBar(
                currentPage = pagerState.currentPage,
                pageCount = 4,
                onPrevious = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = (pagerState.currentPage - 1).coerceAtLeast(0),
                            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
                        )
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = (pagerState.currentPage + 1).coerceAtMost(3),
                            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
                        )
                    }
                },
                onSave = commitSave
            )
        } else {
            // Categorized Accordion Settings Mode for Existing Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        isWizardMode = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Launch Guided Setup Wizard", fontWeight = FontWeight.Bold)
                }

                // 1. Card Identity, Art & Credit Limit
                ExpandableSettingsCard(
                    title = "1. Card Identity, Artwork & Limits",
                    summary = listOfNotNull(
                        cardName.ifBlank { "Unnamed Card" },
                        network,
                        creditLimitCents.takeIf { it > 0L }?.let { "Limit ${MoneyUtils.formatCents(it, showCents = false)}" }
                    ).joinToString(" • "),
                    expanded = expandedSection == 1,
                    onToggle = { haptics.tick(); expandedSection = if (expandedSection == 1) 0 else 1 }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbitTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("Account Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        OrbitTextField(value = accountInstitution, onValueChange = { accountInstitution = it }, label = { Text("Institution") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbitTextField(value = cardName, onValueChange = { cardName = it }, label = { Text("Card Product") }, singleLine = true, modifier = Modifier.weight(1.2f))
                        OrbitTextField(value = issuerName, onValueChange = { issuerName = it }, label = { Text("Issuer") }, singleLine = true, modifier = Modifier.weight(0.8f))
                    }
                    CardArtworkAndNetworkSection(network = network, onNetworkChange = { network = it }, imageUrl = imageUrl, onImageUrlChange = { imageUrl = it }, cardName = cardName, issuerName = issuerName)
                    CurrencyTextField(cents = creditLimitCents, onCentsChange = { creditLimitCents = it }, label = "Credit Limit", modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbitTextField(value = statementClosingDayText, onValueChange = { statementClosingDayText = it }, label = { Text("Closing Day (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OrbitTextField(value = paymentDueDayText, onValueChange = { paymentDueDayText = it }, label = { Text("Due Day (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    }
                }

                // 2. Dates, Annual Fee & Chase 5/24
                ExpandableSettingsCard(
                    title = "2. Dates, Annual Fee & Chase 5/24",
                    summary = listOfNotNull(
                        strategy.cardholder ?: "No Owner",
                        strategy.openedDate?.let { "Opened $it" } ?: "No Open Date",
                        if (annualFeeCents > 0L) "${MoneyUtils.formatCents(annualFeeCents, showCents = false)}/yr" else "$0 Fee"
                    ).joinToString(" • "),
                    expanded = expandedSection == 2,
                    onToggle = { haptics.tick(); expandedSection = if (expandedSection == 2) 0 else 2 }
                ) {
                    SmartHistorySuggestionsBanner(
                        suggestions = historySuggestions,
                        currentOpenedDate = strategy.openedDate,
                        currentAnnualFeeDate = if (hasAnnualFeeDate) LocalDateUtils.formatIsoDate(annualFeeDate) else null,
                        onApplyOpenedDate = { strategy = strategy.copy(openedDate = it) },
                        onApplyFeeAndDate = { feeCents, dateLocal ->
                            if (feeCents != null) annualFeeCents = feeCents
                            annualFeeDate = dateLocal
                            hasAnnualFeeDate = true
                        }
                    )
                    CardholderAndBusinessFields(strategy = strategy, onStrategyChange = { strategy = it })
                    val openedLocal = LocalDateUtils.parseIsoDate(strategy.openedDate) ?: LocalDateUtils.todayLocalDate()
                    DateFieldButton(
                        date = openedLocal,
                        onDateSelected = { strategy = strategy.copy(openedDate = LocalDateUtils.formatIsoDate(it)) },
                        label = if (strategy.openedDate != null) "Sign-Up Date (${strategy.openedDate})" else "Select Sign-Up Date"
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) { CurrencyTextField(cents = annualFeeCents, onCentsChange = { annualFeeCents = it }, label = "Annual Fee") }
                        Box(modifier = Modifier.weight(1f)) { DateFieldButton(date = annualFeeDate, onDateSelected = { annualFeeDate = it; hasAnnualFeeDate = true }, label = "Fee Post Date") }
                    }
                }

                // 3. Welcome Bonus (SUB) & Cooldown
                ExpandableSettingsCard(
                    title = "3. Welcome Bonus (SUB) & Eligibility",
                    summary = strategy.subRewardText ?: if (strategy.subCompleted) "Bonus Earned" else "No active bonus",
                    expanded = expandedSection == 3,
                    onToggle = { haptics.tick(); expandedSection = if (expandedSection == 3) 0 else 3 }
                ) {
                    ProgressiveSubAndCooldownFields(strategy = strategy, onStrategyChange = { strategy = it })
                }

                // 4. Perks, Multipliers & Notes
                ExpandableSettingsCard(
                    title = "4. Statement Credits, Multipliers & Notes",
                    summary = "${benefitList.size} Perks • ${rewardList.size} Multipliers",
                    expanded = expandedSection == 4,
                    onToggle = { haptics.tick(); expandedSection = if (expandedSection == 4) 0 else 4 }
                ) {
                    CardBenefitsEditorSection(benefits = benefitList, onBenefitsChange = { benefitList = it })
                    RewardCategoriesEditorSection(rewards = rewardList, onRewardsChange = { rewardList = it })
                    AuthorizedUsersSection(users = authorizedUsers, onUsersChange = { authorizedUsers = it })
                    OrbitTextField(value = notes, onValueChange = { notes = it }, label = { Text("Card Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = commitSave, modifier = Modifier.weight(1f)) { Text("Save Profile") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
