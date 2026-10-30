package app.overbay.orbit.ui.household.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdIncomeRecord
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

@Composable
fun HouseholdIncomeHistoryCard(
    incomeRecords: List<HouseholdIncomeRecord>,
    members: List<HouseholdMember>,
    onAddSnapshot: (HouseholdIncomeRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showAddDialog by remember { mutableStateOf(false) }
    val memberMap = members.associateBy { it.id }
    val sortedRecords = incomeRecords.sortedByDescending { it.effectiveDate }

    OrbitSectionCard(
        title = "Income & Compensation History",
        subtitle = "Dated compensation adjustments and pay raise milestones",
        action = {
            if (members.isNotEmpty()) {
                Button(
                    onClick = {
                        haptics.tick()
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Add Snapshot", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        modifier = modifier
    ) {
        if (sortedRecords.isEmpty()) {
            EmptyStateCard(
                title = "No Income History Snapshots",
                description = "Record compensation changes, annual merit increases, or new job offers to track wage progression over time."
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                sortedRecords.forEach { record ->
                    val memberName = memberMap[record.memberId]?.name ?: "Member #${record.memberId}"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = memberName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Effective ${record.effectiveDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${MoneyUtils.formatCents(record.grossIncomeAnnualCents, showCents = false)} / yr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (record.netPayPerPeriodCents > 0L) {
                                        Text(
                                            text = "${MoneyUtils.formatCents(record.netPayPerPeriodCents, showCents = false)} / period",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            if (!record.notes.isNullOrBlank()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = record.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && members.isNotEmpty()) {
        AddIncomeSnapshotSheet(
            members = members,
            onDismiss = { showAddDialog = false },
            onSave = {
                onAddSnapshot(it)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIncomeSnapshotSheet(
    members: List<HouseholdMember>,
    onDismiss: () -> Unit,
    onSave: (HouseholdIncomeRecord) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var selectedMember by remember { mutableStateOf(members.first()) }
    var effectiveDate by remember { mutableStateOf(LocalDateUtils.todayLocalDate()) }
    var grossCents by remember(selectedMember) { mutableStateOf(selectedMember.grossIncomeAnnualCents) }
    var netCents by remember(selectedMember) { mutableStateOf(selectedMember.netPayPerPeriodCents) }
    var notes by remember { mutableStateOf("") }

    OrbitModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add Income Snapshot",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OrbitDropdownField(
                label = "Household Member",
                selectedText = selectedMember.name,
                options = members,
                optionLabel = { it.name },
                onOptionSelected = {
                    selectedMember = it
                    grossCents = it.grossIncomeAnnualCents
                    netCents = it.netPayPerPeriodCents
                }
            )

            DateFieldButton(
                date = effectiveDate,
                onDateSelected = { effectiveDate = it },
                label = "Effective Date"
            )

            CurrencyTextField(
                cents = grossCents,
                onCentsChange = { grossCents = it },
                label = "Annual Gross Income"
            )

            CurrencyTextField(
                cents = netCents,
                onCentsChange = { netCents = it },
                label = "Net Pay Per Period"
            )

            OrbitTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Milestone Notes (e.g. Promotion, Annual Merit)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.size(10.dp))
                Button(
                    onClick = {
                        haptics.tick()
                        val record = HouseholdIncomeRecord(
                            householdId = selectedMember.householdId,
                            memberId = selectedMember.id,
                            effectiveDate = effectiveDate.toString(),
                            grossIncomeAnnualCents = grossCents,
                            netPayPerPeriodCents = netCents,
                            payFrequency = selectedMember.payFrequency,
                            payPeriodsPerYear = selectedMember.payPeriodsPerYear,
                            employeeContributionPercent = selectedMember.employeeContributionPercent ?: 0.0,
                            employeeContributionAnnualCents = selectedMember.employeeContributionAnnualCents,
                            employerMatchPercent = selectedMember.employerMatchPercent,
                            employerMatchLimitPercent = selectedMember.employerMatchLimitPercent,
                            employerMatchAnnualCapCents = selectedMember.employerMatchAnnualCapCents,
                            healthPremiumPerMonthCents = selectedMember.healthPremiumPerMonthCents,
                            hsaContributionAnnualCents = selectedMember.hsaContributionAnnualCents,
                            dependentCareFsaAnnualCents = selectedMember.dependentCareFsaAnnualCents,
                            otherBenefitsAnnualCents = selectedMember.otherBenefitsAnnualCents,
                            source = "manual",
                            notes = notes.ifBlank { null }
                        )
                        onSave(record)
                    }
                ) {
                    Text("Save Snapshot")
                }
            }
        }
    }
}
