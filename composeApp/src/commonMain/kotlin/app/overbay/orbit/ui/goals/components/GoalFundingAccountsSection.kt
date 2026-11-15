package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.AccountAllocationSummary
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitExpandableChevron
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
private fun rememberGoalSlicePalette(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val orbit = OrbitTheme.colors
    return remember(scheme, orbit) {
        listOf(
            scheme.primary,
            orbit.income,
            orbit.warning,
            scheme.tertiary,
            orbit.transfer,
            scheme.secondary,
            scheme.inversePrimary,
            scheme.outline
        )
    }
}

private fun goalSliceColor(goalId: Long, palette: List<Color>): Color =
    palette[((goalId.hashCode().toLong() and 0x7FFFFFFF) % palette.size).toInt()]

/**
 * Dedicated Funding Accounts tab content splitting actively allocated accounts from
 * collapsed-by-default unallocated asset accounts.
 */
@Composable
fun GoalFundingAccountsSection(
    allocatedSummaries: List<AccountAllocationSummary>,
    unallocatedSummaries: List<AccountAllocationSummary>,
    goals: List<Goal>,
    showDetails: Boolean,
    showUnallocated: Boolean,
    onToggleDetails: () -> Unit,
    onToggleUnallocated: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAllocated = allocatedSummaries.sumOf { it.allocatedAmountCents }
    val totalUnallocated = allocatedSummaries.sumOf { it.remainingAmountCents } +
        unallocatedSummaries.sumOf { it.remainingAmountCents }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "Funded Accounts (${allocatedSummaries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${MoneyUtils.formatCents(totalAllocated, showCents = false)} allocated · ${MoneyUtils.formatCents(totalUnallocated, showCents = false)} unallocated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (allocatedSummaries.isNotEmpty()) {
                OrbitActionChip(
                    label = if (showDetails) "Hide Split" else "Show Split",
                    onClick = onToggleDetails
                )
            }
        }

        if (allocatedSummaries.isEmpty() && unallocatedSummaries.isNotEmpty()) {
            EmptyStateCard(
                title = "No Accounts Allocated Yet",
                description = "Edit a savings goal and link one or more checking, savings, or investment accounts to track allocations here.",
                icon = OrbitIcons.Accounts
            )
        } else {
            allocatedSummaries.forEach { summary ->
                AllocatedAccountRowCard(
                    summary = summary,
                    goals = goals,
                    showDetails = showDetails,
                    onOpenAccount = { onOpenAccount(summary.accountId) }
                )
            }
        }

        if (unallocatedSummaries.isNotEmpty()) {
            val unallocatedBalance = unallocatedSummaries.sumOf { it.currentBalanceCents }
            OrbitSectionCard(
                title = "Unallocated Accounts",
                subtitle = "${MoneyUtils.formatCents(unallocatedBalance, showCents = false)} available across ${unallocatedSummaries.size} accounts",
                headerBadge = {
                    OrbitBadge(
                        text = unallocatedSummaries.size.toString(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                action = {
                    OrbitExpandableChevron(
                        expanded = showUnallocated,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                showHeaderDivider = showUnallocated,
                onCardClick = onToggleUnallocated
            ) {
                OrbitAccordionVisibility(visible = showUnallocated) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        unallocatedSummaries.forEachIndexed { idx, acct ->
                            OrbitCompactListRow(
                                title = acct.accountName,
                                subtitle = acct.accountType.displayName,
                                amount = MoneyUtils.formatCents(acct.currentBalanceCents),
                                amountDetail = "0% allocated",
                                showTopDivider = idx > 0,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { onOpenAccount(acct.accountId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllocatedAccountRowCard(
    summary: AccountAllocationSummary,
    goals: List<Goal>,
    showDetails: Boolean,
    onOpenAccount: () -> Unit
) {
    val colors = OrbitTheme.colors
    val haptics = LocalOrbitHaptics.current
    val palette = rememberGoalSlicePalette()
    val linkedGoals = remember(goals, summary.accountId) {
        goals.filter { g -> g.allocations.any { it.accountId == summary.accountId } }
    }
    val goalAllocations = remember(linkedGoals, summary, palette) {
        linkedGoals.mapNotNull { g ->
            val alloc = g.allocations.firstOrNull { it.accountId == summary.accountId } ?: return@mapNotNull null
            val amt = if (alloc.allocationType == GoalAllocationType.PERCENT) {
                MoneyUtils.multiplyPercent(summary.allocatableAmountCents, alloc.allocationPercent)
            } else {
                alloc.allocationAmountCents
            }
            Triple(g, amt, goalSliceColor(g.id, palette))
        }
    }

    val statusColor = when {
        summary.isOverAllocated -> colors.danger
        summary.allocatedPercent >= 99.9 -> colors.income
        summary.allocatedPercent > 0.0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = if (summary.isOverAllocated) {
        "Over-allocated"
    } else {
        "${summary.allocatedPercent.roundToInt()}% allocated"
    }

    Surface(
        onClick = {
            haptics.tick()
            onOpenAccount()
        },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .orbitTactilePress(pressedScale = 0.985f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = summary.accountName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary.accountType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyUtils.formatCents(summary.currentBalanceCents),
                        style = TabularNumStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusLabel,
                        style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = statusColor
                    )
                }
            }

            val basis = max(1L, summary.currentBalanceCents).toFloat()
            OrbitStackedSegmentBar(
                segments = goalAllocations.map { (goal, amt, color) ->
                    OrbitBarSegment(
                        key = goal.id,
                        fraction = (amt.toFloat() / basis).coerceAtLeast(0f),
                        color = color
                    )
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            if (goalAllocations.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    goalAllocations.forEach { (goal, amt, color) ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = color.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                                Text(
                                    text = "${goal.name}: ${formatCurrencySmart(amt)}",
                                    style = TabularNumStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Allocated: ${MoneyUtils.formatCents(summary.allocatedAmountCents)}",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Unallocated: ${MoneyUtils.formatCents(summary.remainingAmountCents)}",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OrbitAccordionVisibility(visible = showDetails) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                    if (summary.reserveAmountCents > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Protected Reserve", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = MoneyUtils.formatCents(summary.reserveAmountCents),
                                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    linkedGoals.forEach { goal ->
                        val alloc = goal.allocations.firstOrNull { it.accountId == summary.accountId }
                        val allocText = if (alloc != null) {
                            if (alloc.allocationType == GoalAllocationType.PERCENT) {
                                val resolved = MoneyUtils.multiplyPercent(summary.allocatableAmountCents, alloc.allocationPercent)
                                "${alloc.allocationPercent.roundToInt()}% (${MoneyUtils.formatCents(resolved)})"
                            } else {
                                MoneyUtils.formatCents(alloc.allocationAmountCents)
                            }
                        } else ""

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Text(text = goal.resolvedEmoji, fontSize = 13.sp)
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = allocText,
                                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
