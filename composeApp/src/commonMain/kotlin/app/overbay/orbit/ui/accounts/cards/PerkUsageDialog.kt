package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun PerkUsageDialog(
    title: String,
    periodLabel: String,
    valueCents: Cents,
    initialUsedCents: Cents,
    onSave: (Cents?) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var currentUsedCents by rememberSaveable { mutableStateOf(initialUsedCents) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit Perk Usage",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$title \u00B7 $periodLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (valueCents > 0L) {
                    Text(
                        text = "Total period value: ${MoneyUtils.formatCents(valueCents)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                CurrencyTextField(
                    cents = currentUsedCents,
                    onCentsChange = { currentUsedCents = it },
                    label = "Amount Used"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (valueCents > 0L) {
                        AssistChip(
                            onClick = {
                                haptics.tick()
                                currentUsedCents = valueCents
                            },
                            label = { Text("Full Amount") }
                        )
                    }
                    AssistChip(
                        onClick = {
                            haptics.tick()
                            currentUsedCents = 0L
                        },
                        label = { Text("Clear") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    val toSave = if (currentUsedCents <= 0L) null else currentUsedCents
                    onSave(toSave)
                    onDismiss()
                }
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
