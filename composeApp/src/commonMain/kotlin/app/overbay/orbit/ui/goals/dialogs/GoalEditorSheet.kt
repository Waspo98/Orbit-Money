package app.overbay.orbit.ui.goals.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    goal: Goal?,
    accounts: List<Account>,
    onSave: (Goal) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = goal != null && goal.id > 0L

    var name by remember(goal) { mutableStateOf(goal?.name ?: "") }
    var targetAmountCents by remember(goal) { mutableStateOf(goal?.targetAmountCents ?: 0L) }
    var currentAmountCents by remember(goal) { mutableStateOf(goal?.currentAmountCents ?: 0L) }
    var targetDate by remember(goal) {
        val parsed = goal?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed)
    }
    var linkedAccountId by remember(goal) { mutableStateOf(goal?.linkedAccountId) }
    var accountDropdownOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (isEditing) "Edit Savings Goal" else "New Savings Goal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Goal Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal Name") },
                placeholder = { Text("e.g. Emergency Fund, Vacation, Down Payment") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Target Amount
            CurrencyTextField(
                cents = targetAmountCents,
                onCentsChange = { targetAmountCents = it },
                label = "Target Goal Amount"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Current Saved Amount
            CurrencyTextField(
                cents = currentAmountCents,
                onCentsChange = { currentAmountCents = it },
                label = "Current Saved Amount"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Target Completion Date
            val displayDate = targetDate ?: LocalDateUtils.todayLocalDate()
            DateFieldButton(
                date = displayDate,
                onDateSelected = { targetDate = it },
                label = "Target Date (Optional)"
            )

            if (targetDate != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { targetDate = null }) {
                        Text("Clear Target Date")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Linked Account Picker
            val linkedAccount = accounts.find { it.id == linkedAccountId }
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { accountDropdownOpen = true },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(text = "Linked Bank Account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = linkedAccount?.name ?: "None / Unlinked",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                DropdownMenu(
                    expanded = accountDropdownOpen,
                    onDismissRequest = { accountDropdownOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None / Unlinked") },
                        onClick = {
                            linkedAccountId = null
                            accountDropdownOpen = false
                        }
                    )
                    accounts.forEach { acct ->
                        DropdownMenuItem(
                            text = { Text("${acct.name} (${acct.type.name.lowercase().replaceFirstChar { it.uppercase() }})") },
                            onClick = {
                                linkedAccountId = acct.id
                                accountDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        val updated = (goal ?: Goal(
                            name = name,
                            targetAmountCents = targetAmountCents,
                            currentAmountCents = currentAmountCents
                        )).copy(
                            name = name.trim(),
                            targetAmountCents = targetAmountCents,
                            currentAmountCents = currentAmountCents,
                            targetDate = targetDate?.toString(),
                            linkedAccountId = linkedAccountId
                        )
                        onSave(updated)
                    },
                    enabled = name.isNotBlank() && targetAmountCents > 0L,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        haptics.reject()
                        onDelete(goal.id)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Goal", modifier = Modifier.size(16.dp))
                    Text("Delete Goal")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
