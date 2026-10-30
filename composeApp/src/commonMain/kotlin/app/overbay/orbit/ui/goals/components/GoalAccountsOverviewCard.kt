package app.overbay.orbit.ui.goals.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.AccountAllocationSummary
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Account Overview Card showing how each savings, checking, or cash account is divided across
 * active goals vs unallocated cash and reserve protection.
 * Strictly <= 390 lines.
 */
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.tick()
                        isExpanded = !isExpanded
                    },
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

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (isExpanded) "Hide Details ▲" else "View Details ▼",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            accountSummaries.forEach { summary ->
                AccountSummaryRow(
                    summary = summary,
                    goals = goals,
                    isDetailsVisible = isExpanded,
                    onOpenAccount = onOpenAccount
                )
            }
        }
    }
}

@Composable
private fun AccountSummaryRow(
    summary: AccountAllocationSummary,
    goals: List<Goal>,
    isDetailsVisible: Boolean,
    onOpenAccount: ((Long) -> Unit)?
) {
    val colors = OrbitTheme.colors
    val linkedGoals = remember(goals, summary.accountId) {
        goals.filter { g -> g.allocations.any { it.accountId == summary.accountId } }
    }

    Surface(
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

            // Allocation Progress Bar
            OrbitPillProgressBar(
                percent = (summary.allocatedPercent / 100.0).toFloat().coerceIn(0f, 1f),
                overBudget = summary.allocatedPercent > 100.0,
                customFillColor = if (summary.allocatedPercent > 100.0) colors.danger else MaterialTheme.colorScheme.primary,
                height = 6.dp
            )

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
