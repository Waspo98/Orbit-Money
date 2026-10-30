package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
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

    // Benefits & notes state
    var healthPremiumPerMonthCents by remember(member) { mutableStateOf(member?.healthPremiumPerMonthCents ?: 0L) }
    var hsaContributionAnnualCents by remember(member) { mutableStateOf(member?.hsaContributionAnnualCents ?: 0L) }
    var dependentCareFsaAnnualCents by remember(member) { mutableStateOf(member?.dependentCareFsaAnnualCents ?: 0L) }
    var otherBenefitsAnnualCents by remember(member) { mutableStateOf(member?.otherBenefitsAnnualCents ?: 0L) }
    var notes by remember(member) { mutableStateOf(member?.notes ?: "") }
    var effectiveDate by remember { mutableStateOf<LocalDate?>(null) }

    // Linked accounts state
    var linkedAccounts by remember(member) {
        mutableStateOf(member?.retirementAccounts ?: emptyList())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (member == null) "Add Household Member" else "Edit Member Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

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
            "partner" to "Partner",
            "child" to "Child",
            "dependent" to "Dependent"
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
            "student" to "Student",
            "homemaker" to "Homemaker",
            "retired" to "Retired",
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
            healthPremiumPerMonthCents = healthPremiumPerMonthCents,
            onHealthPremiumChange = { healthPremiumPerMonthCents = it },
            hsaContributionAnnualCents = hsaContributionAnnualCents,
            onHsaContributionChange = { hsaContributionAnnualCents = it },
            dependentCareFsaAnnualCents = dependentCareFsaAnnualCents,
            onDependentCareFsaChange = { dependentCareFsaAnnualCents = it },
            otherBenefitsAnnualCents = otherBenefitsAnnualCents,
            onOtherBenefitsChange = { otherBenefitsAnnualCents = it },
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

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        haptics.tick()
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
                            healthPremiumPerMonthCents = healthPremiumPerMonthCents,
                            hsaContributionAnnualCents = hsaContributionAnnualCents,
                            dependentCareFsaAnnualCents = dependentCareFsaAnnualCents,
                            otherBenefitsAnnualCents = otherBenefitsAnnualCents,
                            notes = notes.ifBlank { null },
                            retirementAccounts = linkedAccounts
                        )
                        val effDateStr = effectiveDate?.toString()
                        onSave(updated, effDateStr)
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Save Member", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
