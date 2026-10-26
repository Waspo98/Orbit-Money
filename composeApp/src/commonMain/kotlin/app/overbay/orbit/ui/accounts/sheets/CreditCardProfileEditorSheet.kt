package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardProfileEditorSheet(
    profile: CreditCardProfile,
    onSave: (CreditCardProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var cardName by remember { mutableStateOf(profile.cardName ?: "") }
    var issuerName by remember { mutableStateOf(profile.issuerName ?: "") }
    var network by remember { mutableStateOf(profile.network ?: "") }
    var annualFeeText by remember { mutableStateOf(if (profile.annualFeeCents != null) (profile.annualFeeCents!! / 100.0).toString() else "") }
    var annualFeePostDate by remember { mutableStateOf(profile.annualFeePostDate ?: "") }
    var creditLimitText by remember { mutableStateOf(if (profile.creditLimitCents != null) (profile.creditLimitCents!! / 100.0).toString() else "") }
    var statementClosingDayText by remember { mutableStateOf(profile.statementClosingDay?.toString() ?: "") }
    var paymentDueDayText by remember { mutableStateOf(profile.paymentDueDay?.toString() ?: "") }

    // Rewards & benefits state
    var rewardList by remember { mutableStateOf(profile.rewardCategories) }
    var newRewardCat by remember { mutableStateOf("") }
    var newRewardMultiplier by remember { mutableStateOf("2.0") }

    var benefitList by remember { mutableStateOf(profile.benefits) }
    var newBenefitTitle by remember { mutableStateOf("") }
    var newBenefitAmount by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
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
            OutlinedTextField(
                value = creditLimitText,
                onValueChange = { creditLimitText = it },
                label = { Text("Credit Limit ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Annual Fee & Post Date
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = annualFeeText,
                    onValueChange = { annualFeeText = it },
                    label = { Text("Annual Fee ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = annualFeePostDate,
                    onValueChange = { annualFeePostDate = it },
                    label = { Text("Fee Post Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newBenefitTitle,
                    onValueChange = { newBenefitTitle = it },
                    label = { Text("Credit Name (e.g. Travel Credit)") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = newBenefitAmount,
                    onValueChange = { newBenefitAmount = it },
                    label = { Text("Amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val amtCents = if (newBenefitAmount.isNotBlank()) MoneyUtils.dollarsToCents(newBenefitAmount) else null
                        if (newBenefitTitle.isNotBlank()) {
                            benefitList = benefitList + CardBenefit(
                                title = newBenefitTitle.trim(),
                                annualCreditValueCents = amtCents
                            )
                            newBenefitTitle = ""
                            newBenefitAmount = ""
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
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
                        val feeCents = if (annualFeeText.isNotBlank()) MoneyUtils.dollarsToCents(annualFeeText) else null
                        val limitCents = if (creditLimitText.isNotBlank()) MoneyUtils.dollarsToCents(creditLimitText) else null

                        val updated = profile.copy(
                            cardName = cardName.trim().ifBlank { null },
                            issuerName = issuerName.trim().ifBlank { null },
                            network = network.trim().ifBlank { null },
                            annualFeeCents = feeCents,
                            annualFeePostDate = annualFeePostDate.trim().ifBlank { null },
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
}
