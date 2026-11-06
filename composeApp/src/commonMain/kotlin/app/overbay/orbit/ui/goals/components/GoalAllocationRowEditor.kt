package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.domain.GoalAllocationEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Multi-account allocation rows editor allowing household owners to assign savings percentages
 * or fixed dollar amounts across eligible asset accounts with reserve protection.
 *
 */
@Composable
fun GoalAllocationRowEditor(
    allocations: List<GoalAllocation>,
    accounts: List<Account>,
    onAllocationsChange: (List<GoalAllocation>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val eligibleAccounts = remember(accounts) {
        accounts.filter { GoalAllocationEngine.isAccountEligible(it) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACCOUNT ALLOCATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Fund this goal from one or multiple asset accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (eligibleAccounts.isNotEmpty()) {
                OrbitActionChip(
                    label = "+ Add Account",
                    leadingIcon = Icons.Default.Add,
                    onClick = {
                        haptics.tick()
                        val unused = eligibleAccounts.firstOrNull { acct ->
                            allocations.none { it.accountId == acct.id }
                        } ?: eligibleAccounts.first()
                        val newAlloc = GoalAllocation(
                            goalId = 0L,
                            accountId = unused.id,
                            accountName = unused.name,
                            allocationType = GoalAllocationType.PERCENT,
                            allocationPercent = if (allocations.isEmpty()) 100.0 else 50.0,
                            allocationAmountCents = 0L,
                            reserveAmountCents = 0L
                        )
                        onAllocationsChange(allocations + newAlloc)
                    }
                )
            }
        }

        if (allocations.isEmpty()) {
            EmptyStateCard(
                title = "No Accounts Linked Yet",
                description = "Tap '+ Add Account' above to allocate savings from your checking or savings accounts."
            )
        } else {
            allocations.forEachIndexed { index, alloc ->
                SingleAllocationCard(
                    allocation = alloc,
                    eligibleAccounts = eligibleAccounts,
                    onChange = { updated ->
                        val list = allocations.toMutableList()
                        list[index] = updated
                        onAllocationsChange(list)
                    },
                    onDelete = {
                        haptics.reject()
                        val list = allocations.toMutableList()
                        list.removeAt(index)
                        onAllocationsChange(list)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleAllocationCard(
    allocation: GoalAllocation,
    eligibleAccounts: List<Account>,
    onChange: (GoalAllocation) -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val currentAccount = eligibleAccounts.find { it.id == allocation.accountId }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Account Selector & Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OrbitDropdownField(
                    label = "Source Account",
                    selectedText = currentAccount?.name ?: allocation.accountName.ifBlank { "Select Account" },
                    options = eligibleAccounts,
                    optionLabel = { "${it.name} (${it.type.displayName} · ${MoneyUtils.formatCents(it.currentBalanceCents)})" },
                    onOptionSelected = { acct ->
                        onChange(allocation.copy(accountId = acct.id, accountName = acct.name))
                    },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Allocation",
                        tint = OrbitTheme.colors.danger
                    )
                }
            }

            // Allocation Type Toggle (% Percent vs $ Fixed)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = allocation.allocationType == GoalAllocationType.PERCENT,
                    onClick = {
                        haptics.tick()
                        onChange(allocation.copy(allocationType = GoalAllocationType.PERCENT))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                    label = { Text("% Percent", style = MaterialTheme.typography.labelMedium) }
                )
                SegmentedButton(
                    selected = allocation.allocationType == GoalAllocationType.FIXED,
                    onClick = {
                        haptics.tick()
                        onChange(allocation.copy(allocationType = GoalAllocationType.FIXED))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                    label = { Text("$ Fixed", style = MaterialTheme.typography.labelMedium) }
                )
            }

            // Percent or Amount input
            if (allocation.allocationType == GoalAllocationType.PERCENT) {
                OrbitTextField(
                    value = if (allocation.allocationPercent > 0.0) allocation.allocationPercent.toString() else "",
                    onValueChange = { str ->
                        val clean = str.filter { it.isDigit() || it == '.' }
                        val dbl = clean.toDoubleOrNull() ?: 0.0
                        onChange(allocation.copy(allocationPercent = dbl.coerceIn(0.0, 100.0)))
                    },
                    label = { Text("Percent of Allocatable Balance (0-100%)") },
                    placeholder = { Text("e.g. 50") }
                )
            } else {
                CurrencyTextField(
                    cents = allocation.allocationAmountCents,
                    onCentsChange = { onChange(allocation.copy(allocationAmountCents = it)) },
                    label = "Fixed Allocation Amount"
                )
            }

            // Account Reserve Input
            CurrencyTextField(
                cents = allocation.reserveAmountCents,
                onCentsChange = { onChange(allocation.copy(reserveAmountCents = it)) },
                label = "Account Reserve (Excluded from Goal)"
            )
        }
    }
}
