package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerkEditorDialog(
    benefit: CardBenefit,
    isNew: Boolean,
    onSave: (CardBenefit) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var title by remember { mutableStateOf(benefit.title) }
    var cadence by remember { mutableStateOf(benefit.cadence) }
    var anchor by remember { mutableStateOf(benefit.anchor) }
    var amountCents by remember { mutableStateOf(benefit.periodValueCents ?: benefit.annualCreditValueCents ?: 0L) }
    var description by remember { mutableStateOf(benefit.description.orEmpty()) }

    val isValid = title.isNotBlank() && amountCents > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Add Card Perk" else "Edit Card Perk",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrbitTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Perk Name (e.g. DoorDash, Uber Cash)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Cadence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BenefitCadence.entries.forEach { c ->
                        val sel = cadence == c
                        FilterChip(
                            selected = sel,
                            onClick = { haptics.tick(); cadence = c },
                            label = { Text(c.displayName) }
                        )
                    }
                }

                val cadenceLabel = when (cadence) {
                    BenefitCadence.MONTHLY -> "$ per month"
                    BenefitCadence.QUARTERLY -> "$ per quarter"
                    BenefitCadence.SEMI_ANNUAL -> "$ per half-year"
                    BenefitCadence.ANNUAL -> "$ per year"
                    BenefitCadence.EVERY_4_YEARS -> "$ per 4 years"
                }

                CurrencyTextField(
                    cents = amountCents,
                    onCentsChange = { amountCents = it },
                    label = cadenceLabel,
                    modifier = Modifier.fillMaxWidth()
                )

                if (cadence == BenefitCadence.ANNUAL) {
                    Text("Reset Anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = anchor == BenefitAnchor.CALENDAR,
                            onClick = { haptics.tick(); anchor = BenefitAnchor.CALENDAR },
                            label = { Text("Calendar Year") }
                        )
                        FilterChip(
                            selected = anchor == BenefitAnchor.CARDMEMBER_YEAR,
                            onClick = { haptics.tick(); anchor = BenefitAnchor.CARDMEMBER_YEAR },
                            label = { Text("Cardmember Year") }
                        )
                    }
                }

                OrbitTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / Restrictions (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    val resolvedId = if (benefit.id.isBlank()) CreditCardBenefitEngine.newBenefitId() else benefit.id
                    onSave(
                        benefit.copy(
                            id = resolvedId,
                            title = title.trim(),
                            cadence = cadence,
                            anchor = anchor,
                            periodValueCents = amountCents,
                            annualCreditValueCents = amountCents,
                            description = description.trim().ifBlank { null }
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}
