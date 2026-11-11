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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.GOAL_PRESETS
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.GoalAllocationEngine
import app.overbay.orbit.domain.GoalAllocationEngine.AllocationConflict
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.goals.components.GoalAllocationRowEditor
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.min

@Composable
fun GoalEditorSheet(
    goal: Goal?,
    accounts: List<Account>,
    existingGoals: List<Goal> = emptyList(),
    onSave: (Goal, Boolean) -> Unit,
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
            existingGoals = existingGoals,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss,
            onOpenLinkedAccount = onOpenLinkedAccount
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorContent(
    goal: Goal?,
    accounts: List<Account>,
    existingGoals: List<Goal> = emptyList(),
    onSave: (Goal, Boolean) -> Unit,
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
    var isOngoing by remember(goal) { mutableStateOf(goal != null && goal.targetDate == null) }
    var targetDate by remember(goal) {
        val parsed = goal?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        mutableStateOf(parsed)
    }
    var allocations by remember(goal) { mutableStateOf(goal?.allocations ?: emptyList()) }
    var notes by remember(goal) { mutableStateOf(goal?.notes ?: "") }
    var conflicts by remember { mutableStateOf<List<AllocationConflict>>(emptyList()) }
    var pendingGoalToSave by remember { mutableStateOf<Goal?>(null) }

    val totalAllocatedCents = remember(allocations, accounts) {
        val accountMap = accounts.associateBy { it.id }
        allocations.sumOf { alloc ->
            val acct = accountMap[alloc.accountId]
            if (acct != null) {
                val allocatable = max(0L, acct.currentBalanceCents - alloc.reserveAmountCents)
                if (alloc.allocationType == GoalAllocationType.PERCENT) {
                    MoneyUtils.multiplyPercent(allocatable, alloc.allocationPercent)
                } else {
                    min(allocatable, alloc.allocationAmountCents)
                }
            } else 0L
        }
    }

    if (conflicts.isNotEmpty() && pendingGoalToSave != null) {
        GoalConflictDialog(
            conflicts = conflicts,
            onConfirmSteal = {
                val g = pendingGoalToSave!!
                conflicts = emptyList()
                pendingGoalToSave = null
                onSave(g, true)
            },
            onDismiss = {
                conflicts = emptyList()
                pendingGoalToSave = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Row
        OrbitSheetHeader(
            title = if (isEditing) "Edit Savings Goal" else "New Savings Goal",
            onDismiss = onDismiss,
            trailingActions = {
                if (goal != null && goal.id > 0L) {
                    OrbitActionChip(
                        label = "Delete",
                        leadingIcon = Icons.Default.Delete,
                        isDestructive = true,
                        onClick = {
                            haptics.reject()
                            onDelete(goal.id)
                        }
                    )
                }
            }
        )

        // Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GOAL_PRESETS.forEach { preset ->
                val isSelected = selectedKind == preset.kind
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        haptics.tick()
                        selectedKind = preset.kind
                        selectedIcon = preset.emoji
                        if (preset.kind != "custom" && (name.isBlank() || GOAL_PRESETS.any { it.label == name })) {
                            name = preset.label
                        }
                    },
                    leadingIcon = { Text(text = preset.emoji, style = MaterialTheme.typography.bodyMedium) },
                    label = {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        // Emoji & Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = selectedIcon,
                onValueChange = { selectedIcon = it.take(16) },
                label = { Text("Emoji") },
                modifier = Modifier.width(84.dp)
            )
            OrbitTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal Name") },
                placeholder = { Text("e.g. Emergency Fund, New Car") },
                modifier = Modifier.weight(1f)
            )
        }

        // Target Amount & Read-Only Allocated Amount Summary Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurrencyTextField(
                cents = targetAmountCents,
                onCentsChange = { targetAmountCents = it },
                label = "Target Goal",
                modifier = Modifier.weight(1.1f)
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.weight(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "TOTAL ALLOCATED",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = MoneyUtils.formatCents(totalAllocatedCents, showCents = false),
                        style = TabularNumStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Target Date Mode Toggle: Target Date vs Ongoing
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isOngoing,
                    onClick = {
                        haptics.tick()
                        isOngoing = false
                        if (targetDate == null) targetDate = LocalDateUtils.todayLocalDate()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                    label = { Text("Target Date", style = MaterialTheme.typography.labelMedium) }
                )
                SegmentedButton(
                    selected = isOngoing,
                    onClick = {
                        haptics.tick()
                        isOngoing = true
                        targetDate = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                    label = { Text("Ongoing / Open-Ended", style = MaterialTheme.typography.labelMedium) }
                )
            }

            if (!isOngoing) {
                val displayDate = targetDate ?: LocalDateUtils.todayLocalDate()
                DateFieldButton(
                    date = displayDate,
                    onDateSelected = { targetDate = it },
                    label = "Target Date"
                )
            }
        }

        // Notes Field
        OrbitTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes & Intentions") },
            placeholder = { Text("Add notes, milestones, or rules for this goal...") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        // Multi-Account Allocations Row Editor
        GoalAllocationRowEditor(
            allocations = allocations,
            accounts = accounts,
            existingGoals = existingGoals,
            onAllocationsChange = { allocations = it }
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

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
                    haptics.confirm()
                    val targetDateIso = if (!isOngoing) targetDate?.toString() else null
                    val currentSaved = if (allocations.isNotEmpty()) totalAllocatedCents else (goal?.currentAmountCents ?: 0L)
                    val updated = (goal ?: Goal(
                        name = name,
                        targetAmountCents = targetAmountCents,
                        currentAmountCents = currentSaved
                    )).copy(
                        name = name.trim(),
                        kind = selectedKind,
                        icon = selectedIcon.trim().ifBlank { "🎯" },
                        targetAmountCents = targetAmountCents,
                        currentAmountCents = currentSaved,
                        targetDate = targetDateIso,
                        allocations = allocations,
                        notes = notes.trim().ifBlank { null }
                    )

                    val detected = GoalAllocationEngine.detectConflicts(updated, existingGoals, accounts)
                    if (detected.isNotEmpty()) {
                        pendingGoalToSave = updated
                        conflicts = detected
                    } else {
                        onSave(updated, false)
                    }
                },
                enabled = name.isNotBlank() && targetAmountCents > 0L,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Save Goal", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
