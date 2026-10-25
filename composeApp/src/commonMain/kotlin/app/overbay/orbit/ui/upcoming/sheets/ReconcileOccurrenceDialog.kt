package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
fun ReconcileOccurrenceDialog(
    item: UpcomingItem,
    onConfirm: (upcomingId: Long, date: String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val isExpense = item.direction.lowercase() != "income"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isExpense) "Mark Bill Paid?" else "Mark Income Received?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This will record a reconciled transaction for ${MoneyUtils.formatCents(abs(item.amountCents))} to ${item.name} and advance the schedule to its next due date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scheduled Date: ${item.nextDate}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirm(item.id, item.nextDate)
                }
            ) {
                Text(if (isExpense) "Confirm Paid" else "Confirm Received")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
