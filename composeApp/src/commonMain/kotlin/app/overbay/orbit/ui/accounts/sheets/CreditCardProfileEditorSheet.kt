package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

@Composable
fun CreditCardProfileEditorSheet(
    profile: CreditCardProfile,
    initialAccountName: String = "",
    initialInstitution: String = "",
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
    onSave: (CreditCardProfile, accountName: String, accountInstitution: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

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
    var notes by remember(profile) { mutableStateOf(profile.notes ?: "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Credit Card Perks & Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Account Name & Institution (persisted to Account)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbitTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Account Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OrbitTextField(
                value = accountInstitution,
                onValueChange = { accountInstitution = it },
                label = { Text("Institution") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Card Product Name & Issuer
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbitTextField(
                value = cardName,
                onValueChange = { cardName = it },
                label = { Text("Card Product (e.g. Sapphire Reserve)") },
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )
            OrbitTextField(
                value = issuerName,
                onValueChange = { issuerName = it },
                label = { Text("Issuer (e.g. Chase)") },
                singleLine = true,
                modifier = Modifier.weight(0.8f)
            )
        }

        // Network and Card Artwork URL with Presets
        CardArtworkAndNetworkSection(
            network = network,
            onNetworkChange = { network = it },
            imageUrl = imageUrl,
            onImageUrlChange = { imageUrl = it },
            cardName = cardName,
            issuerName = issuerName
        )

        // Credit Limit
        CurrencyTextField(
            cents = creditLimitCents,
            onCentsChange = { creditLimitCents = it },
            label = "Credit Limit",
            modifier = Modifier.fillMaxWidth()
        )

        // Annual Fee & Post Date
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                CurrencyTextField(
                    cents = annualFeeCents,
                    onCentsChange = { annualFeeCents = it },
                    label = "Annual Fee"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DateFieldButton(
                    date = annualFeeDate,
                    onDateSelected = {
                        annualFeeDate = it
                        hasAnnualFeeDate = true
                    },
                    label = "Fee Post Date"
                )
            }
        }

        // Statement Cycle Days
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbitTextField(
                value = statementClosingDayText,
                onValueChange = { statementClosingDayText = it },
                label = { Text("Closing Day (1-31)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OrbitTextField(
                value = paymentDueDayText,
                onValueChange = { paymentDueDayText = it },
                label = { Text("Due Day (1-31)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Authorized Users Section
        AuthorizedUsersSection(
            users = authorizedUsers,
            onUsersChange = { authorizedUsers = it }
        )

        // Reward Categories Section
        RewardCategoriesEditorSection(
            rewards = rewardList,
            onRewardsChange = { rewardList = it }
        )

        // Card Benefits Section
        CardBenefitsEditorSection(
            benefits = benefitList,
            onBenefitsChange = { benefitList = it }
        )

        // Notes
        OrbitTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Card Notes & Perks Summary") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    haptics.confirm()
                    val feeCents = if (annualFeeCents > 0L) annualFeeCents else null
                    val limitCents = if (creditLimitCents > 0L) creditLimitCents else null
                    val feePostDate = if (hasAnnualFeeDate || annualFeeCents > 0L) LocalDateUtils.formatIsoDate(annualFeeDate) else null

                    val updated = profile.copy(
                        cardName = cardName.trim().ifBlank { null },
                        issuerName = issuerName.trim().ifBlank { null },
                        network = network.trim().ifBlank { null },
                        imageUrl = imageUrl.trim().ifBlank { null },
                        annualFeeCents = feeCents,
                        annualFeePostDate = feePostDate,
                        creditLimitCents = limitCents,
                        statementClosingDay = statementClosingDayText.toIntOrNull(),
                        paymentDueDay = paymentDueDayText.toIntOrNull(),
                        authorizedUsers = authorizedUsers,
                        rewardCategories = rewardList,
                        benefits = benefitList,
                        notes = notes.trim().ifBlank { null }
                    )
                    onSave(updated, accountName.trim(), accountInstitution.trim())
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Profile")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
