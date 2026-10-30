package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Editorial modal dialog for adding a dated BalanceRecord snapshot to an account.
 * Features DateFieldButton (defaults to today), CurrencyTextField, and a 25% delta
 * confirmation warning banner when new balance sharply deviates from current balance.
 * Strictly under the 390-line ceiling.
 */
@Composable
fun AddBalanceRecordDialog(
    account: Account,
    onAddRecord: (date: String, balanceCents: Cents) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var date by remember { mutableStateOf(LocalDateUtils.todayLocalDate()) }
    var balanceCents by remember { mutableStateOf(account.currentBalanceCents) }
    var confirmedLargeDelta by remember { mutableStateOf(false) }

    val current = account.currentBalanceCents
    val diff = abs(balanceCents - current)
    val isLargeDelta = if (current != 0L) {
        (diff.toDouble() / abs(current).toDouble()) > 0.25
    } else false
    val deltaPercent = if (current != 0L) {
        ((diff.toDouble() / abs(current).toDouble()) * 100.0).roundToInt()
    } else 0

    val canSave = !isLargeDelta || confirmedLargeDelta

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add Balance Snapshot",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Record a balance snapshot for ${account.name}. Current balance is ${MoneyUtils.formatCents(current)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DateFieldButton(
                date = date,
                onDateSelected = { date = it },
                label = "Snapshot Date"
            )

            CurrencyTextField(
                cents = balanceCents,
                onCentsChange = {
                    balanceCents = it
                    confirmedLargeDelta = false
                },
                label = "Balance on Date",
                allowNegative = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isLargeDelta) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Significant Balance Change ($deltaPercent% Diff)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The entered balance of ${MoneyUtils.formatCents(balanceCents)} differs by $deltaPercent% from current account balance ${MoneyUtils.formatCents(current)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirmedLargeDelta = !confirmedLargeDelta }
                        ) {
                            Checkbox(
                                checked = confirmedLargeDelta,
                                onCheckedChange = { confirmedLargeDelta = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I confirm this balance snapshot is accurate",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                        onAddRecord(LocalDateUtils.formatIsoDate(date), balanceCents)
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Record")
                }
            }
        }
    }
}
