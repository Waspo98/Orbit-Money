package app.overbay.orbit.ui.trends.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CategoryBudgetEditorModal(
    category: Category,
    existingBudget: CategoryBudget?,
    averageSpentCents: Cents = 0L,
    onSave: (amountCents: Cents, rollover: Boolean) -> Unit,
    onDelete: (budgetId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var amountCents by remember { mutableLongStateOf(existingBudget?.amountCents ?: 0L) }
    var rollover by remember { mutableStateOf(existingBudget?.rollover ?: false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Save",
        onConfirm = {
            haptics.confirm()
            onSave(amountCents, rollover)
        },
        modifier = modifier,
        title = if (existingBudget != null) "Edit ${category.name} Budget" else "Set ${category.name} Budget",
        headerTrailingActions = if (existingBudget != null) {
            {
                OrbitActionChip(
                    label = "Delete",
                    onClick = {
                        haptics.tick()
                        showDeleteConfirmation = true
                    },
                    icon = Icons.Default.Delete,
                    isDestructive = true
                )
            }
        } else null,
        bodySpacing = 0.dp
    ) {
        CurrencyTextField(
            cents = amountCents,
            onCentsChange = { amountCents = it },
            label = "Monthly Budget Limit",
            modifier = Modifier.fillMaxWidth()
        )

        if (averageSpentCents > 0L) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${MoneyUtils.formatCents(averageSpentCents)} average over this range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OrbitSwitchRow(
            title = "Rollover Unused Funds",
            subtitle = "Carry over surpluses or deficits into the next month",
            checked = rollover,
            onCheckedChange = { rollover = it }
        )
    }

    if (showDeleteConfirmation && existingBudget != null) {
        OrbitConfirmationDialog(
            title = "Delete Budget",
            message = "Are you sure you want to remove the budget for ${category.name}?",
            confirmText = "Delete",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirmation = false
                haptics.confirm()
                onDelete(existingBudget.id)
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}
