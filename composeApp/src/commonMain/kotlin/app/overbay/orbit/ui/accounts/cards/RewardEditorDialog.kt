package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

private val COMMON_CATEGORIES = listOf(
    "Dining & Restaurants", "Groceries", "Travel & Flights",
    "Gas & Transit", "Streaming & Entertainment", "Drugstores", "Everything Else"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardEditorDialog(
    reward: RewardCategory,
    isNew: Boolean,
    onSave: (RewardCategory) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var label by remember { mutableStateOf(reward.label.ifBlank { reward.category }) }
    var category by remember { mutableStateOf(reward.category) }
    var multiplierText by remember { mutableStateOf(reward.multiplier.toString()) }
    var rateType by remember { mutableStateOf(reward.rateType) }
    var isRotating by remember { mutableStateOf(reward.isRotating) }
    var hasCap by remember { mutableStateOf((reward.capCents ?: 0L) > 0L) }
    var capCents by remember { mutableStateOf<Cents>(reward.capCents ?: 0L) }
    var notes by remember { mutableStateOf(reward.notes.orEmpty()) }

    val parsedMult = multiplierText.toDoubleOrNull()
    val isValid = label.isNotBlank() && parsedMult != null && parsedMult > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Add Earning Rate" else "Edit Earning Rate",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Category Quick-Pick", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COMMON_CATEGORIES.forEach { cat ->
                        val sel = category.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = sel,
                            onClick = {
                                haptics.tick()
                                category = cat
                                if (label.isBlank() || COMMON_CATEGORIES.contains(label)) label = cat
                            },
                            label = { Text(cat) }
                        )
                    }
                }

                OrbitTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Display Label (e.g. Dining)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitTextField(
                        value = multiplierText,
                        onValueChange = { multiplierText = it },
                        label = { Text("Rate (e.g. 3.0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.2f)) {
                        SegmentedButton(
                            selected = rateType != "percent",
                            onClick = { haptics.tick(); rateType = "multiplier" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("× Points")
                        }
                        SegmentedButton(
                            selected = rateType == "percent",
                            onClick = { haptics.tick(); rateType = "percent" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("% Cash")
                        }
                    }
                }

                OrbitSwitchRow(
                    title = "Rotating Quarterly Category",
                    checked = isRotating,
                    onCheckedChange = { isRotating = it }
                )

                OrbitSwitchRow(
                    title = "Has Spending Cap",
                    checked = hasCap,
                    onCheckedChange = { hasCap = it }
                )
                if (hasCap) {
                    CurrencyTextField(
                        cents = capCents,
                        onCentsChange = { capCents = it },
                        label = "Quarterly / Annual Cap ($)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OrbitTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onSave(
                        reward.copy(
                            category = category.ifBlank { label.trim() },
                            label = label.trim(),
                            multiplier = parsedMult ?: 1.0,
                            rateType = rateType,
                            isRotating = isRotating,
                            capCents = if (hasCap && capCents > 0L) capCents else null,
                            notes = notes.trim().ifBlank { null }
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
