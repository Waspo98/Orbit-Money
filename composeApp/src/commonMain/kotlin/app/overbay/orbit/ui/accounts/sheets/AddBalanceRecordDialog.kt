package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitModalSheet
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Modal dialog for recording a dated balance update on an account.
 */
@Composable
fun AddBalanceRecordDialog(
    account: Account,
    onAddRecord: (date: String, balanceCents: Cents) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        confirmText = "Save Balance",
        onConfirm = {
            onAddRecord(LocalDateUtils.formatIsoDate(date), balanceCents)
        },
        modifier = modifier,
        title = "Update Balance",
        confirmEnabled = canSave,
        bodySpacing = 14.dp
    ) {
        Text(
            text = "Record a balance entry for ${account.name}. Current balance is ${MoneyUtils.formatCents(current)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DateFieldButton(
            date = date,
            onDateSelected = { date = it },
            label = "Date"
        )

        CurrencyTextField(
            cents = balanceCents,
            onCentsChange = {
                balanceCents = it
                confirmedLargeDelta = false
            },
            label = "Balance",
            allowNegative = true,
            modifier = Modifier.fillMaxWidth()
        )

        OrbitAccordionVisibility(visible = isLargeDelta) {
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
                            text = "Large Balance Change ($deltaPercent%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This entry (${MoneyUtils.formatCents(balanceCents)}) differs by $deltaPercent% from the previous balance (${MoneyUtils.formatCents(current)}).",
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
                            text = "I confirm this balance update is accurate",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
