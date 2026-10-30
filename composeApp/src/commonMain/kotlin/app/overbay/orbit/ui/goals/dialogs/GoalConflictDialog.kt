package app.overbay.orbit.ui.goals.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.GoalAllocationEngine.AllocationConflict
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * M3E confirmation dialog prompt shown when an account's total goal allocations exceed 100% or allocatable funds.
 * Allows household owners to rebalance by reducing allocations on other goals, or cancel to adjust allocations.
 * Strictly <= 390 lines.
 */
@Composable
fun GoalConflictDialog(
    conflicts: List<AllocationConflict>,
    onConfirmSteal: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Account Over-Allocation Detected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Saving this goal will exceed available funds or 100% allocation on the following account(s):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                conflicts.forEach { conflict ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "• ${conflict.accountName}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val otherNames = conflict.conflictingGoals.joinToString(", ") { it.name }
                        Text(
                            text = "Other goals linked: $otherNames",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (conflict.totalPercent > 100.0) {
                            Text(
                                text = "Total combined percentage: ${conflict.totalPercent.toInt()}% (> 100%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.danger
                            )
                        } else if (conflict.totalAllocatedCents > conflict.allocatableCents) {
                            Text(
                                text = "Total allocated: ${MoneyUtils.formatCents(conflict.totalAllocatedCents)} (Available: ${MoneyUtils.formatCents(conflict.allocatableCents)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.danger
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Would you like to prioritize this goal and reduce allocations on the other goals automatically?",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirmSteal()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reduce Other Goals & Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Adjust Allocations")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
