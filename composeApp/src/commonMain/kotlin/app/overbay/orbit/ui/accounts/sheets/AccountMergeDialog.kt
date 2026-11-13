package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalSheet

@Composable
fun AccountMergeDialog(
    sourceAccount: Account,
    availableAccounts: List<Account>,
    sourceTransactionCount: Int = 0,
    getTransactionCount: (Long) -> Int = { 0 },
    onMerge: (sourceId: Long, targetId: Long, combineBalances: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eligibleTargets = availableAccounts.filter { it.id != sourceAccount.id && !it.isArchived }

    var selectedTarget by remember { mutableStateOf(eligibleTargets.firstOrNull()) }
    var combineBalances by remember { mutableStateOf(true) }

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Merge Accounts",
        onConfirm = {
            selectedTarget?.let { target ->
                onMerge(sourceAccount.id, target.id, combineBalances)
            }
        },
        modifier = modifier,
        title = "Merge Account",
        confirmEnabled = selectedTarget != null,
        bodySpacing = 14.dp
    ) {
        Text(
            text = "Merge \"${sourceAccount.name}\" into another account. All $sourceTransactionCount transactions will be reassigned safely to the target account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // SimpleFIN recreation warning banner
        if (!sourceAccount.simplefinAccountId.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Warning: \"${sourceAccount.name}\" is linked to SimpleFIN. Future SimpleFIN syncs may recreate it unless you unlink it first or configure your cutover date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (eligibleTargets.isEmpty()) {
            Text(
                text = "No other eligible accounts available to merge into.",
                style = MaterialTheme.typography.bodyMedium,
                color = OrbitColorTokens.Red
            )
        } else {
            OrbitDropdownField(
                label = "Merge Into (Target Account)",
                selectedText = selectedTarget?.name ?: "Select Target Account",
                options = eligibleTargets,
                optionLabel = { "${it.name} (${it.type.displayName})" },
                onOptionSelected = { selectedTarget = it }
            )

            // Projected Target Impact Card
            OrbitAccordionVisibility(visible = selectedTarget != null) {
                selectedTarget?.let { target ->
                    val targetTxns = getTransactionCount(target.id)
                    val projectedTxns = targetTxns + sourceTransactionCount
                    val projectedBalance = if (combineBalances) target.currentBalanceCents + sourceAccount.currentBalanceCents else target.currentBalanceCents

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "PROJECTED OUTCOME FOR ${target.name.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Transactions:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$targetTxns → $projectedTxns", style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Projected Balance:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${MoneyUtils.formatCents(target.currentBalanceCents)} → ${MoneyUtils.formatCents(projectedBalance)}",
                                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { combineBalances = !combineBalances }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = combineBalances,
                    onCheckedChange = { combineBalances = it }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Combine account balances",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Add source balance (${MoneyUtils.formatCents(sourceAccount.currentBalanceCents)}) to target account balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
