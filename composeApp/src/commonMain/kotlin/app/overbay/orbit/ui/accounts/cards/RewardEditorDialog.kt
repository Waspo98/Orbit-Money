package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitSwitchGroupCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

private val COMMON_CATEGORIES = listOf(
    "Dining & Restaurants",
    "Groceries",
    "Travel & Flights",
    "Gas & Transit",
    "Streaming",
    "Drugstores",
    "Everything Else"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardEditorSubform(
    reward: RewardCategory,
    isNew: Boolean,
    onSave: (RewardCategory) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true
) {
    val haptics = LocalOrbitHaptics.current
    var label by remember(reward) { mutableStateOf(reward.label.ifBlank { reward.category }) }
    var category by remember(reward) { mutableStateOf(reward.category) }
    var multiplierText by remember(reward) { mutableStateOf(reward.multiplier.toString()) }
    var rateType by remember(reward) { mutableStateOf(reward.rateType) }
    var isRotating by remember(reward) { mutableStateOf(reward.isRotating) }
    var hasCap by remember(reward) { mutableStateOf((reward.capCents ?: 0L) > 0L) }
    var capCents by remember(reward) { mutableStateOf<Cents>(reward.capCents ?: 0L) }
    var notes by remember(reward) { mutableStateOf(reward.notes.orEmpty()) }

    val parsedMult = multiplierText.toDoubleOrNull()
    val isValid = label.isNotBlank() && parsedMult != null && parsedMult > 0.0

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitSheetHeader(
            title = if (isNew) "Add Earning Rate" else "Edit Earning Rate",
            subtitle = "Points multiplier or cash-back percentage tier",
            onNavigateBack = if (showBackButton) onDismiss else null,
            onDismiss = if (!showBackButton) onDismiss else null,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OrbitSectionCard(
                title = "Category & Multiplier",
                showHeaderDivider = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        OrbitFieldLabel("Quick-Pick Category")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            COMMON_CATEGORIES.forEach { cat ->
                                val sel = category.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        haptics.tick()
                                        category = cat
                                        if (label.isBlank() || COMMON_CATEGORIES.contains(label)) label = cat
                                    },
                                    label = {
                                        Text(
                                            text = cat,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    OrbitTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Display Label") },
                        placeholder = { Text("e.g. Dining, Flights via Portal, Catch-All") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OrbitTextField(
                            value = multiplierText,
                            onValueChange = { multiplierText = it },
                            label = { Text("Earn Rate") },
                            placeholder = { Text("3.0") },
                            suffixText = if (rateType == "percent") "%" else "×",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                        Column(modifier = Modifier.weight(1.1f)) {
                            OrbitFieldLabel("Reward Type")
                            OrbitSegmentedTabRow(
                                items = listOf("multiplier", "percent"),
                                selected = if (rateType == "percent") "percent" else "multiplier",
                                onSelect = { rateType = it },
                                label = { t -> if (t == "percent") "% Cash" else "× Points" }
                            )
                        }
                    }

                    OrbitTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("e.g. Excludes warehouse clubs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OrbitSwitchGroupCard {
                OrbitSwitchRow(
                    title = "Rotating Quarterly Category",
                    subtitle = "Activates on a quarterly issuer schedule",
                    checked = isRotating,
                    onCheckedChange = { isRotating = it },
                    showContainer = false
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                OrbitSwitchRow(
                    title = "Spending Cap",
                    subtitle = "Limit bonus earn up to a quarterly or annual cap",
                    checked = hasCap,
                    onCheckedChange = { hasCap = it },
                    showContainer = false
                )

                AnimatedVisibility(visible = hasCap) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    ) {
                        CurrencyTextField(
                            cents = capCents,
                            onCentsChange = { capCents = it },
                            label = "Spend Cap Amount",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        OrbitModalFooter(
            confirmText = "Save Rate",
            confirmEnabled = isValid,
            onConfirm = {
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
            onDismiss = onDismiss
        )
    }
}
