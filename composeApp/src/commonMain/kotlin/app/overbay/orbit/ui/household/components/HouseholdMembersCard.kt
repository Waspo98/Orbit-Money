package app.overbay.orbit.ui.household.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun HouseholdMembersCard(
    members: List<HouseholdMember>,
    totalGrossIncomeCents: Cents,
    onAddMember: () -> Unit,
    onEditMember: (HouseholdMember) -> Unit,
    onDeleteMember: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Household Members",
        subtitle = "Profiles, income earners & dependent planning",
        action = {
            Button(
                onClick = onAddMember,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Add Member", style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = modifier
    ) {
        // Income summary header
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Household Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = MoneyUtils.formatCents(totalGrossIncomeCents) + " / yr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${members.size} Member${if (members.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (members.isEmpty()) {
            Text(
                text = "No household members added yet. Tap '+ Add Member' above to record income earners or family members.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            members.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditMember(member) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = member.role.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val subtitleParts = buildList {
                            if (member.employer != null) add(member.employer)
                            if (member.jobTitle != null) add(member.jobTitle)
                            if (member.grossIncomeAnnualCents > 0L) {
                                add(MoneyUtils.formatCents(member.grossIncomeAnnualCents) + "/yr")
                            }
                        }
                        if (subtitleParts.isNotEmpty()) {
                            Text(
                                text = subtitleParts.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { onDeleteMember(member.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete member",
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
        }
    }
}
