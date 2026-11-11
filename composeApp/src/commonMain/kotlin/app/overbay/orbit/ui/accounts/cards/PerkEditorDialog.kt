package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerkEditorSubform(
    benefit: CardBenefit,
    isNew: Boolean,
    onSave: (CardBenefit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true
) {
    val haptics = LocalOrbitHaptics.current
    var title by remember(benefit.id) { mutableStateOf(benefit.title) }
    var cadence by remember(benefit.id) { mutableStateOf(benefit.cadence) }
    var anchor by remember(benefit.id) { mutableStateOf(benefit.anchor) }
    var amountCents by remember(benefit.id) {
        mutableStateOf(benefit.periodValueCents ?: benefit.annualCreditValueCents ?: 0L)
    }
    var description by remember(benefit.id) { mutableStateOf(benefit.description.orEmpty()) }

    val isValid = title.isNotBlank() && amountCents > 0L

    val cadenceAmountLabel = when (cadence) {
        BenefitCadence.MONTHLY -> "Credit per Month"
        BenefitCadence.QUARTERLY -> "Credit per Quarter"
        BenefitCadence.SEMI_ANNUAL -> "Credit per Half-Year"
        BenefitCadence.ANNUAL -> "Credit per Year"
        BenefitCadence.EVERY_4_YEARS -> "Credit per 4 Years"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitSheetHeader(
            title = if (isNew) "Add Card Perk" else "Edit Card Perk",
            subtitle = "Recurring statement credit or cardmember benefit",
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
                title = "Perk Details",
                showHeaderDivider = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrbitTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Perk Name") },
                        placeholder = { Text("e.g. Uber Cash, Dining Credit, Travel Credit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        OrbitFieldLabel("Reset Cadence")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BenefitCadence.entries.forEach { c ->
                                val sel = cadence == c
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        haptics.tick()
                                        cadence = c
                                    },
                                    label = {
                                        Text(
                                            text = c.displayName,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    CurrencyTextField(
                        cents = amountCents,
                        onCentsChange = { amountCents = it },
                        label = cadenceAmountLabel,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cadence == BenefitCadence.ANNUAL) {
                        Column {
                            OrbitFieldLabel("Annual Reset Schedule")
                            OrbitSegmentedTabRow(
                                items = listOf(BenefitAnchor.CALENDAR, BenefitAnchor.CARDMEMBER_YEAR),
                                selected = anchor,
                                onSelect = { anchor = it },
                                label = { a ->
                                    if (a == BenefitAnchor.CALENDAR) "Calendar Year" else "Cardmember Year"
                                }
                            )
                        }
                    }

                    OrbitTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Notes / Restrictions (Optional)") },
                        placeholder = { Text("e.g. Enrollment required, valid on U.S. orders") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        OrbitModalFooter(
            confirmText = "Save Perk",
            confirmEnabled = isValid,
            onConfirm = {
                val resolvedId = if (benefit.id.isBlank()) CreditCardBenefitEngine.newBenefitId() else benefit.id
                onSave(
                    benefit.copy(
                        id = resolvedId,
                        title = title.trim(),
                        cadence = cadence,
                        anchor = anchor,
                        periodValueCents = amountCents,
                        annualCreditValueCents = null,
                        description = description.trim().ifBlank { null }
                    )
                )
            },
            onDismiss = onDismiss
        )
    }
}
