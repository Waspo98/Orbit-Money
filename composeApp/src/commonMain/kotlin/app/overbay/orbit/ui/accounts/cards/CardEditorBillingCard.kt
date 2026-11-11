package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchGroupCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Tab 2 of the Credit Card Editor: statement cycle days, credit limit, purchase APR,
 * annual fee tracking (with smart transaction history detection), and promotional APR.
 */
@Composable
fun CardEditorBillingTab(
    draft: CardEditorDraft,
    controller: CardEditorController,
    transactions: List<Transaction> = emptyList(),
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val historySuggestions = remember(draft.accountId, transactions, draft.annualFeeCents) {
        CreditCardStrategyEngine.detectHistorySuggestions(
            accountId = draft.accountId,
            transactions = transactions,
            templateAnnualFeeCents = draft.annualFeeCents?.takeIf { it > 0L }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OrbitSectionCard(
            title = "Statement & Credit Terms",
            subtitle = "Monthly cycle days, credit limit & standard APR",
            showHeaderDivider = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CurrencyTextField(
                            cents = draft.creditLimitCents ?: 0L,
                            onCentsChange = { cents ->
                                controller.updateDraft { d -> d.copy(creditLimitCents = cents.takeIf { it > 0L }) }
                            },
                            label = "Credit Limit"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OrbitTextField(
                            value = draft.aprText,
                            onValueChange = { txt -> controller.updateDraft { it.copy(aprText = txt) } },
                            label = { Text("Purchase APR") },
                            placeholder = { Text("24.99") },
                            suffixText = "%",
                            isError = draft.errors.containsKey("apr"),
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
                        value = draft.statementCloseDayText,
                        onValueChange = { txt ->
                            val digits = txt.filter(Char::isDigit).take(2)
                            controller.updateDraft {
                                it.copy(statementCloseDayText = digits, statementCloseDay = digits.toIntOrNull())
                            }
                        },
                        label = { Text("Statement Closes") },
                        placeholder = { Text("1–31") },
                        suffixText = "Day",
                        isError = draft.errors.containsKey("statementCloseDay"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OrbitTextField(
                        value = draft.paymentDueDayText,
                        onValueChange = { txt ->
                            val digits = txt.filter(Char::isDigit).take(2)
                            controller.updateDraft {
                                it.copy(paymentDueDayText = digits, paymentDueDay = digits.toIntOrNull())
                            }
                        },
                        label = { Text("Payment Due") },
                        placeholder = { Text("1–31") },
                        suffixText = "Day",
                        isError = draft.errors.containsKey("paymentDueDay"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OrbitSectionCard(
            title = "Annual Fee & Intro Offers",
            subtitle = "Recurring cardmember fee & promotional interest windows",
            showHeaderDivider = false
        ) {
            OrbitSwitchGroupCard {
                OrbitSwitchRow(
                    title = "Annual Fee",
                    subtitle = "Track yearly renewal & net perk value",
                    checked = draft.hasAnnualFee,
                    onCheckedChange = { chk -> controller.updateDraft { it.copy(hasAnnualFee = chk) } },
                    showContainer = false
                )

                AnimatedVisibility(visible = draft.hasAnnualFee) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        historySuggestions.suggestedAnnualFeeDate?.let { feeDate ->
                            val feeCents = historySuggestions.detectedAnnualFeeCents
                            AssistChip(
                                onClick = {
                                    haptics.confirm()
                                    controller.updateDraft {
                                        it.copy(
                                            annualFeeCents = feeCents ?: it.annualFeeCents,
                                            annualFeePostDate = feeDate
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        "Use detected fee: ${feeCents?.let { MoneyUtils.formatCents(it) } ?: ""} on ${CardFormat.formatCardDate(feeDate)}"
                                    )
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CurrencyTextField(
                                cents = draft.annualFeeCents ?: 0L,
                                onCentsChange = { c ->
                                    controller.updateDraft { it.copy(annualFeeCents = c.takeIf { it > 0L }) }
                                },
                                label = "Annual Fee",
                                modifier = Modifier.weight(1f)
                            )
                            DateFieldButton(
                                date = draft.annualFeePostDate?.let { LocalDateUtils.parseIsoDate(it) },
                                onDateSelected = { dt ->
                                    controller.updateDraft { it.copy(annualFeePostDate = LocalDateUtils.formatIsoDate(dt)) }
                                },
                                onClear = {
                                    controller.updateDraft { it.copy(annualFeePostDate = null) }
                                },
                                label = "Fee Post Date",
                                placeholder = "Not Set",
                                isError = draft.errors.containsKey("annualFeePostDate"),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                OrbitSwitchRow(
                    title = "Promotional / Intro APR",
                    subtitle = "Temporary 0% or reduced purchase interest",
                    checked = draft.hasPromo,
                    onCheckedChange = { chk -> controller.updateDraft { it.copy(hasPromo = chk) } },
                    showContainer = false
                )

                AnimatedVisibility(visible = draft.hasPromo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OrbitTextField(
                            value = draft.promoAprText,
                            onValueChange = { txt -> controller.updateDraft { it.copy(promoAprText = txt) } },
                            label = { Text("Promo APR") },
                            placeholder = { Text("0.0") },
                            suffixText = "%",
                            isError = draft.errors.containsKey("promoApr"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        DateFieldButton(
                            date = draft.promoEndDate?.let { LocalDateUtils.parseIsoDate(it) },
                            onDateSelected = { dt ->
                                controller.updateDraft { it.copy(promoEndDate = LocalDateUtils.formatIsoDate(dt)) }
                            },
                            label = "Promo Ends",
                            placeholder = "Select Date",
                            isError = draft.errors.containsKey("promoEndDate"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
