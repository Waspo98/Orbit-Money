package app.overbay.orbit.ui.transactions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.components.OrbitSwitchGroupCard
import app.overbay.orbit.ui.components.OrbitSwitchRow

@Composable
fun TransactionOptionsBlock(
    isTransfer: Boolean,
    onTransferChange: (Boolean) -> Unit,
    mhaEligible: Boolean,
    onMhaEligibleChange: (Boolean) -> Unit,
    onCreateRule: (() -> Unit)? = null,
    onMarkRecurring: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OrbitSwitchGroupCard {
            OrbitSwitchRow(
                title = "Transfer",
                subtitle = "Exclude from spending calculations",
                checked = isTransfer,
                onCheckedChange = onTransferChange,
                showContainer = false
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            )

            OrbitSwitchRow(
                title = "MHA Eligible",
                subtitle = "Include in Military Housing Allowance calculations",
                checked = mhaEligible,
                onCheckedChange = onMhaEligibleChange,
                showContainer = false
            )
        }

        if (onCreateRule != null || onMarkRecurring != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onCreateRule != null) {
                    OutlinedButton(
                        onClick = onCreateRule,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create Rule")
                    }
                }
                if (onMarkRecurring != null) {
                    OutlinedButton(
                        onClick = onMarkRecurring,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mark Recurring")
                    }
                }
            }
        }
    }
}
