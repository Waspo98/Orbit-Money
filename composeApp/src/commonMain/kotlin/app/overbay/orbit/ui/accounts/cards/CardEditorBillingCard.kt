package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField

/**
 * Billing & statement cycle editor card embedded inside [CardEditorFormSections]
 * so credit limit, statement closing day, payment due day, and APR are directly editable
 * from the unified Credit Card detail & editor flow.
 */
@Composable
fun CardEditorBillingCard(
    draft: CardEditorDraft,
    controller: CardEditorController,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "3. Billing & Statement Cycle",
        subtitle = "Credit limit, closing/due days & APR",
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CurrencyTextField(
                        cents = draft.creditLimitCents ?: 0L,
                        onCentsChange = { cents ->
                            controller.updateDraft { d ->
                                d.copy(
                                    creditLimitCents = cents.takeIf { it > 0L },
                                    creditLimitText = if (cents > 0L) (cents / 100).toString() else ""
                                )
                            }
                        },
                        label = "Credit Limit"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OrbitTextField(
                        value = draft.aprText,
                        onValueChange = { txt -> controller.updateDraft { it.copy(aprText = txt) } },
                        label = { Text("Purchase APR %") },
                        isError = draft.errors.containsKey("apr"),
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
                    value = draft.statementCloseDayText,
                    onValueChange = { txt ->
                        if (txt.length <= 2) {
                            val digits = txt.filter(Char::isDigit)
                            controller.updateDraft {
                                it.copy(statementCloseDayText = digits, statementCloseDay = digits.toIntOrNull())
                            }
                        }
                    },
                    label = { Text("Statement Close Day (1-31)") },
                    isError = draft.errors.containsKey("statementCloseDay"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OrbitTextField(
                    value = draft.paymentDueDayText,
                    onValueChange = { txt ->
                        if (txt.length <= 2) {
                            val digits = txt.filter(Char::isDigit)
                            controller.updateDraft {
                                it.copy(paymentDueDayText = digits, paymentDueDay = digits.toIntOrNull())
                            }
                        }
                    },
                    label = { Text("Payment Due Day (1-31)") },
                    isError = draft.errors.containsKey("paymentDueDay"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OrbitSwitchRow(
                title = "Promotional / Intro APR",
                checked = draft.hasPromo,
                onCheckedChange = { chk -> controller.updateDraft { it.copy(hasPromo = chk) } }
            )
            if (draft.hasPromo) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrbitTextField(
                        value = draft.promoAprText,
                        onValueChange = { txt -> controller.updateDraft { it.copy(promoAprText = txt) } },
                        label = { Text("Promo APR % (e.g. 0.0)") },
                        isError = draft.errors.containsKey("promoApr"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        DateFieldButton(
                            date = draft.promoEndDate?.let { LocalDateUtils.parseIsoDate(it) }
                                ?: LocalDateUtils.todayLocalDate(),
                            onDateSelected = { dt ->
                                controller.updateDraft { it.copy(promoEndDate = LocalDateUtils.formatIsoDate(dt)) }
                            },
                            label = draft.promoEndDate?.let { "Ends (${CardFormat.formatCardDate(it)})" }
                                ?: "Promo End Date"
                        )
                    }
                }
            }
        }
    }
}
