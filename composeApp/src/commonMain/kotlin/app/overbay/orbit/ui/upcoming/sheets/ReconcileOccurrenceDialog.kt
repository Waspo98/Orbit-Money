package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.runtime.Composable
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
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

    OrbitConfirmationDialog(
        title = if (isExpense) "Mark Bill Paid?" else "Mark Income Received?",
        message = "This will record a reconciled transaction for ${MoneyUtils.formatCents(abs(item.amountCents))} to ${item.name} (Scheduled: ${item.nextDate}) and advance the schedule to its next due date.",
        confirmText = if (isExpense) "Confirm Paid" else "Confirm Received",
        onConfirm = {
            haptics.confirm()
            onConfirm(item.id, item.nextDate)
        },
        onDismiss = onDismiss
    )
}
