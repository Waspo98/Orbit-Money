package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

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
    healthPremiumPerMonthCents: Cents,
    onHealthPremiumChange: (Cents) -> Unit,
    hsaContributionAnnualCents: Cents,
    onHsaContributionChange: (Cents) -> Unit,
    dependentCareFsaAnnualCents: Cents,
    onDependentCareFsaChange: (Cents) -> Unit,
    otherBenefitsAnnualCents: Cents,
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

        val frequencies = listOf(
            Triple("weekly", "Weekly", 52.0),
            Triple("biweekly", "Biweekly", 26.0),
            Triple("semimonthly", "Semi-Mo", 24.0),
            Triple("monthly", "Monthly", 12.0)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            frequencies.forEachIndexed { index, (freqKey, label, periods) ->
                SegmentedButton(
                    selected = payFrequency.equals(freqKey, ignoreCase = true),
                    onClick = {
                        haptics.tick()
                        onPayFrequencyChange(freqKey, periods)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = frequencies.size),
                    icon = {},
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

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

        OrbitTextField(
            value = retirementAccountType,
            onValueChange = onRetirementAccountTypeChange,
            label = { Text("Primary Plan Type (e.g. 401k, 403b, Simple IRA)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
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
            OutlinedTextField(
                value = if (employerMatchPercent > 0.0) employerMatchPercent.toString() else "",
                onValueChange = { onEmployerMatchPercentChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Match % (e.g. 100)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
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

        // Section: Benefits & Deductions
        Text(
            text = "Pre-Tax Benefits & Deductions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CurrencyTextField(
                cents = healthPremiumPerMonthCents,
                onCentsChange = onHealthPremiumChange,
                label = "Health Premium / Mo",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = hsaContributionAnnualCents,
                onCentsChange = onHsaContributionChange,
                label = "HSA Contribution / Yr",
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CurrencyTextField(
                cents = dependentCareFsaAnnualCents,
                onCentsChange = onDependentCareFsaChange,
                label = "Dep Care FSA / Yr",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = otherBenefitsAnnualCents,
                onCentsChange = onOtherBenefitsChange,
                label = "Other Pre-Tax / Yr",
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
