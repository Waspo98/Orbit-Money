package app.overbay.orbit.ui.goals.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
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
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.domain.GoalAllocationEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitAnimatedEntryColumn
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.ui.components.rememberAnimatedExternalEntries
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-account allocation rows editor allowing household owners to assign savings percentages
 * or fixed dollar amounts across eligible asset accounts with reserve protection.
 *
 */
@Composable
fun GoalAllocationRowEditor(
    allocations: List<GoalAllocation>,
    accounts: List<Account>,
    existingGoals: List<Goal> = emptyList(),
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

        val keyedAllocations = remember(allocations) {
            val seenCounts = mutableMapOf<Long, Int>()
            allocations.map { alloc ->
                val count = seenCounts[alloc.accountId] ?: 0
                seenCounts[alloc.accountId] = count + 1
                "${alloc.accountId}#$count" to alloc
            }
        }
        val (allocationEntries, onAllocationRemoveFinished) = rememberAnimatedExternalEntries(
            items = keyedAllocations,
            itemKey = { it.first }
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitAccordionVisibility(visible = allocations.isEmpty()) {
                EmptyStateCard(
                    title = "No Accounts Linked Yet",
                    description = "Tap '+ Add Account' above to allocate savings from your checking or savings accounts."
                )
            }
            OrbitAnimatedEntryColumn(
                entries = allocationEntries,
                onRemoveFinished = onAllocationRemoveFinished,
                minActiveCount = 0,
                itemSpacing = 12.dp,
                bringIntoViewOnAdd = false
            ) { entry, displayIndex, _ ->
                SingleAllocationCard(
                    allocation = entry.value.second,
                    eligibleAccounts = eligibleAccounts,
                    existingGoals = existingGoals,
                    onChange = { updated ->
                        if (displayIndex < allocations.size) {
                            val list = allocations.toMutableList()
                            list[displayIndex] = updated
                            onAllocationsChange(list)
                        }
                    },
                    onDelete = {
                        haptics.reject()
                        if (displayIndex < allocations.size) {
                            val list = allocations.toMutableList()
                            list.removeAt(displayIndex)
                            onAllocationsChange(list)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SingleAllocationCard(
    allocation: GoalAllocation,
    eligibleAccounts: List<Account>,
    existingGoals: List<Goal>,
    onChange: (GoalAllocation) -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val currentAccount = eligibleAccounts.find { it.id == allocation.accountId }
    val currentBalance = currentAccount?.currentBalanceCents ?: 0L
    val allocatable = max(0L, currentBalance - allocation.reserveAmountCents)

    val otherGoalsOnAccount = remember(existingGoals, allocation.goalId, currentAccount) {
        if (currentAccount == null) emptyList()
        else existingGoals
            .filter { it.id != allocation.goalId && it.id > 0L }
            .mapNotNull { g ->
                val match = g.allocations.firstOrNull { it.accountId == currentAccount.id }
                if (match != null) g.name to match else null
            }
    }

    val otherAllocatedCents = remember(otherGoalsOnAccount, allocatable) {
        otherGoalsOnAccount.sumOf { (_, alloc) ->
            if (alloc.allocationType == GoalAllocationType.PERCENT) {
                MoneyUtils.multiplyPercent(allocatable, alloc.allocationPercent)
            } else {
                min(allocatable, alloc.allocationAmountCents)
            }
        }
    }

    val openBalanceCents = max(0L, allocatable - otherAllocatedCents)

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

            // Open Balance Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Balance: ${MoneyUtils.formatCents(openBalanceCents, showCents = false)} (of ${MoneyUtils.formatCents(currentBalance, showCents = false)})",
                    style = TabularNumStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Chips for other goals already allocated on this account
            if (otherGoalsOnAccount.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Other goals on this account:",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        otherGoalsOnAccount.forEach { (name, otherAlloc) ->
                            val desc = if (otherAlloc.allocationType == GoalAllocationType.PERCENT) {
                                "${otherAlloc.allocationPercent.toInt()}%"
                            } else {
                                MoneyUtils.formatCents(otherAlloc.allocationAmountCents, showCents = false)
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "$name: $desc",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
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

            // Percent or Amount input with Interactive Slider
            AnimatedContent(
                targetState = allocation.allocationType,
                transitionSpec = { orbitMorphContentTransform(clip = false) },
                modifier = Modifier.fillMaxWidth()
            ) { allocType ->
                if (allocType == GoalAllocationType.PERCENT) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Allocation Percent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${allocation.allocationPercent.toInt()}%", style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = allocation.allocationPercent.toFloat(),
                            onValueChange = { onChange(allocation.copy(allocationPercent = it.toDouble())) },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    val maxOpenDollars = max(10f, (openBalanceCents / 100L).toFloat())
                    val currentDollars = (allocation.allocationAmountCents / 100L).toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Fixed Allocation Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyUtils.formatCents(allocation.allocationAmountCents, showCents = false), style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = currentDollars.coerceIn(0f, maxOpenDollars),
                            onValueChange = { onChange(allocation.copy(allocationAmountCents = it.toLong() * 100L)) },
                            valueRange = 0f..maxOpenDollars,
                            modifier = Modifier.fillMaxWidth()
                        )
                        CurrencyTextField(cents = allocation.allocationAmountCents, onCentsChange = { onChange(allocation.copy(allocationAmountCents = it)) }, label = "Type Exact Amount")
                    }
                }
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
