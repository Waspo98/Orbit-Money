package app.overbay.orbit.ui.goals.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.AccountAllocationSummary
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

private val GoalPalette = listOf(
    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6),
    Color(0xFFEC4899), Color(0xFF06B6D4), Color(0xFFF97316), Color(0xFF14B8A6)
)

private fun goalSliceColor(goalId: Long): Color =
    GoalPalette[((goalId.hashCode().toLong() and 0x7FFFFFFF) % GoalPalette.size).toInt()]

@Composable
fun GoalAccountsOverviewCard(
    accountSummaries: List<AccountAllocationSummary>,
    goals: List<Goal>,
    onOpenAccount: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (accountSummaries.isEmpty()) return
    val colors = OrbitTheme.colors
    var isExpanded by remember { mutableStateOf(false) }
    val haptics = LocalOrbitHaptics.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { haptics.tick(); isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACCOUNT ALLOCATION OVERVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${accountSummaries.size} asset account${if (accountSummaries.size == 1) "" else "s"} connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onOpenAccount != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { haptics.tick(); onOpenAccount(0L) }
                        ) {
                            Text(
                                text = "View Accounts →",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            text = if (isExpanded) "Hide Details ▲" else "View Details ▼",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            accountSummaries.forEach { summary ->
                AccountSummaryRow(summary, goals, isExpanded, onOpenAccount)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSummaryRow(
    summary: AccountAllocationSummary,
    goals: List<Goal>,
    isDetailsVisible: Boolean,
    onOpenAccount: ((Long) -> Unit)?
) {
    val colors = OrbitTheme.colors
    val haptics = LocalOrbitHaptics.current
    val linkedGoals = remember(goals, summary.accountId) {
        goals.filter { g -> g.allocations.any { it.accountId == summary.accountId } }
    }
    val goalAllocations = remember(linkedGoals, summary) {
        linkedGoals.mapNotNull { g ->
            val alloc = g.allocations.firstOrNull { it.accountId == summary.accountId } ?: return@mapNotNull null
            val amt = if (alloc.allocationType == GoalAllocationType.PERCENT) {
                ((summary.allocatableAmountCents * alloc.allocationPercent) / 100.0).toLong()
            } else {
                alloc.allocationAmountCents
            }
            Triple(g, amt, goalSliceColor(g.id))
        }
    }

    Surface(
        onClick = {
            haptics.tick()
            onOpenAccount?.invoke(summary.accountId)
        },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account name & Total Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.accountName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summary.accountType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyUtils.formatCents(summary.currentBalanceCents),
                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${summary.allocatedPercent.toInt()}% allocated",
                        style = TabularNumStyle.copy(fontSize = 11.sp),
                        color = if (summary.allocatedPercent >= 100.0) colors.danger else colors.income
                    )
                }
            }

            // Multi-Segmented Allocation Bar
            MultiSegmentedAllocationBar(
                totalBalanceCents = summary.currentBalanceCents,
                allocations = goalAllocations
            )

            // Colored Legend Chips Below Bar
            if (goalAllocations.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    goalAllocations.forEach { (goal, amt, color) ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = color.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = "${goal.name}: ${MoneyUtils.formatCents(amt, showCents = false)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Allocated vs Remaining Unallocated Subtext
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Allocated: ${MoneyUtils.formatCents(summary.allocatedAmountCents)}",
                    style = TabularNumStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Unallocated: ${MoneyUtils.formatCents(summary.remainingAmountCents)}",
                    style = TabularNumStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Detailed Goals Breakdown
            AnimatedVisibility(visible = isDetailsVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                    if (summary.reserveAmountCents > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🛡️ Protected Reserve",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = MoneyUtils.formatCents(summary.reserveAmountCents),
                                style = TabularNumStyle.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (linkedGoals.isEmpty()) {
                        Text(
                            text = "No goals currently funded from this account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        linkedGoals.forEach { goal ->
                            val alloc = goal.allocations.firstOrNull { it.accountId == summary.accountId }
                            val allocText = if (alloc != null) {
                                if (alloc.allocationType == GoalAllocationType.PERCENT) {
                                    "${alloc.allocationPercent.toInt()}% (${MoneyUtils.formatCents(((summary.allocatableAmountCents * alloc.allocationPercent) / 100.0).toLong())})"
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = goal.resolvedEmoji, fontSize = 13.sp)
                                    Text(
                                        text = goal.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
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
}

@Composable
private fun MultiSegmentedAllocationBar(
    totalBalanceCents: Cents,
    allocations: List<Triple<Goal, Cents, Color>>,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val basis = max(1L, totalBalanceCents).toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
    ) {
        val w = size.width
        val h = size.height

        drawRect(color = trackColor, size = size)

        var currentX = 0f
        allocations.forEach { (_, amt, color) ->
            val sliceWidth = ((amt.toFloat() / basis) * w).coerceAtLeast(0f)
            if (sliceWidth > 0f) {
                drawRect(
                    color = color,
                    topLeft = Offset(currentX, 0f),
                    size = Size(sliceWidth.coerceAtMost(w - currentX), h)
                )
                currentX += sliceWidth
            }
        }
    }
}
