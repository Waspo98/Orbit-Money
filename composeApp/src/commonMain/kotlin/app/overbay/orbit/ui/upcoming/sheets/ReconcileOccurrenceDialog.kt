package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.UpcomingEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
fun ReconcileOccurrenceDialog(
    item: UpcomingItem,
    recentTransactions: List<Transaction> = emptyList(),
    onMarkPaid: (upcomingId: Long, date: String, matchedTxnId: Long?) -> Unit,
    onSkipMissed: (upcomingId: Long, date: String) -> Unit,
    onResetPending: (upcomingId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val isExpense = item.direction.lowercase() != "income"

    // Find plausible candidate matches from recent posted transactions
    val candidateMatches = remember(item, recentTransactions) {
        val target = item.merchant ?: item.name
        recentTransactions.filter { txn ->
            !txn.effectiveIsIgnored && !txn.effectiveIsTransfer &&
                UpcomingEngine.textMatchScore(target, txn.effectiveMerchant) >= UpcomingEngine.MIN_MERCHANT_SCORE
        }.take(8)
    }

    var selectedTxnId by remember { mutableStateOf<Long?>(candidateMatches.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isExpense) "Reconcile Bill Occurrence" else "Reconcile Income Occurrence",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name.ifBlank { item.merchant ?: "Recurring Item" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Scheduled Due: ${item.nextDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = MoneyUtils.formatCents(abs(item.amountCents)),
                            style = TabularNumStyle.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isExpense) OrbitTheme.colors.expense else OrbitTheme.colors.income
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (candidateMatches.isNotEmpty()) {
                    Text(
                        text = "LINK POSTED TRANSACTION (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val selectedMatch = candidateMatches.find { it.id == selectedTxnId }
                    OrbitDropdownField(
                        label = "Matched Transaction",
                        selectedText = selectedMatch?.let {
                            "${it.effectiveMerchant} · ${it.effectiveDate} (${MoneyUtils.formatCents(abs(it.effectiveAmountCents))})"
                        } ?: "Record New Transaction Automatically",
                        options = candidateMatches,
                        optionLabel = {
                            "${it.effectiveMerchant} · ${it.effectiveDate} (${MoneyUtils.formatCents(abs(it.effectiveAmountCents))})"
                        },
                        onOptionSelected = { selectedTxnId = it.id },
                        nullOptionLabel = "Record New Transaction Automatically",
                        onNullSelected = { selectedTxnId = null }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = "Marking as paid advances the recurring schedule to the next occurrence date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onMarkPaid(item.id, item.nextDate, selectedTxnId)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isExpense) "Mark Paid" else "Mark Received", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onSkipMissed(item.id, item.nextDate)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Skip Occurrence")
                }

                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
