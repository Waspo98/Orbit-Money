package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.domain.MhaTaxEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingTaxDeductionsStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var filingMenuOpen by remember { mutableStateOf(false) }
    var stateMenuOpen by remember { mutableStateOf(false) }
    val selectedStateLabel = remember(state.taxStateCode) {
        MhaTaxEngine.US_STATES.firstOrNull { it.first.equals(state.taxStateCode, ignoreCase = true) }
            ?.let { "${it.first} — ${it.second}" }
            ?: state.taxStateCode
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OnboardingHeroHeader(
            title = "Tax-Deductible Expense Style",
            subtitle = "Choose how you want Orbit to label and calculate your deductible expenses—switch anytime in Tax Rules.",
            icon = OrbitIcons.MhaTracker
        )

        TaxModeChoiceCard(
            title = "General Deductible Expenses",
            subtitle = "Great for charitable giving, HSA/medical receipts, business write-offs, or general itemized deductions.",
            selected = state.taxTrackingMode == TaxTrackingMode.GENERAL,
            onClick = {
                haptics.confirm()
                onIntent(OnboardingIntent.UpdateTaxTrackingMode(TaxTrackingMode.GENERAL))
            }
        )

        TaxModeChoiceCard(
            title = "Ministerial Housing Allowance (MHA)",
            subtitle = "Tailored for IRS Section 107 clergy housing allowance tracking, including annual designation cap and fair market rental value.",
            selected = state.taxTrackingMode == TaxTrackingMode.MHA,
            onClick = {
                haptics.confirm()
                onIntent(OnboardingIntent.UpdateTaxTrackingMode(TaxTrackingMode.MHA))
            }
        )

        OnboardingCardContainer {
            Text(
                text = "Optional Tax Profile",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Helps estimate your federal and state tax savings. Leave anything blank if you'd rather configure it later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                TaxPickerField(
                    label = "Filing Status",
                    value = state.taxFilingStatus.displayName,
                    onClick = { haptics.tick(); filingMenuOpen = true }
                )
                DropdownMenu(expanded = filingMenuOpen, onDismissRequest = { filingMenuOpen = false }) {
                    FilingStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                filingMenuOpen = false
                                onIntent(OnboardingIntent.UpdateTaxFilingStatus(status))
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                TaxPickerField(
                    label = "State",
                    value = selectedStateLabel,
                    onClick = { haptics.tick(); stateMenuOpen = true }
                )
                DropdownMenu(expanded = stateMenuOpen, onDismissRequest = { stateMenuOpen = false }) {
                    MhaTaxEngine.US_STATES.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text("$code — $name") },
                            onClick = {
                                stateMenuOpen = false
                                onIntent(OnboardingIntent.UpdateTaxState(code))
                            }
                        )
                    }
                }
            }

            OrbitAccordionVisibility(visible = state.taxTrackingMode == TaxTrackingMode.MHA) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CurrencyTextField(
                        cents = state.taxCapCents ?: 0L,
                        onCentsChange = { c -> onIntent(OnboardingIntent.UpdateTaxCap(c.takeIf { it > 0L })) },
                        label = "Annual Designated Cap (Optional, $)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    CurrencyTextField(
                        cents = state.taxFmrvCents ?: 0L,
                        onCentsChange = { c -> onIntent(OnboardingIntent.UpdateTaxFmrv(c.takeIf { it > 0L })) },
                        label = "Fair Market Rental Value (Optional, $)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun TaxModeChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.75.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun TaxPickerField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
