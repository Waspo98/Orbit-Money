package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField

@Composable
fun HouseholdMemberEditorSheet(
    member: HouseholdMember?,
    onDismiss: () -> Unit,
    onSave: (HouseholdMember) -> Unit
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss
    ) {
        HouseholdMemberEditorContent(
            member = member,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdMemberEditorContent(
    member: HouseholdMember?,
    onDismiss: () -> Unit,
    onSave: (HouseholdMember) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(member) { mutableStateOf(member?.name ?: "") }
    var role by remember(member) { mutableStateOf(member?.role ?: "adult") }
    var employer by remember(member) { mutableStateOf(member?.employer ?: "") }
    var jobTitle by remember(member) { mutableStateOf(member?.jobTitle ?: "") }
    var grossIncomeCents by remember(member) { mutableStateOf(member?.grossIncomeAnnualCents ?: 0L) }
    var retirementType by remember(member) { mutableStateOf(member?.retirementAccountType ?: "401k") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (member == null) "Add Household Member" else "Edit Member Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            placeholder = { Text("e.g. Alex Overbay") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Family Role",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val roles = listOf("adult" to "Adult / Partner", "child" to "Child", "dependent" to "Dependent")
            roles.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = role == key,
                    onClick = { role = key },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size),
                    icon = {},
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        CurrencyTextField(
            cents = grossIncomeCents,
            onCentsChange = { grossIncomeCents = it },
            label = "Gross Annual Income",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val saved = member?.copy(
                            name = name.trim(),
                            role = role,
                            employer = employer.ifBlank { null },
                            jobTitle = jobTitle.ifBlank { null },
                            grossIncomeAnnualCents = grossIncomeCents,
                            retirementAccountType = retirementType.ifBlank { null }
                        ) ?: HouseholdMember(
                            name = name.trim(),
                            role = role,
                            employer = employer.ifBlank { null },
                            jobTitle = jobTitle.ifBlank { null },
                            grossIncomeAnnualCents = grossIncomeCents,
                            retirementAccountType = retirementType.ifBlank { null }
                        )
                        onSave(saved)
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Save Member", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
