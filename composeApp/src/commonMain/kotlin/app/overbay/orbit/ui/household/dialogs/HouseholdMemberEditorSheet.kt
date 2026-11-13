package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRetirementAccount
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.household.HouseholdCalculations
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

@Composable
fun HouseholdMemberEditorSheet(
    member: HouseholdMember?,
    availableAccounts: List<Account> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (HouseholdMember, String?) -> Unit
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss
    ) {
        HouseholdMemberEditorContent(
            member = member,
            availableAccounts = availableAccounts,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdMemberEditorContent(
    member: HouseholdMember?,
    availableAccounts: List<Account> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (HouseholdMember, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    // Identity state
    var name by remember(member) { mutableStateOf(member?.name ?: "") }
    var role by remember(member) { mutableStateOf(member?.role ?: "adult") }
    var birthDate by remember(member) {
        mutableStateOf(member?.birthDate?.let { LocalDateUtils.parseIsoDate(it) })
    }
    var employmentStatus by remember(member) { mutableStateOf(member?.employmentStatus ?: "employed") }
    var employer by remember(member) { mutableStateOf(member?.employer ?: "") }
    var jobTitle by remember(member) { mutableStateOf(member?.jobTitle ?: "") }

    // Compensation & retirement state
    var grossIncomeCents by remember(member) { mutableStateOf(member?.grossIncomeAnnualCents ?: 0L) }
    var netPayPerPeriodCents by remember(member) { mutableStateOf(member?.netPayPerPeriodCents ?: 0L) }
    var payFrequency by remember(member) { mutableStateOf(member?.payFrequency ?: "biweekly") }
    var payPeriodsPerYear by remember(member) { mutableStateOf(member?.payPeriodsPerYear ?: 26.0) }
    var retirementType by remember(member) { mutableStateOf(member?.retirementAccountType ?: "401k") }
    var employeeContribPercent by remember(member) { mutableStateOf(member?.employeeContributionPercent ?: 0.0) }
    var employeeContribAnnualCents by remember(member) { mutableStateOf(member?.employeeContributionAnnualCents ?: 0L) }
    var employerMatchPercent by remember(member) { mutableStateOf(member?.employerMatchPercent ?: 0.0) }
    var employerMatchLimitPercent by remember(member) { mutableStateOf(member?.employerMatchLimitPercent ?: 0.0) }
    var employerMatchAnnualCapCents by remember(member) { mutableStateOf(member?.employerMatchAnnualCapCents ?: 0L) }

    // Benefits & notes state (converted to/from per-paycheck entry)
    val initPeriods = HouseholdCalculations.safePayPeriods(member?.payFrequency ?: "biweekly")
    var healthPerPaycheckCents by remember(member) {
        mutableStateOf(
            if (member != null && member.healthPremiumPerMonthCents > 0L) {
                ((member.healthPremiumPerMonthCents.toDouble() * 12.0) / initPeriods).roundToLong()
            } else 0L
        )
    }
    var hsaPerPaycheckCents by remember(member) {
        mutableStateOf(
            if (member != null && member.hsaContributionAnnualCents > 0L) {
                (member.hsaContributionAnnualCents.toDouble() / initPeriods).roundToLong()
            } else 0L
        )
    }
    var fsaPerPaycheckCents by remember(member) {
        mutableStateOf(
            if (member != null && member.dependentCareFsaAnnualCents > 0L) {
                (member.dependentCareFsaAnnualCents.toDouble() / initPeriods).roundToLong()
            } else 0L
        )
    }
    var otherPerPaycheckCents by remember(member) {
        mutableStateOf(
            if (member != null && member.otherBenefitsAnnualCents > 0L) {
                (member.otherBenefitsAnnualCents.toDouble() / initPeriods).roundToLong()
            } else 0L
        )
    }
    var notes by remember(member) { mutableStateOf(member?.notes ?: "") }
    var effectiveDate by remember { mutableStateOf<LocalDate?>(null) }

    // Linked accounts state
    var linkedAccounts by remember(member) {
        mutableStateOf(member?.retirementAccounts ?: emptyList())
    }

    OrbitModalScaffold(
        onDismiss = onDismiss,
        confirmText = "Save Member",
        onConfirm = {
            if (name.isNotBlank()) {
                val periods = HouseholdCalculations.periodsPerYear(payFrequency).toDouble()
                val healthPremiumMonthly = if (periods > 0) ((healthPerPaycheckCents.toDouble() * periods) / 12.0).roundToLong() else 0L
                val hsaAnnual = (hsaPerPaycheckCents.toDouble() * periods).roundToLong()
                val fsaAnnual = (fsaPerPaycheckCents.toDouble() * periods).roundToLong()
                val otherAnnual = (otherPerPaycheckCents.toDouble() * periods).roundToLong()

                val updated = (member ?: HouseholdMember(name = name.trim())).copy(
                    name = name.trim(),
                    role = role,
                    birthDate = birthDate?.toString(),
                    employmentStatus = employmentStatus,
                    employer = employer.ifBlank { null },
                    jobTitle = jobTitle.ifBlank { null },
                    grossIncomeAnnualCents = grossIncomeCents,
                    netPayPerPeriodCents = netPayPerPeriodCents,
                    payFrequency = payFrequency,
                    payPeriodsPerYear = payPeriodsPerYear,
                    retirementAccountType = retirementType.ifBlank { null },
                    employeeContributionPercent = employeeContribPercent,
                    employeeContributionAnnualCents = employeeContribAnnualCents,
                    employerMatchPercent = employerMatchPercent,
                    employerMatchLimitPercent = employerMatchLimitPercent,
                    employerMatchAnnualCapCents = employerMatchAnnualCapCents,
                    healthPremiumPerMonthCents = healthPremiumMonthly,
                    hsaContributionAnnualCents = hsaAnnual,
                    dependentCareFsaAnnualCents = fsaAnnual,
                    otherBenefitsAnnualCents = otherAnnual,
                    notes = notes.ifBlank { null },
                    retirementAccounts = linkedAccounts
                )
                val effDateStr = effectiveDate?.toString()
                onSave(updated, effDateStr)
            }
        },
        modifier = modifier,
        title = if (member == null) "Add Household Member" else "Edit Member Profile",
        confirmEnabled = name.isNotBlank(),
        bodySpacing = 16.dp
    ) {

        // Section: Identity
        Text(
            text = "Personal Identity & Role",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            placeholder = { Text("e.g. Alex Overbay") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        val roles = listOf(
            "adult" to "Adult",
            "child" to "Child"
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            roles.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = role.equals(key, ignoreCase = true),
                    onClick = {
                        haptics.tick()
                        role = key
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size),
                    icon = {},
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        DateFieldButton(
            date = birthDate ?: LocalDate(1990, 1, 1),
            onDateSelected = { birthDate = it },
            label = "Birth Date (Optional)",
            modifier = Modifier.fillMaxWidth()
        )

        val employmentOptions = listOf(
            "employed" to "Employed",
            "self_employed" to "Self-Employed",
            "stay_at_home" to "Stay at Home",
            "student" to "Student",
            "retired" to "Retired",
            "unemployed" to "Unemployed",
            "other" to "Other"
        )
        OrbitDropdownField(
            label = "Employment Status",
            selectedText = employmentOptions.find { it.first == employmentStatus }?.second ?: "Employed",
            options = employmentOptions,
            optionLabel = { it.second },
            onOptionSelected = { employmentStatus = it.first }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitTextField(
                value = employer,
                onValueChange = { employer = it },
                label = { Text("Employer (Optional)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OrbitTextField(
                value = jobTitle,
                onValueChange = { jobTitle = it },
                label = { Text("Job Title (Optional)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // Section: Compensation & Retirement Fields
        MemberCompensationFields(
            grossIncomeCents = grossIncomeCents,
            onGrossIncomeChange = { grossIncomeCents = it },
            netPayPerPeriodCents = netPayPerPeriodCents,
            onNetPayChange = { netPayPerPeriodCents = it },
            payFrequency = payFrequency,
            onPayFrequencyChange = { freq, periods ->
                payFrequency = freq
                payPeriodsPerYear = periods
            },
            retirementAccountType = retirementType,
            onRetirementAccountTypeChange = { retirementType = it },
            employeeContribPercent = employeeContribPercent,
            onEmployeeContribPercentChange = { employeeContribPercent = it },
            employeeContribAnnualCents = employeeContribAnnualCents,
            onEmployeeContribAnnualChange = { employeeContribAnnualCents = it },
            employerMatchPercent = employerMatchPercent,
            onEmployerMatchPercentChange = { employerMatchPercent = it },
            employerMatchLimitPercent = employerMatchLimitPercent,
            onEmployerMatchLimitPercentChange = { employerMatchLimitPercent = it },
            employerMatchAnnualCapCents = employerMatchAnnualCapCents,
            onEmployerMatchAnnualCapChange = { employerMatchAnnualCapCents = it },
            healthPremiumPerPaycheckCents = healthPerPaycheckCents,
            onHealthPremiumChange = { healthPerPaycheckCents = it },
            hsaContributionPerPaycheckCents = hsaPerPaycheckCents,
            onHsaContributionChange = { hsaPerPaycheckCents = it },
            dependentCareFsaPerPaycheckCents = fsaPerPaycheckCents,
            onDependentCareFsaChange = { fsaPerPaycheckCents = it },
            otherBenefitsPerPaycheckCents = otherPerPaycheckCents,
            onOtherBenefitsChange = { otherPerPaycheckCents = it },
            notes = notes,
            onNotesChange = { notes = it },
            effectiveDate = effectiveDate,
            onEffectiveDateChange = { effectiveDate = it }
        )

        // Section: Linked Accounts
        MemberAccountLinkSection(
            linkedAccounts = linkedAccounts,
            availableAccounts = availableAccounts,
            onAddLink = { acctId, kind ->
                linkedAccounts = linkedAccounts.filterNot { it.accountId == acctId } +
                    HouseholdRetirementAccount(memberId = member?.id ?: 0L, accountId = acctId, accountKind = kind)
            },
            onUpdateLinkKind = { acctId, newKind ->
                linkedAccounts = linkedAccounts.map {
                    if (it.accountId == acctId) it.copy(accountKind = newKind) else it
                }
            },
            onRemoveLink = { acctId ->
                linkedAccounts = linkedAccounts.filterNot { it.accountId == acctId }
            }
        )
    }
}
