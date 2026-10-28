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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

@Composable
fun CreditCardProfileEditorSheet(
    profile: CreditCardProfile,
    onSave: (CreditCardProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        CreditCardProfileEditorContent(
            profile = profile,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun CreditCardProfileEditorContent(
    profile: CreditCardProfile,
    onSave: (CreditCardProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var cardName by remember(profile) { mutableStateOf(profile.cardName ?: "") }
    var issuerName by remember(profile) { mutableStateOf(profile.issuerName ?: "") }
    var network by remember(profile) { mutableStateOf(profile.network ?: "") }
    var annualFeeCents by remember(profile) { mutableStateOf(profile.annualFeeCents ?: 0L) }
    var annualFeeDate by remember(profile) {
        val parsed = profile.annualFeePostDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed ?: LocalDateUtils.todayLocalDate())
    }
    var hasAnnualFeeDate by remember(profile) { mutableStateOf(profile.annualFeePostDate != null) }
    var creditLimitCents by remember(profile) { mutableStateOf(profile.creditLimitCents ?: 0L) }
    var statementClosingDayText by remember(profile) { mutableStateOf(profile.statementClosingDay?.toString() ?: "") }
    var paymentDueDayText by remember(profile) { mutableStateOf(profile.paymentDueDay?.toString() ?: "") }

    // Rewards & benefits state
    var rewardList by remember(profile) { mutableStateOf(profile.rewardCategories) }
    var newRewardCat by remember(profile) { mutableStateOf("") }
    var newRewardMultiplier by remember(profile) { mutableStateOf("2.0") }

    var benefitList by remember(profile) { mutableStateOf(profile.benefits) }
    var newBenefitTitle by remember(profile) { mutableStateOf("") }
    var newBenefitAmountCents by remember(profile) { mutableStateOf(0L) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            Text(
                text = "Credit Card Perks & Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Card Name & Issuer
            OutlinedTextField(
                value = cardName,
                onValueChange = { cardName = it },
                label = { Text("Card Product Name (e.g. Sapphire Reserve)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = issuerName,
                    onValueChange = { issuerName = it },
                    label = { Text("Issuer (e.g. Chase)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = network,
                    onValueChange = { network = it },
                    label = { Text("Network (e.g. Visa)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

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
                OutlinedTextField(
                    value = statementClosingDayText,
                    onValueChange = { statementClosingDayText = it },
                    label = { Text("Closing Day (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = paymentDueDayText,
                    onValueChange = { paymentDueDayText = it },
                    label = { Text("Due Day (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Add Reward Category
            Text(
                text = "Reward Categories",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newRewardCat,
                    onValueChange = { newRewardCat = it },
                    label = { Text("Category (e.g. Dining)") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = newRewardMultiplier,
                    onValueChange = { newRewardMultiplier = it },
                    label = { Text("Multiplier (e.g. 3.0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val mult = newRewardMultiplier.toDoubleOrNull() ?: 1.0
                        if (newRewardCat.isNotBlank()) {
                            rewardList = rewardList + RewardCategory(category = newRewardCat.trim(), multiplier = mult)
                            newRewardCat = ""
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Add")
                }
            }

            // Quick Add Statement Credit Benefit
            Text(
                text = "Annual Statement Credits",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newBenefitTitle,
                    onValueChange = { newBenefitTitle = it },
                    label = { Text("Credit Name (e.g. Travel Credit)") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                Box(modifier = Modifier.weight(1f)) {
                    CurrencyTextField(
                        cents = newBenefitAmountCents,
                        onCentsChange = { newBenefitAmountCents = it },
                        label = "Amount"
                    )
                }
                Button(
                    onClick = {
                        val amtCents = if (newBenefitAmountCents > 0L) newBenefitAmountCents else null
                        if (newBenefitTitle.isNotBlank()) {
                            benefitList = benefitList + CardBenefit(
                                title = newBenefitTitle.trim(),
                                annualCreditValueCents = amtCents
                            )
                            newBenefitTitle = ""
                            newBenefitAmountCents = 0L
                        }
                    }
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            annualFeeCents = feeCents,
                            annualFeePostDate = feePostDate,
                            creditLimitCents = limitCents,
                            statementClosingDay = statementClosingDayText.toIntOrNull(),
                            paymentDueDay = paymentDueDayText.toIntOrNull(),
                            rewardCategories = rewardList,
                            benefits = benefitList
                        )
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Card Profile")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
}
