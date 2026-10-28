package app.overbay.orbit.ui.goals.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.GOAL_PRESETS
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

@Composable
fun GoalEditorSheet(
    goal: Goal?,
    accounts: List<Account>,
    onSave: (Goal) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenLinkedAccount: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        GoalEditorContent(
            goal = goal,
            accounts = accounts,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss,
            onOpenLinkedAccount = onOpenLinkedAccount
        )
    }
}

@Composable
fun GoalEditorContent(
    goal: Goal?,
    accounts: List<Account>,
    onSave: (Goal) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenLinkedAccount: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = goal != null && goal.id > 0L

    var name by remember(goal) { mutableStateOf(goal?.name ?: "") }
    var selectedKind by remember(goal) { mutableStateOf(goal?.kind ?: "custom") }
    var selectedIcon by remember(goal) { mutableStateOf(goal?.resolvedEmoji ?: "🎯") }
    var targetAmountCents by remember(goal) { mutableStateOf(goal?.targetAmountCents ?: 0L) }
    var currentAmountCents by remember(goal) { mutableStateOf(goal?.currentAmountCents ?: 0L) }
    var targetDate by remember(goal) {
        val parsed = goal?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed)
    }
    var linkedAccountId by remember(goal) { mutableStateOf(goal?.linkedAccountId) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Savings Goal" else "New Savings Goal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (goal != null && goal.id > 0L) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics.reject()
                            onDelete(goal.id)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Goal",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Goal Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GOAL_PRESETS.forEach { preset ->
                val isSelected = selectedKind == preset.kind
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptics.tick()
                            selectedKind = preset.kind
                            selectedIcon = preset.emoji
                            if (preset.kind != "custom" && (name.isBlank() || GOAL_PRESETS.any { it.label == name })) {
                                name = preset.label
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = preset.emoji, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Goal Emoji + Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = selectedIcon,
                onValueChange = { selectedIcon = it.take(16) },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.width(84.dp)
            )
            OrbitTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal Name") },
                placeholder = { Text("e.g. Emergency Fund, Vacation") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2-Column Bento Row: Target Amount + Current Saved
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurrencyTextField(
                cents = targetAmountCents,
                onCentsChange = { targetAmountCents = it },
                label = "Target Goal",
                modifier = Modifier.weight(1f)
            )

            CurrencyTextField(
                cents = currentAmountCents,
                onCentsChange = { currentAmountCents = it },
                label = "Current Saved",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

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
        OrbitDropdownField(
            label = "Linked Bank Account",
            selectedText = linkedAccount?.name ?: "None / Unlinked",
            options = accounts,
            optionLabel = { "${it.name} (${it.type.name.lowercase().replaceFirstChar { c -> c.uppercase() }})" },
            onOptionSelected = { linkedAccountId = it.id },
            nullOptionLabel = "None / Unlinked",
            onNullSelected = { linkedAccountId = null }
        )

        if (isEditing && linkedAccountId != null && onOpenLinkedAccount != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    onOpenLinkedAccount(linkedAccountId!!)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Linked Account")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons
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
                    haptics.confirm()
                    val updated = (goal ?: Goal(
                        name = name,
                        targetAmountCents = targetAmountCents,
                        currentAmountCents = currentAmountCents
                    )).copy(
                        name = name.trim(),
                        kind = selectedKind,
                        icon = selectedIcon.trim().ifBlank { "🎯" },
                        targetAmountCents = targetAmountCents,
                        currentAmountCents = currentAmountCents,
                        targetDate = targetDate?.toString(),
                        linkedAccountId = linkedAccountId
                    )
                    onSave(updated)
                },
                enabled = name.isNotBlank() && targetAmountCents > 0L,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
