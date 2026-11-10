package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

val RETIREMENT_PLAN_OPTIONS = listOf(
    "none" to "None",
    "401k" to "401(k)",
    "403b" to "403(b)",
    "457b" to "457(b)",
    "ira" to "Traditional IRA",
    "roth_ira" to "Roth IRA",
    "hsa" to "HSA",
    "sep_ira" to "SEP IRA",
    "simple_ira" to "SIMPLE IRA",
    "pension" to "Pension",
    "other" to "Other"
)

val PAY_FREQUENCY_OPTIONS = listOf(
    Triple("weekly", "Weekly (52/yr)", 52.0),
    Triple("biweekly", "Biweekly (26/yr)", 26.0),
    Triple("semimonthly", "Semimonthly (24/yr)", 24.0),
    Triple("monthly", "Monthly (12/yr)", 12.0),
    Triple("annual", "Annual (1/yr)", 1.0),
    Triple("none", "None (0/yr)", 0.0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberCompensationFields(
    grossIncomeCents: Cents,
    onGrossIncomeChange: (Cents) -> Unit,
    netPayPerPeriodCents: Cents,
    onNetPayChange: (Cents) -> Unit,
    payFrequency: String,
    onPayFrequencyChange: (frequency: String, periodsPerYear: Double) -> Unit,
    retirementAccountType: String,
    onRetirementAccountTypeChange: (String) -> Unit,
    employeeContribPercent: Double,
    onEmployeeContribPercentChange: (Double) -> Unit,
    employeeContribAnnualCents: Cents,
    onEmployeeContribAnnualChange: (Cents) -> Unit,
    employerMatchPercent: Double,
    onEmployerMatchPercentChange: (Double) -> Unit,
    employerMatchLimitPercent: Double,
    onEmployerMatchLimitPercentChange: (Double) -> Unit,
    employerMatchAnnualCapCents: Cents,
    onEmployerMatchAnnualCapChange: (Cents) -> Unit,
    healthPremiumPerPaycheckCents: Cents,
    onHealthPremiumChange: (Cents) -> Unit,
    hsaContributionPerPaycheckCents: Cents,
    onHsaContributionChange: (Cents) -> Unit,
    dependentCareFsaPerPaycheckCents: Cents,
    onDependentCareFsaChange: (Cents) -> Unit,
    otherBenefitsPerPaycheckCents: Cents,
    onOtherBenefitsChange: (Cents) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    effectiveDate: LocalDate?,
    onEffectiveDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section: Pay Frequency & Compensation
        Text(
            text = "Compensation & Pay Frequency",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OrbitDropdownField(
            label = "Pay Frequency",
            selectedText = PAY_FREQUENCY_OPTIONS.find { it.first.equals(payFrequency, ignoreCase = true) }?.second ?: "Biweekly (26/yr)",
            options = PAY_FREQUENCY_OPTIONS,
            optionLabel = { it.second },
            onOptionSelected = {
                haptics.tick()
                onPayFrequencyChange(it.first, it.third)
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CurrencyTextField(
                cents = grossIncomeCents,
                onCentsChange = onGrossIncomeChange,
                label = "Annual Gross Income",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = netPayPerPeriodCents,
                onCentsChange = onNetPayChange,
                label = "Net Pay / Period",
                modifier = Modifier.weight(1f)
            )
        }

        // Section: Retirement Planning
        Text(
            text = "Retirement Contributions & Match",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OrbitDropdownField(
            label = "Primary Retirement Plan Type",
            selectedText = RETIREMENT_PLAN_OPTIONS.find { it.first.equals(retirementAccountType, ignoreCase = true) }?.second ?: "401(k)",
            options = RETIREMENT_PLAN_OPTIONS,
            optionLabel = { it.second },
            onOptionSelected = {
                haptics.tick()
                onRetirementAccountTypeChange(it.first)
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitTextField(
                value = if (employeeContribPercent > 0.0) employeeContribPercent.toString() else "",
                onValueChange = { onEmployeeContribPercentChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Employee %") },
                placeholder = { Text("e.g. 6.0") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            CurrencyTextField(
                cents = employeeContribAnnualCents,
                onCentsChange = onEmployeeContribAnnualChange,
                label = "Annual Fixed ($)",
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitTextField(
                value = if (employerMatchPercent > 0.0) employerMatchPercent.toString() else "",
                onValueChange = { onEmployerMatchPercentChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Match % (e.g. 100)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OrbitTextField(
                value = if (employerMatchLimitPercent > 0.0) employerMatchLimitPercent.toString() else "",
                onValueChange = { onEmployerMatchLimitPercentChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Match Limit % (e.g. 5)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        CurrencyTextField(
            cents = employerMatchAnnualCapCents,
            onCentsChange = onEmployerMatchAnnualCapChange,
            label = "Annual Employer Match Dollar Cap (Optional)",
            modifier = Modifier.fillMaxWidth()
        )

        // Section: Benefits & Deductions (Per-Paycheck direct entry)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Pre-Tax Benefits & Deductions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Enter each deduction as it appears on one paycheck. Orbit annualizes it from the pay frequency above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CurrencyTextField(
                cents = healthPremiumPerPaycheckCents,
                onCentsChange = onHealthPremiumChange,
                label = "Health Ins. / Paycheck",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = hsaContributionPerPaycheckCents,
                onCentsChange = onHsaContributionChange,
                label = "HSA / Paycheck",
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CurrencyTextField(
                cents = dependentCareFsaPerPaycheckCents,
                onCentsChange = onDependentCareFsaChange,
                label = "Dep Care FSA / Paycheck",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = otherBenefitsPerPaycheckCents,
                onCentsChange = onOtherBenefitsChange,
                label = "Other Pre-Tax / Paycheck",
                modifier = Modifier.weight(1f)
            )
        }

        // Section: Effective Date & Notes
        Text(
            text = "Effective Date & History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val currentDate = effectiveDate ?: LocalDateUtils.todayLocalDate()
        DateFieldButton(
            date = currentDate,
            onDateSelected = onEffectiveDateChange,
            label = "Compensation Effective Date",
            modifier = Modifier.fillMaxWidth()
        )

        OrbitTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notes & Compensation History Context") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
    }
}
