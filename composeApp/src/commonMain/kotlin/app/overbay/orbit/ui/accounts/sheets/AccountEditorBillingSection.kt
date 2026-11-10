package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.accounts.cards.CardEditorDraft
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitSectionCard
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
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        label = { Text("Purchase APR %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrbitTextField(
                    value = state.statementCloseDayText,
                    onValueChange = { if (it.length <= 2) state.statementCloseDayText = it.filter(Char::isDigit) },
                    label = { Text("Statement Close Day (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OrbitTextField(
                    value = state.paymentDueDayText,
                    onValueChange = { if (it.length <= 2) state.paymentDueDayText = it.filter(Char::isDigit) },
                    label = { Text("Payment Due Day (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OrbitSwitchRow(
                title = "Promotional / Intro APR",
                checked = state.hasPromo,
                onCheckedChange = { state.hasPromo = it }
            )
            if (state.hasPromo) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrbitTextField(
                        value = state.promoAprText,
                        onValueChange = { state.promoAprText = it },
                        label = { Text("Promo APR (e.g. 0.0%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        DateFieldButton(
                            date = state.promoEndDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                            onDateSelected = { dt -> state.promoEndDate = LocalDateUtils.formatIsoDate(dt) },
                            label = state.promoEndDate?.let { "Ends ${CardFormat.formatCardDate(it)}" } ?: "Promo End Date"
                        )
                    }
                }
            }

            OrbitSwitchRow(
                title = "Has Annual Fee",
                checked = state.hasAnnualFee,
                onCheckedChange = { state.hasAnnualFee = it }
            )
            if (state.hasAnnualFee) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CurrencyTextField(
                            cents = state.annualFeeCents,
                            onCentsChange = { state.annualFeeCents = it },
                            label = "Annual Fee"
                        )
                    }
                    Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        DateFieldButton(
                            date = state.annualFeePostDate?.let { LocalDateUtils.parseIsoDate(it) } ?: LocalDateUtils.todayLocalDate(),
                            onDateSelected = { dt -> state.annualFeePostDate = LocalDateUtils.formatIsoDate(dt) },
                            label = state.annualFeePostDate?.let { "Posts ${CardFormat.formatCardDate(it)}" } ?: "Fee Post Date"
                        )
                    }
                }
            }

            Text(
                text = "Statement credits, reward multipliers & 5/24 strategy are managed in Credit Card Tracker.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
