package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.accounts.cards.CardEditorDraft
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchGroupCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField

class AccountBillingFieldsState(
    initialProfile: CreditCardProfile?,
    private val accountId: Long
) {
    var creditLimitCents by mutableStateOf(initialProfile?.creditLimitCents ?: 0L)
    var statementCloseDayText by mutableStateOf(initialProfile?.statementClosingDay?.toString().orEmpty())
    var paymentDueDayText by mutableStateOf(initialProfile?.paymentDueDay?.toString().orEmpty())
    var aprText by mutableStateOf(CardEditorDraft.formatBpsForInput(initialProfile?.aprBps))
    var hasPromo by mutableStateOf(initialProfile?.promoAprBps != null || !initialProfile?.promoEndDate.isNullOrBlank())
    var promoAprText by mutableStateOf(CardEditorDraft.formatBpsForInput(initialProfile?.promoAprBps))
    var promoEndDate by mutableStateOf(initialProfile?.promoEndDate)
    var hasAnnualFee by mutableStateOf((initialProfile?.annualFeeCents ?: 0L) > 0L || !initialProfile?.annualFeePostDate.isNullOrBlank())
    var annualFeeCents by mutableStateOf(initialProfile?.annualFeeCents ?: 0L)
    var annualFeePostDate by mutableStateOf(initialProfile?.annualFeePostDate)

    val closeDayError: Boolean
        get() = statementCloseDayText.trim().let { it.isNotEmpty() && it.toIntOrNull()?.let { d -> d in 1..31 } != true }
    val dueDayError: Boolean
        get() = paymentDueDayText.trim().let { it.isNotEmpty() && it.toIntOrNull()?.let { d -> d in 1..31 } != true }
    val aprError: Boolean
        get() = aprText.isNotBlank() && CardEditorDraft.parseBps(aprText) == null
    val promoAprError: Boolean
        get() = hasPromo && promoAprText.isNotBlank() && CardEditorDraft.parseBps(promoAprText) == null
    val isValid: Boolean
        get() = !closeDayError && !dueDayError && !aprError && !promoAprError
    val firstErrorMessage: String?
        get() = when {
            aprError -> "Purchase APR must be between 0% and 100%"
            closeDayError -> "Statement closing day must be between 1 and 31"
            dueDayError -> "Payment due day must be between 1 and 31"
            promoAprError -> "Promo APR must be between 0% and 100%"
            else -> null
        }

    fun toUpdatedProfile(
        existing: CreditCardProfile?,
        accountName: String,
        institution: String?
    ): CreditCardProfile {
        val base = existing ?: CreditCardProfile(accountId = accountId)
        val closeDay = statementCloseDayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
        val dueDay = paymentDueDayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
        val aprBps = aprText.takeIf { it.isNotBlank() }?.let { CardEditorDraft.parseBps(it) }
        val resolvedPromoBps = if (hasPromo) promoAprText.takeIf { it.isNotBlank() }?.let { CardEditorDraft.parseBps(it) } else null
        val resolvedPromoEnd = if (hasPromo) promoEndDate?.trim()?.ifBlank { null } else null
        val resolvedFeeCents = if (hasAnnualFee && annualFeeCents > 0L) annualFeeCents else null
        val resolvedFeeDate = if (hasAnnualFee) annualFeePostDate?.trim()?.ifBlank { null } else null

        return base.copy(
            accountId = accountId,
            cardName = base.cardName ?: accountName.trim().ifBlank { null },
            issuerName = base.issuerName ?: institution?.trim()?.ifBlank { null },
            creditLimitCents = creditLimitCents.takeIf { it > 0L },
            statementClosingDay = closeDay,
            paymentDueDay = dueDay,
            aprBps = aprBps,
            promoAprBps = resolvedPromoBps,
            promoEndDate = resolvedPromoEnd,
            annualFeeCents = resolvedFeeCents,
            annualFeePostDate = resolvedFeeDate
        )
    }
}

@Composable
fun AccountEditorBillingSection(
    state: AccountBillingFieldsState,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Credit Card Billing & Terms",
        subtitle = "Statement cycle, credit limit, interest rate & annual fee",
        showHeaderDivider = false,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CurrencyTextField(
                        cents = state.creditLimitCents,
                        onCentsChange = { state.creditLimitCents = it },
                        label = "Credit Limit"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OrbitTextField(
                        value = state.aprText,
                        onValueChange = { state.aprText = it },
                        label = { Text("Purchase APR") },
                        placeholder = { Text("24.99") },
                        suffixText = "%",
                        isError = state.aprError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitTextField(
                    value = state.statementCloseDayText,
                    onValueChange = { state.statementCloseDayText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Statement Closes") },
                    placeholder = { Text("1–31") },
                    suffixText = "Day",
                    isError = state.closeDayError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OrbitTextField(
                    value = state.paymentDueDayText,
                    onValueChange = { state.paymentDueDayText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Payment Due") },
                    placeholder = { Text("1–31") },
                    suffixText = "Day",
                    isError = state.dueDayError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OrbitSwitchGroupCard {
                OrbitSwitchRow(
                    title = "Promotional / Intro APR",
                    subtitle = "Temporary 0% or reduced purchase interest",
                    checked = state.hasPromo,
                    onCheckedChange = { state.hasPromo = it },
                    showContainer = false
                )

                OrbitAccordionVisibility(visible = state.hasPromo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OrbitTextField(
                            value = state.promoAprText,
                            onValueChange = { state.promoAprText = it },
                            label = { Text("Promo APR") },
                            placeholder = { Text("0.0") },
                            suffixText = "%",
                            isError = state.promoAprError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        DateFieldButton(
                            date = state.promoEndDate?.let { LocalDateUtils.parseIsoDate(it) },
                            onDateSelected = { dt -> state.promoEndDate = LocalDateUtils.formatIsoDate(dt) },
                            label = "Promo Ends",
                            placeholder = "Select Date",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                OrbitSwitchRow(
                    title = "Annual Fee",
                    subtitle = "Recurring cardmember renewal fee",
                    checked = state.hasAnnualFee,
                    onCheckedChange = { state.hasAnnualFee = it },
                    showContainer = false
                )

                OrbitAccordionVisibility(visible = state.hasAnnualFee) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CurrencyTextField(
                            cents = state.annualFeeCents,
                            onCentsChange = { state.annualFeeCents = it },
                            label = "Annual Fee",
                            modifier = Modifier.weight(1f)
                        )
                        DateFieldButton(
                            date = state.annualFeePostDate?.let { LocalDateUtils.parseIsoDate(it) },
                            onDateSelected = { dt -> state.annualFeePostDate = LocalDateUtils.formatIsoDate(dt) },
                            onClear = { state.annualFeePostDate = null },
                            label = "Fee Post Date",
                            placeholder = "Not Set",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "Statement credits, reward multipliers & 5/24 strategy are managed via Edit Card.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
