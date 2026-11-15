package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
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
    var currentUsedCents by rememberSaveable(title, periodLabel, initialUsedCents) { mutableStateOf(initialUsedCents) }
    val remainingCents = (valueCents - currentUsedCents).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (valueCents > 0L) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Period Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(MoneyUtils.formatCents(valueCents), style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = MoneyUtils.formatCents(remainingCents),
                                    style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = if (remainingCents == 0L) OrbitTheme.colors.income else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                CurrencyTextField(
                    cents = currentUsedCents,
                    onCentsChange = { currentUsedCents = it },
                    label = "Amount Used"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (valueCents > 0L) {
                        if (valueCents >= 200L) {
                            OrbitActionChip(
                                label = "50%",
                                onClick = {
                                    haptics.tick()
                                    currentUsedCents = valueCents / 2L
                                }
                            )
                        }
                        OrbitActionChip(
                            label = "Full Amount",
                            onClick = {
                                haptics.tick()
                                currentUsedCents = valueCents
                            }
                        )
                    }
                    OrbitActionChip(
                        label = "Clear",
                        onClick = {
                            haptics.tick()
                            currentUsedCents = 0L
                        }
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
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save Usage")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}
